package menu;

import java.nio.file.Path;
import java.util.Locale;

import coopmod.CoopCursor;
import coopmod.CoopLanLobby;
import coopmod.CoopLog;
import coopmod.CoopMenuLink;
import coopmod.CoopRuntime;
import coopmod.CoopSteam;
import game.GameSpec;
import game.VERSION;
import game.save.GameLoader;
import game.save.SaveFile;
import init.constant.Config;
import init.paths.PATHS;
import init.sprite.UI.UI;
import snake2d.Mouse;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.text.Str;
import snake2d.util.sprite.text.StringInputSprite;
import util.colors.GCOLOR;
import util.text.DicTime;
import view.menu.MenuScreen;

class ScMultiplayer extends GUI.Shadower implements SC {

	static final CharSequence NAME = "multiplayer";
	private static final String SELF_MOD_NAME = "Syx Together";

	private enum Page {
		CHOICE,
		CUSTOMIZATION,
		LAN,
		LAN_HOST,
		LAN_CONNECT,
		LAN_CLIENT,
		LAN_LOADING,
		LAN_LOAD,
		LAN_INFO,
		STEAM_LOBBY,
		STEAM_LOADING,
		STEAM_LOAD
	}

	private static final COLOR STEAM_PROGRESS = new ColorImp(180, 142, 48);
	private static final COLOR STEAM_READY = new ColorImp(54, 150, 74);
	private static final COLOR SAVE_VANILLA = new ColorImp(142, 154, 188);
	private static final COLOR SAVE_MODDED = new ColorImp(176, 164, 92);
	private static final COLOR SAVE_DIFFERENT_MODS = new ColorImp(198, 128, 68);
	private static final int STEAM_SAVE_ROWS = 8;
	private static final int LAN_PORT = 49710;

	private final Menu menu;
	private final GuiSection content = new GuiSection();
	private final MenuInput hostInput;
	private final MenuInput portInput;
	private Page page = Page.CHOICE;
	private boolean loading;
	private SteamSaveEntry[] steamSaves = new SteamSaveEntry[0];
	private volatile SteamSaveEntry[] pendingSteamSaves;
	private volatile String pendingSteamSaveError;
	private volatile boolean steamSavesLoading;
	private volatile int steamSaveRequest;
	private int steamSaveOffset;
	private SteamSaveEntry hoveredSteamSave;
	private double steamSaveModCycle;
	private final Str steamSaveText = new Str(256);
	private Path lanSelectedSavePath;
	private String lanSelectedSaveName = "";

	ScMultiplayer(Menu menu) {
		CoopLog.installGlobalErrorHandler();
		CoopCursor.loadPersistedSettings();
		this.menu = menu;
		MenuScreen screen = new MenuScreen(NAME, GUI.labelColor) {
			@Override
			protected void back() {
				goBack();
			}
		};
		add(screen);

		hostInput = input(CoopMenuLink.lastHost(), "host ip", 32, 390);
		portInput = input(CoopMenuLink.lastPortText(), "port", 8, 150);

		add(content);
		switchPage(Page.CHOICE);
	}

	private static void crash(String context, RuntimeException e) {
		CoopLog.crash(context, e);
		throw e;
	}

	private static void crash(String context, Error e) {
		CoopLog.crash(context, e);
		throw e;
	}

	private void switchPage(Page next) {
		page = next;
		content.clear();
		if (next == Page.CUSTOMIZATION)
			content.add(customizationPage(), 0, 0);
		else if (next == Page.LAN)
			content.add(lanChoicePage(), 0, 0);
		else if (next == Page.LAN_HOST)
			content.add(lanHostPage(), 0, 0);
		else if (next == Page.LAN_CONNECT)
			content.add(lanConnectPage(), 0, 0);
		else if (next == Page.LAN_CLIENT)
			content.add(lanClientLobbyPage(), 0, 0);
		else if (next == Page.LAN_LOADING)
			content.add(clientLoadingPage("LAN LOBBY"), 0, 0);
		else if (next == Page.LAN_LOAD)
			content.add(steamLoadPage(), 0, 0);
		else if (next == Page.LAN_INFO)
			content.add(lanInfoPage(), 0, 0);
		else if (next == Page.STEAM_LOBBY)
			content.add(steamLobbyPage(), 0, 0);
		else if (next == Page.STEAM_LOADING)
			content.add(clientLoadingPage("STEAM LOBBY"), 0, 0);
		else if (next == Page.STEAM_LOAD)
			content.add(steamLoadPage(), 0, 0);
		else {
			content.add(choicePage(), 0, 0);
			content.body().centerIn(MenuScreen.inner);
			addChoiceCustomizationButton();
			return;
		}
		content.body().centerIn(MenuScreen.inner);
	}

	private GuiSection choicePage() {
		GuiSection section = new GuiSection();
		GuiSection lan = modeOption("LAN", "Direct host and IP connection.", "Experimental.", Page.LAN);
		GuiSection steam = modeOption("STEAM", "Steam lobby, invites and P2P.", "Experimental.", Page.STEAM_LOBBY);
		section.add(lan, 0, 0);
		section.addRightC(90, steam);
		return section;
	}

	private void addChoiceCustomizationButton() {
		CLICKABLE customization = secondaryButton("CUSTOMIZATION", Page.CUSTOMIZATION);
		int x = content.body().x1() + content.body().width() - 15;
		int y = content.body().y1() + content.body().height() + 150;
		content.add(customization, x, y);
	}

	private GuiSection customizationPage() {
		GuiSection section = new GuiSection();
		section.add(UI.FONT().H2.getText("CUSTOMIZATION"), 0, 0);
		section.addDown(18, smallText("Customize your multiplayer cursor without editing config.txt.", 760, true));
		section.addDown(18, cursorPreviewPanel());
		section.addDown(18, cursorToggleRow());
		section.addDown(18, cursorSwatchRow());
		section.addDown(16, cursorColorRow("Red", 0));
		section.addDown(8, cursorColorRow("Green", 1));
		section.addDown(8, cursorColorRow("Blue", 2));
		section.addDown(18, new CursorResetButton());
		return section;
	}

	private GuiSection modeOption(String title, String subtitle, String state, Page target) {
		GuiSection section = new GuiSection();
		CLICKABLE button = new MenuScreen.ScreenButton(title) {
			@Override
			protected void clickA() {
				if (target == Page.STEAM_LOBBY && !CoopSteam.lobbyActive())
					CoopSteam.hostLobby();
				switchPage(target);
			}
		};
		section.add(button, 0, 0);
		section.addDown(22, smallText(subtitle, 300, true));
		section.addDown(8, smallText(state, 300, false));
		return section;
	}

	private CLICKABLE secondaryButton(String title, Page target) {
		return new MenuScreen.ScreenButton(title, UI.FONT().H2) {
			@Override
			protected void clickA() {
				switchPage(target);
			}
		};
	}

	private GuiSection lanChoicePage() {
		GuiSection section = new GuiSection();
		GuiSection host = lanModeOption("HOST LOBBY", "Create a direct IP session.", Page.LAN_HOST, true);
		GuiSection connect = lanModeOption("CONNECT BY IP", "Join a direct IP host.", Page.LAN_CONNECT, false);
		section.add(host, 0, 0);
		section.addRightC(90, connect);
		return section;
	}

	private GuiSection lanModeOption(String title, String subtitle, Page target, boolean prepareHost) {
		GuiSection section = new GuiSection();
		CLICKABLE button = new MenuScreen.ScreenButton(title) {
			@Override
			protected void clickA() {
				if (prepareHost)
					CoopLanLobby.hostLobby(port());
				switchPage(target);
			}
		};
		section.add(button, 0, 0);
		section.addDown(22, smallText(subtitle, 330, true));
		return section;
	}

	private GuiSection lanHostPage() {
		GuiSection section = new GuiSection();

		GuiSection hostButtons = new GuiSection();
		hostButtons.add(new MenuScreen.ScreenButton("new game") {
			@Override
			protected void clickA() {
				try {
					if (!CoopLanLobby.prepareNewGameFlow())
						return;
					menu.switchScreen(menu.sandbox2);
				} catch (RuntimeException e) {
					crash("LAN New Game button failed.", e);
				} catch (Error e) {
					crash("LAN New Game button crashed.", e);
				}
			}
		}, 0, 0);
		hostButtons.addRightC(64, new MenuScreen.ScreenButton("load game") {
			@Override
			protected void clickA() {
				CoopLanLobby.ensureHostLobby(port());
				beginSteamSaveLoad();
				switchPage(Page.LAN_LOAD);
			}
		});
		section.add(hostButtons, 0, 0);

		section.addDown(30, UI.FONT().H2.getText("LAN LOBBY"));
		section.addDown(10, fixedRow("Port", Integer.toString(port()), 150));
		section.addDown(8, smallText("Local IP: " + CoopLanLobby.localAddressHint(), 760, true));
		section.addDown(16, lanLobbySlots());
		section.addDown(18, statusLine(760));
		String selected = CoopLanLobby.selectedSaveName();
		if (selected.length() > 0)
			section.addDown(8, smallText("Selected save: " + selected, 760, true));
		section.addDown(18, lanStartButton());
		return section;
	}

	private GuiSection lanConnectPage() {
		GuiSection section = new GuiSection();

		section.add(UI.FONT().H2.getText("CONNECT TO HOST"), 0, 0);
		section.addDown(10, inputRow("Host IP", hostInput));
		section.addDown(8, fixedRow("Port", Integer.toString(port()), 150));

		CLICKABLE connect = new MenuScreen.ScreenButton("connect") {
			@Override
			protected void clickA() {
				String h = host();
				if (h.length() == 0) {
					CoopMenuLink.setStatus("Enter the host IP first.");
					return;
				}
				CoopLanLobby.connect(h, port());
				switchPage(Page.LAN_CLIENT);
			}
		};
		GuiSection buttons = new GuiSection();
		buttons.add(connect, 0, 0);
		section.addDown(14, buttons);
		section.addDown(18, statusLine(620));
		return section;
	}

	private GuiSection lanClientLobbyPage() {
		GuiSection section = new GuiSection();
		section.add(UI.FONT().H2.getText("LAN LOBBY"), 0, 0);
		section.addDown(12, lanLobbySlots());
		section.addDown(18, statusLine(760));
		section.addDown(8, smallText("Waiting for the host to choose a save or start a new game.", 760, true));
		return section;
	}

	private GuiSection clientLoadingPage(String title) {
		GuiSection section = new GuiSection();
		section.add(UI.FONT().H2.getText(title), 0, 0);
		section.addDown(28, UI.FONT().H2.getText("LOADING..."));
		section.addDown(18, statusLine(760));
		section.addDown(8, smallText("Waiting for the host world to be ready.", 760, true));
		return section;
	}

	private GuiSection lanInfoPage() {
		GuiSection section = new GuiSection();
		section.add(UI.FONT().H2.getText("LAN INFO"), 0, 0);
		section.addDown(18, textBlock(new String[] {
				"Same network / Hamachi",
				"Use the host PC IP, for example 192.168.x.x or the Hamachi IPv4 address.",
				"",
				"Internet",
				"Use the host public IP. The host must forward TCP port 49710 to this PC.",
				"Windows Firewall and the router must allow java.exe / Songs of Syx.",
				"",
				"Connection timeout",
				"The client could not reach that IP and port. This happens before save or mod checks.",
				"If the host is behind CGNAT, public IP hosting may not work without Steam P2P or a VPN."
		}, 820));
		return section;
	}

	private GuiSection steamLobbyPage() {
		GuiSection section = new GuiSection();

		GuiSection topButtons = new GuiSection();
		topButtons.add(new MenuScreen.ScreenButton("invite") {
			@Override
			protected void clickA() {
				CoopSteam.inviteFriends();
			}
		}, 0, 0);
		topButtons.addRightC(52, new MenuScreen.ScreenButton("new game") {
			@Override
			protected void clickA() {
				try {
					if (!steamCanStart())
						return;
					CoopSteam.prepareNewGameFlow();
					CoopRuntime.menuSteamHost();
					menu.switchScreen(menu.sandbox2);
				} catch (RuntimeException e) {
					crash("Steam New Game button failed.", e);
				} catch (Error e) {
					crash("Steam New Game button crashed.", e);
				}
			}
		});
		topButtons.addRightC(52, new MenuScreen.ScreenButton("load game") {
			@Override
			protected void clickA() {
				if (!steamCanStart())
					return;
				beginSteamSaveLoad();
				switchPage(Page.STEAM_LOAD);
			}
		});
		section.add(topButtons, 0, 0);

		section.addDown(34, UI.FONT().H2.getText("STEAM LOBBY"));
		section.addDown(12, steamLobbySlots());
		section.addDown(18, smallText("Invite a friend through Steam, then start or load the host game once they join.", 760, false));
		if (CoopSteam.selectedHostSaveName().length() > 0)
			section.addDown(8, smallText("Selected save: " + CoopSteam.selectedHostSaveName(), 760, true));
		section.addDown(18, steamStartButton());
		return section;
	}

	private GuiSection steamLoadPage() {
		GuiSection section = new GuiSection();
		section.add(UI.FONT().H2.getText("SELECT SAVE"), 0, 0);
		section.addDown(16, steamSaveRows());
		GuiSection controls = new GuiSection();
		controls.add(new MenuScreen.ScreenButton("<") {
			@Override
			protected void renAction() {
				activeSet(steamSaveOffset > 0);
			}

			@Override
			protected void clickA() {
				steamSaveOffset = Math.max(0, steamSaveOffset - STEAM_SAVE_ROWS);
				switchPage(page);
			}
		}, 0, 0);
		controls.addRightC(48, new MenuScreen.ScreenButton(">") {
			@Override
			protected void renAction() {
				activeSet(steamSaveOffset + STEAM_SAVE_ROWS < steamSaves.length);
			}

			@Override
			protected void clickA() {
				steamSaveOffset = Math.min(Math.max(0, steamSaves.length - 1), steamSaveOffset + STEAM_SAVE_ROWS);
				switchPage(page);
			}
		});
		section.addDown(18, controls);
		section.addDown(16, steamSaveInfoPanel());
		return section;
	}

	private RENDEROBJ steamStartButton() {
		return new MenuScreen.ScreenButton("start game") {
			@Override
			protected void renAction() {
				activeSet(CoopSteam.canStartLobbyGame());
			}

			@Override
			protected void clickA() {
				Path path = CoopSteam.selectedHostSavePath();
				if (path == null) {
					CoopMenuLink.setStatus("Choose a save before starting.");
					return;
				}
				if (!CoopSteam.canStartLobbyGame()) {
					CoopMenuLink.setStatus("Waiting for every client to finish downloading the save.");
					return;
				}
				CoopSteam.startLobbyGame();
				CoopRuntime.menuSteamHost();
				loading = true;
				menu.start(new GameLoader(path));
			}
		};
	}

	private RENDEROBJ lanStartButton() {
		return new MenuScreen.ScreenButton("start game") {
			@Override
			protected void renAction() {
				activeSet(CoopLanLobby.canStartLobbyGame());
			}

			@Override
			protected void clickA() {
				if (lanSelectedSavePath == null) {
					CoopMenuLink.setStatus("Choose a save before starting.");
					return;
				}
				if (!CoopLanLobby.canStartLobbyGame()) {
					CoopMenuLink.setStatus("Waiting for the LAN client to finish downloading the save.");
					return;
				}
				CoopLanLobby.startLobbyGame();
				loading = true;
				menu.start(new GameLoader(lanSelectedSavePath));
			}
		};
	}

	private void beginSteamSaveLoad() {
		final int request = steamSaveRequest + 1;
		steamSaveRequest = request;
		steamSaves = new SteamSaveEntry[0];
		pendingSteamSaves = null;
		pendingSteamSaveError = null;
		steamSavesLoading = true;
		steamSaveOffset = 0;
		hoveredSteamSave = null;
		Thread loader = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					SaveFile[] saves = SaveFile.list();
					String[] currentExternalMods = currentExternalMods();
					SteamSaveEntry[] entries = new SteamSaveEntry[saves.length];
					for (int i = 0; i < saves.length; i++) {
						if (request != steamSaveRequest)
							return;
						entries[i] = steamSaveEntry(saves[i], currentExternalMods);
					}
					if (request == steamSaveRequest)
						pendingSteamSaves = entries;
				} catch (Exception e) {
					CoopLog.error("Failed to read multiplayer saves.", e);
					if (request == steamSaveRequest) {
						pendingSteamSaveError = "Could not read saves.";
					}
				}
			}
		}, "Syx Together Save List");
		loader.setDaemon(true);
		loader.start();
	}

	private void applyPendingSteamSaves() {
		SteamSaveEntry[] ready = pendingSteamSaves;
		if (ready != null) {
			pendingSteamSaves = null;
			pendingSteamSaveError = null;
			steamSavesLoading = false;
			steamSaves = ready;
			if (steamSaveOffset >= steamSaves.length)
				steamSaveOffset = Math.max(0, steamSaves.length - STEAM_SAVE_ROWS);
			if (page == Page.STEAM_LOAD || page == Page.LAN_LOAD)
				switchPage(page);
			return;
		}
		if (pendingSteamSaveError != null && steamSavesLoading) {
			steamSavesLoading = false;
			if (page == Page.STEAM_LOAD || page == Page.LAN_LOAD)
				switchPage(page);
		}
	}

	private RENDEROBJ steamSaveRows() {
		GuiSection rows = new GuiSection();
		if (steamSavesLoading) {
			rows.add(smallText("Loading saves...", 760, true), 0, 0);
			return rows;
		}
		if (pendingSteamSaveError != null) {
			rows.add(smallText(pendingSteamSaveError, 760, true), 0, 0);
			return rows;
		}
		if (steamSaves.length == 0) {
			rows.add(smallText("No saves found.", 760, true), 0, 0);
			return rows;
		}
		int max = Math.min(steamSaves.length, steamSaveOffset + STEAM_SAVE_ROWS);
		for (int i = steamSaveOffset; i < max; i++) {
			SteamSaveRow row = new SteamSaveRow(steamSaves[i]);
			if (i == steamSaveOffset)
				rows.add(row, 0, 0);
			else
				rows.addDown(6, row);
		}
		return rows;
	}

	private RENDEROBJ steamSaveInfoPanel() {
		return new RENDEROBJ.RenderImp(760, 154) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				SteamSaveEntry entry = hoveredSteamSave;
				if (entry == null)
					return;
				renderSteamSaveInfo(r, ds, entry, body().x1(), body().y1());
			}
		};
	}

	private void renderSteamSaveInfo(SPRITE_RENDERER r, float ds, SteamSaveEntry entry, int x, int y) {
		final int width = 760;
		final int height = 142;
		GCOLOR.UI().bg(false, false, false).render(r, x, x + width, y, y + height);
		GCOLOR.UI().border().renderFrame(r, x, x + width, y, y + height, 0, 2);

		try {
			GameSpec spec = entry.spec;
			int row = 0;
			renderSavePair(r, x + 14, y + 12 + row++ * 24, "Capital", spec.city);
			renderSavePair(r, x + 300, y + 12, "Ruler", spec.ruler);
			renderSavePair(r, x + 540, y + 12, "Species", spec.race);
			renderSavePair(r, x + 14, y + 12 + row++ * 24, "Population", saveNumber(spec.population));
			renderSavePair(r, x + 300, y + 36, "Regions", saveNumber(spec.regions));
			renderSavePair(r, x + 540, y + 36, "Subjects", saveNumber(spec.regPop));
			double years = spec.playSeconds / (Config.sett().secondsPerHour * Config.sett().hoursPerDay * 16.0);
			renderSavePair(r, x + 14, y + 12 + row++ * 24, "PlayTime", DicTime.setYears(steamSaveText.clear(), years));
			renderSaveMods(r, ds, spec, x + 14, y + 12 + row++ * 24);
			renderSaveWarning(r, entry, x + 14, y + 12 + row * 24);
		} catch (Exception e) {
			STEAM_PROGRESS.bind();
			UI.FONT().M.render(r, "Save details unavailable.", x + 14, y + 14);
			COLOR.unbind();
		}
	}

	private void renderSavePair(SPRITE_RENDERER r, int x, int y, CharSequence label, CharSequence value) {
		GUI.COLORS.label.bind();
		UI.FONT().H2.render(r, label, x, y);
		COLOR.unbind();
		GUI.COLORS.inactive.bind();
		UI.FONT().M.render(r, value, x + UI.FONT().H2.width(label) + 8, y + 2);
		COLOR.unbind();
	}

	private void renderSaveMods(SPRITE_RENDERER r, float ds, GameSpec spec, int x, int y) {
		GUI.COLORS.label.bind();
		UI.FONT().H2.render(r, "Mods", x, y);
		COLOR.unbind();
		if (spec.mods.length == 0) {
			GUI.COLORS.inactive.bind();
			UI.FONT().M.render(r, "0/0", x + UI.FONT().H2.width("Mods") + 8, y + 2);
			COLOR.unbind();
			return;
		}
		steamSaveModCycle += ds;
		if (steamSaveModCycle >= spec.mods.length)
			steamSaveModCycle -= (int) steamSaveModCycle;
		int i = (int) steamSaveModCycle;
		if (i < 0 || i >= spec.mods.length)
			i = 0;
		steamSaveText.clear().add(i + 1).add('/').add(spec.mods.length).s().add(spec.mods[i]).setMaxChars(86);
		GUI.COLORS.inactive.bind();
		UI.FONT().M.render(r, steamSaveText, x + UI.FONT().H2.width("Mods") + 8, y + 2);
		COLOR.unbind();
	}

	private void renderSaveWarning(SPRITE_RENDERER r, SteamSaveEntry entry, int x, int y) {
		CharSequence warning = entry.warning;
		if (warning == null)
			return;
		if (entry.spec.fubar)
			COLOR.REDISH.bind();
		else
			STEAM_PROGRESS.bind();
		steamSaveText.clear().add(warning).setMaxChars(105);
		UI.FONT().M.render(r, steamSaveText, x, y + 2);
		COLOR.unbind();
	}

	private String saveNumber(long value) {
		if (value < 0)
			return "-" + saveNumber(-value);
		if (value >= 1_000_000_000_000L)
			return String.format(Locale.ROOT, "%.2fT", value / 1_000_000_000_000.0);
		if (value >= 1_000_000_000L)
			return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
		if (value >= 1_000_000L)
			return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
		if (value >= 1_000L)
			return String.format(Locale.ROOT, "%.2fK", value / 1_000.0);
		return Long.toString(value);
	}

	private SteamSaveEntry steamSaveEntry(SaveFile save, String[] currentExternalMods) {
		GameSpec spec = save.spec();
		boolean badVersion = VERSION.VERSION_MAJOR != VERSION.versionMajor(save.version);
		String status;
		COLOR color;
		String warning = null;
		if (spec.fubar) {
			status = "Problem";
			color = COLOR.REDISH;
			warning = "Save details unavailable.";
		} else if (badVersion) {
			status = "Version mismatch";
			color = COLOR.REDISH;
			warning = "Save was made with another game version.";
		} else {
			int state = steamSaveModState(spec.mods, currentExternalMods);
			if (state == 0) {
				status = "Vanilla";
				color = SAVE_VANILLA;
			} else if (state == 1) {
				status = "Modded";
				color = SAVE_MODDED;
			} else {
				status = "Mod mismatch";
				color = SAVE_DIFFERENT_MODS;
				warning = "Saved mod list does not match the active mods.";
			}
		}
		return new SteamSaveEntry(save, spec, color, status, warning);
	}

	private COLOR steamSaveColor(SteamSaveEntry entry) {
		return entry.color;
	}

	private int steamSaveModState(String[] savedMods, String[] currentExternalMods) {
		int savedExternal = externalModCount(savedMods);
		if (savedExternal == 0 && currentExternalMods.length == 0)
			return 0;
		if (externalModsEqualCurrent(savedMods, currentExternalMods))
			return 1;
		return 2;
	}

	private CharSequence steamSaveStatus(SteamSaveEntry entry) {
		return entry.status;
	}

	private int externalModCount(String[] mods) {
		int count = 0;
		for (String mod : mods) {
			if (!isSelfMod(mod))
				count++;
		}
		return count;
	}

	private boolean externalModsEqualCurrent(String[] savedMods, String[] currentExternalMods) {
		int savedCount = externalModCount(savedMods);
		if (savedCount != currentExternalMods.length)
			return false;
		int index = 0;
		for (String saved : savedMods) {
			if (isSelfMod(saved))
				continue;
			String current = currentExternalMods[index++];
			if (current == null || !current.equals(saved))
				return false;
		}
		return true;
	}

	private String[] currentExternalMods() {
		String[] tmp = new String[PATHS.currentMods().size()];
		int count = 0;
		for (int i = 0; i < PATHS.currentMods().size(); i++) {
			String descriptor = currentModDescriptor(i);
			if (isSelfMod(descriptor))
				continue;
			tmp[count++] = descriptor;
		}
		String[] result = new String[count];
		for (int i = 0; i < count; i++)
			result[i] = tmp[i];
		return result;
	}

	private String currentModDescriptor(int index) {
		return "'" + PATHS.currentMods().get(index).name + "', version: " + PATHS.currentMods().get(index).version;
	}

	private boolean isSelfMod(String mod) {
		return mod != null && mod.startsWith("'" + SELF_MOD_NAME + "', version:");
	}

	private boolean steamCanStart() {
		if (!CoopSteam.lobbyActive()) {
			CoopMenuLink.setStatus("Create a Steam lobby before starting.");
			return false;
		}
		if (!CoopSteam.localIsLobbyOwner()) {
			CoopMenuLink.setStatus("Only the lobby host can start or load the game.");
			return false;
		}
		return true;
	}

	private RENDEROBJ lanLobbySlots() {
		return new RENDEROBJ.RenderImp(760, 154) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				int x = body().x1();
				int y = body().y1();
				renderLanSlot(r, x, y, CoopLanLobby.lobbyMemberName(0), CoopLanLobby.lobbyMemberRole(0), CoopLanLobby.lobbyMemberStateText(0),
						CoopLanLobby.lobbyMemberStateColor(0), "H");
				renderLanSlot(r, x, y + 72, CoopLanLobby.lobbyMemberName(1), CoopLanLobby.lobbyMemberRole(1), CoopLanLobby.lobbyMemberStateText(1),
						CoopLanLobby.lobbyMemberStateColor(1), "2");
			}
		};
	}

	private void renderLanSlot(SPRITE_RENDERER r, int x, int y, String name, String role, String state, int stateColor, String icon) {
		final int width = 640;
		final int height = 58;
		GCOLOR.UI().bg(false, false, false).render(r, x, x + width, y, y + height);
		GCOLOR.UI().border().renderFrame(r, x, x + width, y, y + height, 0, 2);

		int avatarX1 = x + 12;
		int avatarY1 = y + 9;
		int avatarX2 = avatarX1 + 40;
		int avatarY2 = avatarY1 + 40;
		GUI.COLORS.inactive.bind();
		COLOR.WHITE15.render(r, avatarX1, avatarX2, avatarY1, avatarY2);
		COLOR.unbind();

		GUI.COLORS.label.bind();
		int textX = avatarX1 + (avatarX2 - avatarX1 - UI.FONT().M.width(icon)) / 2;
		int textY = avatarY1 + (avatarY2 - avatarY1 - UI.FONT().M.height()) / 2;
		UI.FONT().M.render(r, icon, textX, textY);
		COLOR.unbind();
		GCOLOR.UI().border().renderFrame(r, avatarX1, avatarX2, avatarY1, avatarY2, 0, 1);

		GUI.COLORS.label.bind();
		UI.FONT().M.render(r, name, x + 68, y + 10);
		COLOR.unbind();

		GUI.COLORS.inactive.bind();
		UI.FONT().S.render(r, role, x + 68, y + 37);
		COLOR.unbind();

		if (stateColor == 2)
			STEAM_READY.bind();
		else if (stateColor == 1)
			STEAM_PROGRESS.bind();
		else
			GUI.COLORS.inactive.bind();
		UI.FONT().S.render(r, state, x + 500, y + 22);
		COLOR.unbind();
	}

	private RENDEROBJ steamLobbySlots() {
		return new RENDEROBJ.RenderImp(760, 154) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				int x = body().x1();
				int y = body().y1();
				for (int i = 0; i < CoopSteam.maxPlayers(); i++)
					renderSteamSlot(r, x, y + i * 72, i);
			}
		};
	}

	private void renderSteamSlot(SPRITE_RENDERER r, int x, int y, int slot) {
		final int width = 640;
		final int height = 58;
		GCOLOR.UI().bg(false, false, false).render(r, x, x + width, y, y + height);
		GCOLOR.UI().border().renderFrame(r, x, x + width, y, y + height, 0, 2);

		int avatarX1 = x + 12;
		int avatarY1 = y + 9;
		int avatarX2 = avatarX1 + 40;
		int avatarY2 = avatarY1 + 40;
		GUI.COLORS.inactive.bind();
		COLOR.WHITE15.render(r, avatarX1, avatarX2, avatarY1, avatarY2);
		COLOR.unbind();

		int[] avatar = CoopSteam.lobbyMemberAvatarPixels(slot);
		String initial = CoopSteam.lobbyMemberInitial(slot);
		if (avatar != null)
			renderSteamAvatar(r, avatar, avatarX1, avatarY1);
		else {
			GUI.COLORS.label.bind();
			int textX = avatarX1 + (avatarX2 - avatarX1 - UI.FONT().M.width(initial)) / 2;
			int textY = avatarY1 + (avatarY2 - avatarY1 - UI.FONT().M.height()) / 2;
			UI.FONT().M.render(r, initial, textX, textY);
			COLOR.unbind();
		}
		GCOLOR.UI().border().renderFrame(r, avatarX1, avatarX2, avatarY1, avatarY2, 0, 1);

		String name = CoopSteam.lobbyMemberName(slot);
		String role = CoopSteam.lobbyMemberRole(slot);
		boolean occupied = !"Open Slot".equals(name) && !"Searching...".equals(name);
		GUI.COLORS.label.bind();
		UI.FONT().M.render(r, name, x + 68, y + 10);
		COLOR.unbind();

		GUI.COLORS.inactive.bind();
		UI.FONT().S.render(r, role, x + 68, y + 37);
		COLOR.unbind();

		String state = CoopSteam.lobbyMemberStateText(slot);
		int stateColor = CoopSteam.lobbyMemberStateColor(slot);
		if (stateColor == 2)
			STEAM_READY.bind();
		else if (stateColor == 1)
			STEAM_PROGRESS.bind();
		else
			GUI.COLORS.inactive.bind();
		UI.FONT().S.render(r, state, x + 500, y + 22);
		COLOR.unbind();
	}

	private void renderSteamAvatar(SPRITE_RENDERER r, int[] avatar, int x1, int y1) {
		int grid = CoopSteam.avatarGrid();
		int cell = 40 / grid;
		if (cell <= 0)
			cell = 1;
		for (int y = 0; y < grid; y++) {
			for (int x = 0; x < grid; x++) {
				int pixel = avatar[x + y * grid];
				int a = (pixel >>> 24) & 0x0FF;
				if (a < 16)
					continue;
				int red = (pixel >>> 16) & 0x0FF;
				int green = (pixel >>> 8) & 0x0FF;
				int blue = pixel & 0x0FF;
				ColorImp.TMP.set(red / 2, green / 2, blue / 2);
				ColorImp.TMP.render(r, x1 + x * cell, x1 + (x + 1) * cell, y1 + y * cell, y1 + (y + 1) * cell);
			}
		}
	}

	private RENDEROBJ cursorPreviewPanel() {
		return new RENDEROBJ.RenderImp(760, 82) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				int x = body().x1();
				int y = body().y1();
				GCOLOR.UI().bg(false, false, false).render(r, x, x + body().width(), y, y + body().height());
				GCOLOR.UI().border().renderFrame(r, body(), 0, 2);
				CoopCursor.renderMenuCursor(r, x + 22, y + 20);
				GUI.COLORS.label.bind();
				UI.FONT().M.render(r, "Mouse color", x + 76, y + 18);
				COLOR.unbind();
				GUI.COLORS.inactive.bind();
				UI.FONT().S.render(r, "This is the cursor color other players will see.", x + 76, y + 46);
				COLOR.unbind();
			}
		};
	}

	private GuiSection cursorToggleRow() {
		GuiSection row = new GuiSection();
		row.add(UI.FONT().M.getText("Ally cursor"), 0, 0);
		CursorToggleButton toggle = new CursorToggleButton();
		toggle.body().moveX1(210);
		toggle.body().centerY(row.getLast());
		row.add(toggle);
		return row;
	}

	private GuiSection cursorSwatchRow() {
		GuiSection row = new GuiSection();
		row.add(UI.FONT().M.getText("Mouse color"), 0, 0);
		for (int i = 0; i < CoopCursor.swatchCount(); i++)
			row.add(new CursorSwatchButton(i), 210 + i * 34, 0);
		return row;
	}

	private GuiSection cursorColorRow(String label, int component) {
		GuiSection row = new GuiSection();
		row.add(UI.FONT().M.getText(label), 0, 0);
		CursorColorStepButton minus = new CursorColorStepButton(component, -16, "-");
		minus.body().moveX1(210);
		minus.body().centerY(row.getLast());
		row.add(minus);
		RENDEROBJ value = cursorValueBox(component);
		value.body().moveX1(252);
		value.body().centerY(row.getLast());
		row.add(value);
		CursorColorStepButton plus = new CursorColorStepButton(component, 16, "+");
		plus.body().moveX1(334);
		plus.body().centerY(row.getLast());
		row.add(plus);
		return row;
	}

	private RENDEROBJ cursorValueBox(int component) {
		return new RENDEROBJ.RenderImp(64, 28) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GCOLOR.UI().bg(false, false, false).render(r, body());
				GCOLOR.UI().border().renderFrame(r, body(), 0, 1);
				GUI.COLORS.inactive.bind();
				String value = Integer.toString(cursorColorComponent(component));
				UI.FONT().S.render(r, value, body().x1() + (body().width() - UI.FONT().S.width(value)) / 2,
						body().y1() + (body().height() - UI.FONT().S.height()) / 2 - 1);
				COLOR.unbind();
			}
		};
	}

	private int cursorColorComponent(int component) {
		if (component == 0)
			return CoopCursor.colorR();
		if (component == 1)
			return CoopCursor.colorG();
		return CoopCursor.colorB();
	}

	private RENDEROBJ smallText(String text, int width, boolean dim) {
		return new RENDEROBJ.RenderImp(width, 18) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				if (dim)
					GUI.COLORS.inactive.bind();
				else
					GUI.COLORS.label.bind();
				UI.FONT().S.render(r, text, body().x1(), body().y1());
				COLOR.unbind();
			}
		};
	}

	private RENDEROBJ textBlock(String[] lines, int width) {
		return new RENDEROBJ.RenderImp(width, Math.max(1, lines.length) * 22) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				int y = body().y1();
				for (int i = 0; i < lines.length; i++) {
					String line = lines[i];
					if (line.length() == 0) {
						y += 22;
						continue;
					}
					if (i == 0 || "Internet".equals(line) || "Connection timeout".equals(line))
						GUI.COLORS.label.bind();
					else
						GUI.COLORS.inactive.bind();
					UI.FONT().M.render(r, line, body().x1(), y);
					COLOR.unbind();
					y += 22;
				}
			}
		};
	}

	private RENDEROBJ statusLine(int width) {
		return new RENDEROBJ.RenderImp(width, 22) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GUI.COLORS.label.bind();
				UI.FONT().M.render(r, CoopMenuLink.status(), body().x1(), body().y1());
				COLOR.unbind();
			}
		};
	}

	private MenuInput input(String value, String placeholder, int size, int width) {
		StringInputSprite s = new StringInputSprite(size, UI.FONT().M).placeHolder(placeholder);
		s.text().add(value);
		return new MenuInput(s, width);
	}

	private GuiSection inputRow(String label, MenuInput input) {
		GuiSection row = new GuiSection();
		row.add(UI.FONT().M.getText(label), 0, 0);
		input.body().moveX1(130);
		input.body().centerY(row.getLast());
		row.add(input);
		return row;
	}

	private GuiSection fixedRow(String label, String value, int width) {
		GuiSection row = new GuiSection();
		row.add(UI.FONT().M.getText(label), 0, 0);
		RENDEROBJ box = new RENDEROBJ.RenderImp(width, UI.FONT().M.height() + 12) {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GCOLOR.UI().bg(false, false, false).render(r, body());
				GUI.COLORS.inactive.bind();
				UI.FONT().M.render(r, value, body().x1() + 6, body().y1() + (body().height() - UI.FONT().M.height()) / 2 - 2);
				COLOR.unbind();
				GCOLOR.UI().border().renderFrame(r, body(), 0, 2);
			}
		};
		box.body().moveX1(130);
		box.body().centerY(row.getLast());
		row.add(box);
		return row;
	}

	private String host() {
		return hostInput.text().toString().trim();
	}

	private int port() {
		return LAN_PORT;
	}

	@Override
	public void render(SPRITE_RENDERER r, float ds) {
		applyPendingSteamSaves();
		hoveredSteamSave = null;
		if (!loading) {
			if (CoopSteam.clientWaitingForHostStart() && page != Page.STEAM_LOADING)
				switchPage(Page.STEAM_LOADING);
			else if (CoopLanLobby.clientWaitingForHostStart() && page != Page.LAN_LOADING)
				switchPage(Page.LAN_LOADING);
		}
		super.render(r, ds);
		if (!loading) {
			Path steamStart = CoopSteam.consumeSteamStartSave();
			if (steamStart != null) {
				loading = true;
				try {
					menu.start(new GameLoader(steamStart));
				} catch (RuntimeException e) {
					CoopLog.error("Client failed to load Steam lobby save: " + steamStart, e);
					throw e;
				}
				return;
			}
			Path lanStart = CoopLanLobby.consumeStartSave();
			if (lanStart != null) {
				loading = true;
				try {
					menu.start(new GameLoader(lanStart));
				} catch (RuntimeException e) {
					CoopLog.error("Client failed to load LAN lobby save: " + lanStart, e);
					throw e;
				}
				return;
			}
			Path p = CoopMenuLink.consumeReceivedSave();
			if (p != null) {
				loading = true;
				try {
					menu.start(new GameLoader(p));
				} catch (RuntimeException e) {
					CoopLog.error("Client failed to load received multiplayer save: " + p, e);
					throw e;
				}
			}
		}
	}

	private void goBack() {
		if (page == Page.CHOICE)
			menu.switchScreen(menu.main);
		else if (page == Page.LAN_HOST || page == Page.LAN_CONNECT || page == Page.LAN_CLIENT || page == Page.LAN_LOADING)
			switchPage(Page.LAN);
		else if (page == Page.LAN_INFO)
			switchPage(Page.LAN);
		else if (page == Page.LAN)
			switchPage(Page.CHOICE);
		else if (page == Page.LAN_LOAD)
			switchPage(Page.LAN_HOST);
		else if (page == Page.STEAM_LOAD || page == Page.STEAM_LOADING)
			switchPage(Page.STEAM_LOBBY);
		else if (page == Page.STEAM_LOBBY)
			switchPage(Page.CHOICE);
		else
			switchPage(Page.CHOICE);
	}

	@Override
	public boolean back(Menu menu) {
		goBack();
		return true;
	}

	@Override
	public boolean hover(COORDINATE mCoo) {
		return super.hover(mCoo);
	}

	@Override
	public boolean click() {
		return super.click();
	}

	private static final class CursorToggleButton extends CLICKABLE.ClickableAbs {

		CursorToggleButton() {
			body.setWidth(160);
			body.setHeight(32);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			GCOLOR.UI().border().renderFrame(r, body, 0, 2);
			String text = CoopCursor.remoteCursorVisible() ? "shown" : "hidden";
			if (CoopCursor.remoteCursorVisible())
				STEAM_READY.bind();
			else
				GUI.COLORS.inactive.bind();
			UI.FONT().M.render(r, text, body.x1() + (body.width() - UI.FONT().M.width(text)) / 2,
					body.y1() + (body.height() - UI.FONT().M.height()) / 2 - 1);
			COLOR.unbind();
		}

		@Override
		protected void clickA() {
			CoopCursor.setRemoteCursorVisible(!CoopCursor.remoteCursorVisible());
		}
	}

	private static final class CursorSwatchButton extends CLICKABLE.ClickableAbs {

		private final int index;

		CursorSwatchButton(int index) {
			this.index = index;
			body.setWidth(26);
			body.setHeight(26);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			ColorImp.TMP.set(CoopCursor.swatchR(index), CoopCursor.swatchG(index), CoopCursor.swatchB(index));
			ColorImp.TMP.render(r, body.x1() + 4, body.x2() - 4, body.y1() + 4, body.y2() - 4);
			GCOLOR.UI().border().renderFrame(r, body, 0, isHovered ? 2 : 1);
		}

		@Override
		protected void clickA() {
			CoopCursor.setColor(CoopCursor.swatchR(index), CoopCursor.swatchG(index), CoopCursor.swatchB(index));
		}
	}

	private static final class CursorColorStepButton extends CLICKABLE.ClickableAbs {

		private final int component;
		private final int amount;
		private final String label;

		CursorColorStepButton(int component, int amount, String label) {
			this.component = component;
			this.amount = amount;
			this.label = label;
			body.setWidth(34);
			body.setHeight(28);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			GCOLOR.UI().border().renderFrame(r, body, 0, 1);
			GUI.COLORS.label.bind();
			UI.FONT().M.render(r, label, body.x1() + (body.width() - UI.FONT().M.width(label)) / 2,
					body.y1() + (body.height() - UI.FONT().M.height()) / 2 - 1);
			COLOR.unbind();
		}

		@Override
		protected void clickA() {
			CoopCursor.adjustColor(component, amount);
		}
	}

	private static final class CursorResetButton extends CLICKABLE.ClickableAbs {

		CursorResetButton() {
			body.setWidth(160);
			body.setHeight(32);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			GCOLOR.UI().border().renderFrame(r, body, 0, 2);
			GUI.COLORS.label.bind();
			UI.FONT().M.render(r, "reset color", body.x1() + (body.width() - UI.FONT().M.width("reset color")) / 2,
					body.y1() + (body.height() - UI.FONT().M.height()) / 2 - 1);
			COLOR.unbind();
		}

		@Override
		protected void clickA() {
			CoopCursor.resetColor();
		}
	}

	private static final class MenuInput extends CLICKABLE.ClickableAbs {

		private static final int TEXT_Y_OFFSET = -2;
		private final StringInputSprite input;

		MenuInput(StringInputSprite input, int width) {
			this.input = input;
			body.setWidth(width);
			body.setHeight(input.height() + 12);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			input.renAction();
			if (Mouse.currentClicked == this)
				input.listen();
			if (isHovered || Mouse.currentClicked == this)
				GCOLOR.UI().NORMAL.hovered.render(r, body());
			int x1 = body().x1() + 6;
			int y1 = body().y1() + (body().height() - input.height()) / 2 + TEXT_Y_OFFSET;
			input.render(r, x1, y1);
			GCOLOR.UI().border().renderFrame(r, body, 0, 2);
		}

		@Override
		public boolean click() {
			if (!hoveredIs() || !activeIs())
				return false;
			Mouse.currentClicked = this;
			input.listen();
			input.selectAll();
			return true;
		}

		public Str text() {
			return input.text();
		}
	}

	private static final class SteamSaveEntry {

		private final SaveFile save;
		private final GameSpec spec;
		private final COLOR color;
		private final String status;
		private final String warning;

		SteamSaveEntry(SaveFile save, GameSpec spec, COLOR color, String status, String warning) {
			this.save = save;
			this.spec = spec;
			this.color = color;
			this.status = status;
			this.warning = warning;
		}
	}

	private final class SteamSaveRow extends CLICKABLE.ClickableAbs {

		private final SteamSaveEntry entry;

		SteamSaveRow(SteamSaveEntry entry) {
			this.entry = entry;
			body.setWidth(760);
			body.setHeight(34);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			if (isHovered)
				hoveredSteamSave = entry;
			GCOLOR.UI().bg(isActive, isSelected, isHovered).render(r, body);
			GCOLOR.UI().border().renderFrame(r, body, 0, 2);
			steamSaveColor(entry).bind();
			steamSaveText.clear().add(VERSION.versionMajor(entry.save.version)).add('.').add(VERSION.versionMinor(entry.save.version));
			UI.FONT().M.render(r, steamSaveText, body.x1() + 14, body.y1() + 7);
			steamSaveText.clear().add(entry.save.name).setMaxChars(32);
			UI.FONT().M.render(r, steamSaveText, body.x1() + 82, body.y1() + 7);
			COLOR.unbind();
			steamSaveColor(entry).bind();
			UI.FONT().S.render(r, steamSaveStatus(entry), body.x2() - 300, body.y1() + 10);
			COLOR.unbind();
			GUI.COLORS.inactive.bind();
			UI.FONT().S.render(r, entry.save.ago, body.x2() - 145, body.y1() + 10);
			COLOR.unbind();
		}

		@Override
		protected void clickA() {
			if (entry.spec.fubar) {
				CoopMenuLink.setStatus("That save cannot be loaded with the current game setup.");
				return;
			}
			if (page == Page.LAN_LOAD) {
				lanSelectedSavePath = entry.save.path;
				lanSelectedSaveName = entry.save.name;
				if (CoopLanLobby.hostSelectedSave(entry.save.path, entry.save.name))
					switchPage(Page.LAN_HOST);
				return;
			}
			if (CoopSteam.hostSelectedSave(entry.save.path, entry.save.name))
				switchPage(Page.STEAM_LOBBY);
		}
	}
}
