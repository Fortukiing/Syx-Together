package coopmod;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;

import game.GameSpec;
import game.GAME;
import init.paths.PATHS;
import snake2d.util.file.FileGetter;

final class CoopSnapshotSync {

	private static final ConcurrentLinkedQueue<Path> fullStateLoads = new ConcurrentLinkedQueue<>();
	private static volatile boolean fullStateSync = false;
	private static volatile boolean fullStateLoadLive = false;
	private static volatile int fullStateIntervalMillis = 1000;
	private static volatile boolean fullStateSaving;
	private static volatile boolean fullStateLoading;
	private static long lastFullStateMillis;

	private CoopSnapshotSync() {
	}

	static boolean loadConfig(String key, String value) {
		if ("FULL_STATE_SYNC".equals(key)) {
			fullStateSync = false;
			return true;
		}
		if ("FULL_STATE_LIVE_LOAD".equals(key)) {
			fullStateLoadLive = false;
			return true;
		}
		if ("FULL_STATE_INTERVAL_MS".equals(key)) {
			fullStateIntervalMillis = Math.max(250, Integer.parseInt(value));
			return true;
		}
		if ("FULL_STATE_INTERVAL_SECONDS".equals(key)) {
			fullStateIntervalMillis = Math.max(250, Integer.parseInt(value) * 1000);
			return true;
		}
		return false;
	}

	static void afterConfigLoaded() {
		fullStateSync = false;
		fullStateLoadLive = false;
	}

	static boolean loading() {
		return fullStateLoading;
	}

	static boolean liveLoadEnabled() {
		return fullStateSync && fullStateLoadLive;
	}

	static boolean beginTimedFullState(boolean active) {
		if (!fullStateSync || fullStateSaving || !active)
			return false;
		long now = System.currentTimeMillis();
		long interval = Math.max(250, fullStateIntervalMillis);
		if (now - lastFullStateMillis < interval)
			return false;
		lastFullStateMillis = now;
		fullStateSaving = true;
		return true;
	}

	static void endTimedFullState() {
		fullStateSaving = false;
	}

	static void receiveFullState(String line) {
		if (!liveLoadEnabled())
			return;
		try {
			String[] parts = line.split("\t", 3);
			if (parts.length < 3) {
				CoopLog.warn("Invalid full state packet from host. parts=" + parts.length);
				return;
			}
			byte[] bytes = Base64.getDecoder().decode(parts[2]);
			String saveName = safeStateSaveName();
			Path path = PATHS.local().save().create(saveName);
			Files.write(path, bytes);
			fullStateLoads.add(path);
			CoopRuntime.setStatus("full state received");
		} catch (Exception e) {
			CoopLog.error("Client failed to receive full state packet.", e);
		}
	}

	static void applyQueuedFullState() {
		if (!liveLoadEnabled() || fullStateLoading)
			return;
		Path latest = null;
		Path p;
		while ((p = fullStateLoads.poll()) != null) {
			if (latest != null) {
				try {
					Files.deleteIfExists(latest);
				} catch (IOException ignored) {
				}
			}
			latest = p;
		}
		if (latest != null)
			applyFullState(latest);
	}

	private static void applyFullState(Path path) {
		FileGetter getter = null;
		try {
			if (path == null || !Files.exists(path)) {
				CoopLog.warn("Client full state file was missing before load: " + path);
				return;
			}
			fullStateLoading = true;
			CoopRuntime.beginRemoteApply();
			getter = new FileGetter(path, true);
			GameSpec spec = GameSpec.get(getter);
			CharSequence problem = spec.crashCause();
			if (problem != null) {
				CoopLog.warn("Client refused full state snapshot because it does not match this game: " + problem);
				return;
			}
			Method load = GAME.saver().getClass().getDeclaredMethod("load", FileGetter.class);
			load.setAccessible(true);
			load.invoke(GAME.saver(), getter);
			CoopRuntime.setStatus("full state applied");
			CoopRuntime.log("Full state applied: " + path.getFileName());
		} catch (Exception e) {
			Throwable real = e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() != null
					? ((InvocationTargetException) e).getTargetException()
					: e;
			CoopLog.error("Client failed to apply full state snapshot: " + path, real);
			CoopRuntime.setStatus("full state failed");
		} finally {
			if (getter != null)
				getter.close();
			fullStateLoading = false;
			CoopRuntime.endRemoteApply();
			if (path != null) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static String safeStateSaveName() {
		return "Coop Multiplayer State-" + Long.toHexString(System.currentTimeMillis()) + "-"
				+ Integer.toHexString(game.VERSION.VERSION) + "-" + Integer.toHexString(PATHS.modHash()) + "-0";
	}
}
