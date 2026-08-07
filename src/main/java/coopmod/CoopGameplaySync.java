package coopmod;

final class CoopGameplaySync {

	private static volatile boolean diplomacyDealSync = true;
	private static volatile int diplomacyDealSyncIntervalMillis = 50;
	private static volatile boolean techSync = true;
	private static volatile boolean tradeSettingsSync = true;
	private static volatile boolean roomEmploymentSync = true;
	private static volatile boolean gameplayMenuSync = true;
	private static long lastDiplomacyDealSyncMillis;
	private static String lastDiplomacyDealSnapshot = "";

	private CoopGameplaySync() {
	}

	static boolean loadConfig(String key, String value) {
		if ("DIPLOMACY_DEAL_SYNC".equals(key)) {
			diplomacyDealSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("DIPLOMACY_DEAL_SYNC_INTERVAL_MS".equals(key)) {
			diplomacyDealSyncIntervalMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("TECH_SYNC".equals(key)) {
			techSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("TRADE_SETTINGS_SYNC".equals(key)) {
			tradeSettingsSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("ROOM_EMPLOYMENT_SYNC".equals(key)) {
			roomEmploymentSync = Boolean.parseBoolean(value);
			return true;
		}
		if ("GAMEPLAY_MENU_SYNC".equals(key)) {
			gameplayMenuSync = Boolean.parseBoolean(value);
			return true;
		}
		return false;
	}

	static boolean canSendTech(boolean applyingRemote, boolean networkActive, boolean hostMode, boolean clientMode,
			boolean hasRemoteClients, boolean clientConnected, Object tech) {
		return canSend(techSync, applyingRemote, networkActive, hostMode, clientMode, hasRemoteClients, clientConnected, tech);
	}

	static boolean canSendTrade(boolean applyingRemote, boolean networkActive, boolean hostMode, boolean clientMode,
			boolean hasRemoteClients, boolean clientConnected, Object tradable) {
		return canSend(tradeSettingsSync, applyingRemote, networkActive, hostMode, clientMode, hasRemoteClients, clientConnected, tradable);
	}

	static boolean canSendRoomEmployment(boolean applyingRemote, boolean networkActive, boolean hostMode, boolean clientMode,
			boolean hasRemoteClients, boolean clientConnected, Object target) {
		return canSend(roomEmploymentSync, applyingRemote, networkActive, hostMode, clientMode, hasRemoteClients, clientConnected, target);
	}

	static boolean canSendGameplayMenu(boolean applyingRemote, boolean networkActive, boolean hostMode, boolean clientMode,
			boolean hasRemoteClients, boolean clientConnected) {
		return canSend(gameplayMenuSync, applyingRemote, networkActive, hostMode, clientMode, hasRemoteClients, clientConnected, new Object());
	}

	static boolean canSendDiplomacyDeal(boolean applyingRemote, boolean networkActive, boolean hostMode, boolean clientMode,
			boolean hasRemoteClients, boolean clientConnected, Object deal) {
		return canSend(diplomacyDealSync, applyingRemote, networkActive, hostMode, clientMode, hasRemoteClients, clientConnected, deal);
	}

	static boolean canApplyDiplomacyDealSnapshot(String snapshot) {
		return diplomacyDealSync && snapshot != null && snapshot.length() > 0;
	}

	static boolean shouldSendDiplomacyDealNow() {
		long now = System.currentTimeMillis();
		if (now - lastDiplomacyDealSyncMillis < Math.max(16, diplomacyDealSyncIntervalMillis))
			return false;
		lastDiplomacyDealSyncMillis = now;
		return true;
	}

	static boolean acceptOutgoingDealSnapshot(String snapshot) {
		if (snapshot == null || snapshot.equals(lastDiplomacyDealSnapshot))
			return false;
		lastDiplomacyDealSnapshot = snapshot;
		return true;
	}

	static void acceptAppliedDealSnapshot(String snapshot) {
		lastDiplomacyDealSnapshot = snapshot == null ? "" : snapshot;
	}

	private static boolean canSend(boolean enabled, boolean applyingRemote, boolean networkActive, boolean hostMode,
			boolean clientMode, boolean hasRemoteClients, boolean clientConnected, Object target) {
		if (!enabled || applyingRemote || !networkActive || target == null)
			return false;
		if (hostMode && !hasRemoteClients)
			return false;
		return !clientMode || clientConnected;
	}
}
