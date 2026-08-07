package coopmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import game.VERSION;
import init.paths.PATHS;

public final class CoopMenuLink {

	private static volatile boolean connecting;
	private static volatile String status = "Idle.";
	private static volatile Path receivedSave;
	private static final String MENU_CACHE = "multiplayer-menu-cache.txt";

	private CoopMenuLink() {
	}

	public static void prepareHost(int port) {
		CoopRuntime.menuHost(port);
		status = "LAN host prepared on port " + port + ".";
	}

	public static void prepareClient(String host, int port) {
		CoopRuntime.menuClient(host, port);
		status = "Client prepared for " + host + ":" + port + ".";
	}

	public static void requestSnapshot(String host, int port) {
		if (connecting) {
			status = "Already connecting...";
			return;
		}
		if (host == null || host.trim().length() == 0) {
			status = "Enter the host IP first.";
			return;
		}
		final String connectHost = host.trim();
		rememberEndpoint(connectHost, port);
		prepareClient(connectHost, port);
		connecting = true;
		status = "Connecting to " + connectHost + ":" + port + "...";
		Thread t = new Thread(() -> {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(connectHost, port), 8000);
				socket.setSoTimeout(120000);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				status = "Connected. Checking versions...";
				out.println(CoopRuntime.helloLine());
				out.flush();
				String line = CoopProtocol.readNetworkLine(in);
				String problem = CoopRuntime.validateHandshakeLine(line, "host");
				if (problem != null) {
					status = problem;
					CoopLog.warn("Host refused multiplayer handshake: " + problem);
					return;
				}
				status = "Handshake OK. Requesting host save...";
				out.println("REQ_SAVE");
				out.flush();
				line = readSnapshotResponse(in);
				if (line == null) {
					status = "Host closed the connection.";
					CoopLog.warn("Client did not receive a save because the host closed the connection. host=" + connectHost + ":" + port);
					return;
				}
				if (line.startsWith("ERR\t")) {
					status = line.substring(4);
					CoopLog.warn("Host refused snapshot request: " + status);
					return;
				}
				CoopSaveTransfer.Snapshot snapshot = CoopSaveTransfer.readSnapshot(in, line);
				String saveName = safeReceivedSaveName();
				Path path = PATHS.local().save().create(saveName);
				Files.write(path, snapshot.bytes);
				CoopRuntime.expectReplayAfter(snapshot.replayAfter);
				receivedSave = path;
				status = "Save received. Loading...";
			} catch (ProtocolException e) {
				status = e.getMessage();
				CoopLog.warn("Client snapshot protocol failed. host=" + connectHost + ":" + port + " error=" + e.getMessage());
			} catch (Exception e) {
				CoopRuntime.rethrowFatal(e);
				status = "Connect failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
				CoopLog.error("Client connect/snapshot receive failed. host=" + connectHost + ":" + port, e);
			} finally {
				connecting = false;
			}
		}, "sos-coop-menu-client");
		t.setDaemon(true);
		t.start();
	}

	public static Path consumeReceivedSave() {
		Path p = receivedSave;
		if (p != null)
			receivedSave = null;
		return p;
	}

	public static String status() {
		return status;
	}

	public static void setStatus(String value) {
		status = value == null ? "" : value;
	}

	public static String lastHost() {
		return cacheValue("LAST_HOST", "");
	}

	public static String lastPortText() {
		String p = cacheValue("LAST_PORT", "49710");
		try {
			int port = Integer.parseInt(p.trim());
			if (port > 0 && port < 65536)
				return Integer.toString(port);
		} catch (Exception e) {
		}
		return "49710";
	}

	private static String safeReceivedSaveName() {
		return "Coop Multiplayer Client-" + Long.toHexString(System.currentTimeMillis()) + "-"
				+ Integer.toHexString(VERSION.VERSION) + "-" + Integer.toHexString(PATHS.modHash()) + "-0";
	}

	private static String readSnapshotResponse(BufferedReader in) throws IOException {
		for (int i = 0; i < 256; i++) {
			String line = CoopProtocol.readNetworkLine(in);
			if (line == null || line.startsWith("SAVE\t") || line.startsWith("SAVE_META\t") || line.startsWith("ERR\t"))
				return line;
			CoopLog.warn("Ignored non-save packet while waiting for snapshot: " + trim(line));
		}
		throw new ProtocolException("Host sent too many non-save packets while preparing the snapshot.");
	}

	static void rememberEndpoint(String host, int port) {
		try {
			String text = "LAST_HOST=" + host + "\r\nLAST_PORT=" + port + "\r\n";
			Files.write(cachePath(), text.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			CoopRuntime.rethrowFatal(e);
			CoopLog.warn("Could not save multiplayer menu cache: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static String cacheValue(String key, String fallback) {
		try {
			Path p = cachePath();
			if (!Files.exists(p))
				return fallback;
			for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.startsWith(key + "="))
					return line.substring(key.length() + 1).trim();
			}
		} catch (Exception e) {
			CoopRuntime.rethrowFatal(e);
		}
		return fallback;
	}

	private static Path cachePath() {
		try {
			URI uri = CoopMenuLink.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path jar = Paths.get(uri);
			Path script = jar.getParent();
			Path v71 = script == null ? null : script.getParent();
			Path root = v71 == null ? null : v71.getParent();
			if (root != null)
				return root.resolve(MENU_CACHE);
		} catch (Exception e) {
		}
		return Paths.get(MENU_CACHE);
	}

	private static String trim(String line) {
		if (line == null)
			return "";
		if (line.length() <= 220)
			return line;
		return line.substring(0, 220) + "...";
	}

	private static long parseReplayCursor(String value) {
		try {
			return Math.max(0L, Long.parseLong(value));
		} catch (NumberFormatException e) {
			CoopLog.warn("Invalid snapshot replay cursor: " + value);
			return 0L;
		}
	}

	private static final class ProtocolException extends IOException {
		ProtocolException(String message) {
			super(message);
		}
	}
}
