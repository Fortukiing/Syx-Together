package coopmod;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;

final class CoopNpcLogicSync {

	private static final int MIN_SCAN_BUDGET_PER_TICK = 64;
	private static final int TARGET_SWEEP_MILLIS = 500;
	private static final int MAX_PACKET_CHARS = 24000;
	private static final long FULL_REFRESH_MILLIS = 30000L;
	private static final ConcurrentHashMap<Integer, SentStats> lastStats = new ConcurrentHashMap<>();
	private static volatile Field individualDataField;
	private static int cursor;
	private static int entitiesIdentity;
	private static long lastCleanupMillis;

	private CoopNpcLogicSync() {
	}

	static void sendHost(ENTITY[] all, int limit, long now) {
		if (all == null || limit <= 0)
			return;
		int identity = System.identityHashCode(all);
		if (entitiesIdentity != identity) {
			lastStats.clear();
			cursor = 0;
			entitiesIdentity = identity;
		}
		StringBuilder packet = new StringBuilder(8192);
		int humanoids = 0;
		int scanned = 0;
		int sweepTicks = Math.max(1, TARGET_SWEEP_MILLIS / 16);
		int scanBudget = Math.max(MIN_SCAN_BUDGET_PER_TICK, (limit + sweepTicks - 1) / sweepTicks);
		while (scanned++ < limit && humanoids < scanBudget) {
			if (cursor < 0 || cursor >= limit)
				cursor = 0;
			ENTITY entity = all[cursor++];
			if (!(entity instanceof Humanoid))
				continue;
			humanoids++;
			appendChanges(packet, (Humanoid) entity, now);
			if (packet.length() >= MAX_PACKET_CHARS)
				flush(packet);
		}
		flush(packet);
		if (now - lastCleanupMillis >= 5000L) {
			lastCleanupMillis = now;
			lastStats.entrySet().removeIf(entry -> !(settlement.main.SETT.ENTITIES().getByID(entry.getKey()) instanceof Humanoid));
		}
	}

	static void apply(boolean clientMode, String batch) {
		if (!clientMode || batch == null || batch.length() == 0)
			return;
		for (String entry : batch.split(";")) {
			try {
				String[] parts = entry.split(",");
				if (parts.length < 2)
					continue;
				int id = Integer.parseInt(parts[0]);
				ENTITY entity = settlement.main.SETT.ENTITIES().getByID(id);
				if (!(entity instanceof Humanoid))
					continue;
				long[] data = data(((Humanoid) entity).indu());
				if (data == null)
					continue;
				for (int i = 1; i < parts.length; i++) {
					int separator = parts[i].indexOf('=');
					if (separator <= 0)
						continue;
					int index = Integer.parseInt(parts[i].substring(0, separator), 36);
					if (index < 0 || index >= data.length)
						continue;
					data[index] = Long.parseUnsignedLong(parts[i].substring(separator + 1), 36);
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				CoopLog.warn("Skipped invalid NPC logic delta: " + CoopProtocol.trim(entry) + " / "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
	}

	private static void appendChanges(StringBuilder packet, Humanoid humanoid, long now) {
		try {
			long[] current = data(humanoid.indu());
			if (current == null)
				return;
			SentStats sent = lastStats.get(humanoid.id());
			if (sent == null || sent.values.length != current.length) {
				sent = new SentStats(current.length);
				lastStats.put(humanoid.id(), sent);
			}
			boolean full = sent.lastFullMillis == 0 || now - sent.lastFullMillis >= FULL_REFRESH_MILLIS;
			int entryStart = packet.length();
			if (entryStart > 0)
				packet.append(';');
			packet.append(humanoid.id());
			int changed = 0;
			for (int i = 0; i < current.length; i++) {
				long value = current[i];
				if (!full && value == sent.values[i])
					continue;
				packet.append(',').append(Integer.toString(i, 36)).append('=')
						.append(Long.toUnsignedString(value, 36));
				sent.values[i] = value;
				changed++;
			}
			if (full)
				sent.lastFullMillis = now;
			if (changed == 0)
				packet.setLength(entryStart);
		} catch (ReflectiveOperationException | RuntimeException e) {
			CoopLog.warn("Could not inspect NPC stats for sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void flush(StringBuilder packet) {
		if (packet.length() == 0)
			return;
		CoopRuntime.sendStateLine("NL\t" + packet);
		packet.setLength(0);
	}

	private static long[] data(Induvidual individual) throws ReflectiveOperationException {
		Field field = individualDataField;
		if (field == null) {
			field = Induvidual.class.getDeclaredField("data");
			field.setAccessible(true);
			individualDataField = field;
		}
		return (long[]) field.get(individual);
	}

	private static final class SentStats {
		final long[] values;
		long lastFullMillis;

		SentStats(int size) {
			values = new long[size];
		}
	}
}
