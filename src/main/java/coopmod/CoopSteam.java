package coopmod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.lwjgl.BufferUtils;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamFriendsCallback;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamNetworking;
import com.codedisaster.steamworks.SteamNetworkingCallback;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;

import game.GAME;
import game.VERSION;
import game.save.SaveFile;
import init.paths.PATHS;

public final class CoopSteam {

	private static final String KEY_MOD = "syx_together";
	private static final String KEY_MOD_VERSION = "mod_version";
	private static final String KEY_PROTOCOL = "protocol";
	private static final String KEY_GAME = "game_version";
	private static final String KEY_MODS = "mods_hash";
	private static final String KEY_STATE = "state";
	private static final String KEY_PROGRESS = "progress";
	private static final String KEY_TRANSFER_ID = "transfer_id";
	private static final String KEY_SAVE_NAME = "save_name";
	private static final String KEY_SAVE_SIZE = "save_size";
	private static final String STATE_HOST = "host";
	private static final String STATE_CONNECTED = "connected";
	private static final String STATE_DOWNLOADING = "downloading";
	private static final String STATE_READY = "ready";
	private static final String STATE_LOADING = "loading";
	private static final int SAVE_CHANNEL = 7;
	private static final int GAME_CHANNEL = 8;
	private static final int SAVE_CHUNK_BYTES = 12000;
	private static final int MAX_P2P_PACKET_BYTES = 8 * 1024 * 1024;
	private static final int MAX_PLAYERS = 2;
	private static final int AVATAR_GRID = 20;

	private static SteamMatchmaking matchmaking;
	private static SteamFriends friends;
	private static SteamUser user;
	private static SteamUtils utils;
	private static SteamNetworking networking;
	private static SteamID lobby;
	private static boolean callbacksRunning;
	private static boolean searching;
	private static final HashMap<Integer, int[]> avatarCache = new HashMap<>();
	private static final HashSet<Integer> avatarFailures = new HashSet<>();
	private static final HashMap<String, IncomingSave> incomingSaves = new HashMap<>();
	private static Path selectedSavePath;
	private static String selectedSaveName = "";
	private static String selectedTransferId = "";
	private static long selectedSaveSize;
	private static long selectedReplayAfterSeq;
	private static volatile Path steamStartSave;
	private static volatile boolean steamStartRequested;
	private static volatile boolean steamStartWaiting;
	private static volatile String pendingStartTransferId = "";
	private static volatile boolean hostStartWaitingForRuntime;
	private static volatile boolean pendingNewGameSnapshot;
	private static volatile boolean newGameSnapshotSent;
	private static volatile boolean newGameStartSent;

	private CoopSteam() {
	}

	public static synchronized void hostLobby() {
		if (!ensureSteam())
			return;
		CoopMenuLink.setStatus("Creating Steam lobby...");
		matchmaking.createLobby(SteamMatchmaking.LobbyType.FriendsOnly, MAX_PLAYERS);
	}

	public static synchronized void joinLobby() {
		if (!ensureSteam())
			return;
		searching = true;
		CoopMenuLink.setStatus("Searching Steam lobbies...");
		matchmaking.addRequestLobbyListStringFilter(KEY_MOD, "1", SteamMatchmaking.LobbyComparison.Equal);
		matchmaking.addRequestLobbyListStringFilter(KEY_PROTOCOL, Integer.toString(CoopProtocol.PROTOCOL_VERSION), SteamMatchmaking.LobbyComparison.Equal);
		matchmaking.addRequestLobbyListStringFilter(KEY_GAME, Integer.toString(VERSION.VERSION), SteamMatchmaking.LobbyComparison.Equal);
		matchmaking.addRequestLobbyListStringFilter(KEY_MODS, Integer.toString(PATHS.modHash()), SteamMatchmaking.LobbyComparison.Equal);
		matchmaking.addRequestLobbyListDistanceFilter(SteamMatchmaking.LobbyDistanceFilter.Worldwide);
		matchmaking.addRequestLobbyListResultCountFilter(20);
		matchmaking.requestLobbyList();
	}

	public static synchronized void inviteFriends() {
		if (!ensureSteam())
			return;
		if (lobby == null || !lobby.isValid()) {
			CoopMenuLink.setStatus("Create a Steam lobby first.");
			return;
		}
		friends.activateGameOverlayInviteDialog(lobby);
		CoopMenuLink.setStatus("Steam invite overlay opened.");
	}

	public static synchronized void prepareNewGameFlow() {
		if (!lobbyActive() || !localIsLobbyOwner()) {
			CoopMenuLink.setStatus("Create a Steam lobby before starting a new game.");
			return;
		}
		pendingNewGameSnapshot = true;
		newGameSnapshotSent = false;
		newGameStartSent = false;
		selectedSavePath = null;
		selectedSaveName = "New Game";
		selectedTransferId = "";
		selectedSaveSize = 0L;
		selectedReplayAfterSeq = CoopRuntime.replayCursor();
		steamStartSave = null;
		steamStartRequested = false;
		steamStartWaiting = false;
		pendingStartTransferId = "";
		hostStartWaitingForRuntime = false;
		incomingSaves.clear();
		matchmaking.setLobbyData(lobby, KEY_TRANSFER_ID, "");
		matchmaking.setLobbyData(lobby, KEY_SAVE_NAME, selectedSaveName);
		matchmaking.setLobbyData(lobby, KEY_SAVE_SIZE, "0");
		matchmaking.setLobbyData(lobby, "session_state", "new_game_generating");
		setLocalLobbyState(STATE_HOST, 100);
		sendToClients("HOST_LOADING\tNEW_GAME");
		CoopMenuLink.setStatus("Host is creating the city. Clients will load after the throne room is placed.");
	}

	public static void tickNewGameHostSync() {
		if (!pendingNewGameSnapshot || !lobbyActive() || !localIsLobbyOwner())
			return;
		if (!newGameSnapshotSent) {
			sendGeneratedNewGameSnapshot();
			return;
		}
		if (!newGameStartSent && canStartLobbyGame()) {
			startLobbyGame(true);
			newGameStartSent = true;
			pendingNewGameSnapshot = false;
		}
	}

	public static synchronized boolean hostSelectedSave(Path path, String displayName) {
		if (!lobbyActive() || !localIsLobbyOwner()) {
			CoopMenuLink.setStatus("Only the Steam lobby host can choose the save.");
			return false;
		}
		if (path == null || !Files.exists(path)) {
			CoopMenuLink.setStatus("Selected save was not found.");
			return false;
		}
		try {
			boolean newGameFlow = pendingNewGameSnapshot;
			selectedSavePath = path;
			selectedSaveName = displayName == null || displayName.trim().length() == 0 ? path.getFileName().toString() : displayName.trim();
			selectedSaveSize = Files.size(path);
			selectedReplayAfterSeq = CoopRuntime.replayCursor();
			selectedTransferId = Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(System.currentTimeMillis());
			if (!newGameFlow) {
				pendingNewGameSnapshot = false;
				newGameSnapshotSent = false;
				newGameStartSent = false;
			}
			matchmaking.setLobbyData(lobby, KEY_TRANSFER_ID, selectedTransferId);
			matchmaking.setLobbyData(lobby, KEY_SAVE_NAME, selectedSaveName);
			matchmaking.setLobbyData(lobby, KEY_SAVE_SIZE, Long.toString(selectedSaveSize));
			setLocalLobbyState(STATE_HOST, 100);
			CoopMenuLink.setStatus("Sending save to Steam lobby...");
			sendSelectedSaveToClients(null);
			return true;
		} catch (IOException e) {
			CoopMenuLink.setStatus("Could not read selected save: " + e.getMessage());
			CoopLog.error("Steam lobby save selection failed: " + path, e);
			return false;
		} catch (RuntimeException e) {
			CoopMenuLink.setStatus("Steam save setup failed: " + e.getClass().getSimpleName());
			CoopLog.error("Steam lobby save setup failed: " + path, e);
			return false;
		}
	}

	public static synchronized boolean canStartLobbyGame() {
		return selectedSavePath != null && lobbyActive() && localIsLobbyOwner() && allClientsReady();
	}

	public static synchronized Path selectedHostSavePath() {
		return selectedSavePath;
	}

	public static synchronized String selectedHostSaveName() {
		return selectedSaveName == null ? "" : selectedSaveName;
	}

	public static synchronized boolean allClientsReady() {
		if (!lobbyActive() || !localIsLobbyOwner())
			return false;
		int members = lobbyMembers();
		if (members < 2)
			return false;
		for (int i = 0; i < members; i++) {
			SteamID id = member(i);
			if (id == null || isLobbyOwner(id))
				continue;
			if (!STATE_READY.equals(memberData(id, KEY_STATE)))
				return false;
		}
		return true;
	}

	public static synchronized void startLobbyGame() {
		startLobbyGame(false);
	}

	public static synchronized void startLobbyGame(boolean hostAlreadyReady) {
		if (!canStartLobbyGame()) {
			CoopMenuLink.setStatus("Waiting for client save readiness.");
			return;
		}
		if (hostAlreadyReady) {
			hostStartWaitingForRuntime = false;
			sendToClients("HOST_READY\t" + selectedTransferId);
			matchmaking.setLobbyData(lobby, "session_state", "playing");
			CoopMenuLink.setStatus("Host world is ready. Clients are loading.");
			return;
		}
		hostStartWaitingForRuntime = true;
		sendToClients("HOST_LOADING\t" + selectedTransferId);
		matchmaking.setLobbyData(lobby, "session_state", "host_loading");
		CoopMenuLink.setStatus("Host is loading. Clients will join after the world is ready.");
	}

	public static synchronized void hostRuntimeReady() {
		if (!hostStartWaitingForRuntime || !lobbyActive() || !localIsLobbyOwner())
			return;
		hostStartWaitingForRuntime = false;
		sendToClients("HOST_READY\t" + selectedTransferId);
		matchmaking.setLobbyData(lobby, "session_state", "playing");
		CoopMenuLink.setStatus("Host loaded. Clients may enter now.");
	}

	private static void sendGeneratedNewGameSnapshot() {
		try {
			if (!CoopRuntime.hostReadyForClientSnapshot())
				return;
			Path path = GAME.saver().save(SaveFile.stamp("Syx Together New Game"), true);
			if (path == null || !Files.exists(path)) {
				CoopLog.warn("Steam new-game snapshot failed; save path was null or missing.");
				CoopMenuLink.setStatus("New game sync failed: snapshot missing.");
				return;
			}
			if (hostSelectedSave(path, "New Game")) {
				newGameSnapshotSent = true;
				matchmaking.setLobbyData(lobby, "session_state", "new_game_syncing");
				CoopMenuLink.setStatus("New game snapshot sent. Waiting for clients to become ready.");
			}
		} catch (RuntimeException e) {
			CoopMenuLink.setStatus("New game sync failed: " + e.getClass().getSimpleName());
			CoopLog.error("Steam new-game snapshot runtime failure.", e);
		}
	}

	public static Path consumeSteamStartSave() {
		if (!steamStartRequested)
			return null;
		Path p = steamStartSave;
		if (p != null) {
			steamStartRequested = false;
			steamStartSave = null;
			steamStartWaiting = false;
			pendingStartTransferId = "";
		}
		return p;
	}

	public static boolean clientWaitingForHostStart() {
		return steamStartWaiting && !steamStartRequested;
	}

	public static synchronized String lobbyMemberStateText(int slot) {
		SteamID id = member(slot);
		if (id == null)
			return slot == 0 && searching ? "Searching" : "Waiting";
		if (isLobbyOwner(id))
			return "Host";
		String state = memberData(id, KEY_STATE);
		if (STATE_READY.equals(state))
			return "Ready";
		if (STATE_DOWNLOADING.equals(state))
			return "Downloading " + clampProgress(memberData(id, KEY_PROGRESS)) + "%";
		if (STATE_LOADING.equals(state))
			return "Loading";
		if (STATE_CONNECTED.equals(state))
			return "Connected";
		return "Connected";
	}

	public static synchronized int lobbyMemberStateColor(int slot) {
		SteamID id = member(slot);
		if (id == null)
			return 0;
		String state = memberData(id, KEY_STATE);
		if (STATE_READY.equals(state))
			return 2;
		if (STATE_DOWNLOADING.equals(state))
			return 1;
		if (STATE_LOADING.equals(state))
			return 1;
		return 0;
	}

	public static synchronized void leaveLobby() {
		if (matchmaking != null && lobby != null && lobby.isValid()) {
			try {
				matchmaking.leaveLobby(lobby);
			} catch (RuntimeException e) {
				CoopLog.warn("Could not leave Steam lobby cleanly: " + e.getMessage());
			}
		}
		lobby = null;
		searching = false;
		selectedSavePath = null;
		selectedSaveName = "";
		selectedTransferId = "";
		selectedSaveSize = 0;
		steamStartSave = null;
		steamStartRequested = false;
		steamStartWaiting = false;
		pendingStartTransferId = "";
		hostStartWaitingForRuntime = false;
		pendingNewGameSnapshot = false;
		newGameSnapshotSent = false;
		newGameStartSent = false;
		incomingSaves.clear();
		CoopMenuLink.setStatus("Left Steam lobby.");
	}

	public static synchronized boolean lobbyActive() {
		return lobby != null && lobby.isValid();
	}

	public static synchronized boolean searching() {
		return searching;
	}

	public static int maxPlayers() {
		return MAX_PLAYERS;
	}

	public static int avatarGrid() {
		return AVATAR_GRID;
	}

	public static synchronized int lobbyMembers() {
		if (!lobbyActive() || matchmaking == null)
			return 0;
		try {
			return Math.max(0, Math.min(MAX_PLAYERS, matchmaking.getNumLobbyMembers(lobby)));
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam lobby members: " + e.getMessage());
			return 0;
		}
	}

	public static synchronized boolean localIsLobbyOwner() {
		if (!lobbyActive() || matchmaking == null || user == null)
			return false;
		try {
			return same(matchmaking.getLobbyOwner(lobby), user.getSteamID());
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam lobby owner: " + e.getMessage());
			return false;
		}
	}

	public static synchronized String lobbyMemberName(int slot) {
		SteamID id = member(slot);
		if (id == null)
			return slot == 0 && searching ? "Searching..." : "Open Slot";
		String name = null;
		try {
			if (friends != null && user != null && same(id, user.getSteamID()))
				name = friends.getPersonaName();
			if ((name == null || name.length() == 0) && friends != null)
				name = friends.getFriendPersonaName(id);
			if (name == null || name.trim().length() == 0)
				name = "Steam " + lobbyLabel(id);
			return name.trim();
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam lobby member name: " + e.getMessage());
			return "Steam " + lobbyLabel(id);
		}
	}

	public static synchronized String lobbyMemberRole(int slot) {
		SteamID id = member(slot);
		if (id == null)
			return slot == 0 ? "Host" : "Client";
		try {
			return matchmaking != null && same(matchmaking.getLobbyOwner(lobby), id) ? "Host" : "Client";
		} catch (RuntimeException e) {
			return slot == 0 ? "Host" : "Client";
		}
	}

	public static synchronized String lobbyMemberInitial(int slot) {
		String name = lobbyMemberName(slot);
		if (name.length() == 0 || name.equals("Open Slot") || name.equals("Searching..."))
			return Integer.toString(slot + 1);
		return name.substring(0, 1).toUpperCase();
	}

	public static synchronized int lobbyMemberAvatarHandle(int slot) {
		SteamID id = member(slot);
		if (id == null || friends == null)
			return 0;
		return avatarHandle(id);
	}

	public static synchronized int[] lobbyMemberAvatarPixels(int slot) {
		SteamID id = member(slot);
		if (id == null || friends == null || utils == null)
			return null;
		int account = id.getAccountID();
		int[] cached = avatarCache.get(account);
		if (cached != null)
			return cached;
		int image = avatarHandle(id);
		if (image <= 0)
			return null;
		try {
			int[] size = new int[2];
			if (!utils.getImageSize(image, size) || size[0] <= 0 || size[1] <= 0)
				return null;
			int width = size[0];
			int height = size[1];
			ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);
			if (!utils.getImageRGBA(image, rgba))
				return null;
			int[] pixels = new int[AVATAR_GRID * AVATAR_GRID];
			for (int y = 0; y < AVATAR_GRID; y++) {
				int sy = Math.min(height - 1, (y * height) / AVATAR_GRID);
				for (int x = 0; x < AVATAR_GRID; x++) {
					int sx = Math.min(width - 1, (x * width) / AVATAR_GRID);
					int i = (sx + sy * width) * 4;
					int r = rgba.get(i) & 0x0FF;
					int g = rgba.get(i + 1) & 0x0FF;
					int b = rgba.get(i + 2) & 0x0FF;
					int a = rgba.get(i + 3) & 0x0FF;
					pixels[x + y * AVATAR_GRID] = (a << 24) | (r << 16) | (g << 8) | b;
				}
			}
			avatarCache.put(account, pixels);
			avatarFailures.remove(account);
			return pixels;
		} catch (SteamException e) {
			logAvatarFailure(account, "Steam avatar image read failed.", e);
		} catch (RuntimeException e) {
			logAvatarFailure(account, "Steam avatar runtime failure.", e);
		}
		return null;
	}

	private static int avatarHandle(SteamID id) {
		try {
			int image = friends.getMediumFriendAvatar(id);
			if (image <= 0)
				friends.requestUserInformation(id, true);
			return image;
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam avatar handle: " + e.getMessage());
			return 0;
		}
	}

	private static void logAvatarFailure(int account, String message, Exception e) {
		if (avatarFailures.add(account))
			CoopLog.warn(message + " account=" + account + " error=" + e.getMessage());
	}

	private static boolean ensureSteam() {
		try {
			if (matchmaking != null && friends != null && utils != null && networking != null) {
				startCallbacks();
				return true;
			}
			SteamAPI.loadLibraries();
			boolean initialized = SteamAPI.init();
			if (!initialized && !SteamAPI.isSteamRunning()) {
				CoopMenuLink.setStatus("Steam is not running.");
				return false;
			}
			matchmaking = new SteamMatchmaking(matchmakingCallback);
			friends = new SteamFriends(friendsCallback);
			user = new SteamUser(userCallback);
			utils = new SteamUtils(utilsCallback);
			networking = new SteamNetworking(networkingCallback);
			networking.allowP2PPacketRelay(true);
			startCallbacks();
			return true;
		} catch (SteamException e) {
			CoopMenuLink.setStatus("Steam failed: " + e.getMessage());
			CoopLog.error("Steam lobby initialization failed.", e);
		} catch (RuntimeException e) {
			CoopMenuLink.setStatus("Steam failed: " + e.getClass().getSimpleName());
			CoopLog.error("Steam lobby runtime failure.", e);
		}
		return false;
	}

	private static synchronized void startCallbacks() {
		if (callbacksRunning)
			return;
		callbacksRunning = true;
		Thread t = new Thread(() -> {
			while (callbacksRunning) {
				try {
					if (SteamAPI.isSteamRunning())
						SteamAPI.runCallbacks();
					pollSavePackets();
					pollGamePackets();
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					callbacksRunning = false;
				} catch (RuntimeException e) {
					CoopLog.error("Steam callback loop failed.", e);
					callbacksRunning = false;
				}
			}
		}, "syx-together-steam-callbacks");
		t.setDaemon(true);
		t.start();
	}

	private static void setLobbyMetadata(SteamID id) {
		matchmaking.setLobbyData(id, KEY_MOD, "1");
		matchmaking.setLobbyData(id, KEY_MOD_VERSION, CoopProtocol.MOD_VERSION);
		matchmaking.setLobbyData(id, KEY_PROTOCOL, Integer.toString(CoopProtocol.PROTOCOL_VERSION));
		matchmaking.setLobbyData(id, KEY_GAME, Integer.toString(VERSION.VERSION));
		matchmaking.setLobbyData(id, KEY_MODS, Integer.toString(PATHS.modHash()));
		matchmaking.setLobbyData(id, "name", "Syx Together");
		matchmaking.setLobbyJoinable(id, true);
	}

	private static void setLocalLobbyState(String state, int progress) {
		if (matchmaking == null || lobby == null || !lobby.isValid())
			return;
		try {
			matchmaking.setLobbyMemberData(lobby, KEY_STATE, state);
			matchmaking.setLobbyMemberData(lobby, KEY_PROGRESS, Integer.toString(Math.max(0, Math.min(100, progress))));
		} catch (RuntimeException e) {
			CoopLog.warn("Could not set Steam lobby member state: " + e.getMessage());
		}
	}

	private static String lobbyLabel(SteamID id) {
		if (id == null)
			return "unknown";
		return Integer.toString(id.getAccountID());
	}

	private static boolean isLobbyOwner(SteamID id) {
		try {
			return lobbyActive() && matchmaking != null && same(matchmaking.getLobbyOwner(lobby), id);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static synchronized boolean isAuthorizedPeer(SteamID remote) {
		if (remote == null || !remote.isValid() || !lobbyActive() || matchmaking == null || user == null)
			return false;
		try {
			SteamID owner = matchmaking.getLobbyOwner(lobby);
			if (!same(owner, user.getSteamID()))
				return same(owner, remote);
			int members = lobbyMembers();
			for (int i = 0; i < members; i++) {
				SteamID candidate = member(i);
				if (same(candidate, remote) && !same(candidate, user.getSteamID()))
					return true;
			}
		} catch (RuntimeException e) {
			CoopLog.warn("Could not authorize Steam lobby peer: " + e.getMessage());
		}
		return false;
	}

	private static String memberData(SteamID id, String key) {
		if (id == null || matchmaking == null || lobby == null || !lobby.isValid())
			return "";
		try {
			String value = matchmaking.getLobbyMemberData(lobby, id, key);
			return value == null ? "" : value;
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam lobby member data: " + key + " / " + e.getMessage());
			return "";
		}
	}

	private static int clampProgress(String value) {
		try {
			int p = Integer.parseInt(value == null ? "0" : value.trim());
			return Math.max(0, Math.min(100, p));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static SteamID member(int slot) {
		if (!lobbyActive() || matchmaking == null || slot < 0)
			return null;
		try {
			int members = matchmaking.getNumLobbyMembers(lobby);
			if (slot >= members)
				return null;
			SteamID id = matchmaking.getLobbyMemberByIndex(lobby, slot);
			return id != null && id.isValid() ? id : null;
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read Steam lobby member slot " + slot + ": " + e.getMessage());
			return null;
		}
	}

	private static boolean same(SteamID a, SteamID b) {
		return a != null && b != null && a.isValid() && b.isValid() && a.getAccountID() == b.getAccountID();
	}

	private static void sendSelectedSaveToClients(SteamID only) {
		final Path path = selectedSavePath;
		final String id = selectedTransferId;
		final String name = selectedSaveName;
		final long size = selectedSaveSize;
		final long replayAfter = selectedReplayAfterSeq;
		if (path == null || id.length() == 0)
			return;
		List<SteamID> targets = new ArrayList<>();
		if (only != null) {
			targets.add(new SteamID(only));
		} else {
			int members = lobbyMembers();
			for (int i = 0; i < members; i++) {
				SteamID member = member(i);
				if (member != null && !isLobbyOwner(member))
					targets.add(new SteamID(member));
			}
		}
		if (targets.isEmpty())
			return;
		Thread t = new Thread(() -> {
			try {
				byte[] bytes = CoopSaveTransfer.readSaveBytes(path);
				String hash = CoopSaveTransfer.sha256(bytes);
				for (SteamID target : targets)
					sendSaveToClient(target, id, name, size, replayAfter, hash, bytes);
				CoopMenuLink.setStatus("Save sent. Waiting for clients to become ready.");
			} catch (IOException e) {
				CoopMenuLink.setStatus("Could not send Steam save: " + e.getMessage());
				CoopLog.error("Failed to read selected Steam save for transfer: " + path, e);
			} catch (RuntimeException e) {
				CoopMenuLink.setStatus("Steam save transfer failed: " + e.getClass().getSimpleName());
				CoopLog.error("Steam save transfer failed.", e);
			}
		}, "syx-together-steam-save-send");
		t.setDaemon(true);
		t.start();
	}

	private static void sendSaveToClient(SteamID target, String id, String name, long size, long replayAfter, String hash,
			byte[] bytes) {
		sendPacket(target, "SAVE_META\t" + id + "\t" + CoopProtocol.enc(name) + "\t" + size + "\t" + replayAfter + "\t" + hash);
		for (int offset = 0; offset < bytes.length; offset += SAVE_CHUNK_BYTES) {
			int end = Math.min(bytes.length, offset + SAVE_CHUNK_BYTES);
			String chunk = Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, end));
			sendPacket(target, "SAVE_CHUNK\t" + id + "\t" + offset + "\t" + chunk);
		}
		sendPacket(target, "SAVE_DONE\t" + id);
	}

	private static void sendToClients(String line) {
		sendToClients(line, SAVE_CHANNEL);
	}

	private static void sendToClients(String line, int channel) {
		int members = lobbyMembers();
		for (int i = 0; i < members; i++) {
			SteamID member = member(i);
			if (member != null && !isLobbyOwner(member))
				sendPacket(member, line, channel);
		}
	}

	private static boolean sendPacket(SteamID target, String line) {
		return sendPacket(target, line, SAVE_CHANNEL);
	}

	private static boolean sendPacket(SteamID target, String line, int channel) {
		if (networking == null || target == null || !target.isValid())
			return false;
		try {
			byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_P2P_PACKET_BYTES) {
				CoopLog.warn("Rejected oversized Steam P2P send: " + bytes.length + " bytes.");
				return false;
			}
			ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
			buffer.put(bytes);
			buffer.flip();
			return networking.sendP2PPacket(target, buffer, SteamNetworking.P2PSend.Reliable, channel);
		} catch (SteamException e) {
			CoopLog.warn("Steam P2P send failed: " + e.getMessage());
		} catch (RuntimeException e) {
			CoopLog.warn("Steam P2P send runtime failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		return false;
	}

	public static synchronized void sendGameLine(String line) {
		if (line == null || line.length() == 0 || !lobbyActive())
			return;
		if (!ensureSteam())
			return;
		if (localIsLobbyOwner()) {
			sendToClients(line, GAME_CHANNEL);
			return;
		}
		try {
			SteamID owner = matchmaking.getLobbyOwner(lobby);
			if (owner != null && owner.isValid())
				sendPacket(owner, line, GAME_CHANNEL);
		} catch (RuntimeException e) {
			CoopLog.warn("Steam game P2P owner send failed: " + e.getMessage());
		}
	}

	public static void pumpGamePackets() {
		pollGamePackets();
	}

	private static void pollSavePackets() {
		SteamNetworking net = networking;
		if (net == null)
			return;
		int[] size = new int[1];
		int guard = 0;
		while (guard++ < 128 && net.isP2PPacketAvailable(SAVE_CHANNEL, size) && size[0] > 0) {
			if (size[0] > MAX_P2P_PACKET_BYTES) {
				rejectOversizedPacket(SAVE_CHANNEL, size[0]);
				return;
			}
			try {
				ByteBuffer buffer = BufferUtils.createByteBuffer(size[0]);
				SteamID remote = new SteamID();
				int read = net.readP2PPacket(remote, buffer, SAVE_CHANNEL);
				if (read <= 0)
					continue;
				byte[] bytes = new byte[read];
				buffer.position(0);
				buffer.get(bytes, 0, read);
				handleSavePacket(remote, new String(bytes, StandardCharsets.UTF_8));
			} catch (SteamException e) {
				CoopLog.warn("Steam P2P read failed: " + e.getMessage());
			} catch (RuntimeException e) {
				CoopLog.warn("Steam P2P read runtime failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
	}

	private static synchronized void pollGamePackets() {
		SteamNetworking net = networking;
		if (net == null)
			return;
		int[] size = new int[1];
		int guard = 0;
		while (guard++ < 512 && net.isP2PPacketAvailable(GAME_CHANNEL, size) && size[0] > 0) {
			if (size[0] > MAX_P2P_PACKET_BYTES) {
				rejectOversizedPacket(GAME_CHANNEL, size[0]);
				return;
			}
			try {
				ByteBuffer buffer = BufferUtils.createByteBuffer(size[0]);
				SteamID remote = new SteamID();
				int read = net.readP2PPacket(remote, buffer, GAME_CHANNEL);
				if (read <= 0)
					continue;
				byte[] bytes = new byte[read];
				buffer.position(0);
				buffer.get(bytes, 0, read);
				handleGamePacket(remote, new String(bytes, StandardCharsets.UTF_8));
			} catch (SteamException e) {
				CoopLog.warn("Steam game P2P read failed: " + e.getMessage());
			} catch (RuntimeException e) {
				CoopLog.warn("Steam game P2P read runtime failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
	}

	private static synchronized void rejectOversizedPacket(int channel, int size) {
		CoopLog.warn("Rejected oversized Steam P2P packet on channel " + channel + ": " + size + " bytes.");
		SteamNetworking net = networking;
		SteamUser localUser = user;
		if (net == null || localUser == null)
			return;
		SteamID local = localUser.getSteamID();
		int members = lobbyMembers();
		for (int i = 0; i < members; i++) {
			SteamID candidate = member(i);
			if (candidate != null && candidate.isValid() && !same(candidate, local))
				net.closeP2PChannelWithUser(candidate, channel);
		}
	}

	private static void handleGamePacket(SteamID remote, String line) {
		if (line == null || line.length() == 0)
			return;
		if (!isAuthorizedPeer(remote)) {
			CoopLog.warn("Rejected Steam game packet from a user outside the active lobby: " + lobbyLabel(remote));
			return;
		}
		if (line.startsWith("PING\t")) {
			sendPacket(remote, "PONG\t" + line.substring(5), GAME_CHANNEL);
			return;
		}
		if (line.startsWith("PONG\t"))
			return;
		CoopRuntime.receiveSteamGameLine(line);
	}

	private static synchronized void handleSavePacket(SteamID remote, String line) {
		if (line == null || line.length() == 0)
			return;
		if (!isAuthorizedPeer(remote)) {
			CoopLog.warn("Rejected Steam save packet from a user outside the active lobby: " + lobbyLabel(remote));
			return;
		}
		String[] p = line.split("\t", -1);
		try {
			if ("REQ_SAVE".equals(p[0])) {
				if (localIsLobbyOwner())
					sendSelectedSaveToClients(remote);
				return;
			}
			if ("SAVE_META".equals(p[0]) && p.length >= 4) {
				long replayAfter = p.length >= 5 ? parseReplayCursor(p[4]) : 0L;
				String hash = p.length >= 6 ? p[5] : "";
				IncomingSave save = new IncomingSave(p[1], CoopProtocol.dec(p[2]), Long.parseLong(p[3]), replayAfter, hash);
				incomingSaves.put(save.id, save);
				setLocalLobbyState(STATE_DOWNLOADING, 0);
				CoopMenuLink.setStatus("Downloading Steam lobby save...");
				return;
			}
			if ("SAVE_CHUNK".equals(p[0]) && p.length >= 4) {
				IncomingSave save = incomingSaves.get(p[1]);
				if (save == null)
					return;
				save.transfer.append(Long.parseLong(p[2]), p[3]);
				int progress = save.transfer.progress();
				setLocalLobbyState(STATE_DOWNLOADING, progress);
				return;
			}
			if ("SAVE_DONE".equals(p[0]) && p.length >= 2) {
				IncomingSave save = incomingSaves.remove(p[1]);
				if (save == null)
					return;
				Path path = PATHS.local().save().create(safeSteamSaveName());
				Files.write(path, save.transfer.finish());
				steamStartSave = path;
				CoopRuntime.expectReplayAfter(save.replayAfterSeq);
				setLocalLobbyState(STATE_READY, 100);
				if (steamStartWaiting)
					CoopMenuLink.setStatus("Save ready. Waiting for host world...");
				else
					CoopMenuLink.setStatus("Save ready. Waiting for host to start.");
				return;
			}
			if ("HOST_LOADING".equals(p[0])) {
				pendingStartTransferId = p.length >= 2 ? p[1] : "";
				steamStartWaiting = true;
				setLocalLobbyState(STATE_LOADING, 100);
				CoopMenuLink.setStatus("Host is loading. Waiting for the host world...");
				return;
			}
			if ("HOST_READY".equals(p[0]) || "START".equals(p[0])) {
				if (steamStartSave == null) {
					steamStartWaiting = true;
					pendingStartTransferId = p.length >= 2 ? p[1] : pendingStartTransferId;
					CoopMenuLink.setStatus("Host is ready, but this client is still waiting for the save.");
					return;
				}
				if (pendingStartTransferId.length() > 0 && p.length >= 2 && !"NEW_GAME".equals(pendingStartTransferId)
						&& !pendingStartTransferId.equals(p[1])) {
					CoopMenuLink.setStatus("Host ready signal did not match this save transfer.");
					CoopLog.warn("Steam host ready transfer mismatch. waiting=" + pendingStartTransferId + " ready=" + p[1]);
					return;
				}
				CoopRuntime.menuSteamClient();
				steamStartWaiting = false;
				pendingStartTransferId = "";
				steamStartRequested = true;
				CoopMenuLink.setStatus("Host world ready. Loading Steam save...");
			}
		} catch (IOException e) {
			CoopMenuLink.setStatus("Steam save write failed: " + e.getMessage());
			CoopLog.error("Steam lobby save receive failed.", e);
		} catch (RuntimeException e) {
			CoopMenuLink.setStatus("Steam save packet failed: " + e.getClass().getSimpleName());
			CoopLog.error("Steam lobby save packet failed: " + CoopProtocol.trim(line), e);
		}
	}

	private static String safeSteamSaveName() {
		return "Syx Together Client-" + Long.toHexString(System.currentTimeMillis()) + "-"
				+ Integer.toHexString(VERSION.VERSION) + "-" + Integer.toHexString(PATHS.modHash()) + "-0";
	}

	private static long parseReplayCursor(String value) {
		try {
			return Math.max(0L, Long.parseLong(value));
		} catch (NumberFormatException e) {
			CoopLog.warn("Invalid Steam snapshot replay cursor: " + value);
			return 0L;
		}
	}

	private static final SteamMatchmakingCallback matchmakingCallback = new SteamMatchmakingCallback() {
		@Override
		public void onLobbyCreated(SteamResult result, SteamID steamIDLobby) {
			if (result != SteamResult.OK) {
				CoopMenuLink.setStatus("Steam lobby failed: " + result);
				return;
			}
			lobby = steamIDLobby;
			setLobbyMetadata(steamIDLobby);
			CoopMenuLink.setStatus("Steam lobby created. Invite friends when ready.");
			setLocalLobbyState(STATE_HOST, 100);
		}

		@Override
		public void onLobbyEnter(SteamID steamIDLobby, int chatPermissions, boolean blocked, SteamMatchmaking.ChatRoomEnterResponse response) {
			if (response != SteamMatchmaking.ChatRoomEnterResponse.Success) {
				CoopMenuLink.setStatus("Steam lobby join failed: " + response);
				return;
			}
			lobby = steamIDLobby;
			CoopMenuLink.setStatus("Joined Steam lobby " + lobbyLabel(steamIDLobby) + ".");
			if (localIsLobbyOwner())
				setLocalLobbyState(STATE_HOST, 100);
			else {
				setLocalLobbyState(STATE_CONNECTED, 0);
				String transfer = matchmaking.getLobbyData(steamIDLobby, KEY_TRANSFER_ID);
				if (transfer != null && transfer.length() > 0)
					sendPacket(matchmaking.getLobbyOwner(steamIDLobby), "REQ_SAVE\t" + transfer);
			}
		}

		@Override
		public void onLobbyMatchList(int lobbiesMatching) {
			searching = false;
			if (lobbiesMatching <= 0) {
				CoopMenuLink.setStatus("No compatible Steam lobbies found.");
				return;
			}
			for (int i = 0; i < lobbiesMatching; i++) {
				SteamID id = matchmaking.getLobbyByIndex(i);
				if (id != null && id.isValid()) {
					CoopMenuLink.setStatus("Joining Steam lobby " + lobbyLabel(id) + "...");
					matchmaking.joinLobby(id);
					return;
				}
			}
			CoopMenuLink.setStatus("No valid Steam lobby found.");
		}

		@Override
		public void onLobbyInvite(SteamID steamIDUser, SteamID steamIDLobby, long gameID) {
			CoopMenuLink.setStatus("Steam lobby invite received. Joining...");
			matchmaking.joinLobby(steamIDLobby);
		}

		@Override
		public void onLobbyDataUpdate(SteamID steamIDLobby, SteamID steamIDMember, boolean success) {
		}

		@Override
		public void onLobbyChatUpdate(SteamID steamIDLobby, SteamID steamIDUserChanged, SteamID steamIDMakingChange, SteamMatchmaking.ChatMemberStateChange stateChange) {
			if (lobby != null && lobby.getAccountID() == steamIDLobby.getAccountID()) {
				CoopMenuLink.setStatus("Steam lobby updated: " + stateChange);
				if (localIsLobbyOwner() && selectedSavePath != null && steamIDUserChanged != null && !isLobbyOwner(steamIDUserChanged))
					sendSelectedSaveToClients(steamIDUserChanged);
			}
		}

		@Override
		public void onLobbyChatMessage(SteamID steamIDLobby, SteamID steamIDUser, SteamMatchmaking.ChatEntryType entryType, int chatID) {
		}

		@Override
		public void onLobbyGameCreated(SteamID steamIDLobby, SteamID steamIDGameServer, int ip, short port) {
		}

		@Override
		public void onLobbyKicked(SteamID steamIDLobby, SteamID steamIDAdmin, boolean kickedDueToDisconnect) {
			if (lobby != null && lobby.getAccountID() == steamIDLobby.getAccountID())
				lobby = null;
			CoopMenuLink.setStatus("Left Steam lobby.");
		}

		@Override
		public void onFavoritesListChanged(int ip, int queryPort, int connPort, int appID, int flags, boolean add, int accountID) {
		}

		@Override
		public void onFavoritesListAccountsUpdated(SteamResult result) {
		}
	};

	private static final SteamFriendsCallback friendsCallback = new SteamFriendsCallback() {
		@Override
		public void onGameLobbyJoinRequested(SteamID steamIDLobby, SteamID steamIDFriend) {
			if (ensureSteam()) {
				CoopMenuLink.setStatus("Joining Steam invite...");
				matchmaking.joinLobby(steamIDLobby);
			}
		}

		@Override
		public void onGameOverlayActivated(boolean active) {
		}

		@Override
		public void onSetPersonaNameResponse(boolean success, boolean localSuccess, SteamResult result) {
		}

		@Override
		public void onPersonaStateChange(SteamID steamID, SteamFriends.PersonaChange change) {
		}

		@Override
		public void onAvatarImageLoaded(SteamID steamID, int image, int width, int height) {
			if (steamID != null)
				avatarCache.remove(steamID.getAccountID());
		}

		@Override
		public void onFriendRichPresenceUpdate(SteamID steamIDFriend, int appID) {
		}

		@Override
		public void onGameRichPresenceJoinRequested(SteamID steamIDFriend, String connect) {
		}

		@Override
		public void onGameServerChangeRequested(String server, String password) {
		}
	};

	private static final SteamUserCallback userCallback = new SteamUserCallback() {
		@Override
		public void onAuthSessionTicket(com.codedisaster.steamworks.SteamAuthTicket ticket, SteamResult result) {
		}

		@Override
		public void onValidateAuthTicket(SteamID steamID, com.codedisaster.steamworks.SteamAuth.AuthSessionResponse authSessionResponse, SteamID ownerSteamID) {
		}

		@Override
		public void onMicroTxnAuthorization(int appID, long orderID, boolean authorized) {
		}

		@Override
		public void onEncryptedAppTicket(SteamResult result) {
		}
	};

	private static final SteamUtilsCallback utilsCallback = new SteamUtilsCallback() {
		@Override
		public void onSteamShutdown() {
			CoopMenuLink.setStatus("Steam has shut down.");
			callbacksRunning = false;
		}
	};

	private static final SteamNetworkingCallback networkingCallback = new SteamNetworkingCallback() {
		@Override
		public void onP2PSessionConnectFail(SteamID steamIDRemote, SteamNetworking.P2PSessionError sessionError) {
			CoopLog.warn("Steam P2P session failed with " + lobbyLabel(steamIDRemote) + ": " + sessionError);
		}

		@Override
		public void onP2PSessionRequest(SteamID steamIDRemote) {
			if (networking != null && isAuthorizedPeer(steamIDRemote))
				networking.acceptP2PSessionWithUser(steamIDRemote);
			else
				CoopLog.warn("Rejected Steam P2P session request outside the active lobby: " + lobbyLabel(steamIDRemote));
		}
	};

	private static final class IncomingSave {
		final String id;
		final String name;
		final long size;
		final long replayAfterSeq;
		final CoopSaveTransfer.Incoming transfer;

		IncomingSave(String id, String name, long size, long replayAfterSeq, String hash) throws IOException {
			this.id = id;
			this.name = name;
			this.size = size;
			this.replayAfterSeq = Math.max(0L, replayAfterSeq);
			this.transfer = new CoopSaveTransfer.Incoming(id, name, size, hash);
		}
	}
}
