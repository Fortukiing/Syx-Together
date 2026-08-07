package coopmod;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import init.constant.C;
import settlement.entity.ENTITY;
import settlement.entity.animal.Animal;
import settlement.main.SETT;

final class CoopAnimalSync {

	private static final int PACKET_LIMIT = 128;
	private static final int PACKETS_PER_TICK = 4;
	private static final int BACKGROUND_BUDGET = 64;
	private static final ConcurrentHashMap<Integer, AnimalTarget> targets = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Integer, SentState> sentStates = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Field> fields = new ConcurrentHashMap<>();
	private static long sequence;
	private static int visibleCursor;
	private static int backgroundCursor;
	private static long lastCleanupMillis;

	private CoopAnimalSync() {
	}

	static boolean remoteControls(Animal animal, boolean clientMode, boolean connected) {
		if (!clientMode || !connected || animal == null)
			return false;
		AnimalTarget target = targets.get(animal.id());
		return target != null && target.initialized && System.currentTimeMillis() - target.updated <= 5000L;
	}

	static void sendHost(ENTITY[] all, int limit, long now) {
		if (all == null || limit <= 0)
			return;
		try {
			Batch batch = new Batch(now);
			boolean focused = CoopCursor.remoteSettlementViewFresh();
			int totalBudget = PACKET_LIMIT * PACKETS_PER_TICK;
			int visibleBudget = focused ? totalBudget - BACKGROUND_BUDGET : 0;
			int backgroundBudget = focused ? BACKGROUND_BUDGET : totalBudget;

			int scanned = 0;
			while (scanned++ < limit && batch.total < visibleBudget) {
				ENTITY entity = all[nextIndex(true, limit)];
				if (entity instanceof Animal && visible((Animal) entity) && shouldSend((Animal) entity, now, true))
					batch.add((Animal) entity);
			}

			scanned = 0;
			int backgroundSent = 0;
			while (scanned++ < limit && backgroundSent < backgroundBudget && batch.total < totalBudget) {
				ENTITY entity = all[nextIndex(false, limit)];
				if (entity instanceof Animal && shouldSend((Animal) entity, now, false)) {
					batch.add((Animal) entity);
					backgroundSent++;
				}
			}
			batch.flush();
			cleanupHostStates(now);
		} catch (ReflectiveOperationException | RuntimeException e) {
			CoopLog.warn("Host animal sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void cleanupHostStates(long now) {
		if (now - lastCleanupMillis < 5000L)
			return;
		lastCleanupMillis = now;
		sentStates.entrySet().removeIf(entry -> !(SETT.ENTITIES().getByID(entry.getKey()) instanceof Animal));
	}

	static void apply(boolean clientMode, long snapshotSequence, long hostMillis, String batch) {
		if (!clientMode || batch == null || batch.length() == 0)
			return;
		for (String entry : batch.split(";")) {
			try {
				String[] p = entry.split(",", -1);
				if (p.length < 21)
					continue;
				int id = Integer.parseInt(p[0]);
				ENTITY entity = SETT.ENTITIES().getByID(id);
				if (!(entity instanceof Animal))
					continue;
				Animal animal = (Animal) entity;
				AnimalTarget target = targets.computeIfAbsent(id, ignored -> new AnimalTarget());
				if (target.initialized && snapshotSequence <= target.sequence)
					continue;
				boolean first = !target.initialized;
				int x = Integer.parseInt(p[1]);
				int y = Integer.parseInt(p[2]);
				if (target.initialized) {
					long dt = hostMillis - target.hostMillis;
					if (dt >= 5 && dt <= 1500) {
						target.vx = (x - target.x) / (double) dt;
						target.vy = (y - target.y) / (double) dt;
					}
				}
				target.previousX = target.x;
				target.previousY = target.y;
				target.previousHostMillis = target.hostMillis;
				target.x = x;
				target.y = y;
				target.nx = Integer.parseInt(p[3]) / 1000.0;
				target.ny = Integer.parseInt(p[4]) / 1000.0;
				target.magnitude = Integer.parseInt(p[5]) / 100.0;
				target.hostMillis = hostMillis;
				target.updated = System.currentTimeMillis();
				target.sequence = snapshotSequence;
				target.initialized = true;

				setFloat(animal, "spriteTimer", Integer.parseInt(p[6]) / 1000.0f);
				setEnumOrdinal(animal, "state", Integer.parseInt(p[7]));
				setFloat(animal, "stateTimer", Integer.parseInt(p[8]) / 1000.0f);
				setInt(animal, "stateI", Integer.parseInt(p[9]));
				setBoolean(animal, "inWater", "1".equals(p[10]));
				setFloat(animal, "damage", Integer.parseInt(p[11]) / 1000.0f);
				setBoolean(animal, "domesticated", "1".equals(p[12]));
				setInt(animal, "birthDay", Integer.parseInt(p[13]));
				setInt(animal, "upHour", Integer.parseInt(p[14]));
				setBoolean(animal, "cub", "1".equals(p[15]));
				setInt(animal, "spotI", Integer.parseInt(p[16]));
				setInt(animal, "killSwitch", Integer.parseInt(p[17]));
				setFloat(animal, "nTimer", Integer.parseInt(p[18]) / 1000.0f);
				setBoolean(animal, "markedForTheHunt", "1".equals(p[19]));
				setBoolean(animal, "huntedReserved", "1".equals(p[20]));
				if (first || distanceSquared(animal.body().cX(), animal.body().cY(), x, y) > square(C.TILE_SIZE * 3))
					move(animal, x, y);
			} catch (ReflectiveOperationException | RuntimeException e) {
				CoopLog.warn("Skipped invalid animal snapshot: " + CoopProtocol.trim(entry) + " / "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
		long now = System.currentTimeMillis();
		if (now - lastCleanupMillis >= 5000L) {
			lastCleanupMillis = now;
			targets.entrySet().removeIf(entry -> !(SETT.ENTITIES().getByID(entry.getKey()) instanceof Animal)
					|| now - entry.getValue().updated > 15000L);
		}
	}

	static void applyTarget(Animal animal, double ds) {
		if (animal == null || animal.isRemoved())
			return;
		AnimalTarget target = targets.get(animal.id());
		if (target == null)
			return;
		long now = System.currentTimeMillis();
		long estimatedHostMillis = target.hostMillis + Math.max(0, now - target.updated);
		long renderHostMillis = estimatedHostMillis - 32;
		int wantedX;
		int wantedY;
		long span = target.hostMillis - target.previousHostMillis;
		if (target.previousHostMillis > 0 && span > 0 && renderHostMillis <= target.hostMillis) {
			double alpha = Math.max(0.0, Math.min(1.0,
					(renderHostMillis - target.previousHostMillis) / (double) span));
			wantedX = target.previousX + (int) Math.round((target.x - target.previousX) * alpha);
			wantedY = target.previousY + (int) Math.round((target.y - target.previousY) * alpha);
		} else {
			long predict = Math.min(64, Math.max(0, renderHostMillis - target.hostMillis));
			wantedX = target.x + (int) Math.round(target.vx * predict);
			wantedY = target.y + (int) Math.round(target.vy * predict);
		}
		int cx = animal.body().cX();
		int cy = animal.body().cY();
		int dx = wantedX - cx;
		int dy = wantedY - cy;
		long distance = (long) dx * dx + (long) dy * dy;
		if (distance > square(C.TILE_SIZE * 3)) {
			move(animal, wantedX, wantedY);
		} else if (distance > 0) {
			double factor = 1.0 - Math.exp(-Math.max(0.0, ds) * 28.0);
			int x = cx + (int) Math.round(dx * factor);
			int y = cy + (int) Math.round(dy * factor);
			if (x == cx && dx != 0)
				x += dx > 0 ? 1 : -1;
			if (y == cy && dy != 0)
				y += dy > 0 ? 1 : -1;
			move(animal, x, y);
		}
		animal.speed.setRawNormalized(target.nx, target.ny, target.magnitude);
		animal.speed.magnitudeTargetSetPrecise(target.magnitude);
	}

	private static boolean visible(Animal animal) {
		int margin = C.TILE_SIZE * 10;
		return Math.abs(animal.body().cX() - CoopCursor.remoteViewCenterX()) <= CoopCursor.remoteViewHalfWidth() + margin
				&& Math.abs(animal.body().cY() - CoopCursor.remoteViewCenterY()) <= CoopCursor.remoteViewHalfHeight() + margin;
	}

	private static boolean shouldSend(Animal animal, long now, boolean visible) throws ReflectiveOperationException {
		SentState sent = sentStates.get(animal.id());
		int hash = stateHash(animal);
		if (sent == null) {
			sentStates.put(animal.id(), new SentState());
			return true;
		}
		if (sent.sentMillis == now)
			return false;
		return sent.x != animal.body().cX() || sent.y != animal.body().cY() || sent.hash != hash
				|| now - sent.sentMillis >= (visible ? 100L : 1000L);
	}

	private static void append(StringBuilder data, Animal animal, long now) throws ReflectiveOperationException {
		if (data.length() > 0)
			data.append(';');
		data.append(animal.id()).append(',').append(animal.body().cX()).append(',').append(animal.body().cY()).append(',')
				.append((int) Math.round(animal.speed.nX() * 1000.0)).append(',')
				.append((int) Math.round(animal.speed.nY() * 1000.0)).append(',')
				.append((int) Math.round(animal.speed.magnitude() * 100.0)).append(',')
				.append((int) Math.round(getFloat(animal, "spriteTimer") * 1000.0f)).append(',')
				.append(getEnumOrdinal(animal, "state")).append(',')
				.append((int) Math.round(getFloat(animal, "stateTimer") * 1000.0f)).append(',')
				.append(getInt(animal, "stateI")).append(',').append(getBoolean(animal, "inWater") ? 1 : 0).append(',')
				.append((int) Math.round(getFloat(animal, "damage") * 1000.0f)).append(',')
				.append(getBoolean(animal, "domesticated") ? 1 : 0).append(',').append(getInt(animal, "birthDay")).append(',')
				.append(getInt(animal, "upHour")).append(',').append(getBoolean(animal, "cub") ? 1 : 0).append(',')
				.append(getInt(animal, "spotI")).append(',').append(getInt(animal, "killSwitch")).append(',')
				.append((int) Math.round(getFloat(animal, "nTimer") * 1000.0f)).append(',')
				.append(getBoolean(animal, "markedForTheHunt") ? 1 : 0).append(',')
				.append(getBoolean(animal, "huntedReserved") ? 1 : 0);
		SentState sent = sentStates.computeIfAbsent(animal.id(), ignored -> new SentState());
		sent.x = animal.body().cX();
		sent.y = animal.body().cY();
		sent.hash = stateHash(animal);
		sent.sentMillis = now;
	}

	private static int stateHash(Animal animal) throws ReflectiveOperationException {
		int hash = 17;
		hash = 31 * hash + getEnumOrdinal(animal, "state");
		hash = 31 * hash + getInt(animal, "stateI");
		hash = 31 * hash + (getBoolean(animal, "inWater") ? 1 : 0);
		hash = 31 * hash + (getBoolean(animal, "cub") ? 1 : 0);
		hash = 31 * hash + Float.floatToIntBits(getFloat(animal, "damage"));
		return hash;
	}

	private static int nextIndex(boolean visible, int limit) {
		int index;
		if (visible) {
			if (visibleCursor < 0 || visibleCursor >= limit)
				visibleCursor = 0;
			index = visibleCursor++;
		} else {
			if (backgroundCursor < 0 || backgroundCursor >= limit)
				backgroundCursor = 0;
			index = backgroundCursor++;
		}
		return index;
	}

	private static void move(Animal animal, int x, int y) {
		animal.physics.body().moveC(x, y);
		SETT.ENTITIES().move(animal);
	}

	private static long distanceSquared(int ax, int ay, int bx, int by) {
		long dx = (long) ax - bx;
		long dy = (long) ay - by;
		return dx * dx + dy * dy;
	}

	private static long square(int value) {
		return (long) value * value;
	}

	private static Field field(String name) throws ReflectiveOperationException {
		Field field = fields.get(name);
		if (field == null) {
			field = Animal.class.getDeclaredField(name);
			field.setAccessible(true);
			fields.put(name, field);
		}
		return field;
	}

	private static int getInt(Animal animal, String name) throws ReflectiveOperationException {
		return ((Number) field(name).get(animal)).intValue();
	}

	private static float getFloat(Animal animal, String name) throws ReflectiveOperationException {
		return ((Number) field(name).get(animal)).floatValue();
	}

	private static boolean getBoolean(Animal animal, String name) throws ReflectiveOperationException {
		return field(name).getBoolean(animal);
	}

	private static int getEnumOrdinal(Animal animal, String name) throws ReflectiveOperationException {
		return ((Enum<?>) field(name).get(animal)).ordinal();
	}

	private static void setInt(Animal animal, String name, int value) throws ReflectiveOperationException {
		Field field = field(name);
		Class<?> type = field.getType();
		if (type == byte.class)
			field.setByte(animal, (byte) value);
		else
			field.setInt(animal, value);
	}

	private static void setFloat(Animal animal, String name, float value) throws ReflectiveOperationException {
		field(name).setFloat(animal, value);
	}

	private static void setBoolean(Animal animal, String name, boolean value) throws ReflectiveOperationException {
		field(name).setBoolean(animal, value);
	}

	private static void setEnumOrdinal(Animal animal, String name, int ordinal) throws ReflectiveOperationException {
		Field field = field(name);
		Object[] constants = field.getType().getEnumConstants();
		if (constants != null && ordinal >= 0 && ordinal < constants.length)
			field.set(animal, constants[ordinal]);
	}

	private static final class Batch {
		final long hostMillis;
		final StringBuilder data = new StringBuilder(8192);
		int entries;
		int total;

		Batch(long hostMillis) {
			this.hostMillis = hostMillis;
		}

		void add(Animal animal) throws ReflectiveOperationException {
			append(data, animal, hostMillis);
			entries++;
			total++;
			if (entries >= PACKET_LIMIT)
				flush();
		}

		void flush() {
			if (entries == 0)
				return;
			CoopRuntime.sendStateLine("A2\t" + (++sequence) + "\t" + hostMillis + "\t" + data);
			data.setLength(0);
			entries = 0;
		}
	}

	private static final class SentState {
		int x;
		int y;
		int hash;
		long sentMillis;
	}

	private static final class AnimalTarget {
		int x;
		int y;
		int previousX;
		int previousY;
		double nx;
		double ny;
		double magnitude;
		double vx;
		double vy;
		long hostMillis;
		long previousHostMillis;
		long updated;
		long sequence;
		boolean initialized;
	}
}
