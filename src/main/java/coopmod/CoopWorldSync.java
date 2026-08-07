package coopmod;

import game.faction.FACTIONS;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.main.SETT;
import settlement.weather.WeatherThing;
import snake2d.util.sets.LIST;

final class CoopWorldSync {

	private static volatile boolean worldSync = true;
	private static volatile int worldSyncIntervalMillis = 16;
	private static volatile boolean uiSanitySync = true;
	private static volatile int uiSanityIntervalMillis = 16;
	private static volatile boolean uiSanityLogMismatches = true;
	private static volatile int uiSanityMismatchLogIntervalMillis = 2000;
	private static long lastWorldSyncMillis;
	private static long lastUiSanityMillis;
	private static long lastUiSanityMismatchMillis;

	private CoopWorldSync() {
	}

	static boolean loadConfig(String key, String value) {
		if ("WORLD_SYNC".equals(key)) {
			worldSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("WORLD_SYNC_INTERVAL_MS".equals(key)) {
			worldSyncIntervalMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("UI_SANITY_SYNC".equals(key)) {
			uiSanitySync = Boolean.parseBoolean(value);
			return true;
		}
		if ("UI_SANITY_INTERVAL_MS".equals(key)) {
			uiSanityIntervalMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("UI_SANITY_LOG_MISMATCHES".equals(key)) {
			uiSanityLogMismatches = Boolean.parseBoolean(value);
			return true;
		}
		if ("UI_SANITY_MISMATCH_LOG_INTERVAL_MS".equals(key)) {
			uiSanityMismatchLogIntervalMillis = Math.max(250, Integer.parseInt(value));
			return true;
		}
		return false;
	}

	static void sendWorldState(boolean active) {
		if (!worldSync || !active)
			return;
		long now = System.currentTimeMillis();
		if (now - lastWorldSyncMillis < Math.max(16, worldSyncIntervalMillis))
			return;
		lastWorldSyncMillis = now;
		try {
			StringBuilder weather = new StringBuilder(128);
			LIST<WeatherThing> all = SETT.WEATHER().all();
			for (int i = 0; i < all.size(); i++) {
				if (i > 0)
					weather.append(',');
				weather.append((int) Math.round(all.get(i).getD() * 10000.0));
			}
			CoopRuntime.sendStateLine("U\t" + (long) Math.round(TIME.currentSecond() * 1000.0) + "\t" + CoopProtocol.enc(weather.toString()));
		} catch (Exception e) {
			CoopLog.error("Host world/time/weather sync failed.", e);
		}
	}

	static void sendUiSanityState(boolean active) {
		if (!uiSanitySync || !active)
			return;
		long now = System.currentTimeMillis();
		if (now - lastUiSanityMillis < Math.max(16, uiSanityIntervalMillis))
			return;
		lastUiSanityMillis = now;
		try {
			long credits = Math.round(FACTIONS.player().credits().credits() * 100.0);
			CoopRuntime.sendStateLine("US\t" + credits + "\t" + CoopProtocol.enc(resourceAvailableCsv()) + "\t" + CoopProtocol.enc(resourceHistoryCsv()));
		} catch (Exception e) {
			CoopLog.warn("Host UI sanity sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyWorldSync(boolean clientMode, String timeMillisText, String weatherText) {
		if (!worldSync || !clientMode)
			return;
		try {
			double hostTime = Long.parseLong(timeMillisText) / 1000.0;
			double localTime = TIME.currentSecond();
			if (Math.abs(localTime - hostTime) > 0.05)
				TIME.set(hostTime);
			if (weatherText != null && weatherText.length() > 0) {
				String[] values = weatherText.split(",", -1);
				LIST<WeatherThing> all = SETT.WEATHER().all();
				int n = Math.min(values.length, all.size());
				for (int i = 0; i < n; i++) {
					if (values[i].length() > 0)
						all.get(i).setD(Integer.parseInt(values[i]) / 10000.0);
				}
			}
		} catch (Exception e) {
			CoopLog.warn("Failed to apply world/time/weather sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyCreditsSync(boolean clientMode, double credits) {
		if (!clientMode)
			return;
		try {
			FACTIONS.player().credits().set(credits);
		} catch (Exception e) {
			CoopLog.warn("Failed to apply synced player credits: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyUiSanitySync(boolean clientMode, String creditsText, String availableText, String totalText) {
		if (!uiSanitySync || !clientMode)
			return;
		try {
			applyCreditsSync(true, Long.parseLong(creditsText) / 100.0);
			StringBuilder sample = new StringBuilder(160);
			int availableDiffs = countResourceDiffs(availableText, true, sample);
			int totalDiffs = countResourceDiffs(totalText, false, sample);
			if (availableDiffs == 0 && totalDiffs == 0)
				return;
			if (!uiSanityLogMismatches)
				return;
			long now = System.currentTimeMillis();
			if (now - lastUiSanityMismatchMillis < Math.max(250, uiSanityMismatchLogIntervalMillis))
				return;
			lastUiSanityMismatchMillis = now;
			CoopRuntime.setStatus("ui/resource drift detected");
			CoopLog.warn("UI sanity mismatch: available=" + availableDiffs + " history=" + totalDiffs
					+ (sample.length() == 0 ? "" : " sample=" + sample));
		} catch (Exception e) {
			CoopLog.warn("Failed to apply UI sanity sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static String resourceAvailableCsv() {
		StringBuilder sb = new StringBuilder(RESOURCES.ALL().size() * 8);
		for (int i = 0; i < RESOURCES.ALL().size(); i++) {
			if (i > 0)
				sb.append(',');
			RESOURCE res = RESOURCES.ALL().get(i);
			sb.append(FACTIONS.player().res().getAvailable(res.tr()));
		}
		return sb.toString();
	}

	private static String resourceHistoryCsv() {
		StringBuilder sb = new StringBuilder(RESOURCES.ALL().size() * 8);
		for (int i = 0; i < RESOURCES.ALL().size(); i++) {
			if (i > 0)
				sb.append(',');
			RESOURCE res = RESOURCES.ALL().get(i);
			sb.append(FACTIONS.player().res().total().get(res.tr()));
		}
		return sb.toString();
	}

	private static int countResourceDiffs(String csv, boolean available, StringBuilder sample) {
		if (csv == null || csv.length() == 0)
			return 0;
		String[] values = csv.split(",", -1);
		int n = Math.min(values.length, RESOURCES.ALL().size());
		int diffs = 0;
		for (int i = 0; i < n; i++) {
			if (values[i].length() == 0)
				continue;
			RESOURCE res = RESOURCES.ALL().get(i);
			int hostValue = Integer.parseInt(values[i]);
			int localValue = available ? FACTIONS.player().res().getAvailable(res.tr()) : FACTIONS.player().res().total().get(res.tr());
			if (hostValue == localValue)
				continue;
			diffs++;
			if (sample != null && sample.length() < 140) {
				if (sample.length() > 0)
					sample.append("; ");
				sample.append(res.key).append(available ? " available " : " history ").append(localValue).append("->").append(hostValue);
			}
		}
		return diffs;
	}
}
