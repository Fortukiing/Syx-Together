package coopmod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import game.battle.div.Div;
import init.constant.C;
import init.resources.RESOURCE;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AISTATE;
import settlement.main.SETT;

final class CoopNpcSync {

	private static final ConcurrentHashMap<String, AISTATE> aiStateByKey = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Integer, CoopNpcTarget> npcTargets = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Integer, SentState> sentStates = new ConcurrentHashMap<>();
	private static volatile Field aiResourceField;
	private static volatile Field aiResourceAmountField;
	private static volatile boolean npcSync = true;
	private static volatile int npcSyncIntervalMillis = 16;
	private static volatile int npcSyncMaxPerPacket = 2000;
	private static volatile int npcSyncMaxPacketsPerTick = 4;
	private static volatile int npcSyncPixelThreshold = 0;
	private static volatile boolean npcRemoteAuthority = true;
	private static volatile int npcInterpolationDelayMillis = 32;
	private static volatile int npcExtrapolationLimitMillis = 64;
	private static long lastNpcSyncMillis;
	private static long npcSyncSequence;
	private static long legacySequence;
	private static int visibleCursor;
	private static int backgroundCursor;
	private static long lastCleanupMillis;

	private CoopNpcSync() {
	}

	static boolean loadConfig(String key, String value) {
		if ("NPC_SYNC".equals(key)) {
			npcSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("NPC_SYNC_INTERVAL_MS".equals(key)) {
			npcSyncIntervalMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("NPC_SYNC_MAX_PER_PACKET".equals(key)) {
			npcSyncMaxPerPacket = Math.max(1, Integer.parseInt(value));
			return true;
		}
		if ("NPC_SYNC_MAX_PACKETS_PER_TICK".equals(key)) {
			npcSyncMaxPacketsPerTick = Math.max(1, Integer.parseInt(value));
			return true;
		}
		if ("NPC_SYNC_PIXEL_THRESHOLD".equals(key)) {
			npcSyncPixelThreshold = Math.max(0, Integer.parseInt(value));
			return true;
		}
		if ("NPC_REMOTE_AUTHORITY".equals(key)) {
			npcRemoteAuthority = Boolean.parseBoolean(value);
			return true;
		}
		if ("NPC_INTERPOLATION_DELAY_MS".equals(key)) {
			npcInterpolationDelayMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("NPC_EXTRAPOLATION_LIMIT_MS".equals(key)) {
			npcExtrapolationLimitMillis = Math.max(0, Integer.parseInt(value));
			return true;
		}
		return false;
	}

	static boolean remoteControls(Humanoid humanoid, boolean clientMode, boolean connected) {
		if (!npcRemoteAuthority || !npcSync || !clientMode || !connected || humanoid == null)
			return false;
		CoopNpcTarget target = npcTargets.get(humanoid.id());
		return target != null && target.initialized && System.currentTimeMillis() - target.updated <= 5000L;
	}

	static void sendHost(boolean active) {
		if (!npcSync || !active)
			return;
		long now = System.currentTimeMillis();
		if (now - lastNpcSyncMillis < Math.max(16, npcSyncIntervalMillis))
			return;
		lastNpcSyncMillis = now;
		try {
			ENTITY[] all = SETT.ENTITIES().getAllEnts();
			if (all == null || all.length == 0)
				return;
			int maxIndex = Math.min(SETT.ENTITIES().Imax(), all.length - 1);
			if (maxIndex < 0)
				return;
			int limit = maxIndex + 1;
			int packetLimit = Math.min(256, Math.max(1, npcSyncMaxPerPacket));
			int totalBudget = packetLimit * Math.max(1, npcSyncMaxPacketsPerTick);
			PacketBatch batch = new PacketBatch(packetLimit, now);
			boolean focused = CoopCursor.remoteSettlementViewFresh();
			int sweepTicks = Math.max(1, 250 / Math.max(16, npcSyncIntervalMillis));
			int sweepBudget = Math.max(128, (limit + sweepTicks - 1) / sweepTicks);
			int backgroundBudget = focused ? Math.min(totalBudget, sweepBudget) : totalBudget;
			int visibleBudget = focused ? Math.max(0, totalBudget - backgroundBudget) : 0;

			int scanned = 0;
			while (scanned++ < limit && batch.totalSent < visibleBudget) {
				int index = nextIndex(true, limit);
				ENTITY e = all[index];
				if (e instanceof Humanoid && visibleToClient((Humanoid) e)
						&& shouldSend((Humanoid) e, now, true))
					batch.add((Humanoid) e);
			}

			scanned = 0;
			int backgroundSent = 0;
			while (scanned++ < limit && backgroundSent < backgroundBudget && batch.totalSent < totalBudget) {
				int index = nextIndex(false, limit);
				ENTITY e = all[index];
				if (e instanceof Humanoid && shouldSend((Humanoid) e, now, false)) {
					batch.add((Humanoid) e);
					backgroundSent++;
				}
			}
			batch.flush();
			cleanupHostStates(now);
			CoopNpcLogicSync.sendHost(all, limit, now);
			CoopAnimalSync.sendHost(all, limit, now);
		} catch (Exception e) {
			CoopLog.error("Host NPC sync failed.", e);
		}
	}

	private static void cleanupHostStates(long now) {
		if (now - lastCleanupMillis < 5000L)
			return;
		lastCleanupMillis = now;
		sentStates.entrySet().removeIf(entry -> !(SETT.ENTITIES().getByID(entry.getKey()) instanceof Humanoid));
	}

	private static int nextIndex(boolean visible, int limit) {
		if (visible) {
			if (visibleCursor < 0 || visibleCursor >= limit)
				visibleCursor = 0;
			int index = visibleCursor++;
			if (visibleCursor >= limit)
				visibleCursor = 0;
			return index;
		}
		if (backgroundCursor < 0 || backgroundCursor >= limit)
			backgroundCursor = 0;
		int index = backgroundCursor++;
		if (backgroundCursor >= limit)
			backgroundCursor = 0;
		return index;
	}

	private static boolean visibleToClient(Humanoid h) {
		int margin = C.TILE_SIZE * 10;
		int dx = Math.abs(h.body().cX() - CoopCursor.remoteViewCenterX());
		int dy = Math.abs(h.body().cY() - CoopCursor.remoteViewCenterY());
		return dx <= CoopCursor.remoteViewHalfWidth() + margin
				&& dy <= CoopCursor.remoteViewHalfHeight() + margin;
	}

	private static boolean shouldSend(Humanoid h, long now, boolean visible) {
		SentState state = sentStates.get(h.id());
		int visualHash = visualHash(h);
		if (state == null) {
			state = new SentState();
			sentStates.put(h.id(), state);
			return true;
		}
		if (state.sentMillis == now)
			return false;
		int dx = Math.abs(state.x - h.body().cX());
		int dy = Math.abs(state.y - h.body().cY());
		long keepAlive = visible ? 100L : 1000L;
		return dx > 0 || dy > 0 || state.visualHash != visualHash || now - state.sentMillis >= keepAlive;
	}

	private static int visualHash(Humanoid h) {
		int hash = 17;
		hash = 31 * hash + (int) Math.round(h.speed.nX() * 1000.0);
		hash = 31 * hash + (int) Math.round(h.speed.nY() * 1000.0);
		hash = 31 * hash + (int) Math.round(h.speed.magnitude() * 100.0);
		hash = 31 * hash + (h.spriteoff & 0xFF);
		hash = 31 * hash + (h.inWater ? 1 : 0);
		AIManager ai = aiManager(h);
		if (ai != null) {
			AISTATE state = ai.state();
			hash = 31 * hash + (state == null || state.key == null ? 0 : state.key.hashCode());
			RESOURCE resource = ai.resourceCarried();
			hash = 31 * hash + (resource == null ? -1 : resource.bIndex());
			hash = 31 * hash + ai.resourceA();
		}
		return hash;
	}

	private static void rememberSent(Humanoid h, long now) {
		SentState state = sentStates.computeIfAbsent(h.id(), ignored -> new SentState());
		state.x = h.body().cX();
		state.y = h.body().cY();
		state.visualHash = visualHash(h);
		state.sentMillis = now;
	}

	private static final class PacketBatch {
		private final int packetLimit;
		private final long hostMillis;
		private final StringBuilder data;
		private int packetEntries;
		private int totalSent;

		PacketBatch(int packetLimit, long hostMillis) {
			this.packetLimit = packetLimit;
			this.hostMillis = hostMillis;
			this.data = new StringBuilder(packetLimit * 64);
		}

		void add(Humanoid h) {
			appendNpc(data, h);
			rememberSent(h, hostMillis);
			packetEntries++;
			totalSent++;
			if (packetEntries >= packetLimit)
				flush();
		}

		void flush() {
			if (packetEntries <= 0)
				return;
			long sequence = ++npcSyncSequence;
			CoopRuntime.sendStateLine("N2\t" + sequence + "\t" + hostMillis + "\t" + data);
			data.setLength(0);
			packetEntries = 0;
		}
	}

	private static final class SentState {
		int x;
		int y;
		int visualHash;
		long sentMillis;
	}

	static void apply(boolean clientMode, String batch) {
		applySnapshot(clientMode, ++legacySequence, System.currentTimeMillis(), batch);
	}

	static void applySnapshot(boolean clientMode, long sequence, long hostMillis, String batch) {
		if (!npcSync || !clientMode || batch == null || batch.length() == 0)
			return;
		String[] entries = batch.split(";");
		for (String entry : entries) {
			try {
				String[] p = entry.split(",", -1);
				if (p.length < 6)
					continue;
				int id = Integer.parseInt(p[0]);
				int x = Integer.parseInt(p[1]);
				int y = Integer.parseInt(p[2]);
				ENTITY e = SETT.ENTITIES().getByID(id);
				if (!(e instanceof Humanoid))
					continue;
				Humanoid h = (Humanoid) e;
				CoopNpcTarget target = npcTargets.computeIfAbsent(id, k -> new CoopNpcTarget());
				if (target.initialized && sequence <= target.sequence)
					continue;
				long now = System.currentTimeMillis();
				boolean first = !target.initialized;
				if (target.initialized) {
					long dt = hostMillis - target.hostMillis;
					if (dt >= 5 && dt <= 500) {
						target.vx = (x - target.x) / (double) dt;
						target.vy = (y - target.y) / (double) dt;
					} else {
						target.vx = 0;
						target.vy = 0;
					}
				} else {
					target.initialized = true;
					target.vx = 0;
					target.vy = 0;
				}
				target.previousX = target.x;
				target.previousY = target.y;
				target.previousHostMillis = target.hostMillis;
				target.x = x;
				target.y = y;
				target.nx = Integer.parseInt(p[3]) / 1000.0;
				target.ny = Integer.parseInt(p[4]) / 1000.0;
				target.magnitude = Integer.parseInt(p[5]) / 100.0;
				target.updated = now;
				target.hostMillis = hostMillis;
				target.sequence = sequence;
				int hardSnap = C.TILE_SIZE * 3;
				long hardSnapSq = (long) hardSnap * hardSnap;
				long dx = (long) h.body().cX() - x;
				long dy = (long) h.body().cY() - y;
				long distSq = dx * dx + dy * dy;
				if (first || distSq > hardSnapSq)
					moveHumanoidTo(h, x, y);
				h.speed.setRawNormalized(target.nx, target.ny, target.magnitude);
				h.speed.magnitudeTargetSetPrecise(target.magnitude);
				if (p.length >= 8) {
					h.spriteoff = (byte) Integer.parseInt(p[6]);
					float spriteTimer = Integer.parseInt(p[7]) / 1000.0f;
					if (first || Math.abs(h.spriteTimer - spriteTimer) > 2.0f)
						h.spriteTimer = spriteTimer;
				}
				if (p.length >= 10) {
					float relTimer = Integer.parseInt(p[8]) / 1000.0f;
					if (first || Math.abs(h.relTimer - relTimer) > 3.0f)
						h.relTimer = relTimer;
					h.inWater = "1".equals(p[9]);
				}
				if (p.length >= 12)
					applyNpcAiState(h, CoopProtocol.dec(p[10]), Integer.parseInt(p[11]));
				if (p.length >= 14)
					applyNpcResource(h, Integer.parseInt(p[12]), Integer.parseInt(p[13]));
			} catch (Exception e) {
				CoopLog.warn("Skipped invalid NPC sync entry: " + CoopProtocol.trim(entry) + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
		long now = System.currentTimeMillis();
		if (now - lastCleanupMillis >= 5000L) {
			lastCleanupMillis = now;
			npcTargets.entrySet().removeIf(entry -> !(SETT.ENTITIES().getByID(entry.getKey()) instanceof Humanoid)
					|| now - entry.getValue().updated > 15000L);
		}
	}

	static void applyTarget(Humanoid h, double ds) {
		if (h == null || h.isRemoved())
			return;
		CoopNpcTarget target = npcTargets.get(h.id());
		if (target == null)
			return;
		int cx = h.body().cX();
		int cy = h.body().cY();
		long now = System.currentTimeMillis();
		long elapsed = Math.max(0, now - target.updated);
		long estimatedHostMillis = target.hostMillis + elapsed;
		long renderHostMillis = estimatedHostMillis - Math.max(16, npcInterpolationDelayMillis);
		int wantedX;
		int wantedY;
		long sampleSpan = target.hostMillis - target.previousHostMillis;
		if (target.previousHostMillis > 0 && sampleSpan > 0 && renderHostMillis <= target.hostMillis) {
			double alpha = (renderHostMillis - target.previousHostMillis) / (double) sampleSpan;
			alpha = Math.max(0.0, Math.min(1.0, alpha));
			wantedX = target.previousX + (int) Math.round((target.x - target.previousX) * alpha);
			wantedY = target.previousY + (int) Math.round((target.y - target.previousY) * alpha);
		} else {
			long predictMs = Math.min(Math.max(0, npcExtrapolationLimitMillis), Math.max(0, renderHostMillis - target.hostMillis));
			wantedX = target.x + (int) Math.round(target.vx * predictMs);
			wantedY = target.y + (int) Math.round(target.vy * predictMs);
		}
		int dx = wantedX - cx;
		int dy = wantedY - cy;
		long distSq = (long) dx * dx + (long) dy * dy;
		int threshold = Math.max(0, npcSyncPixelThreshold);
		int snap = C.TILE_SIZE * 3;
		if (distSq > (long) snap * snap) {
			h.physics.body().moveC(wantedX, wantedY);
		} else if (distSq > (long) threshold * threshold) {
			double factor = 1.0 - Math.exp(-Math.max(0.0, ds) * 28.0);
			if (distSq <= 1)
				factor = 1.0;
			int nx = cx + (int) Math.round(dx * factor);
			int ny = cy + (int) Math.round(dy * factor);
			if (nx == cx && dx != 0)
				nx += dx > 0 ? 1 : -1;
			if (ny == cy && dy != 0)
				ny += dy > 0 ? 1 : -1;
			h.physics.body().moveC(nx, ny);
		}
		if (distSq > (long) threshold * threshold)
			SETT.ENTITIES().move(h);
		Div d = h.division();
		if (d != null)
			d.reporter.reportPosition(h.divSpot(), h.body().cX(), h.body().cY());
		h.speed.setRawNormalized(target.nx, target.ny, target.magnitude);
		h.speed.magnitudeTargetSetPrecise(target.magnitude);
		AIManager ai = aiManager(h);
		if (ai != null && ai.state() != null)
			ai.state().sprite(h).tick(h, Math.max(0.0, ds));
	}

	private static void appendNpc(StringBuilder sb, Humanoid h) {
		AIManager ai = aiManager(h);
		String stateKey = "";
		int stateTimer = 0;
		int resource = -1;
		int resourceAmount = 0;
		if (ai != null) {
			AISTATE state = ai.state();
			if (state != null && state.key != null)
				stateKey = state.key;
			stateTimer = (int) Math.round(ai.stateTimer * 1000.0f);
			RESOURCE res = ai.resourceCarried();
			if (res != null) {
				resource = res.bIndex();
				resourceAmount = ai.resourceA();
			}
		}
		if (sb.length() > 0)
			sb.append(';');
		sb.append(h.id()).append(',')
				.append(h.body().cX()).append(',')
				.append(h.body().cY()).append(',')
				.append((int) Math.round(h.speed.nX() * 1000.0)).append(',')
				.append((int) Math.round(h.speed.nY() * 1000.0)).append(',')
				.append((int) Math.round(h.speed.magnitude() * 100.0)).append(',')
				.append(h.spriteoff & 0xFF).append(',')
				.append((int) Math.round(h.spriteTimer * 1000.0f)).append(',')
				.append((int) Math.round(h.relTimer * 1000.0f)).append(',')
				.append(h.inWater ? 1 : 0).append(',')
				.append(CoopProtocol.enc(stateKey)).append(',')
				.append(stateTimer).append(',')
				.append(resource).append(',')
				.append(resourceAmount);
	}

	private static void moveHumanoidTo(Humanoid h, int x, int y) {
		h.physics.body().moveC(x, y);
		SETT.ENTITIES().move(h);
		Div d = h.division();
		if (d != null)
			d.reporter.reportPosition(h.divSpot(), x, y);
	}

	private static AIManager aiManager(Humanoid h) {
		if (h == null)
			return null;
		try {
			return h.ai() instanceof AIManager ? (AIManager) h.ai() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static void applyNpcAiState(Humanoid h, String key, int stateTimerMillis) {
		if (key == null || key.length() == 0)
			return;
		AIManager ai = aiManager(h);
		if (ai == null)
			return;
		AISTATE state = aiState(key);
		if (state == null)
			return;
		if (ai.state() != state)
			ai.overwrite(h, state);
		ai.stateTimer = stateTimerMillis / 1000.0f;
	}

	private static AISTATE aiState(String key) {
		AISTATE state = aiStateByKey.get(key);
		if (state != null)
			return state;
		try {
			scanAiStates(AI.STATES(), Collections.newSetFromMap(new IdentityHashMap<>()), 0);
		} catch (Exception e) {
			CoopLog.error("Failed to scan humanoid AI states.", e);
		}
		return aiStateByKey.get(key);
	}

	private static void scanAiStates(Object o, Set<Object> seen, int depth) throws IllegalAccessException {
		if (o == null || depth > 8 || seen.contains(o))
			return;
		seen.add(o);
		if (o instanceof AISTATE) {
			AISTATE state = (AISTATE) o;
			if (state.key != null)
				aiStateByKey.putIfAbsent(state.key, state);
			return;
		}
		Class<?> c = o.getClass();
		if (skipClass(c))
			return;
		if (c.isArray()) {
			int len = java.lang.reflect.Array.getLength(o);
			for (int i = 0; i < len; i++)
				scanAiStates(java.lang.reflect.Array.get(o, i), seen, depth + 1);
			return;
		}
		while (c != null && c != Object.class) {
			for (Field f : c.getDeclaredFields()) {
				if ((f.getModifiers() & Modifier.STATIC) != 0)
					continue;
				Class<?> ft = f.getType();
				if (ft.isPrimitive() || ft == String.class || Number.class.isAssignableFrom(ft) || ft == Boolean.class || ft == Character.class)
					continue;
				f.setAccessible(true);
				scanAiStates(f.get(o), seen, depth + 1);
			}
			c = c.getSuperclass();
		}
	}

	private static void applyNpcResource(Humanoid h, int resource, int amount) {
		AIManager ai = aiManager(h);
		if (ai == null)
			return;
		try {
			Field r = aiResourceField;
			if (r == null) {
				r = AIManager.class.getDeclaredField("resource");
				r.setAccessible(true);
				aiResourceField = r;
			}
			Field a = aiResourceAmountField;
			if (a == null) {
				a = AIManager.class.getDeclaredField("resourceA");
				a.setAccessible(true);
				aiResourceAmountField = a;
			}
			if (resource < 0 || amount <= 0) {
				r.setByte(ai, (byte) -1);
				a.setByte(ai, (byte) 0);
			} else {
				r.setByte(ai, (byte) resource);
				a.setByte(ai, (byte) amount);
			}
		} catch (Exception e) {
			CoopLog.warn("Failed to apply NPC carried resource: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean skipClass(Class<?> c) {
		if (c == null)
			return true;
		String n = c.getName();
		return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("sun.") || n.startsWith("com.sun.")
				|| n.startsWith("jdk.") || n.startsWith("org.lwjgl.") || n.startsWith("org.apache.");
	}
}
