package coopmod;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import game.battle.div.Div;
import game.event.engine.EContext;
import game.event.engine.Event;
import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealSave;
import game.GameSpec;
import game.GAME;
import game.save.SaveFile;
import game.time.TIME;
import init.constant.C;
import init.paths.PATHS;
import init.race.RACES;
import init.race.Race;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.tech.TECH;
import init.tech.TECHS;
import init.trade.TR;
import init.trade.TRADABLE;
import init.type.CRIME_PUNISHMENTS;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import init.type.HGROUP;
import init.type.HGROUP.HTypeBits;
import init.type.HGROUP.HTypeBitsImp;
import init.type.WGROUP;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.animal.Animal;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AISTATE;
import settlement.job.Job;
import settlement.job.JobBuild;
import settlement.job.ROOM_JOBBER;
import settlement.main.SETT;
import settlement.room.home.house.HomeInstance;
import settlement.room.home.house.HomeInstance.State;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import settlement.room.main.ROOMA;
import settlement.room.main.employment.RoomEmployment;
import settlement.room.main.furnisher.FurnisherItemGroup;
import settlement.room.main.job.StorageCrate;
import settlement.room.main.job.ROOM_EMPLOY_AUTO;
import settlement.room.main.placement.RoomPlacer;
import settlement.room.main.throne.THRONE;
import settlement.room.main.util.RoomState;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBattle.StatTraining;
import settlement.stats.colls.StatsBurial.StatGrave;
import settlement.stats.colls.StatsEducation;
import settlement.stats.colls.StatsReligion.StatReligion;
import settlement.stats.equip.EquipBattle;
import settlement.stats.equip.EquipCivic;
import settlement.stats.law.StatCrime;
import settlement.stats.muls.StatsMultipliers.StatMultiplier;
import settlement.stats.muls.StatsMultipliers.StatMultiplierAction;
import settlement.stats.service.StatServiceImp;
import settlement.stats.stat.STAT;
import settlement.stats.stat.StatDecree;
import settlement.thing.THINGS.Thing;
import settlement.thing.ThingsResources.ScatteredResource;
import settlement.weather.WeatherThing;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Rec;
import snake2d.util.file.FileGetter;
import snake2d.util.sets.LIST;
import view.main.VIEW;
import view.ui.message.Message;
import view.world.ui.faction.UIFactions;
import view.tool.PLACABLE;
import view.tool.PLACER_TYPE;
import view.tool.PlacableFixed;
import view.tool.PlacableMulti;
import view.tool.PlacableSimple;
import view.tool.PlacableSimpleTile;
import view.tool.PlacableSingle;
import world.WORLD;
import world.entity.army.WArmy;
import world.map.regions.Region;

public final class CoopRuntime {

	private enum Mode {
		OFF,
		HOST,
		CLIENT
	}

	private static final String MOD_VERSION = CoopProtocol.MOD_VERSION;
	private static final int PROTOCOL_VERSION = CoopProtocol.PROTOCOL_VERSION;
	private static final int PING_INTERVAL_MILLIS = 1000;
	private static final int PENDING_FINISH_RETRY_MILLIS = 250;
	private static final int PENDING_FINISH_MAX_ATTEMPTS = 60;
	private static final int REPLAY_BUFFER_LIMIT = 20000;
	private static final int SAVE_CHUNK_BYTES = 12000;
	private static final int MAX_INCOMING_PACKETS = 16384;

	private static final ConcurrentHashMap<String, PLACABLE> registry = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, String> classFallback = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, String> nameFallback = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Incoming> incoming = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger incomingCount = new AtomicInteger();
	private static final ConcurrentLinkedQueue<CoopPendingFinish> pendingFinishes = new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<Peer> snapshotRequests = new ConcurrentLinkedQueue<>();
	private static final CopyOnWriteArrayList<Peer> clients = new CopyOnWriteArrayList<>();
	private static final Set<String> appliedCommandIds = ConcurrentHashMap.newKeySet();
	private static final Queue<String> appliedCommandOrder = new ConcurrentLinkedQueue<>();
	private static final Set<String> pendingFinishKeys = ConcurrentHashMap.newKeySet();
	private static final AtomicLong commandSeq = new AtomicLong();
	private static final AtomicLong replaySeq = new AtomicLong();
	private static final Object replayLock = new Object();
	private static final Deque<ReplayEntry> replayBuffer = new ArrayDeque<>();
	private static final String nodeId = Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(System.currentTimeMillis());

	private static volatile boolean applyingRemote;
	private static volatile boolean started;
	private static volatile boolean debug = true;
	private static volatile Mode mode = Mode.OFF;
	private static volatile String host = "127.0.0.1";
	private static volatile int port = 49710;
	private static volatile Peer serverPeer;
	private static volatile ServerSocket serverSocket;
	private static volatile boolean networkActive;
	private static volatile boolean networkStartDeferred;
	private static volatile boolean steamTransport;
	private static volatile String status = "not started";
	private static volatile boolean menuOverride;
	private static volatile Mode menuMode = Mode.OFF;
	private static volatile String menuHost = "127.0.0.1";
	private static volatile int menuPort = 49710;
	private static volatile long pendingReplayAfterSeq = -1L;
	private static volatile boolean pendingReplayRequested;
	private static int tick;
	private static int lastScanTick = -100000;

	private CoopRuntime() {
	}

	public static void rethrowFatal(Throwable t) {
		if (t == null)
			return;
		Throwable real = t instanceof InvocationTargetException && ((InvocationTargetException) t).getTargetException() != null
				? ((InvocationTargetException) t).getTargetException()
				: t;
		CoopFatal.rethrow(real);
	}

	public static void update() {
		if (!started) {
			started = true;
			loadConfig();
			startNetwork();
		}
		if (steamTransport)
			CoopSteam.pumpGamePackets();
		tick++;
		requestPendingReplay(null);
		if (tick - lastScanTick > 300) {
			lastScanTick = tick;
			scanForPlacables();
		}
		if (mode == Mode.CLIENT)
			CoopSnapshotSync.applyQueuedFullState();
		Incoming incomingLine;
		int guard = 0;
		while (guard++ < 1024 && (incomingLine = incoming.poll()) != null) {
			incomingCount.decrementAndGet();
			applyIncoming(incomingLine);
		}
		if (mode == Mode.CLIENT)
			applyPendingFinishes();
		Peer peer;
		guard = 0;
		while (guard++ < 2 && (peer = snapshotRequests.poll()) != null) {
			sendSnapshot(peer);
		}
		if (mode == Mode.HOST) {
			if (steamTransport) {
				CoopSteam.hostRuntimeReady();
				CoopSteam.tickNewGameHostSync();
			}
			sendTimedFullState();
			sendWorldState();
			sendUiSanityState();
			sendNpcState();
		}
		sendCursorState();
		sendPings();
	}

	public static void render(Renderer r) {
		CoopCursor.render(r, mode != Mode.OFF && networkActive);
	}

	public static void mouseClick(MButt button) {
		CoopCursor.mouseClick(button, mode != Mode.OFF && networkActive, configPath());
	}

	public static void hover(COORDINATE mCoo, boolean mouseHasMoved) {
		CoopCursor.capture(mCoo);
	}

	public static void menuHost(int p) {
		prepareMenuRuntime();
		configureMenuHost(p, false);
	}

	static void menuHostDeferred(int p) {
		prepareMenuRuntime();
		configureMenuHost(p, true);
	}

	private static void configureMenuHost(int p, boolean deferred) {
		menuOverride = true;
		menuMode = Mode.HOST;
		menuHost = "127.0.0.1";
		menuPort = p;
		steamTransport = false;
		mode = Mode.HOST;
		port = p;
		networkStartDeferred = deferred;
		clearPendingReplayRequest();
		status = deferred ? "waiting for LAN lobby release" : "host prepared";
	}

	static synchronized void releaseDeferredNetworkStart() {
		networkStartDeferred = false;
		if (started && mode == Mode.HOST && !networkActive)
			startNetwork();
	}

	static boolean hostReadyForClientSnapshot() {
		try {
			if (!VIEW.canSave())
				return false;
			COORDINATE throne = THRONE.coo();
			return throne != null && SETT.ROOMS().THRONE.get(throne.x(), throne.y()) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static void menuClient(String h, int p) {
		prepareMenuRuntime();
		menuOverride = true;
		menuMode = Mode.CLIENT;
		menuHost = h;
		menuPort = p;
		steamTransport = false;
		mode = Mode.CLIENT;
		host = h;
		port = p;
		status = "client prepared";
	}

	public static void menuSteamHost() {
		prepareMenuRuntime();
		menuOverride = true;
		menuMode = Mode.HOST;
		steamTransport = true;
		mode = Mode.HOST;
		clearPendingReplayRequest();
		status = "steam host prepared";
	}

	public static void menuSteamClient() {
		prepareMenuRuntime();
		menuOverride = true;
		menuMode = Mode.CLIENT;
		steamTransport = true;
		mode = Mode.CLIENT;
		status = "steam client prepared";
	}

	public static void resetForMenuLobby() {
		prepareMenuRuntime();
		menuOverride = false;
		mode = Mode.OFF;
		status = "menu lobby ready";
	}

	private static synchronized void prepareMenuRuntime() {
		if (networkActive || serverSocket != null || serverPeer != null || !clients.isEmpty())
			disconnect("resetting multiplayer runtime for menu");
		started = false;
		networkStartDeferred = false;
		incoming.clear();
		incomingCount.set(0);
		appliedCommandIds.clear();
		appliedCommandOrder.clear();
		clearPendingReplayRequest();
	}

	static long replayCursor() {
		return replaySeq.get();
	}

	public static void expectReplayAfter(long seq) {
		pendingReplayAfterSeq = Math.max(0L, seq);
		pendingReplayRequested = false;
	}

	private static void clearPendingReplayRequest() {
		pendingReplayAfterSeq = -1L;
		pendingReplayRequested = false;
	}

	public static boolean isApplyingRemote() {
		return applyingRemote;
	}

	static void beginRemoteApply() {
		applyingRemote = true;
	}

	static void endRemoteApply() {
		applyingRemote = false;
	}

	static void remoteApplyFailed(String line, Throwable e) {
		log("Failed remote action: " + line + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
		CoopLog.error("Failed to apply remote action: " + line, e);
		rethrowFatal(e);
	}

	public static boolean remoteControlsHumanoid(Humanoid humanoid) {
		return CoopNpcSync.remoteControls(humanoid, mode == Mode.CLIENT,
				networkActive && clientConnected() && !CoopSnapshotSync.loading());
	}

	public static boolean remoteControlsAnimal(Animal animal) {
		return CoopAnimalSync.remoteControls(animal, mode == Mode.CLIENT,
				networkActive && clientConnected() && !CoopSnapshotSync.loading());
	}

	public static boolean remoteControlsEvents() {
		return mode == Mode.CLIENT && networkActive && clientConnected() && !CoopSnapshotSync.loading() && !applyingRemote;
	}

	private static boolean hasRemoteClients() {
		return steamTransport ? CoopSteam.lobbyMembers() > 1 : !clients.isEmpty();
	}

	private static boolean clientConnected() {
		return steamTransport ? CoopSteam.lobbyActive() : serverPeer != null;
	}

	public static boolean remoteHumanoidUpdate(Humanoid h, double ds) {
		CoopNpcSync.applyTarget(h, ds);
		return h != null && !h.isRemoved();
	}

	public static boolean remoteAnimalUpdate(Animal animal, double ds) {
		CoopAnimalSync.applyTarget(animal, ds);
		return animal != null && !animal.isRemoved();
	}

	public static void register(PLACABLE placer) {
		if (placer == null)
			return;
		String key = key(placer);
		registry.put(key, placer);
		classFallback.putIfAbsent(placer.getClass().getName(), key);
		String n = safeName(placer);
		if (n.length() > 0)
			nameFallback.putIfAbsent(n, key);
	}

	public static void activated(PLACABLE placer) {
		register(placer);
	}

	public static void placedMulti(PlacableMulti placer, PLACER_TYPE type, AREA area) {
		if (applyingRemote || placer == null || area == null || area.area() <= 0)
			return;
		register(placer);
		RoomPlacer rp = roomPlacerFrom(placer);
		RoomBlueprintImp blue = roomBlueprint(rp);
		if (rp != null && blue != null) {
			int kind = rp.coopMultiKind(placer);
			if (kind >= 0) {
				sendCommand("RM\t" + CoopProtocol.enc(blue.key) + "\t" + roomUpgrade(rp) + "\t" + kind + "\t"
						+ typeIndex(type) + "\t" + CoopProtocol.enc(tiles(area)));
				return;
			}
		}
		sendCommand("M\t" + CoopProtocol.enc(key(placer)) + "\t" + typeIndex(type) + "\t" + CoopProtocol.enc(tiles(area)));
	}

	public static void placedFixed(PlacableFixed placer, int cx, int cy) {
		if (applyingRemote || placer == null)
			return;
		register(placer);
		RoomPlacer rp = roomPlacerFrom(placer);
		RoomBlueprintImp blue = roomBlueprint(rp);
		int group = roomItemGroup(placer);
		if (rp != null && blue != null && group >= 0) {
			sendCommand("RF\t" + CoopProtocol.enc(blue.key) + "\t" + roomUpgrade(rp) + "\t" + group + "\t" + cx + "\t" + cy + "\t" + placer.rot() + "\t" + placer.size());
			return;
		}
		sendCommand("F\t" + CoopProtocol.enc(key(placer)) + "\t" + cx + "\t" + cy + "\t" + placer.rot() + "\t" + placer.size());
	}

	public static void placedSimple(PlacableSimple placer, int x, int y) {
		if (applyingRemote || placer == null)
			return;
		register(placer);
		sendCommand("S\t" + CoopProtocol.enc(key(placer)) + "\t" + x + "\t" + y);
	}

	public static void placedSimpleTile(PlacableSimpleTile placer, int tx, int ty) {
		if (applyingRemote || placer == null)
			return;
		register(placer);
		sendCommand("T\t" + CoopProtocol.enc(key(placer)) + "\t" + tx + "\t" + ty);
	}

	public static void placedSingle(PlacableSingle placer, int tx, int ty) {
		if (applyingRemote || placer == null)
			return;
		register(placer);
		sendCommand("G\t" + CoopProtocol.enc(key(placer)) + "\t" + tx + "\t" + ty);
	}

	public static void speedChanged(double speed) {
		if (applyingRemote)
			return;
		sendCommand("V\t" + speed);
	}

	public static void roomInit(RoomBlueprintImp blueprint, int upgrade) {
		if (applyingRemote)
			return;
		if (blueprint == null)
			return;
		String key = blueprint == null ? "" : blueprint.key;
		sendCommand("RI\t" + CoopProtocol.enc(key) + "\t" + upgrade);
	}

	public static void roomCreate(RoomPlacer placer) {
		if (applyingRemote)
			return;
		RoomBlueprintImp blue = roomBlueprint(placer);
		if (blue == null)
			return;
		sendCommand("RC\t" + CoopProtocol.enc(blue.key) + "\t" + roomUpgrade(placer));
	}

	public static boolean deferRoomCreateToHost(RoomPlacer placer) {
		if (applyingRemote || mode != Mode.CLIENT || !networkActive || !clientConnected())
			return false;
		roomCreate(placer);
		return true;
	}

	public static void roomConstructionFinished(int tx, int ty) {
		if (applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		sendCommand("RFIN\t" + tx + "\t" + ty);
	}

	public static void roomJobFinished(int tx, int ty, RESOURCE resource, int amount) {
		if (applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		int ri = resource == null ? -1 : resource.index();
		sendCommand("RJ\t" + tx + "\t" + ty + "\t" + ri + "\t" + amount);
	}

	public static void jobBuildFinished(String jobKey, int tx, int ty) {
		if (applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		sendCommand("JB\t" + CoopProtocol.enc(jobKey == null ? "" : jobKey) + "\t" + tx + "\t" + ty);
	}

	public static void messageSent(Message message) {
		if (message == null || applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		if ("game.event.engine.EventMessage".equals(message.getClass().getName()))
			return;
		try {
			sendCommand("MSG\t" + CoopProtocol.enc(CoopProtocol.serializeMessage(message)));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to serialize/sync message: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void eventStarted(int index, String key, EContext context, boolean keepInfo, boolean keepTime, boolean clearContext, boolean message) {
		if (applyingRemote || mode != Mode.HOST || !networkActive || context == null)
			return;
		try {
			sendCommand("EVS\t" + index + "\t" + CoopProtocol.enc(key == null ? "" : key) + "\t" + CoopProtocol.bool(keepInfo) + "\t" + CoopProtocol.bool(keepTime) + "\t" + CoopProtocol.bool(clearContext) + "\t" + CoopProtocol.bool(message) + "\t" + CoopProtocol.enc(CoopProtocol.serializeContext(context)));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to serialize/sync event start: " + key + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void eventTemporary(int index, String key, EContext context) {
		if (applyingRemote || mode != Mode.HOST || !networkActive || context == null)
			return;
		try {
			sendCommand("EVT\t" + index + "\t" + CoopProtocol.enc(key == null ? "" : key) + "\t" + CoopProtocol.enc(CoopProtocol.serializeContext(context)));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to serialize/sync temporary event: " + key + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void eventCleared() {
		if (applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		sendCommand("EVE");
	}

	public static boolean eventChoiceRequest(Event event, EContext context, int choice) {
		if (applyingRemote || event == null || context == null)
			return false;
		if (mode == Mode.CLIENT && networkActive && clientConnected()) {
			try {
				sendCommand("EVC\t" + event.allIndex + "\t" + CoopProtocol.enc(event.key) + "\t" + choice + "\t" + CoopProtocol.enc(CoopProtocol.serializeContext(context)));
			} catch (Exception e) {
				rethrowFatal(e);
				CoopLog.warn("Failed to send event choice request: " + event.key + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			return true;
		}
		return false;
	}

	public static void eventChoiceSelected(Event event, EContext context, int choice) {
		if (applyingRemote || mode != Mode.HOST || !networkActive || event == null || context == null)
			return;
		try {
			sendCommand("EVC\t" + event.allIndex + "\t" + CoopProtocol.enc(event.key) + "\t" + choice + "\t" + CoopProtocol.enc(CoopProtocol.serializeContext(context)));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync event choice: " + event.key + " / " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void playerCreditsChanged(FCredits creditsObject, double credits) {
		if (applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		try {
			if (creditsObject != FACTIONS.player().credits())
				return;
			sendCommand("CR\t" + Math.round(credits * 100.0));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync player credits: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void diplomacyChanged(Faction instigator, Faction accepter, DipStance stance) {
		if (applyingRemote || mode == Mode.OFF || !networkActive || instigator == null || accepter == null || stance == null)
			return;
		if (mode == Mode.CLIENT && instigator != FACTIONS.player() && accepter != FACTIONS.player())
			return;
		sendCommand("DP\t" + instigator.index() + "\t" + accepter.index() + "\t" + stance.index());
	}

	public static void techLevelChanged(TECH tech, int level) {
		if (!CoopGameplaySync.canSendTech(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected(), tech))
			return;
		sendCommand("TECH\t" + tech.index() + "\t" + level);
	}

	public static void tradeImportSettingsChanged(TRADABLE tradable) {
		if (!canSendTradeSettings(tradable))
			return;
		try {
			sendCommand("TBI\t" + tradable.index() + "\t" + FACTIONS.player().buyer(tradable).priceCapsI.get() + "\t"
					+ FACTIONS.player().buyer(tradable).minMoney.get() + "\t" + FACTIONS.player().buyer(tradable).limit.get());
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync import settings: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void tradeExportSettingsChanged(TRADABLE tradable) {
		if (!canSendTradeSettings(tradable))
			return;
		try {
			sendCommand("TBE\t" + tradable.index() + "\t" + FACTIONS.player().seller(tradable).priceCapsI.get() + "\t"
					+ FACTIONS.player().seller(tradable).limit.get());
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync export settings: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void roomEmploymentNeededChanged(RoomInstance room, int needed) {
		if (!canSendRoomEmployment(room))
			return;
		sendCommand("REN\t" + room.mX() + "\t" + room.mY() + "\t" + needed);
	}

	public static void roomAutoEmployChanged(RoomInstance room, boolean auto) {
		if (!canSendRoomEmployment(room))
			return;
		sendCommand("RAE\t" + room.mX() + "\t" + room.mY() + "\t" + CoopProtocol.bool(auto));
	}

	public static void roomEmploymentPriorityChanged(RoomEmployment employment, int priority) {
		if (!canSendEmploymentType(employment))
			return;
		sendCommand("REP\t" + employment.index() + "\t" + priority);
	}

	public static void roomEmploymentGroupPriorityChanged(RoomEmployment employment, WGROUP group, int priority) {
		if (!canSendEmploymentType(employment) || group == null)
			return;
		sendCommand("REGP\t" + employment.index() + "\t" + group.index() + "\t" + priority);
	}

	private static boolean canSendGameplayMenu() {
		return CoopGameplaySync.canSendGameplayMenu(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected());
	}

	private static int raceIndex(Race race) {
		return race == null ? -1 : race.index;
	}

	private static int hclassIndex(HCLASS cl) {
		return cl == null ? -1 : cl.index();
	}

	private static Race raceByIndex(int index) {
		if (index < 0 || index >= RACES.all().size())
			return null;
		return RACES.all().get(index);
	}

	private static HCLASS hclassByIndex(int index) {
		if (index < 0 || index >= HCLASSES.ALL().size())
			return null;
		return HCLASSES.ALL().get(index);
	}

	private static HCLASS_RACE hclassRaceByIndex(int index) {
		if (index < 0 || index >= HCLASS_RACE.ALL().size())
			return null;
		return HCLASS_RACE.ALL().get(index);
	}

	private static <T> int indexOf(LIST<T> list, T value) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == value)
				return i;
		}
		return -1;
	}

	private static String bitsString(HTypeBits bits) {
		StringBuilder sb = new StringBuilder(HGROUP.all().size());
		for (int i = 0; i < HGROUP.all().size(); i++)
			sb.append(bits != null && bits.is(i) ? '1' : '0');
		return sb.toString();
	}

	private static HTypeBitsImp bitsFromString(String value) {
		HTypeBitsImp bits = new HTypeBitsImp(false);
		if (value != null) {
			for (int i = 0; i < value.length() && i < HGROUP.all().size(); i++) {
				if (value.charAt(i) == '1')
					bits.set(HGROUP.all().get(i));
			}
		}
		return bits;
	}

	public static void foodPermissionChanged(int foodIndex, HCLASS cl, Race race, boolean value) {
		if (canSendGameplayMenu())
			sendCommand("GPF\t" + foodIndex + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + CoopProtocol.bool(value));
	}

	public static void civicEquipmentTargetChanged(EquipCivic civic, HCLASS cl, Race race, int target) {
		if (canSendGameplayMenu() && civic != null)
			sendCommand("GCE\t" + civic.index() + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + target);
	}

	public static void homeFurnitureTargetChanged(RESOURCE res, HCLASS cl, Race race, int target) {
		if (canSendGameplayMenu() && res != null)
			sendCommand("GHF\t" + res.index() + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + target);
	}

	public static void servicePermissionChanged(StatServiceImp service, HCLASS_RACE group, boolean value) {
		if (canSendGameplayMenu() && service != null && group != null)
			sendCommand("GSP\t" + indexOf(STATS.SERVICE().ALL, service) + "\t" + group.index() + "\t" + CoopProtocol.bool(value));
	}

	public static void religionPermissionChanged(StatReligion religion, int kind, HCLASS cl, Race race, boolean value) {
		if (canSendGameplayMenu() && religion != null)
			sendCommand("GRP\t" + religion.index() + "\t" + kind + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + CoopProtocol.bool(value));
	}

	public static void gravePermissionChanged(StatGrave grave, HCLASS cl, Race race, boolean value) {
		if (canSendGameplayMenu() && grave != null)
			sendCommand("GGP\t" + indexOf(STATS.BURIAL().graves(), grave) + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + CoopProtocol.bool(value));
	}

	public static void immigrationAutoChanged(Race race, int value) {
		if (canSendGameplayMenu() && race != null)
			sendCommand("GIA\t" + race.index + "\t" + value);
	}

	public static void reproductionLimitChanged(HCLASS cl, Race race, int value) {
		if (canSendGameplayMenu())
			sendCommand("GRL\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + value);
	}

	public static void reproductionForcedChanged(HCLASS cl, Race race, int value) {
		if (canSendGameplayMenu())
			sendCommand("GRF\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + value);
	}

	public static void educationLimitChanged(StatsEducation.AgeType age, HCLASS cl, Race race, int value) {
		if (canSendGameplayMenu() && age != null)
			sendCommand("GEL\t" + indexOf(STATS.EDUCATION().allAges, age) + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + value);
	}

	public static void statDecreeChanged(StatDecree decree, HCLASS cl, Race race, int value) {
		if (!canSendGameplayMenu() || decree == null)
			return;
		int statIndex = statIndexForDecree(decree);
		if (statIndex >= 0)
			sendCommand("GSD\t" + statIndex + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + value);
	}

	public static void statMultiplierAutoChanged(StatMultiplierAction action, HCLASS cl, Race race, int value) {
		if (canSendGameplayMenu() && action != null)
			sendCommand("GMA\t" + action.index() + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + value);
	}

	public static void statMultiplierMarkChanged(StatMultiplierAction action, HCLASS cl, Race race, int amount) {
		if (canSendGameplayMenu() && action != null)
			sendCommand("GMM\t" + action.index() + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + amount);
	}

	public static void statMultiplierUnmarkChanged(StatMultiplierAction action, HCLASS cl, Race race) {
		if (canSendGameplayMenu() && action != null)
			sendCommand("GMU\t" + action.index() + "\t" + hclassIndex(cl) + "\t" + raceIndex(race));
	}

	public static void lawPunishmentChanged(StatCrime crime, HCLASS cl, Race race, CRIME_PUNISHMENTS.PUNISHMENT punishment) {
		if (canSendGameplayMenu() && crime != null && punishment != null)
			sendCommand("GLP\t" + indexOf(STATS.LAW().crimes, crime) + "\t" + hclassIndex(cl) + "\t" + raceIndex(race) + "\t" + punishment.index());
	}

	public static void homeSettingChanged(int tx, int ty, HTypeBits bits) {
		if (canSendGameplayMenu() && bits != null)
			sendCommand("GHS\t" + tx + "\t" + ty + "\t" + CoopProtocol.enc(bitsString(bits)));
	}

	public static void divisionMenChanged(Div div, int men) {
		if (canSendGameplayMenu() && div != null && div.player())
			sendCommand("GDM\t" + div.index() + "\t" + men);
	}

	public static void divisionRaceChanged(Div div, Race race) {
		if (canSendGameplayMenu() && div != null && div.player() && race != null)
			sendCommand("GDR\t" + div.index() + "\t" + race.index);
	}

	public static void divisionTrainingChanged(Div div, StatTraining training, double value) {
		if (canSendGameplayMenu() && div != null && div.player() && training != null)
			sendCommand("GDT\t" + div.index() + "\t" + training.tIndex + "\t" + Math.round(value * 10000.0));
	}

	public static void divisionEquipmentChanged(Div div, EquipBattle equip, int target) {
		if (canSendGameplayMenu() && div != null && div.player() && equip != null)
			sendCommand("GDE\t" + div.index() + "\t" + equip.indexMilitary() + "\t" + target);
	}

	public static void divisionBannerChanged(Div div, int banner) {
		if (canSendGameplayMenu() && div != null && div.player())
			sendCommand("GDB\t" + div.index() + "\t" + banner);
	}

	public static void guardActiveDutyChanged(Div div, boolean active) {
		if (canSendGameplayMenu() && div != null && div.player())
			sendCommand("GGA\t" + div.index() + "\t" + CoopProtocol.bool(active));
	}

	public static void armyMove(WArmy army, int tx, int ty) {
		if (!canSendArmy(army))
			return;
		sendCommand("WM\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration() + "\t" + tx + "\t" + ty);
	}

	public static void armyBesiege(WArmy army, Region region) {
		if (!canSendArmy(army) || region == null)
			return;
		sendCommand("WB\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration() + "\t" + region.index());
	}

	public static void armyRaidRegion(WArmy army, Region region) {
		if (!canSendArmy(army) || region == null)
			return;
		sendCommand("WRG\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration() + "\t" + region.index());
	}

	public static void armyRaidToggle(WArmy army, boolean raid) {
		if (!canSendArmy(army))
			return;
		sendCommand("WRT\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration() + "\t" + CoopProtocol.bool(raid));
	}

	public static void armyIntercept(WArmy army, WArmy target) {
		if (!canSendArmy(army) || target == null || target.faction() == null)
			return;
		sendCommand("WI\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration() + "\t"
				+ armyFactionIndex(target) + "\t" + target.armyIndex() + "\t" + target.iteration());
	}

	public static void armyStop(WArmy army) {
		if (!canSendArmy(army))
			return;
		sendCommand("WS\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration());
	}

	public static void armyDisband(WArmy army) {
		if (!canSendArmy(army))
			return;
		sendCommand("WD\t" + armyFactionIndex(army) + "\t" + army.armyIndex() + "\t" + army.iteration());
	}

	public static void storageCrateChanged(StorageCrate crate) {
		if (crate == null || applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		try {
			Room room = SETT.ROOMS().map.get(crate.x(), crate.y());
			if (!(room instanceof StockpileInstance))
				return;
			RESOURCE res = crate.resource();
			int ri = res == null ? -1 : res.index();
			sendCommand("SC\t" + crate.x() + "\t" + crate.y() + "\t" + ri + "\t" + crate.amount() + "\t" + crate.reserved() + "\t" + crate.storageReserved());
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync storage crate: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void scatteredResourceChanged(ScatteredResource resource) {
		if (resource == null || applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		try {
			sendCommand("GR\t" + resource.x() + "\t" + resource.y() + "\t" + resource.resource().index() + "\t" + resource.amount() + "\t" + resource.amountReserved());
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync scattered resource: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void scatteredResourceRemoved(int tx, int ty, RESOURCE resource) {
		if (resource == null || applyingRemote || mode != Mode.HOST || !networkActive)
			return;
		try {
			sendCommand("GR\t" + tx + "\t" + ty + "\t" + resource.index() + "\t0\t0");
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync removed scattered resource: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void applyIncoming(Incoming incomingLine) {
		String line = incomingLine.line;
		if (line == null || line.length() == 0)
			return;
		if (line.startsWith("REQ_REPLAY\t")) {
			handleReplayRequest(line, incomingLine.source);
			return;
		}
		if (line.startsWith("C\t")) {
			applyCommandEnvelope(line, incomingLine.source);
			return;
		}
		if (mode == Mode.HOST && incomingLine.source != null)
			relay(line, incomingLine.source);
		applyRemoteLine(line);
	}

	private static void applyCommandEnvelope(String line, Peer source) {
		String[] p = line.split("\t", 3);
		if (p.length < 3) {
			CoopLog.warn("Invalid command envelope: " + CoopProtocol.trim(line));
			return;
		}
		try {
			String commandId = CoopProtocol.dec(p[1]);
			String payload = CoopProtocol.dec(p[2]);
			if (!markCommandApplied(commandId))
				return;
			if (mode == Mode.HOST && source != null && payload.startsWith("N\t"))
				return;
			if (mode == Mode.HOST && source != null) {
				rememberReplayCommand(line);
				relay(line, source);
			}
			applyRemoteLine(payload);
		} catch (Exception e) {
			CoopLog.error("Failed to apply command envelope: " + CoopProtocol.trim(line), e);
		}
	}

	private static RoomPlacer roomPlacerForApply() {
		return SETT.ROOMS().placement.placer;
	}

	private static void applyRemoteLine(String line) {
		CoopCommandRouter.apply(line);
	}

	static void applyMulti(String id, int typeIndex, String tileString) {
		PLACABLE pl = find(id);
		if (!(pl instanceof PlacableMulti)) {
			log("Missing multi placable: " + id);
			return;
		}
		PlacableMulti m = (PlacableMulti) pl;
		applyMultiWith(m, typeIndex, tileString);
	}

	private static void applyMultiWith(PlacableMulti m, int typeIndex, String tileString) {
		PLACER_TYPE type = placerType(typeIndex);
		CoopTileArea area = new CoopTileArea(tileString);
		m.finishChecking(area);
		if (m.isPlacable(area, type) != null)
			return;
		for (COORDINATE c : area.body()) {
			if (area.is(c.x(), c.y()) && m.isPlacable(c.x(), c.y(), area, type) == null)
				m.place(c.x(), c.y(), area, type);
		}
		m.finishPlacing(area);
	}

	static void applyRoomMulti(String blueprintKey, int upgrade, int kind, int typeIndex, String tileString) {
		RoomBlueprintImp blue = roomBlueprint(blueprintKey);
		if (blue == null) {
			CoopLog.warn("Missing room blueprint for remote room area: " + blueprintKey);
			return;
		}
		RoomPlacer rp = roomPlacerForApply();
		RoomBlueprintImp current = roomBlueprint(rp);
		if (current != blue)
			rp.init(blue, upgrade);
		else if (roomUpgrade(rp) != upgrade)
			rp.setUpgrade(upgrade);
		PlacableMulti multi = rp.coopMultiPlacer(kind);
		if (multi == null) {
			CoopLog.warn("Unknown room multi operation " + kind + " for blueprint: " + blueprintKey);
			return;
		}
		register(multi);
		applyMultiWith(multi, typeIndex, tileString);
	}

	static void applyRoomInit(String blueprintKey, int upgrade) {
		if (blueprintKey == null || blueprintKey.length() == 0) {
			CoopLog.warn("Skipped remote room init with empty blueprint.");
			return;
		}
		RoomBlueprintImp blue = roomBlueprint(blueprintKey);
		if (blueprintKey.length() > 0 && blue == null) {
			CoopLog.warn("Missing room blueprint for remote room init: " + blueprintKey);
			return;
		}
		RoomPlacer rp = SETT.ROOMS().placement.placer;
		rp.init(blue, upgrade);
		register(rp.area());
	}

	static void applyRoomCreate(String blueprintKey, int upgrade) {
		RoomBlueprintImp blue = roomBlueprint(blueprintKey);
		if (blue == null) {
			CoopLog.warn("Skipped remote room create; missing blueprint: " + blueprintKey);
			return;
		}
		RoomPlacer rp = roomPlacerForApply();
		if (roomBlueprint(rp) != blue) {
			CoopLog.warn("Skipped remote room create; active plan does not match " + blueprintKey + ".");
			return;
		}
		if (roomUpgrade(rp) != upgrade)
			rp.setUpgrade(upgrade);
		CharSequence problem = rp.createProblem();
		if (problem != null) {
			CoopLog.warn("Skipped remote room create because placement has problem: " + problem);
			return;
		}
		rp.create();
	}

	static void applyRoomFixed(String blueprintKey, int upgrade, int group, int cx, int cy, int rot, int size) {
		RoomBlueprintImp blue = roomBlueprint(blueprintKey);
		if (blue == null) {
			CoopLog.warn("Missing room blueprint for remote room item: " + blueprintKey);
			return;
		}
		RoomPlacer rp = roomPlacerForApply();
		RoomBlueprintImp current = roomBlueprint(rp);
		if (current != blue)
			rp.init(blue, upgrade);
		PlacableFixed fixed = rp.createItemPlacer(blue, group, upgrade);
		register(fixed);
		applyFixedWith(fixed, cx, cy, rot, size);
	}

	static boolean applyRoomConstructionFinished(int tx, int ty, boolean quiet) {
		try {
			Room room = SETT.ROOMS().map.get(tx, ty);
			if (room == null) {
				if (!quiet)
					CoopLog.warn("Remote room finish skipped; no room at " + tx + "," + ty);
				return false;
			}
			if (!room.getClass().getName().equals("settlement.room.main.construction.ConstructionInstance")) {
				log("Remote room finish already resolved at " + tx + "," + ty + " room=" + room.getClass().getSimpleName());
				return true;
			}
			int mx = room.mX(tx, ty);
			int my = room.mY(tx, ty);
			clearRoomJobs(room, mx, my);
			Method finish = room.getClass().getDeclaredMethod("finish");
			finish.setAccessible(true);
			finish.invoke(room);
			log("Applied remote room finish at " + mx + "," + my);
			return true;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			rethrowFatal(cause);
			if (!quiet)
				CoopLog.warn("Remote room finish failed at " + tx + "," + ty + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
		} catch (Exception e) {
			rethrowFatal(e);
			if (!quiet)
				CoopLog.warn("Remote room finish failed at " + tx + "," + ty + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		return false;
	}

	private static void clearRoomJobs(Room room, int mx, int my) {
		if (!(room instanceof ROOMA))
			return;
		ROOMA area = (ROOMA) room;
		for (COORDINATE c : area.body()) {
			if (area.is(c.x(), c.y()))
				SETT.JOBS().clearer.set(c);
		}
		log("Cleared remote room jobs before finish at " + mx + "," + my);
	}

	static boolean applyRoomJobFinished(int tx, int ty, int resourceIndex, int amount, boolean quiet) {
		try {
			Room room = SETT.ROOMS().map.get(tx, ty);
			if (room == null) {
				if (!quiet)
					CoopLog.warn("Remote room job finish skipped; no room at " + tx + "," + ty);
				return false;
			}
			if (!(room instanceof ROOM_JOBBER)) {
				log("Remote room job finish already resolved at " + tx + "," + ty + " room=" + room.getClass().getSimpleName());
				return true;
			}
			RESOURCE resource = null;
			if (resourceIndex >= 0) {
				if (resourceIndex >= RESOURCES.ALL().size()) {
					if (!quiet)
						CoopLog.warn("Remote room job finish skipped; invalid resource index " + resourceIndex + " at " + tx + "," + ty);
					return true;
				}
				resource = RESOURCES.ALL().get(resourceIndex);
			}
			SETT.JOBS().clearer.set(tx, ty);
			((ROOM_JOBBER) room).jobFinsih(tx, ty, resource, amount);
			log("Applied remote room job finish at " + tx + "," + ty + " resource=" + resourceIndex + " amount=" + amount);
			return true;
		} catch (Exception e) {
			rethrowFatal(e);
			if (!quiet)
				CoopLog.warn("Remote room job finish failed at " + tx + "," + ty + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		return false;
	}

	static boolean applyJobBuildFinished(String jobKey, int tx, int ty, boolean quiet) {
		Job job = SETT.JOBS().getter.get(tx, ty);
		if (!(job instanceof JobBuild)) {
			if (!quiet)
				CoopLog.warn("Remote job build finish skipped; no build job at " + tx + "," + ty + " key=" + jobKey);
			return false;
		}
		if (jobKey != null && jobKey.length() > 0 && !jobKey.equals(job.key())) {
			if (!quiet)
				CoopLog.warn("Remote job build finish skipped; key mismatch at " + tx + "," + ty + " expected=" + jobKey + " found=" + job.key());
			return false;
		}
		if (!((JobBuild) job).coopForceConstruct(tx, ty)) {
			if (!quiet)
				CoopLog.warn("Remote job build finish did not complete after force construct at " + tx + "," + ty + " key=" + jobKey);
			return false;
		}
		return true;
	}

	static void queuePendingFinish(CoopPendingFinish finish) {
		if (finish == null)
			return;
		String key = finish.key();
		if (!pendingFinishKeys.add(key))
			return;
		finish.nextMillis = System.currentTimeMillis() + 50L;
		pendingFinishes.add(finish);
		CoopLog.warn("Queued remote construction finish retry: " + finish.describe());
	}

	private static void applyPendingFinishes() {
		long now = System.currentTimeMillis();
		CoopPendingFinish finish;
		int guard = 0;
		while (guard++ < 256 && (finish = pendingFinishes.poll()) != null) {
			if (now - finish.createdMillis > 15000L) {
				pendingFinishKeys.remove(finish.key());
				CoopLog.warn("Dropped stale remote construction finish: " + finish.describe());
				continue;
			}
			if (now < finish.nextMillis) {
				pendingFinishes.add(finish);
				continue;
			}
			boolean ok = false;
			if (finish.type == CoopPendingFinish.ROOM_FINISH)
				ok = applyRoomConstructionFinished(finish.tx, finish.ty, true);
			else if (finish.type == CoopPendingFinish.ROOM_JOB)
				ok = applyRoomJobFinished(finish.tx, finish.ty, finish.resourceIndex, finish.amount, true);
			else if (finish.type == CoopPendingFinish.BUILD_JOB)
				ok = applyJobBuildFinished(finish.jobKey, finish.tx, finish.ty, true);
			if (ok) {
				pendingFinishKeys.remove(finish.key());
				log("Applied delayed construction finish: " + finish.describe());
				continue;
			}
			finish.attempts++;
			if (finish.attempts >= PENDING_FINISH_MAX_ATTEMPTS) {
				pendingFinishKeys.remove(finish.key());
				CoopLog.error("Dropped remote construction finish after retries: " + finish.describe(), null);
				continue;
			}
			finish.nextMillis = now + PENDING_FINISH_RETRY_MILLIS;
			pendingFinishes.add(finish);
		}
	}

	static void applyMessage(String serialized) {
		try {
			Message message = CoopProtocol.deserializeMessage(serialized);
			if (message != null)
				message.send();
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply synced message: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyCreditsSync(double credits) {
		CoopWorldSync.applyCreditsSync(mode == Mode.CLIENT, credits);
	}

	static void applyUiSanitySync(String creditsText, String availableText, String totalText) {
		CoopWorldSync.applyUiSanitySync(mode == Mode.CLIENT, creditsText, availableText, totalText);
	}

	static void applyDiplomacy(int instigatorIndex, int accepterIndex, int stanceIndex) {
		try {
			Faction instigator = FACTIONS.getByIndex(instigatorIndex);
			Faction accepter = FACTIONS.getByIndex(accepterIndex);
			DipStance stance = diplomacyStance(stanceIndex);
			if (instigator == null || accepter == null || stance == null) {
				CoopLog.warn("Remote diplomacy skipped; invalid indexes " + instigatorIndex + "," + accepterIndex + "," + stanceIndex);
				return;
			}
			stance.set(instigator, accepter);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply diplomacy sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public static void diplomacyDealTick(Deal deal) {
		if (!CoopGameplaySync.canSendDiplomacyDeal(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected(), deal))
			return;
		if (!CoopGameplaySync.shouldSendDiplomacyDealNow())
			return;
		try {
			if (deal.npc.npc() == null)
				return;
			String snapshot = CoopProtocol.serializeDealSave(new DealSave(deal));
			if (!CoopGameplaySync.acceptOutgoingDealSnapshot(snapshot))
				return;
			sendCommand("DPS\t" + snapshot);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to sync diplomacy deal snapshot: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDiplomacyDealSnapshot(String snapshot) {
		if (!CoopGameplaySync.canApplyDiplomacyDealSnapshot(snapshot))
			return;
		try {
			DealSave save = CoopProtocol.deserializeDealSave(snapshot);
			UIFactions factions = VIEW.world().UI.factions;
			if (factions.coopApplyDealSave(save))
				CoopGameplaySync.acceptAppliedDealSnapshot(snapshot);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply diplomacy deal snapshot: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static DipStance diplomacyStance(int index) {
		if (DIP.NEUTRAL().index() == index)
			return DIP.NEUTRAL();
		if (DIP.WAR().index() == index)
			return DIP.WAR();
		if (DIP.TRADE().index() == index)
			return DIP.TRADE();
		if (DIP.PACT().index() == index)
			return DIP.PACT();
		if (DIP.ALLY().index() == index)
			return DIP.ALLY();
		if (DIP.VASSAL().index() == index)
			return DIP.VASSAL();
		if (DIP.OVERLORD().index() == index)
			return DIP.OVERLORD();
		return null;
	}

	static void applyTechLevel(int techIndex, int level) {
		try {
			if (techIndex < 0 || techIndex >= TECHS.ALL().size()) {
				CoopLog.warn("Remote tech sync skipped; invalid tech index " + techIndex);
				return;
			}
			TECH tech = TECHS.ALL().get(techIndex);
			FACTIONS.player().tech().levelSet(tech, level);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply tech sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean canSendTradeSettings(TRADABLE tradable) {
		return CoopGameplaySync.canSendTrade(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected(), tradable);
	}

	private static TRADABLE tradeByIndex(int tradableIndex) {
		if (tradableIndex < 0 || tradableIndex >= TR.ALL().size())
			return null;
		return TR.ALL().get(tradableIndex);
	}

	static void applyTradeImportSettings(int tradableIndex, int priceCap, int minMoney, int limit) {
		try {
			TRADABLE tradable = tradeByIndex(tradableIndex);
			if (tradable == null) {
				CoopLog.warn("Remote import settings skipped; invalid tradable index " + tradableIndex);
				return;
			}
			FACTIONS.player().buyer(tradable).priceCapsI.set(priceCap);
			FACTIONS.player().buyer(tradable).minMoney.set(minMoney);
			FACTIONS.player().buyer(tradable).limit.set(limit);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply import settings sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyTradeExportSettings(int tradableIndex, int priceCap, int limit) {
		try {
			TRADABLE tradable = tradeByIndex(tradableIndex);
			if (tradable == null) {
				CoopLog.warn("Remote export settings skipped; invalid tradable index " + tradableIndex);
				return;
			}
			FACTIONS.player().seller(tradable).priceCapsI.set(priceCap);
			FACTIONS.player().seller(tradable).limit.set(limit);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply export settings sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean canSendRoomEmployment(RoomInstance room) {
		return CoopGameplaySync.canSendRoomEmployment(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected(), room);
	}

	private static boolean canSendEmploymentType(RoomEmployment employment) {
		return CoopGameplaySync.canSendRoomEmployment(applyingRemote, networkActive, mode == Mode.HOST, mode == Mode.CLIENT,
				hasRemoteClients(), clientConnected(), employment);
	}

	private static RoomInstance roomInstanceAt(int mx, int my) {
		Room room = SETT.ROOMS().map.get(mx, my);
		return room instanceof RoomInstance ? (RoomInstance) room : null;
	}

	private static RoomEmployment employmentByIndex(int index) {
		if (index < 0 || index >= SETT.ROOMS().employment.ALL().size())
			return null;
		return SETT.ROOMS().employment.ALL().get(index);
	}

	static void applyRoomEmploymentNeeded(int mx, int my, int needed) {
		try {
			RoomInstance room = roomInstanceAt(mx, my);
			if (room == null || room.employees() == null) {
				CoopLog.warn("Remote room employment skipped; no room at " + mx + "," + my);
				return;
			}
			room.employees().neededSet(needed);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply room employment sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyRoomAutoEmploy(int mx, int my, boolean auto) {
		try {
			RoomInstance room = roomInstanceAt(mx, my);
			if (room == null || !(room.blueprintI() instanceof ROOM_EMPLOY_AUTO)) {
				CoopLog.warn("Remote auto-employ skipped; unsupported room at " + mx + "," + my);
				return;
			}
			((ROOM_EMPLOY_AUTO) room.blueprintI()).autoEmploy(room, auto);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply auto-employ sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyRoomEmploymentPriority(int employmentIndex, int priority) {
		try {
			RoomEmployment employment = employmentByIndex(employmentIndex);
			if (employment == null) {
				CoopLog.warn("Remote employment priority skipped; invalid index " + employmentIndex);
				return;
			}
			employment.priority.set(priority);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply employment priority sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyRoomEmploymentGroupPriority(int employmentIndex, int groupIndex, int priority) {
		try {
			RoomEmployment employment = employmentByIndex(employmentIndex);
			if (employment == null || groupIndex < 0 || groupIndex >= WGROUP.all().size()) {
				CoopLog.warn("Remote group priority skipped; invalid indexes " + employmentIndex + "/" + groupIndex);
				return;
			}
			employment.priorities.set(WGROUP.all().get(groupIndex), priority);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply group priority sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static int statIndexForDecree(StatDecree decree) {
		for (int i = 0; i < STATS.all().size(); i++) {
			STAT stat = STATS.all().get(i);
			if (stat != null && stat.decree() == decree)
				return i;
		}
		return -1;
	}

	private static Div playerDivision(int index) {
		try {
			if (index < 0)
				return null;
			Div div = GAME.ARMIES().division((short) index);
			return div != null && div.player() ? div : null;
		} catch (Exception e) {
			rethrowFatal(e);
			return null;
		}
	}

	private static StatTraining trainingByTypeIndex(int typeIndex) {
		for (StatTraining training : STATS.BATTLE().TRAINING_ALL) {
			if (training.tIndex == typeIndex)
				return training;
		}
		return null;
	}

	static void applyFoodPermission(int foodIndex, int classIndex, int raceIndex, boolean value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || foodIndex < 0)
				return;
			STATS.FOOD().allowed(foodIndex).set(cl, race, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply food permission sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyCivicEquipmentTarget(int civicIndex, int classIndex, int raceIndex, int target) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || civicIndex < 0 || civicIndex >= STATS.EQUIP().civics().size())
				return;
			STATS.EQUIP().civics().get(civicIndex).targetSet(target, cl, race);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply civic equipment sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyHomeFurnitureTarget(int resourceIndex, int classIndex, int raceIndex, int target) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || resourceIndex < 0 || resourceIndex >= RESOURCES.ALL().size())
				return;
			STATS.HOME().targetSet(target, cl, race, RESOURCES.ALL().get(resourceIndex));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply home furniture sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyServicePermission(int serviceIndex, int groupIndex, boolean value) {
		try {
			HCLASS_RACE group = hclassRaceByIndex(groupIndex);
			if (group == null || serviceIndex < 0 || serviceIndex >= STATS.SERVICE().ALL.size())
				return;
			STATS.SERVICE().ALL.get(serviceIndex).permission().set(group, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply service permission sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyReligionPermission(int religionIndex, int kind, int classIndex, int raceIndex, boolean value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || religionIndex < 0 || religionIndex >= STATS.RELIGION().ALL.size())
				return;
			StatReligion religion = STATS.RELIGION().ALL.get(religionIndex);
			if (kind == 0)
				religion.permissionShrine.set(cl, race, value);
			else if (kind == 1)
				religion.permissionTemple.set(cl, race, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply religion permission sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyGravePermission(int graveIndex, int classIndex, int raceIndex, boolean value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || graveIndex < 0 || graveIndex >= STATS.BURIAL().graves().size())
				return;
			STATS.BURIAL().graves().get(graveIndex).grave().permission().set(cl, race, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply grave permission sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyImmigrationAuto(int raceIndex, int value) {
		try {
			Race race = raceByIndex(raceIndex);
			if (race != null)
				SETT.ENTRY().immi().auto(race).set(value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply immigration sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyReproductionLimit(int classIndex, int raceIndex, int value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl != null && race != null)
				STATS.POP().reproduction.limit.set(HCLASS_RACE.clP(race, cl), value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply reproduction limit sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyReproductionForced(int classIndex, int raceIndex, int value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl != null && race != null)
				STATS.POP().reproduction.settings.set(HCLASS_RACE.clP(race, cl).index, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply forced reproduction sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyEducationLimit(int ageIndex, int classIndex, int raceIndex, int value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || ageIndex < 0 || ageIndex >= STATS.EDUCATION().allAges.size())
				return;
			STATS.EDUCATION().allAges.get(ageIndex).limitSet(cl, race, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply education limit sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyStatDecree(int statIndex, int classIndex, int raceIndex, int value) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || statIndex < 0 || statIndex >= STATS.all().size())
				return;
			STAT stat = STATS.all().get(statIndex);
			if (stat != null && stat.decree() != null)
				stat.decree().getI(cl).set(race, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply stat decree sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static StatMultiplierAction actionByIndex(int index) {
		if (index < 0 || index >= STATS.MULTIPLIERS().all().size())
			return null;
		StatMultiplier multiplier = STATS.MULTIPLIERS().all().get(index);
		return multiplier instanceof StatMultiplierAction ? (StatMultiplierAction) multiplier : null;
	}

	static void applyStatMultiplierAuto(int actionIndex, int classIndex, int raceIndex, int value) {
		try {
			StatMultiplierAction action = actionByIndex(actionIndex);
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (action != null && cl != null && race != null)
				action.auto(cl, race).set(value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply multiplier auto sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyStatMultiplierMark(int actionIndex, int classIndex, int raceIndex, int amount) {
		try {
			StatMultiplierAction action = actionByIndex(actionIndex);
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (action != null && cl != null && race != null)
				action.mark(cl, race, amount);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply multiplier mark sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyStatMultiplierUnmark(int actionIndex, int classIndex, int raceIndex) {
		try {
			StatMultiplierAction action = actionByIndex(actionIndex);
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (action != null && cl != null && race != null)
				action.unmark(cl, race);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply multiplier unmark sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyLawPunishment(int crimeIndex, int classIndex, int raceIndex, int punishmentIndex) {
		try {
			HCLASS cl = hclassByIndex(classIndex);
			Race race = raceByIndex(raceIndex);
			if (cl == null || race == null || crimeIndex < 0 || crimeIndex >= STATS.LAW().crimes.size()
					|| punishmentIndex < 0 || punishmentIndex >= CRIME_PUNISHMENTS.ALL().size())
				return;
			STATS.LAW().crimes.get(crimeIndex).punishmentSet(cl, race, CRIME_PUNISHMENTS.ALL().get(punishmentIndex));
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply law punishment sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyHomeSetting(int tx, int ty, String bitsText) {
		try {
			HTypeBitsImp bits = bitsFromString(bitsText);
			HomeInstance home = SETT.ROOMS().HOME.getter.get(tx, ty);
			if (home != null) {
				home.settingSet(bits);
				return;
			}
			RoomState state = SETT.ROOMS().construction.state(tx, ty);
			if (state instanceof State)
				((State) state).egroup.copy(bits);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply home assignment sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDivisionMen(int divIndex, int men) {
		try {
			Div div = playerDivision(divIndex);
			if (div != null)
				div.info.menSet(men);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply division men sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDivisionRace(int divIndex, int raceIndex) {
		try {
			Div div = playerDivision(divIndex);
			Race race = raceByIndex(raceIndex);
			if (div != null && race != null)
				div.info.raceSet(race);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply division race sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDivisionTraining(int divIndex, int trainingIndex, double value) {
		try {
			Div div = playerDivision(divIndex);
			StatTraining training = trainingByTypeIndex(trainingIndex);
			if (div != null && training != null)
				div.info.trainingSet(training, value);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply division training sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDivisionEquipment(int divIndex, int equipIndex, int target) {
		try {
			Div div = playerDivision(divIndex);
			if (div == null || equipIndex < 0 || equipIndex >= STATS.EQUIP().BATTLE_ALL().size())
				return;
			STATS.EQUIP().BATTLE_ALL().get(equipIndex).targetSet(div, target);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply division equipment sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyDivisionBanner(int divIndex, int banner) {
		try {
			Div div = playerDivision(divIndex);
			if (div != null)
				div.info.bannerISet(banner);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply division banner sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyGuardActiveDuty(int divIndex, boolean active) {
		try {
			Div div = playerDivision(divIndex);
			if (div != null)
				SETT.ROOMS().GUARD.activeDuty.set(div, active);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply guard active duty sync: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean canSendArmy(WArmy army) {
		if (applyingRemote || mode == Mode.OFF || !networkActive || army == null || army.faction() == null || army.armyIndex() < 0)
			return false;
		return mode == Mode.HOST || army.faction() == FACTIONS.player();
	}

	private static int armyFactionIndex(WArmy army) {
		return army.faction() == null ? -1 : army.faction().index();
	}

	private static WArmy findArmy(int factionIndex, int armyIndex, int iteration) {
		try {
			Faction faction = FACTIONS.getByIndex(factionIndex);
			if (faction != null) {
				for (WArmy army : faction.armies().all()) {
					if (army.armyIndex() == armyIndex && (iteration < 0 || army.iteration() == iteration))
						return army;
				}
			}
			WArmy army = WORLD.ENTITIES().armies.get(armyIndex);
			if (army != null && army.faction() != null && army.faction().index() == factionIndex && (iteration < 0 || army.iteration() == iteration))
				return army;
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to resolve army " + factionIndex + "/" + armyIndex + "/" + iteration + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		return null;
	}

	static void applyArmyMove(int factionIndex, int armyIndex, int iteration, int tx, int ty) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		if (army != null)
			army.setDestination(tx, ty);
	}

	static void applyArmyBesiege(int factionIndex, int armyIndex, int iteration, int regionIndex) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		Region region = WORLD.REGIONS().getByIndex(regionIndex);
		if (army != null && region != null)
			army.besiege(region);
	}

	static void applyArmyRaidRegion(int factionIndex, int armyIndex, int iteration, int regionIndex) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		Region region = WORLD.REGIONS().getByIndex(regionIndex);
		if (army != null && region != null)
			army.raid(region);
	}

	static void applyArmyRaidToggle(int factionIndex, int armyIndex, int iteration, boolean raid) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		if (army != null)
			army.raid(raid);
	}

	static void applyArmyIntercept(int factionIndex, int armyIndex, int iteration, int targetFactionIndex, int targetArmyIndex, int targetIteration) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		WArmy target = findArmy(targetFactionIndex, targetArmyIndex, targetIteration);
		if (army != null && target != null)
			army.intercept(target);
	}

	static void applyArmyStop(int factionIndex, int armyIndex, int iteration) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		if (army != null)
			army.stop();
	}

	static void applyArmyDisband(int factionIndex, int armyIndex, int iteration) {
		WArmy army = findArmy(factionIndex, armyIndex, iteration);
		if (army != null)
			army.disband();
	}

	static void applyStorageCrate(int tx, int ty, int resourceIndex, int amount, int reserved, int reservedSpace) {
		if (mode != Mode.CLIENT)
			return;
		try {
			Room room = SETT.ROOMS().map.get(tx, ty);
			if (!(room instanceof StockpileInstance)) {
				CoopLog.warn("Remote stockpile crate skipped; no stockpile at " + tx + "," + ty);
				return;
			}
			StorageCrate crate = ((StockpileInstance) room).crate(tx, ty);
			if (crate == null) {
				CoopLog.warn("Remote stockpile crate skipped; no crate at " + tx + "," + ty);
				return;
			}
			if (crate.resource() != null)
				crate.remove();
			crate.disposeSilent();
			if (resourceIndex < 0)
				return;
			if (resourceIndex >= RESOURCES.ALL().size()) {
				CoopLog.warn("Remote stockpile crate skipped; invalid resource index " + resourceIndex + " at " + tx + "," + ty);
				return;
			}
			RESOURCE res = RESOURCES.ALL().get(resourceIndex);
			crate.resourceSet(res);
			crate.amountSet(Math.max(0, amount));
			crate.reservedSet(Math.max(0, Math.min(reserved, crate.amount())));
			int reserveSpace = Math.max(0, Math.min(reservedSpace, crate.storageReservable()));
			if (reserveSpace > 0)
				crate.storageReserve(reserveSpace);
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply synced stockpile crate at " + tx + "," + ty + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyScatteredResource(int tx, int ty, int resourceIndex, int amount, int reserved) {
		if (mode != Mode.CLIENT)
			return;
		try {
			if (!SETT.IN_BOUNDS(tx, ty) || resourceIndex < 0 || resourceIndex >= RESOURCES.ALL().size())
				return;
			RESOURCE res = RESOURCES.ALL().get(resourceIndex);
			Thing thing = SETT.THINGS().getFirst(tx, ty);
			while (thing != null) {
				Thing next = thing.tileNext();
				if (thing instanceof ScatteredResource) {
					ScatteredResource scattered = (ScatteredResource) thing;
					if (scattered.resource() == res)
						scattered.remove();
				}
				thing = next;
			}
			if (amount <= 0)
				return;
			SETT.THINGS().resources.createPrecise(tx, ty, res, amount);
			ScatteredResource created = SETT.THINGS().resources.get(tx, ty);
			if (created != null && created.resource() == res) {
				int r = Math.max(0, Math.min(reserved, created.amount()));
				for (int i = 0; i < r && created.findableReservedCanBe(); i++)
					created.findableReserve();
			}
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed to apply synced scattered resource at " + tx + "," + ty + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	static void applyFixed(String id, int cx, int cy, int rot, int size) {
		PLACABLE pl = find(id);
		if (!(pl instanceof PlacableFixed)) {
			log("Missing fixed placable: " + id);
			return;
		}
		PlacableFixed f = (PlacableFixed) pl;
		applyFixedWith(f, cx, cy, rot, size);
	}

	private static void applyFixedWith(PlacableFixed f, int cx, int cy, int rot, int size) {
		f.rotSet(rot);
		f.sizeSet(size);
		f.init(cx, cy);
		int w = f.width();
		int h = f.height();
		int x1 = cx - w / 2;
		int y1 = cy - h / 2;
		if (f.placableWhole(x1, y1) != null)
			return;
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				if (f.placable(x1 + dx, y1 + dy, dx, dy) != null)
					return;
			}
		}
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				f.place(x1 + dx, y1 + dy, dx, dy);
			}
		}
		f.afterPlaced(x1, y1);
	}

	static void applySimple(String id, int x, int y) {
		PLACABLE pl = find(id);
		if (!(pl instanceof PlacableSimple)) {
			log("Missing simple placable: " + id);
			return;
		}
		PlacableSimple s = (PlacableSimple) pl;
		if (s.isPlacable(x, y) == null)
			s.place(x, y);
	}

	static void applySimpleTile(String id, int tx, int ty) {
		PLACABLE pl = find(id);
		if (!(pl instanceof PlacableSimpleTile)) {
			log("Missing simple-tile placable: " + id);
			return;
		}
		PlacableSimpleTile s = (PlacableSimpleTile) pl;
		if (s.isPlacable(tx, ty) == null)
			s.place(tx, ty);
	}

	static void applySingle(String id, int tx, int ty) throws Exception {
		PLACABLE pl = find(id);
		if (!(pl instanceof PlacableSingle)) {
			log("Missing single placable: " + id);
			return;
		}
		PlacableSingle s = (PlacableSingle) pl;
		invokeSingleInit(s, tx, ty);
		if (s.isPlacable(tx, ty) != null)
			return;
		s.placeFirst(tx, ty);
		s.placeExpanded(tx, ty);
	}

	private static void invokeSingleInit(PlacableSingle s, int tx, int ty) throws Exception {
		Method m = PlacableSingle.class.getDeclaredMethod("init", int.class, int.class);
		m.setAccessible(true);
		m.invoke(s, tx, ty);
	}

	static PLACABLE find(String id) {
		PLACABLE p = registry.get(id);
		if (p != null)
			return p;
		String[] parts = id.split("\\|", 2);
		if (parts.length > 0) {
			String k = classFallback.get(parts[0]);
			if (k != null) {
				p = registry.get(k);
				if (p != null)
					return p;
			}
		}
		if (parts.length > 1) {
			String k = nameFallback.get(parts[1]);
			if (k != null)
				return registry.get(k);
		}
		scanForPlacables();
		return registry.get(id);
	}

	private static RoomPlacer roomPlacerFrom(Object placer) {
		if (placer == null)
			return null;
		if (placer instanceof PlacableMulti) {
			try {
				RoomPlacer active = SETT.ROOMS().placement.placer;
				if (active.coopMultiKind((PlacableMulti) placer) >= 0)
					return active;
			} catch (Exception e) {
				rethrowFatal(e);
				CoopLog.warn("Failed matching room multi placer: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
		Class<?> c = placer.getClass();
		while (c != null && c != Object.class) {
			for (String name : new String[] {"embrio", "embryo"}) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					Object value = f.get(placer);
					if (value instanceof RoomPlacer)
						return (RoomPlacer) value;
				} catch (NoSuchFieldException e) {
				} catch (Exception e) {
					rethrowFatal(e);
					CoopLog.warn("Failed reading room placer from placable: " + e.getClass().getSimpleName() + ": " + e.getMessage());
					return null;
				}
			}
			c = c.getSuperclass();
		}
		return null;
	}

	private static RoomBlueprintImp roomBlueprint(RoomPlacer rp) {
		if (rp == null)
			return null;
		try {
			return rp.blueprint();
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed reading room blueprint: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	private static RoomBlueprintImp roomBlueprint(String key) {
		if (key == null || key.length() == 0)
			return null;
		try {
			RoomBlueprint b = SETT.ROOMS().collection.tryGet(key);
			if (b instanceof RoomBlueprintImp)
				return (RoomBlueprintImp) b;
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Failed resolving room blueprint '" + key + "': " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
		return null;
	}

	private static int roomUpgrade(RoomPlacer rp) {
		if (rp == null)
			return 0;
		try {
			Field f = RoomPlacer.class.getDeclaredField("instance");
			f.setAccessible(true);
			Object instance = f.get(rp);
			Method m = instance.getClass().getMethod("upgrade");
			Object value = m.invoke(instance);
			return value instanceof Number ? ((Number) value).intValue() : 0;
		} catch (Exception e) {
			rethrowFatal(e);
			return 0;
		}
	}

	private static int roomItemGroup(Object placer) {
		if (placer == null)
			return -1;
		Class<?> c = placer.getClass();
		while (c != null && c != Object.class) {
			try {
				Field f = c.getDeclaredField("group");
				f.setAccessible(true);
				Object value = f.get(placer);
				if (value instanceof FurnisherItemGroup)
					return ((FurnisherItemGroup) value).index();
			} catch (NoSuchFieldException e) {
			} catch (Exception e) {
				rethrowFatal(e);
				CoopLog.warn("Failed reading room item group: " + e.getClass().getSimpleName() + ": " + e.getMessage());
				return -1;
			}
			c = c.getSuperclass();
		}
		return -1;
	}

	private static String key(PLACABLE p) {
		return p.getClass().getName() + "|" + safeName(p);
	}

	private static String safeName(PLACABLE p) {
		try {
			CharSequence n = p.name();
			return n == null ? "" : n.toString();
		} catch (Exception e) {
			rethrowFatal(e);
			return "";
		}
	}

	private static String tiles(AREA area) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (COORDINATE c : area.body()) {
			if (!area.is(c.x(), c.y()))
				continue;
			if (!first)
				sb.append(';');
			first = false;
			sb.append(c.x()).append(',').append(c.y());
		}
		return sb.toString();
	}

	private static int typeIndex(PLACER_TYPE type) {
		LIST<PLACER_TYPE> all = PLACER_TYPE.all;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i) == type)
				return i;
		}
		return 0;
	}

	private static PLACER_TYPE placerType(int index) {
		if (index < 0 || index >= PLACER_TYPE.all.size())
			return PLACER_TYPE.SQUARE;
		return PLACER_TYPE.all.get(index);
	}

	private static void scanForPlacables() {
		try {
			Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
			scan(VIEW.s(), seen, 0, new int[] {0});
			scan(SETT.JOBS(), seen, 0, new int[] {0});
			scan(SETT.ROOMS(), seen, 0, new int[] {0});
		} catch (Exception e) {
			rethrowFatal(e);
			log("Placable scan failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void scan(Object o, Set<Object> seen, int depth, int[] count) throws IllegalAccessException {
		if (o == null || depth > 7 || count[0] > 6000 || seen.contains(o))
			return;
		seen.add(o);
		count[0]++;
		if (o instanceof PLACABLE) {
			register((PLACABLE) o);
			return;
		}
		Class<?> c = o.getClass();
		if (skipClass(c))
			return;
		if (c.isArray()) {
			int len = java.lang.reflect.Array.getLength(o);
			if (len > 512)
				return;
			for (int i = 0; i < len; i++)
				scan(java.lang.reflect.Array.get(o, i), seen, depth + 1, count);
			return;
		}
		if (o instanceof Iterable<?>) {
			int i = 0;
			for (Object e : (Iterable<?>) o) {
				if (i++ > 512)
					break;
				scan(e, seen, depth + 1, count);
			}
		}
		while (c != null && c != Object.class) {
			for (Field f : c.getDeclaredFields()) {
				if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0)
					continue;
				Class<?> ft = f.getType();
				if (ft.isPrimitive() || ft == String.class || Number.class.isAssignableFrom(ft) || ft == Boolean.class || ft == Character.class)
					continue;
				f.setAccessible(true);
				scan(f.get(o), seen, depth + 1, count);
			}
			c = c.getSuperclass();
		}
	}

	private static boolean skipClass(Class<?> c) {
		if (c == null)
			return true;
		String n = c.getName();
		return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("sun.") || n.startsWith("com.sun.")
				|| n.startsWith("jdk.") || n.startsWith("org.lwjgl.") || n.startsWith("org.apache.");
	}

	private static void loadConfig() {
		Path p = configPath();
		if (!Files.exists(p)) {
			log("Config not found: " + p);
			return;
		}
		try {
			for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.length() == 0 || line.startsWith("#"))
					continue;
				int eq = line.indexOf('=');
				if (eq < 0)
					continue;
				String k = line.substring(0, eq).trim().toUpperCase();
				String v = line.substring(eq + 1).trim();
				if ("MODE".equals(k))
					mode = parseMode(v);
				else if ("HOST".equals(k))
					host = v;
				else if ("PORT".equals(k))
					port = Integer.parseInt(v);
				else if ("DEBUG".equals(k))
					debug = Boolean.parseBoolean(v);
				else if (CoopSnapshotSync.loadConfig(k, v)) {
				}
				else if (CoopNpcSync.loadConfig(k, v)) {
				} else if (CoopWorldSync.loadConfig(k, v)) {
				}
				else if (CoopGameplaySync.loadConfig(k, v)) {
				}
				else
					CoopCursor.loadConfig(k, v);
			}
			CoopSnapshotSync.afterConfigLoaded();
			applyMenuOverride();
			log("Config loaded: " + mode + " " + host + ":" + port);
		} catch (Exception e) {
			log("Config error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			CoopLog.error("Config error while reading Syx Together config.", e);
			mode = Mode.OFF;
		}
	}

	private static void applyMenuOverride() {
		if (!menuOverride)
			return;
		mode = menuMode;
		host = menuHost;
		port = menuPort;
		debug = true;
	}

	private static Mode parseMode(String value) {
		try {
			return Mode.valueOf(value.trim().toUpperCase());
		} catch (Exception e) {
			return Mode.OFF;
		}
	}

	private static Path configPath() {
		try {
			URI uri = CoopRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path jar = Paths.get(uri);
			Path script = jar.getParent();
			Path v71 = script == null ? null : script.getParent();
			Path root = v71 == null ? null : v71.getParent();
			if (root != null)
				return root.resolve("config.txt");
		} catch (Exception e) {
		}
		return Paths.get("config.txt");
	}

	public static Path configFilePath() {
		return configPath();
	}

	private static synchronized void startNetwork() {
		if (networkActive) {
			status = "already active";
			return;
		}
		if (mode == Mode.OFF) {
			status = "disabled";
			log("Disabled.");
			return;
		}
		if (networkStartDeferred) {
			status = "waiting for LAN lobby release";
			return;
		}
		if (steamTransport) {
			networkActive = true;
			status = mode == Mode.HOST ? "steam hosting" : "steam connected";
			log("Using Steam P2P transport.");
			return;
		}
		if (mode == Mode.HOST)
			startHost();
		else
			startClient();
	}

	private static void startHost() {
		networkActive = true;
		status = "starting host";
		Thread t = new Thread(() -> {
			try (ServerSocket ss = new ServerSocket(port, 1, InetAddress.getByName("0.0.0.0"))) {
				serverSocket = ss;
				log("Hosting on port " + port);
				status = "hosting";
				while (networkActive) {
					Socket s = ss.accept();
					s.setTcpNoDelay(true);
					Peer p = new Peer(s);
					if (!acceptCoopHandshake(p)) {
						p.close();
						continue;
					}
					clients.add(p);
					p.start();
					log("Client connected: " + s.getRemoteSocketAddress() + " " + p.remoteSummary);
					status = "hosting " + clients.size() + " client(s)";
				}
			} catch (SocketException e) {
				if (networkActive)
					log("Host socket closed: " + e.getMessage());
			} catch (Exception e) {
				log("Host failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
				CoopLog.error("Host failed on port " + port, e);
				status = "host failed";
			} finally {
				serverSocket = null;
				if (networkActive)
					networkActive = false;
			}
		}, "sos-coop-host");
		t.setDaemon(true);
		t.start();
	}

	private static void startClient() {
		networkActive = true;
		status = "connecting";
		Thread t = new Thread(() -> {
			int failures = 0;
			while (networkActive) {
				try {
					Socket s = new Socket(host, port);
					s.setTcpNoDelay(true);
					Peer p = new Peer(s);
					clientCoopHandshake(p);
					serverPeer = p;
					serverPeer.start();
					requestPendingReplay(p);
					log("Connected to host " + host + ":" + port + " " + p.remoteSummary);
					status = "connected";
					return;
				} catch (Exception e) {
					failures++;
					log("Connect failed, retrying: " + e.getClass().getSimpleName() + ": " + e.getMessage());
					if (failures == 1 || failures % 20 == 0)
						CoopLog.warn("Client connect attempt failed, will retry. attempt=" + failures + " host=" + host + ":" + port + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
					status = "retrying";
					try {
						Thread.sleep(3000);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						CoopLog.warn("Client reconnect thread interrupted; stopping reconnect loop.");
						status = "interrupted";
						return;
					}
				}
			}
			status = "disconnected";
		}, "sos-coop-client");
		t.setDaemon(true);
		t.start();
	}

	private static boolean acceptCoopHandshake(Peer peer) {
		try {
			String line = CoopProtocol.readNetworkLine(peer.in);
			String problem = validateHandshakeLine(line, "client");
			if (problem != null) {
				peer.send("ERR\t" + problem);
				CoopLog.warn("Rejected multiplayer client during handshake: " + problem);
				return false;
			}
			CoopHandshake h = parseHandshake(line);
			peer.remoteSummary = h.summary();
			peer.send(helloOkLine());
			return true;
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Rejected multiplayer client; handshake failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			return false;
		}
	}

	private static void clientCoopHandshake(Peer peer) throws IOException {
		peer.send(helloLine());
		String line = CoopProtocol.readNetworkLine(peer.in);
		if (line == null)
			throw new IOException("Host closed during handshake.");
		if (line.startsWith("ERR\t"))
			throw new IOException(line.substring(4));
		String problem = validateHandshakeLine(line, "host");
		if (problem != null)
			throw new IOException(problem);
		CoopHandshake h = parseHandshake(line);
		peer.remoteSummary = h.summary();
	}

	static String helloLine() {
		return helloPacket("HELLO");
	}

	static String helloOkLine() {
		return helloPacket("HELLO_OK");
	}

	private static String helloPacket(String prefix) {
		return prefix + "\t" + PROTOCOL_VERSION + "\t" + MOD_VERSION + "\t"
				+ Integer.toHexString(game.VERSION.VERSION) + "\t" + Integer.toHexString(PATHS.modHash());
	}

	static String validateHandshakeLine(String line, String remoteRole) {
		if (line == null)
			return "Connection closed before handshake.";
		if (line.startsWith("ERR\t"))
			return line.substring(4);
		if (!line.startsWith("HELLO\t") && !line.startsWith("HELLO_OK\t"))
			return "Invalid multiplayer handshake from " + remoteRole + ".";
		try {
			CoopHandshake h = parseHandshake(line);
			if (h.protocol != PROTOCOL_VERSION)
				return "Coop protocol mismatch. Local=" + PROTOCOL_VERSION + " " + remoteRole + "=" + h.protocol + ".";
			if (!MOD_VERSION.equals(h.modVersion))
				return "Syx Together version mismatch. Local=" + MOD_VERSION + " " + remoteRole + "=" + h.modVersion + ".";
			String gameVersion = Integer.toHexString(game.VERSION.VERSION);
			if (!gameVersion.equals(h.gameVersion))
				return "Songs of Syx version mismatch. Local=" + gameVersion + " " + remoteRole + "=" + h.gameVersion + ".";
			String modHash = Integer.toHexString(PATHS.modHash());
			if (!modHash.equals(h.modHash))
				return "Mod list/order mismatch. Both players need the same enabled mods. Local=" + modHash + " " + remoteRole + "=" + h.modHash + ".";
			return null;
		} catch (Exception e) {
			rethrowFatal(e);
			return "Invalid multiplayer handshake from " + remoteRole + ": " + e.getClass().getSimpleName() + ".";
		}
	}

	private static CoopHandshake parseHandshake(String line) {
		String[] p = line.split("\t", -1);
		if (p.length < 5)
			throw new IllegalArgumentException("parts=" + p.length);
		return new CoopHandshake(Integer.parseInt(p[1]), p[2], p[3], p[4]);
	}

	private static void disconnect(String newStatus) {
		networkActive = false;
		steamTransport = false;
		Peer p = serverPeer;
		serverPeer = null;
		if (p != null)
			p.close();
		for (Peer c : clients)
			c.close();
		clients.clear();
		ServerSocket ss = serverSocket;
		serverSocket = null;
		if (ss != null) {
			try {
				ss.close();
			} catch (IOException ignored) {
			}
		}
		status = newStatus;
		log(newStatus);
	}

	private static void sendSnapshot(Peer peer) {
		if (peer == null || mode != Mode.HOST || peer.closed) {
			return;
		}
		try {
			if (!hostReadyForClientSnapshot()) {
				peer.send("ERR\tHost is not ready to send a save yet.");
				CoopLog.warn("Host refused snapshot request because the throne room is not ready yet.");
				return;
			}
			Path path = GAME.saver().save(SaveFile.stamp("Coop Multiplayer Host"), true);
			if (path == null || !Files.exists(path)) {
				peer.send("ERR\tHost failed to create a multiplayer snapshot.");
				CoopLog.warn("Host failed to create a multiplayer snapshot; save path was null or missing.");
				return;
			}
			long replayAfter = replayCursor();
			byte[] bytes = CoopSaveTransfer.readSaveBytes(path);
			if (bytes.length > CoopSaveTransfer.MAX_SAVE_BYTES)
				throw new IOException("Snapshot exceeds the maximum supported size.");
			String transferId = Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(replayAfter);
			String hash = CoopSaveTransfer.sha256(bytes);
			if (!peer.send("SAVE_META\t" + transferId + "\t" + CoopProtocol.enc(path.getFileName().toString()) + "\t"
					+ bytes.length + "\t" + replayAfter + "\t" + hash))
				throw new IOException("Client disconnected before snapshot metadata was sent.");
			for (int offset = 0; offset < bytes.length; offset += SAVE_CHUNK_BYTES) {
				int end = Math.min(bytes.length, offset + SAVE_CHUNK_BYTES);
				String chunk = Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, end));
				if (!peer.send("SAVE_CHUNK\t" + transferId + "\t" + offset + "\t" + chunk))
					throw new IOException("Client disconnected during snapshot transfer.");
			}
			if (!peer.send("SAVE_DONE\t" + transferId))
				throw new IOException("Client disconnected before snapshot completion.");
			status = "snapshot sent";
			log("Snapshot sent: " + path.getFileName() + " bytes=" + bytes.length);
		} catch (Exception e) {
			rethrowFatal(e);
			peer.send("ERR\tSnapshot failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			log("Snapshot failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			CoopLog.error("Host snapshot creation/send failed.", e);
		} finally {
			peer.close();
		}
	}

	private static void sendTimedFullState() {
		if (!CoopSnapshotSync.beginTimedFullState(hasRemoteClients() && networkActive))
			return;
		try {
			if (!VIEW.canSave()) {
				CoopLog.warn("Host skipped full state sync because VIEW.canSave() is false.");
				return;
			}
			Path path = GAME.saver().save(SaveFile.stamp("Coop Multiplayer State"), true);
			if (path == null || !Files.exists(path)) {
				CoopLog.warn("Host failed to create a full state sync save.");
				return;
			}
			byte[] bytes = CoopSaveTransfer.readSaveBytes(path);
			String packet = "STATE\t" + CoopProtocol.enc(path.getFileName().toString()) + "\t" + Base64.getEncoder().encodeToString(bytes);
			for (Peer p : clients) {
				if (!p.snapshotOnly)
					p.send(packet);
			}
			status = "full state sent";
			log("Full state sent: " + path.getFileName() + " bytes=" + bytes.length);
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
			}
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.error("Host full state sync failed.", e);
			log("Full state sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		} finally {
			CoopSnapshotSync.endTimedFullState();
		}
	}

	private static void sendWorldState() {
		CoopWorldSync.sendWorldState(hasRemoteClients() && networkActive);
	}

	private static void sendUiSanityState() {
		CoopWorldSync.sendUiSanityState(hasRemoteClients() && networkActive);
	}

	static void applyWorldSync(String timeMillisText, String weatherText) {
		CoopWorldSync.applyWorldSync(mode == Mode.CLIENT, timeMillisText, weatherText);
	}
	private static void sendCursorState() {
		String payload = CoopCursor.pollCommand(mode != Mode.OFF && networkActive, mode == Mode.HOST, mode == Mode.CLIENT, hasRemoteClients(), clientConnected());
		if (payload != null)
			sendCommand(payload);
	}

	private static void sendPings() {
		if (mode == Mode.OFF || !networkActive)
			return;
		if (steamTransport)
			return;
		long now = System.currentTimeMillis();
		if (mode == Mode.HOST) {
			for (Peer p : clients) {
				if (!p.snapshotOnly)
					p.ping(now);
			}
		} else {
			Peer p = serverPeer;
			if (p != null)
				p.ping(now);
		}
	}

	private static void applyPong(Peer peer, String line) {
		try {
			long sent = Long.parseLong(line.substring(5));
			long now = System.currentTimeMillis();
			long rtt = Math.max(0L, now - sent);
			peer.lastPongMillis = now;
			peer.lastRttMillis = rtt;
			if (mode == Mode.CLIENT)
				status = "connected " + rtt + "ms";
			else if (mode == Mode.HOST)
				status = "hosting " + clients.size() + " client(s), ping " + rtt + "ms";
		} catch (Exception e) {
			rethrowFatal(e);
			CoopLog.warn("Invalid ping response: " + CoopProtocol.trim(line));
		}
	}

	private static int clamp(int value, int min, int max) {
		if (max < min)
			return min;
		if (value < min)
			return min;
		if (value > max)
			return max;
		return value;
	}

	private static void sendNpcState() {
		CoopNpcSync.sendHost(hasRemoteClients() && networkActive);
	}

	static void applyNpcSync(String batch) {
		CoopNpcSync.apply(mode == Mode.CLIENT, batch);
	}

	static void applyNpcSnapshot(long sequence, long hostMillis, String batch) {
		CoopNpcSync.applySnapshot(mode == Mode.CLIENT, sequence, hostMillis, batch);
	}

	static void applyNpcLogic(String batch) {
		CoopNpcLogicSync.apply(mode == Mode.CLIENT, batch);
	}

	static void applyAnimalSnapshot(long sequence, long hostMillis, String batch) {
		CoopAnimalSync.apply(mode == Mode.CLIENT, sequence, hostMillis, batch);
	}
	private static void receiveFullState(String line) {
		CoopSnapshotSync.receiveFullState(line);
	}

	private static void send(String line) {
		if (mode == Mode.OFF || !networkActive)
			return;
		if (steamTransport) {
			CoopSteam.sendGameLine(line);
			return;
		}
		if (mode == Mode.HOST) {
			for (Peer p : clients) {
				if (!p.snapshotOnly)
					p.send(line);
			}
		} else {
			Peer p = serverPeer;
			if (p != null)
				p.send(line);
		}
	}

	public static void receiveSteamGameLine(String line) {
		enqueueIncoming(line, null);
	}

	private static boolean enqueueIncoming(String line, Peer source) {
		if (line == null || line.length() == 0)
			return false;
		if (line.length() > CoopProtocol.MAX_NETWORK_LINE_CHARS) {
			CoopLog.warn("Rejected oversized multiplayer packet: " + line.length() + " characters.");
			status = "rejected oversized multiplayer packet";
			return false;
		}
		int queued = incomingCount.incrementAndGet();
		if (queued > MAX_INCOMING_PACKETS) {
			incomingCount.decrementAndGet();
			CoopLog.warn("Incoming multiplayer queue is full; connection cannot keep up safely.");
			status = "multiplayer queue overflow";
			return false;
		}
		incoming.add(new Incoming(line, source));
		return true;
	}

	static void sendCommand(String payload) {
		if (mode == Mode.OFF || !networkActive)
			return;
		String commandId = nodeId + "-" + commandSeq.incrementAndGet();
		markCommandApplied(commandId);
		String envelope = "C\t" + CoopProtocol.enc(commandId) + "\t" + CoopProtocol.enc(payload);
		if (mode == Mode.HOST)
			rememberReplayCommand(envelope);
		send(envelope);
	}

	/**
	 * Sends authoritative state that is continuously refreshed. Unlike gameplay
	 * commands, these packets must not consume command IDs or the replay buffer.
	 */
	static void sendStateLine(String payload) {
		if (payload == null || payload.length() == 0)
			return;
		send(payload);
	}

	private static boolean markCommandApplied(String commandId) {
		if (commandId == null || commandId.length() == 0 || !appliedCommandIds.add(commandId))
			return false;
		appliedCommandOrder.add(commandId);
		while (appliedCommandIds.size() > REPLAY_BUFFER_LIMIT) {
			String oldest = appliedCommandOrder.poll();
			if (oldest == null)
				break;
			appliedCommandIds.remove(oldest);
		}
		return true;
	}

	private static void rememberReplayCommand(String envelope) {
		if (envelope == null || envelope.length() == 0)
			return;
		synchronized (replayLock) {
			long seq = replaySeq.incrementAndGet();
			replayBuffer.addLast(new ReplayEntry(seq, envelope));
			while (replayBuffer.size() > REPLAY_BUFFER_LIMIT)
				replayBuffer.removeFirst();
		}
	}

	private static void requestPendingReplay(Peer peer) {
		if (mode != Mode.CLIENT || !networkActive || pendingReplayAfterSeq < 0 || pendingReplayRequested)
			return;
		String line = "REQ_REPLAY\t" + pendingReplayAfterSeq;
		boolean sent;
		if (peer != null)
			sent = peer.send(line);
		else if (steamTransport) {
			CoopSteam.sendGameLine(line);
			sent = true;
		} else {
			Peer p = serverPeer;
			sent = p != null && p.send(line);
		}
		if (sent) {
			pendingReplayRequested = true;
			CoopLog.warn("Requested host replay after snapshot cursor " + pendingReplayAfterSeq + ".");
		}
	}

	private static void handleReplayRequest(String line, Peer source) {
		if (mode != Mode.HOST)
			return;
		long after = 0L;
		try {
			String[] p = line.split("\t", -1);
			if (p.length >= 2)
				after = Math.max(0L, Long.parseLong(p[1]));
		} catch (NumberFormatException e) {
			CoopLog.warn("Ignored invalid replay request: " + CoopProtocol.trim(line));
			return;
		}
		List<String> replay = new ArrayList<>();
		synchronized (replayLock) {
			for (ReplayEntry entry : replayBuffer) {
				if (entry.seq > after)
					replay.add(entry.line);
			}
		}
		for (String replayLine : replay) {
			if (source != null)
				source.send(replayLine);
			else
				send(replayLine);
		}
		CoopLog.warn("Sent " + replay.size() + " replay command(s) after snapshot cursor " + after + ".");
	}

	private static void relay(String line, Peer source) {
		for (Peer p : clients) {
			if (p != source && !p.snapshotOnly)
				p.send(line);
		}
	}

	static void setStatus(String newStatus) {
		status = newStatus;
	}

	static void log(String s) {
		if (debug)
			System.out.println("[Syx Together] " + s);
	}

	private static final class Peer {
		private final Socket socket;
		private final PrintWriter out;
		private final BufferedReader in;
		private volatile boolean closed;
		private volatile boolean snapshotOnly;
		private String remoteSummary = "";
		private long lastPingMillis;
		private long lastPongMillis;
		private long lastRttMillis = -1;

		Peer(Socket socket) throws IOException {
			this.socket = socket;
			this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
		}

		void start() {
			Thread t = new Thread(() -> {
				try {
					String line;
					while ((line = CoopProtocol.readNetworkLine(in)) != null) {
						if ("REQ_SAVE".equals(line)) {
							snapshotOnly = true;
							clients.remove(this);
							snapshotRequests.add(this);
						}
						else if (line.startsWith("PING\t"))
							send("PONG\t" + line.substring(5));
						else if (line.startsWith("PONG\t"))
							applyPong(this, line);
						else if (line.startsWith("STATE\t")) {
							if (CoopSnapshotSync.liveLoadEnabled())
								receiveFullState(line);
							else
								CoopLog.warn("Ignored live full-state packet; command sync is authoritative.");
						}
						else if (!enqueueIncoming(line, this))
							throw new IOException("Rejected unsafe or overflowing multiplayer packet.");
					}
				} catch (Exception e) {
					log("Peer disconnected: " + e.getClass().getSimpleName() + ": " + e.getMessage());
				} finally {
					clients.remove(this);
					if (serverPeer == this)
						serverPeer = null;
					try {
						in.close();
					} catch (IOException ignored) {
					}
					try {
						socket.close();
					} catch (IOException ignored) {
					}
					if (networkActive && mode == Mode.CLIENT)
						status = "connection lost";
					else if (networkActive && mode == Mode.HOST)
						status = "hosting " + clients.size() + " client(s)";
				}
			}, "sos-coop-peer");
			t.setDaemon(true);
			t.start();
		}

		void ping(long now) {
			if (now - lastPingMillis < PING_INTERVAL_MILLIS)
				return;
			lastPingMillis = now;
			send("PING\t" + now);
		}

		boolean send(String line) {
			if (closed)
				return false;
			if (line == null || line.length() > CoopProtocol.MAX_NETWORK_LINE_CHARS) {
				CoopLog.warn("Rejected oversized LAN multiplayer send.");
				close();
				return false;
			}
			try {
				out.println(line);
				out.flush();
				if (out.checkError()) {
					closed = true;
					clients.remove(this);
					if (serverPeer == this)
						serverPeer = null;
					close();
					log("Send failed: writer reported an error.");
					return false;
				}
				return true;
			} catch (Exception e) {
				closed = true;
				clients.remove(this);
				if (serverPeer == this)
					serverPeer = null;
				close();
				log("Send failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
				CoopLog.error("Network send failed.", e);
				return false;
			}
		}

		void close() {
			closed = true;
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static final class Incoming {
		final String line;
		final Peer source;

		Incoming(String line, Peer source) {
			this.line = line;
			this.source = source;
		}
	}

	private static final class ReplayEntry {
		final long seq;
		final String line;

		ReplayEntry(long seq, String line) {
			this.seq = seq;
			this.line = line;
		}
	}

}

