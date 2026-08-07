package coopmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;

import game.VERSION;
import init.paths.PATHS;

public final class CoopLanLobby {

	private static final int SAVE_CHUNK_BYTES = 12000;
	private static final String STATE_CONNECTED = "Connected";
	private static final String STATE_DOWNLOADING = "Downloading";
	private static final String STATE_READY = "Ready";

	private static volatile ServerSocket serverSocket;
	private static volatile LobbyPeer clientPeer;
	private static volatile LobbyPeer serverPeer;
	private static volatile boolean hostActive;
	private static volatile boolean clientConnecting;
	private static volatile boolean clientConnected;
	private static volatile boolean clientReady;
	private static volatile boolean startRequested;
	private static volatile boolean runtimeSnapshotRetrying;
	private static volatile boolean clientWaitingForHostStart;
	private static volatile boolean waitingForNewGameSnapshot;
	private static volatile int clientProgress;
	private static volatile int port = 49710;
	private static volatile String clientLabel = "Open Slot";
	private static volatile String clientState = "Waiting";
	private static volatile String selectedSaveName = "";
	private static volatile String selectedTransferId = "";
	private static volatile String releaseAckId = "";
	private static volatile Path selectedSavePath;
	private static volatile Path clientStartSave;

	private CoopLanLobby() {
	}

	public static synchronized void hostLobby(int p) {
		CoopRuntime.resetForMenuLobby();
		port = p;
		closeClientSide();
		closeHostSide();
		hostActive = true;
		clientConnected = false;
		clientReady = false;
		clientProgress = 0;
		clientLabel = "Open Slot";
		clientState = "Waiting";
		selectedSaveName = "";
		selectedSavePath = null;
		selectedTransferId = "";
		releaseAckId = "";
		startRequested = false;
		clientWaitingForHostStart = false;
		waitingForNewGameSnapshot = false;
		startDaemon(new HostServerTask(p), "syx-together-lan-lobby-host");
		CoopMenuLink.setStatus("LAN lobby open on port " + p + ".");
	}

	public static synchronized void ensureHostLobby(int p) {
		if (hostActive) {
			CoopMenuLink.setStatus("LAN lobby already open on port " + port + ".");
			return;
		}
		hostLobby(p);
	}

	public static synchronized void connect(String host, int p) {
		CoopRuntime.resetForMenuLobby();
		if (clientConnecting) {
			CoopMenuLink.setStatus("Already connecting...");
			return;
		}
		if (host == null || host.trim().length() == 0) {
			CoopMenuLink.setStatus("Enter the host IP first.");
			return;
		}
		closeHostSide();
		closeClientSide();
		final String target = host.trim();
		port = p;
		clientConnecting = true;
		clientConnected = false;
		clientReady = false;
		clientProgress = 0;
		clientLabel = "You";
		clientStartSave = null;
		startRequested = false;
		runtimeSnapshotRetrying = false;
		clientWaitingForHostStart = false;
		waitingForNewGameSnapshot = false;
		CoopMenuLink.rememberEndpoint(target, p);
		CoopMenuLink.setStatus("Connecting to LAN lobby " + target + ":" + p + "...");
		startDaemon(new ClientConnectionTask(target, p), "syx-together-lan-lobby-client");
	}

	public static synchronized boolean hostSelectedSave(Path path, String displayName) {
		if (!hostActive) {
			CoopMenuLink.setStatus("Create a LAN lobby before choosing a save.");
			return false;
		}
		if (path == null || !Files.exists(path)) {
			CoopMenuLink.setStatus("Selected save was not found.");
			return false;
		}
		try {
			selectedSavePath = path;
			selectedSaveName = displayName == null || displayName.trim().length() == 0 ? path.getFileName().toString() : displayName.trim();
			selectedTransferId = Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(System.currentTimeMillis());
			clientReady = false;
			clientProgress = 0;
			if (clientConnected)
				clientState = STATE_DOWNLOADING + " 0%";
			CoopMenuLink.setStatus("Selected save: " + selectedSaveName + ".");
			sendSelectedSaveToClient();
			return true;
		} catch (RuntimeException e) {
			CoopMenuLink.setStatus("LAN save setup failed: " + e.getClass().getSimpleName());
			CoopLog.error("LAN lobby save setup failed: " + path, e);
			return false;
		}
	}

	public static synchronized boolean canStartLobbyGame() {
		return hostActive && selectedSavePath != null && clientConnected && clientReady;
	}

	public static synchronized boolean canStartNewGame() {
		return hostActive && clientConnected;
	}

	public static synchronized boolean prepareNewGameFlow() {
		if (!canStartNewGame()) {
			CoopMenuLink.setStatus("Waiting for a LAN client before starting a new game.");
			return false;
		}
		selectedSavePath = null;
		selectedSaveName = "New Game";
		selectedTransferId = Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(System.currentTimeMillis());
		releaseAckId = "";
		clientReady = false;
		clientProgress = 0;
		clientState = "Waiting for host setup";
		waitingForNewGameSnapshot = true;
		LobbyPeer peer = clientPeer;
		CoopRuntime.menuHostDeferred(port);
		releaseHostLobbyAsync(peer, "NEW_GAME_WAIT\t" + selectedTransferId, "starting LAN new game");
		CoopMenuLink.setStatus("LAN new game started. Client will join after the throne room is placed.");
		return true;
	}

	public static synchronized void startLobbyGame() {
		if (!canStartLobbyGame()) {
			CoopMenuLink.setStatus("Waiting for the LAN client to finish downloading the save.");
			return;
		}
		LobbyPeer peer = clientPeer;
		releaseAckId = "";
		CoopRuntime.menuHostDeferred(port);
		releaseHostLobbyAsync(peer, "HOST_LOADING\t" + selectedTransferId, "loading LAN save");
		CoopMenuLink.setStatus("Host is loading. Client will join after the world is ready.");
	}

	public static synchronized void stopLobby() {
		closeHostSide();
		closeClientSide();
	}

	public static Path consumeStartSave() {
		if (!startRequested)
			return null;
		Path p = clientStartSave;
		if (p != null) {
			startRequested = false;
			clientStartSave = null;
			clientWaitingForHostStart = false;
			waitingForNewGameSnapshot = false;
		}
		return p;
	}

	public static boolean clientWaitingForHostStart() {
		return clientWaitingForHostStart && !startRequested;
	}

	public static String selectedSaveName() {
		return selectedSaveName == null ? "" : selectedSaveName;
	}

	public static String lobbyMemberName(int slot) {
		if (slot == 0)
			return "Host";
		if (clientConnecting)
			return clientLabel;
		return clientConnected ? clientLabel : "Open Slot";
	}

	public static String lobbyMemberRole(int slot) {
		return slot == 0 ? "Host" : "Client";
	}

	public static String lobbyMemberStateText(int slot) {
		if (slot == 0)
			return "Host";
		if (clientConnected)
			return clientState;
		if (clientConnecting)
			return "Connecting";
		return "Waiting";
	}

	public static int lobbyMemberStateColor(int slot) {
		if (slot == 0)
			return 2;
		if (clientReady)
			return 2;
		if (clientConnected && clientProgress > 0)
			return 1;
		return 0;
	}

	public static String localAddressHint() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
					continue;
				Enumeration<InetAddress> addresses = ni.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress a = addresses.nextElement();
					String value = a.getHostAddress();
					if (value != null && value.indexOf(':') < 0 && !value.startsWith("127."))
						return value;
				}
			}
		} catch (SocketException e) {
			CoopLog.warn("Could not detect LAN IP: " + e.getMessage());
		}
		return "unknown";
	}

	private static void runHostServer(int p) {
		try (ServerSocket ss = new ServerSocket(p, 1, InetAddress.getByName("0.0.0.0"))) {
			serverSocket = ss;
			while (hostActive) {
				Socket socket = ss.accept();
				socket.setTcpNoDelay(true);
				LobbyPeer peer = new LobbyPeer(socket);
				if (!acceptHandshake(peer)) {
					peer.close();
					continue;
				}
				synchronized (CoopLanLobby.class) {
					if (clientPeer != null && !clientPeer.closed) {
						peer.send("ERR\tLAN lobby is full.");
						peer.close();
						continue;
					}
					clientPeer = peer;
					clientConnected = true;
					clientReady = false;
					clientProgress = 0;
					clientLabel = "LAN Client";
					clientState = STATE_CONNECTED;
				}
				CoopMenuLink.setStatus("LAN client connected.");
				peer.startHostReader();
				sendSelectedSaveToClient();
			}
		} catch (SocketException e) {
			if (hostActive) {
				CoopLog.warn("LAN lobby host socket closed: " + e.getMessage());
				CoopMenuLink.setStatus("LAN lobby failed on port " + p + ": " + e.getMessage());
				markHostFailed();
			}
		} catch (IOException e) {
			CoopMenuLink.setStatus("LAN lobby failed on port " + p + ": " + e.getClass().getSimpleName());
			CoopLog.error("LAN lobby host failed on port " + p, e);
			markHostFailed();
		} finally {
			serverSocket = null;
		}
	}

	private static synchronized void markHostFailed() {
		hostActive = false;
		clientConnected = false;
		clientReady = false;
		clientProgress = 0;
		clientLabel = "Open Slot";
		clientState = "Waiting";
	}

	private static void runClient(String host, int p) {
		try {
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(host, p), 8000);
			socket.setSoTimeout(120000);
			socket.setTcpNoDelay(true);
			LobbyPeer peer = new LobbyPeer(socket);
			peer.send(CoopRuntime.helloLine());
			String line = CoopProtocol.readNetworkLine(peer.in);
			String problem = CoopRuntime.validateHandshakeLine(line, "host");
			if (problem != null) {
				CoopMenuLink.setStatus(problem);
				peer.close();
				return;
			}
			synchronized (CoopLanLobby.class) {
				serverPeer = peer;
				clientConnected = true;
				clientConnecting = false;
				clientReady = false;
				clientProgress = 0;
				clientLabel = "You";
				clientState = STATE_CONNECTED;
			}
			CoopMenuLink.setStatus("Connected to LAN lobby. Waiting for host save.");
			peer.startClientReader(host, p);
		} catch (java.net.SocketTimeoutException e) {
			CoopMenuLink.setStatus("Connection timed out. Host IP is unreachable or TCP port " + p + " is not open.");
			CoopLog.warn("LAN lobby connect timed out. host=" + host + ":" + p);
		} catch (java.net.ConnectException e) {
			CoopMenuLink.setStatus("Connection refused. The host is not listening on TCP port " + p + ".");
			CoopLog.warn("LAN lobby connection refused. host=" + host + ":" + p + " error=" + e.getMessage());
		} catch (IOException e) {
			CoopMenuLink.setStatus("Connect failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			CoopLog.error("LAN lobby connect failed. host=" + host + ":" + p, e);
		} finally {
			clientConnecting = false;
		}
	}

	private static boolean acceptHandshake(LobbyPeer peer) {
		try {
			String line = CoopProtocol.readNetworkLine(peer.in);
			String problem = CoopRuntime.validateHandshakeLine(line, "client");
			if (problem != null) {
				peer.send("ERR\t" + problem);
				CoopLog.warn("Rejected LAN lobby client: " + problem);
				return false;
			}
			peer.send(CoopRuntime.helloOkLine());
			return true;
		} catch (IOException e) {
			CoopLog.warn("Rejected LAN lobby client; handshake failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			return false;
		}
	}

	private static void sendSelectedSaveToClient() {
		final LobbyPeer peer = clientPeer;
		final Path path = selectedSavePath;
		final String id = selectedTransferId;
		final String name = selectedSaveName;
		if (peer == null || peer.closed || path == null || id.length() == 0)
			return;
		startDaemon(new SaveSenderTask(peer, path, id, name), "syx-together-lan-save-send");
	}

	private static String safeLanSaveName() {
		return "Syx Together LAN Client-" + Long.toHexString(System.currentTimeMillis()) + "-"
				+ Integer.toHexString(VERSION.VERSION) + "-" + Integer.toHexString(PATHS.modHash()) + "-0";
	}

	private static void releaseHostLobbyAsync(final LobbyPeer peer, final String line, final String reason) {
		final String ackId = transitionId(line);
		startDaemon(new LobbyReleaseTask(peer, line, reason, ackId), "syx-together-lan-lobby-release");
	}

	private static String transitionId(String line) {
		if (line == null)
			return "";
		String[] parts = line.split("\t", -1);
		if (parts.length < 2)
			return "";
		return parts[1];
	}

	private static synchronized void closeHostSide() {
		hostActive = false;
		LobbyPeer p = clientPeer;
		clientPeer = null;
		if (p != null)
			p.close();
		ServerSocket ss = serverSocket;
		serverSocket = null;
		if (ss != null) {
			try {
				ss.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static synchronized void closeClientSide() {
		LobbyPeer p = serverPeer;
		serverPeer = null;
		if (p != null)
			p.close();
		clientConnecting = false;
		runtimeSnapshotRetrying = false;
		clientWaitingForHostStart = false;
		waitingForNewGameSnapshot = false;
	}

	private static void startDaemon(Runnable task, String name) {
		Thread t = new Thread(task, name);
		t.setDaemon(true);
		t.start();
	}

	private static final class HostServerTask implements Runnable {
		private final int port;

		HostServerTask(int port) {
			this.port = port;
		}

		@Override
		public void run() {
			runHostServer(port);
		}
	}

	private static final class ClientConnectionTask implements Runnable {
		private final String host;
		private final int port;

		ClientConnectionTask(String host, int port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public void run() {
			runClient(host, port);
		}
	}

	private static final class SaveSenderTask implements Runnable {
		private final LobbyPeer peer;
		private final Path path;
		private final String id;
		private final String name;

		SaveSenderTask(LobbyPeer peer, Path path, String id, String name) {
			this.peer = peer;
			this.path = path;
			this.id = id;
			this.name = name;
		}

		@Override
		public void run() {
			try {
				byte[] bytes = CoopSaveTransfer.readSaveBytes(path);
				peer.send("SAVE_META\t" + id + "\t" + CoopProtocol.enc(name) + "\t" + bytes.length + "\t"
						+ CoopSaveTransfer.sha256(bytes));
				for (int offset = 0; offset < bytes.length; offset += SAVE_CHUNK_BYTES) {
					int end = Math.min(bytes.length, offset + SAVE_CHUNK_BYTES);
					String chunk = Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, end));
					peer.send("SAVE_CHUNK\t" + id + "\t" + offset + "\t" + chunk);
				}
				peer.send("SAVE_DONE\t" + id);
				CoopMenuLink.setStatus("LAN save sent. Waiting for client Ready.");
			} catch (IOException e) {
				CoopMenuLink.setStatus("Could not send LAN save: " + e.getMessage());
				CoopLog.error("Failed to send LAN lobby save: " + path, e);
			} catch (RuntimeException e) {
				CoopMenuLink.setStatus("LAN save transfer failed: " + e.getClass().getSimpleName());
				CoopLog.error("LAN lobby save transfer failed.", e);
			}
		}
	}

	private static final class LobbyReleaseTask implements Runnable {
		private final LobbyPeer peer;
		private final String line;
		private final String reason;
		private final String ackId;

		LobbyReleaseTask(LobbyPeer peer, String line, String reason, String ackId) {
			this.peer = peer;
			this.line = line;
			this.reason = reason;
			this.ackId = ackId;
		}

		@Override
		public void run() {
			try {
				boolean sent = false;
				for (int i = 0; i < 8; i++) {
					if (peer == null || peer.closed || line == null)
						break;
					sent |= peer.send(line);
					if (ackId.length() > 0 && ackId.equals(releaseAckId))
						break;
					try {
						Thread.sleep(100L);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				if (!sent)
					CoopLog.warn("LAN lobby release could not send transition while " + reason + ".");
				else if (ackId.length() > 0 && !ackId.equals(releaseAckId))
					CoopLog.warn("LAN lobby release closed without client ack while " + reason + ".");
			} catch (RuntimeException e) {
				CoopLog.error("LAN lobby release failed while " + reason + ".", e);
			} finally {
				closeHostSide();
				CoopRuntime.releaseDeferredNetworkStart();
			}
		}
	}

	private static final class ProtocolException extends IOException {
		ProtocolException(String message) {
			super(message);
		}
	}

	private static final class LobbyPeer {
		final Socket socket;
		final PrintWriter out;
		final BufferedReader in;
		volatile boolean closed;
		CoopSaveTransfer.Incoming incoming;

		LobbyPeer(Socket socket) throws IOException {
			this.socket = socket;
			this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		}

		void startHostReader() {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						String line;
						while ((line = CoopProtocol.readNetworkLine(in)) != null) {
							if (line.startsWith("READY\t")) {
								clientReady = true;
								clientProgress = 100;
								clientState = STATE_READY;
								CoopMenuLink.setStatus("LAN client Ready.");
							} else if (line.startsWith("ERR\t")) {
								CoopMenuLink.setStatus(line.substring(4));
							} else if (line.startsWith("WAIT_ACK\t")) {
								releaseAckId = line.substring(9);
							}
						}
					} catch (IOException e) {
						if (!closed)
							CoopLog.warn("LAN lobby client disconnected: " + e.getMessage());
					} finally {
						closed = true;
						if (clientPeer == LobbyPeer.this) {
							clientPeer = null;
							clientConnected = false;
							clientReady = false;
							clientProgress = 0;
							clientLabel = "Open Slot";
							clientState = "Waiting";
						}
						close();
					}
				}
			}, "syx-together-lan-lobby-host-peer");
			t.setDaemon(true);
			t.start();
		}

		void startClientReader(final String host, final int p) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						String line;
						while ((line = CoopProtocol.readNetworkLine(in)) != null)
							handleClientLine(line, host, p);
					} catch (IOException e) {
						if (!closed)
							CoopLog.warn("LAN lobby host disconnected: " + e.getMessage());
					} finally {
						closed = true;
						close();
					}
				}
			}, "syx-together-lan-lobby-client-peer");
			t.setDaemon(true);
			t.start();
		}

		void handleClientLine(String line, String host, int p) {
			String[] parts = line.split("\t", -1);
			try {
				if ("SAVE_META".equals(parts[0]) && parts.length >= 4) {
					String hash = parts.length >= 5 ? parts[4] : "";
					incoming = new CoopSaveTransfer.Incoming(parts[1], CoopProtocol.dec(parts[2]), Long.parseLong(parts[3]), hash);
					clientReady = false;
					clientProgress = 0;
					clientState = STATE_DOWNLOADING + " 0%";
					CoopMenuLink.setStatus("Downloading LAN lobby save...");
					return;
				}
				if ("SAVE_CHUNK".equals(parts[0]) && parts.length >= 4) {
					if (incoming == null || !incoming.id.equals(parts[1]))
						return;
					incoming.append(Long.parseLong(parts[2]), parts[3]);
					clientProgress = incoming.progress();
					clientState = STATE_DOWNLOADING + " " + clientProgress + "%";
					return;
				}
				if ("SAVE_DONE".equals(parts[0]) && parts.length >= 2) {
					if (incoming == null || !incoming.id.equals(parts[1]))
						return;
					Path path = PATHS.local().save().create(safeLanSaveName());
					Files.write(path, incoming.finish());
					clientStartSave = path;
					clientReady = true;
					clientProgress = 100;
					clientState = STATE_READY;
					send("READY\t" + incoming.id);
					incoming = null;
					CoopMenuLink.setStatus("LAN save Ready. Waiting for host to start.");
					return;
				}
				if ("NEW_GAME_WAIT".equals(parts[0])) {
					if (parts.length > 1)
						send("WAIT_ACK\t" + parts[1]);
					clientReady = false;
					clientProgress = 0;
					clientState = "Waiting for host setup";
					clientWaitingForHostStart = true;
					waitingForNewGameSnapshot = true;
					CoopMenuLink.setStatus("Host is creating the city. You will join after the throne room is placed.");
					startRuntimeSnapshotRetry(host, p);
					return;
				}
				if ("HOST_LOADING".equals(parts[0])) {
					if (parts.length > 1)
						send("WAIT_ACK\t" + parts[1]);
					clientWaitingForHostStart = true;
					waitingForNewGameSnapshot = false;
					clientState = "Waiting for host";
					CoopMenuLink.setStatus("Host is loading. Waiting for the host world...");
					startRuntimeSnapshotRetry(host, p);
					return;
				}
				if ("START".equals(parts[0])) {
					if (clientStartSave == null) {
						send("ERR\tClient save is not ready yet.");
						CoopMenuLink.setStatus("Host started before this client had the save ready.");
						return;
					}
					CoopRuntime.menuClient(host, p);
					startRequested = true;
					CoopMenuLink.setStatus("Host started. Loading LAN save...");
				}
			} catch (IOException e) {
				CoopMenuLink.setStatus("LAN save write failed: " + e.getMessage());
				CoopLog.error("LAN lobby save receive failed.", e);
			} catch (RuntimeException e) {
				CoopMenuLink.setStatus("LAN save packet failed: " + e.getClass().getSimpleName());
				CoopLog.error("LAN lobby save packet failed: " + CoopProtocol.trim(line), e);
			}
		}

		void startRuntimeSnapshotRetry(final String host, final int p) {
			if (runtimeSnapshotRetrying)
				return;
			runtimeSnapshotRetrying = true;
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					int attempts = 0;
					try {
						while (runtimeSnapshotRetrying && !startRequested) {
							attempts++;
							int result = requestRuntimeSnapshot(host, p, attempts);
							if (result > 0)
								return;
							if (result < 0)
								return;
							try {
								Thread.sleep(2500);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								CoopMenuLink.setStatus("LAN new game wait was interrupted.");
								return;
							}
						}
					} finally {
						runtimeSnapshotRetrying = false;
					}
				}
			}, "syx-together-lan-new-game-wait");
			t.setDaemon(true);
			t.start();
		}

		int requestRuntimeSnapshot(String host, int p, int attempts) {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(host, p), 3000);
				socket.setSoTimeout(120000);
				socket.setTcpNoDelay(true);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				out.println(CoopRuntime.helloLine());
				out.flush();
				String line = CoopProtocol.readNetworkLine(in);
				String problem = CoopRuntime.validateHandshakeLine(line, "host");
				if (problem != null) {
					if (problem.indexOf("Connection closed before handshake") >= 0) {
						if (attempts == 1 || attempts % 8 == 0)
							CoopMenuLink.setStatus(waitingForNewGameSnapshot ? "Waiting for host to place the throne room..." : "Waiting for host to finish loading...");
						return 0;
					}
					CoopMenuLink.setStatus(problem);
					CoopLog.warn("LAN new game host refused handshake: " + problem);
					return -1;
				}
				out.println("REQ_SAVE");
				out.flush();
				line = readRuntimeSnapshotResponse(in);
				if (line == null) {
					CoopMenuLink.setStatus(waitingForNewGameSnapshot ? "Waiting for host city setup..." : "Waiting for host to finish loading...");
					return 0;
				}
				if (line.startsWith("ERR\t")) {
					if (attempts == 1 || attempts % 8 == 0)
						CoopMenuLink.setStatus(waitingForNewGameSnapshot ? "Waiting for host to place the throne room..." : "Waiting for host to finish loading...");
					return 0;
				}
				CoopSaveTransfer.Snapshot snapshot = CoopSaveTransfer.readSnapshot(in, line);
				Path path = PATHS.local().save().create(safeLanSaveName());
				Files.write(path, snapshot.bytes);
				clientStartSave = path;
				CoopRuntime.expectReplayAfter(snapshot.replayAfter);
				clientReady = true;
				clientProgress = 100;
				clientState = STATE_READY;
				CoopRuntime.menuClient(host, p);
				clientWaitingForHostStart = false;
				waitingForNewGameSnapshot = false;
				startRequested = true;
				CoopMenuLink.setStatus("Host city ready. Loading LAN save...");
				return 1;
			} catch (ProtocolException e) {
				CoopMenuLink.setStatus("LAN new game sync failed: " + e.getMessage());
				CoopLog.warn("LAN new game snapshot protocol failed. host=" + host + ":" + p + " error=" + e.getMessage());
				return -1;
			} catch (java.net.SocketTimeoutException e) {
				if (attempts == 1 || attempts % 8 == 0)
					CoopMenuLink.setStatus("Waiting for host to open the LAN game...");
				return 0;
			} catch (java.net.ConnectException e) {
				if (attempts == 1 || attempts % 8 == 0)
					CoopMenuLink.setStatus("Waiting for host to open the LAN game...");
				return 0;
			} catch (java.net.SocketException e) {
				if (attempts == 1 || attempts % 8 == 0)
					CoopMenuLink.setStatus(waitingForNewGameSnapshot ? "Waiting for host to place the throne room..." : "Waiting for host to finish loading...");
				if (attempts == 1 || attempts % 16 == 0)
					CoopLog.warn("LAN runtime snapshot socket not ready yet. attempt=" + attempts + " host=" + host + ":" + p + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
				return 0;
			} catch (IOException e) {
				CoopMenuLink.setStatus("LAN new game sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
				CoopLog.error("LAN new game snapshot receive failed. host=" + host + ":" + p, e);
				return -1;
			} catch (RuntimeException e) {
				CoopMenuLink.setStatus("LAN new game sync failed: " + e.getClass().getSimpleName());
				CoopLog.error("LAN new game snapshot packet failed.", e);
				return -1;
			}
		}

		String readRuntimeSnapshotResponse(BufferedReader in) throws IOException {
			for (int i = 0; i < 256; i++) {
				String line = CoopProtocol.readNetworkLine(in);
				if (line == null || line.startsWith("SAVE\t") || line.startsWith("SAVE_META\t") || line.startsWith("ERR\t"))
					return line;
				CoopLog.warn("Ignored non-save packet while waiting for LAN new game snapshot: " + CoopProtocol.trim(line));
			}
			throw new ProtocolException("Host sent too many non-save packets while preparing the new game snapshot.");
		}

		long parseReplayCursor(String value) {
			try {
				return Math.max(0L, Long.parseLong(value));
			} catch (NumberFormatException e) {
				CoopLog.warn("Invalid LAN snapshot replay cursor: " + value);
				return 0L;
			}
		}

		boolean send(String line) {
			if (closed)
				return false;
			if (line == null || line.length() > CoopProtocol.MAX_NETWORK_LINE_CHARS) {
				CoopLog.warn("Rejected oversized LAN lobby packet.");
				close();
				return false;
			}
			out.println(line);
			out.flush();
			if (out.checkError()) {
				closed = true;
				return false;
			}
			return true;
		}

		void close() {
			closed = true;
			try {
				out.flush();
			} catch (RuntimeException ignored) {
			}
			try {
				socket.shutdownOutput();
			} catch (IOException ignored) {
			} catch (RuntimeException ignored) {
			}
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}
}
