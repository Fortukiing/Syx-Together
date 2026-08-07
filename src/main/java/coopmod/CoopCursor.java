package coopmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.RECTANGLE;
import view.main.VIEW;
import view.subview.GameWindow;

public final class CoopCursor {

	private static final int ORIGINAL_R = 127;
	private static final int ORIGINAL_G = 127;
	private static final int ORIGINAL_B = 127;
	private static final int PANEL_W = 262;
	private static final int PANEL_H = 174;
	private static final int[][] SWATCHES = new int[][] {
			{ 127, 127, 127 },
			{ 40, 120, 160 },
			{ 150, 64, 50 },
			{ 150, 112, 42 },
			{ 70, 145, 76 },
			{ 128, 76, 150 }
	};

	private static volatile boolean sync = true;
	private static volatile boolean render = true;
	private static volatile int intervalMillis = 50;
	private static volatile int colorR = ORIGINAL_R;
	private static volatile int colorG = ORIGINAL_G;
	private static volatile int colorB = ORIGINAL_B;
	private static volatile boolean panelOpen;
	private static volatile int localMouseScreenX = -1;
	private static volatile int localMouseScreenY = -1;
	private static volatile int localLookPixelX = -1;
	private static volatile int localLookPixelY = -1;
	private static volatile int localViewCenterX = -1;
	private static volatile int localViewCenterY = -1;
	private static volatile int localViewHalfWidth;
	private static volatile int localViewHalfHeight;
	private static volatile int localViewMode;
	private static volatile boolean localPointerInGame;
	private static long lastSyncMillis;

	private static final CoopRemotePointer remotePointer = new CoopRemotePointer();
	private static final COLOR POINTER_OUTLINE = new ColorImp(3, 10, 13);
	private static final ColorImp cursorTint = new ColorImp(ORIGINAL_R, ORIGINAL_G, ORIGINAL_B);
	private static final ColorImp panelColor = new ColorImp(ORIGINAL_R, ORIGINAL_G, ORIGINAL_B);

	private CoopCursor() {
	}

	public static boolean remoteCursorVisible() {
		return render;
	}

	public static void setRemoteCursorVisible(boolean visible) {
		render = visible;
		persistRender(CoopRuntime.configFilePath());
	}

	public static int colorR() {
		return colorR;
	}

	public static int colorG() {
		return colorG;
	}

	public static int colorB() {
		return colorB;
	}

	public static int swatchCount() {
		return SWATCHES.length;
	}

	public static int swatchR(int index) {
		return SWATCHES[index][0];
	}

	public static int swatchG(int index) {
		return SWATCHES[index][1];
	}

	public static int swatchB(int index) {
		return SWATCHES[index][2];
	}

	public static void setColor(int red, int green, int blue) {
		setColor(red, green, blue, CoopRuntime.configFilePath());
	}

	public static void adjustColor(int component, int amount) {
		adjustColor(component, amount, CoopRuntime.configFilePath());
	}

	public static void resetColor() {
		setColor(ORIGINAL_R, ORIGINAL_G, ORIGINAL_B);
	}

	static boolean remoteSettlementViewFresh() {
		return remotePointer.viewMode == 1 && remotePointer.viewCenterX >= 0
				&& System.currentTimeMillis() - remotePointer.updatedMillis <= 2500;
	}

	static int remoteViewCenterX() {
		return remotePointer.viewCenterX;
	}

	static int remoteViewCenterY() {
		return remotePointer.viewCenterY;
	}

	static int remoteViewHalfWidth() {
		return Math.max(C.TILE_SIZE * 12, remotePointer.viewHalfWidth);
	}

	static int remoteViewHalfHeight() {
		return Math.max(C.TILE_SIZE * 8, remotePointer.viewHalfHeight);
	}

	public static void loadPersistedSettings() {
		Path configPath = CoopRuntime.configFilePath();
		if (!Files.exists(configPath))
			return;
		try {
			for (String raw : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.length() == 0 || line.startsWith("#"))
					continue;
				int separator = line.indexOf('=');
				if (separator < 0)
					continue;
				String key = line.substring(0, separator).trim().toUpperCase();
				String value = line.substring(separator + 1).trim();
				loadConfig(key, value);
			}
		} catch (IOException | RuntimeException e) {
			CoopLog.warn("Could not load cursor settings: " + e.getMessage());
		}
	}

	public static void renderMenuCursor(SPRITE_RENDERER r, int x, int y) {
		renderNativeCursor(r, x, y, colorR, colorG, colorB);
	}

	static boolean loadConfig(String key, String value) {
		if ("CURSOR_SYNC".equals(key)) {
			sync = Boolean.parseBoolean(value);
			return true;
		}
		if ("CURSOR_RENDER".equals(key)) {
			render = Boolean.parseBoolean(value);
			return true;
		}
		if ("CURSOR_SYNC_INTERVAL_MS".equals(key)) {
			intervalMillis = Math.max(16, Integer.parseInt(value));
			return true;
		}
		if ("CURSOR_COLOR_R".equals(key)) {
			colorR = clampColor(Integer.parseInt(value));
			return true;
		}
		if ("CURSOR_COLOR_G".equals(key)) {
			colorG = clampColor(Integer.parseInt(value));
			return true;
		}
		if ("CURSOR_COLOR_B".equals(key)) {
			colorB = clampColor(Integer.parseInt(value));
			return true;
		}
		return false;
	}

	static void capture(COORDINATE mCoo) {
		if (mCoo == null) {
			localPointerInGame = false;
			return;
		}
		localMouseScreenX = mCoo.x();
		localMouseScreenY = mCoo.y();
		int viewMode = activeViewMode();
		localViewMode = viewMode;
		GameWindow w = activeWindow(viewMode);
		if (w == null) {
			localPointerInGame = false;
			return;
		}
		localViewCenterX = w.pixels().cX();
		localViewCenterY = w.pixels().cY();
		localViewHalfWidth = Math.max(1, w.pixels().width() / 2);
		localViewHalfHeight = Math.max(1, w.pixels().height() / 2);
		if (w.view().holdsPoint(mCoo)) {
			localPointerInGame = true;
			localLookPixelX = w.pixels().x1() + ((mCoo.x() - w.view().x1()) << w.zoomout());
			localLookPixelY = w.pixels().y1() + ((mCoo.y() - w.view().y1()) << w.zoomout());
		} else {
			localPointerInGame = false;
		}
	}

	static String pollCommand(boolean runtimeActive, boolean hostMode, boolean clientMode, boolean hostHasClients, boolean clientConnected) {
		if (!sync || !runtimeActive || localViewMode == 0 || localViewCenterX < 0)
			return null;
		if (localPointerInGame && localLookPixelX < 0)
			return null;
		if (hostMode && !hostHasClients)
			return null;
		if (clientMode && !clientConnected)
			return null;
		long now = System.currentTimeMillis();
		if (now - lastSyncMillis < Math.max(16, intervalMillis))
			return null;
		lastSyncMillis = now;
		return "P\t" + localViewMode + "\t" + localMouseScreenX + "\t" + localMouseScreenY + "\t" + localLookPixelX
				+ "\t" + localLookPixelY + "\t" + localViewCenterX + "\t" + localViewCenterY + "\t" + now
				+ "\t" + colorR + "\t" + colorG + "\t" + colorB + "\t" + (localPointerInGame ? 1 : 0)
				+ "\t" + localViewHalfWidth + "\t" + localViewHalfHeight;
	}

	static void applyRemote(String[] p) {
		if (!sync)
			return;
		remotePointer.viewMode = Integer.parseInt(p[1]);
		remotePointer.mouseScreenX = Integer.parseInt(p[2]);
		remotePointer.mouseScreenY = Integer.parseInt(p[3]);
		remotePointer.lookPixelX = Integer.parseInt(p[4]);
		remotePointer.lookPixelY = Integer.parseInt(p[5]);
		remotePointer.viewCenterX = Integer.parseInt(p[6]);
		remotePointer.viewCenterY = Integer.parseInt(p[7]);
		remotePointer.sentMillis = Long.parseLong(p[8]);
		if (p.length >= 12) {
			remotePointer.colorR = clampColor(Integer.parseInt(p[9]));
			remotePointer.colorG = clampColor(Integer.parseInt(p[10]));
			remotePointer.colorB = clampColor(Integer.parseInt(p[11]));
		} else {
			remotePointer.colorR = ORIGINAL_R;
			remotePointer.colorG = ORIGINAL_G;
			remotePointer.colorB = ORIGINAL_B;
		}
		remotePointer.inGame = p.length < 13 || "1".equals(p[12]) || "true".equalsIgnoreCase(p[12]);
		if (p.length >= 15) {
			remotePointer.viewHalfWidth = Math.max(0, Integer.parseInt(p[13]));
			remotePointer.viewHalfHeight = Math.max(0, Integer.parseInt(p[14]));
		}
		remotePointer.updatedMillis = System.currentTimeMillis();
	}

	static void render(Renderer r, boolean runtimeActive) {
		renderRemotePointer(r, runtimeActive);
		renderLocalPointerTint(r, runtimeActive);
	}

	static void mouseClick(MButt button, boolean runtimeActive, Path configPath) {
		panelOpen = false;
	}

	private static void renderRemotePointer(Renderer r, boolean runtimeActive) {
		if (!render || !sync || !runtimeActive)
			return;
		long updated = remotePointer.updatedMillis;
		if (updated == 0 || System.currentTimeMillis() - updated > 2500)
			return;
		if (!remotePointer.inGame)
			return;
		int viewMode = activeViewMode();
		if (viewMode == 0 || remotePointer.viewMode != viewMode)
			return;
		GameWindow w = activeWindow(viewMode);
		if (w == null)
			return;
		if (remotePointer.lookPixelX < 0 || remotePointer.lookPixelY < 0)
			return;
		int sx = screenX(w, remotePointer.lookPixelX);
		int sy = screenY(w, remotePointer.lookPixelY);
		RECTANGLE view = w.view();
		boolean inside = view.holdsPoint(sx, sy);
		int x = clamp(sx, view.x1() + 12, view.x2() - 12);
		int y = clamp(sy, view.y1() + 12, view.y2() - 12);
		r.newLayer(true, 0);
		renderNativeCursor(r, x, y, remotePointer.colorR, remotePointer.colorG, remotePointer.colorB);
		if (!inside) {
			OPACITY.O66.bind();
			POINTER_OUTLINE.renderFrame(r, x - 3, x + 27, y - 3, y + 27, 0, 2);
			OPACITY.unbind();
		}
	}

	private static void renderLocalPointerTint(Renderer r, boolean runtimeActive) {
		if (!sync || !runtimeActive || isOriginalColor(colorR, colorG, colorB))
			return;
		int x = localMouseScreenX;
		int y = localMouseScreenY;
		try {
			if (VIEW.mouse() != null) {
				x = VIEW.mouse().x();
				y = VIEW.mouse().y();
			}
		} catch (RuntimeException e) {
			return;
		}
		if (x < 0 || y < 0)
			return;
		r.newLayer(true, 0);
		renderCursorTint(r, x, y, colorR, colorG, colorB);
	}

	private static void renderPanel(SPRITE_RENDERER r, boolean runtimeActive) {
		if (!sync || !runtimeActive)
			return;
		int bx = buttonX();
		int by = buttonY();
		renderSmallButton(r, bx, by, 114, 30, "cursor");
		renderNativeCursor(r, bx + 84, by + 3, colorR, colorG, colorB);
		if (!panelOpen)
			return;
		int x = panelX();
		int y = panelY();
		COLOR.WHITE15.render(r, x, x + PANEL_W, y, y + PANEL_H);
		COLOR.WHITE50.renderFrame(r, x, x + PANEL_W, y, y + PANEL_H, 0, 2);
		COLOR.WHITE100.bind();
		UI.FONT().M.render(r, "MULTIPLAYER CURSOR", x + 14, y + 12);
		COLOR.unbind();
		renderNativeCursor(r, x + 18, y + 44, colorR, colorG, colorB);
		renderSwatches(r, x + 66, y + 46);
		renderColorRow(r, x + 18, y + 88, "R", colorR);
		renderColorRow(r, x + 18, y + 112, "G", colorG);
		renderColorRow(r, x + 18, y + 136, "B", colorB);
		renderSmallButton(r, x + 156, y + 132, 82, 28, "reset");
	}

	private static int activeViewMode() {
		try {
			if (VIEW.s() != null && VIEW.s().isActive())
				return 1;
			if (VIEW.world() != null && VIEW.world().isActive())
				return 2;
			if (VIEW.b() != null && VIEW.b().isActive())
				return 3;
		} catch (RuntimeException e) {
			CoopRuntime.rethrowFatal(e);
		}
		return 0;
	}

	private static GameWindow activeWindow(int viewMode) {
		try {
			if (viewMode == 1)
				return VIEW.s().getWindow();
			if (viewMode == 2)
				return VIEW.world().window;
			if (viewMode == 3)
				return VIEW.b().getWindow();
		} catch (RuntimeException e) {
			CoopRuntime.rethrowFatal(e);
		}
		return null;
	}

	private static int screenX(GameWindow w, int pixelX) {
		return w.view().x1() + ((pixelX - w.pixels().x1()) >> w.zoomout());
	}

	private static int screenY(GameWindow w, int pixelY) {
		return w.view().y1() + ((pixelY - w.pixels().y1()) >> w.zoomout());
	}

	private static void renderNativeCursor(SPRITE_RENDERER r, int x, int y, int red, int green, int blue) {
		OPACITY.O50.bind();
		COLOR.BLACK.bind();
		UI.decor().mouse.render(r, x + 2, y + 2);
		COLOR.unbind();
		OPACITY.unbind();
		UI.decor().mouse.render(r, x, y);
		if (!isOriginalColor(red, green, blue))
			renderCursorTint(r, x, y, red, green, blue);
	}

	private static void renderCursorTint(SPRITE_RENDERER r, int x, int y, int red, int green, int blue) {
		OPACITY.O66.bind();
		cursorTint.set(clampColor(red), clampColor(green), clampColor(blue)).bind();
		UI.decor().mouse.render(r, x, y);
		COLOR.unbind();
		OPACITY.unbind();
	}

	private static boolean isOriginalColor(int red, int green, int blue) {
		return clampColor(red) == ORIGINAL_R && clampColor(green) == ORIGINAL_G && clampColor(blue) == ORIGINAL_B;
	}

	private static void renderSwatches(SPRITE_RENDERER r, int x, int y) {
		for (int i = 0; i < SWATCHES.length; i++) {
			int sx = x + i * 28;
			int[] c = SWATCHES[i];
			panelColor.set(c[0], c[1], c[2]).render(r, sx, sx + 20, y, y + 20);
			COLOR.WHITE50.renderFrame(r, sx, sx + 20, y, y + 20, 0, 1);
		}
	}

	private static void renderColorRow(SPRITE_RENDERER r, int x, int y, String label, int value) {
		COLOR.WHITE100.bind();
		UI.FONT().S.render(r, label, x, y + 5);
		COLOR.unbind();
		renderSmallButton(r, x + 28, y, 24, 22, "-");
		COLOR.WHITE65.bind();
		UI.FONT().S.render(r, Integer.toString(value), x + 62, y + 5);
		COLOR.unbind();
		renderSmallButton(r, x + 104, y, 24, 22, "+");
	}

	private static void renderSmallButton(SPRITE_RENDERER r, int x, int y, int w, int h, String text) {
		COLOR.WHITE10.render(r, x, x + w, y, y + h);
		COLOR.WHITE35.renderFrame(r, x, x + w, y, y + h, 0, 1);
		COLOR.WHITE100.bind();
		UI.FONT().S.render(r, text, x + 8, y + (h - UI.FONT().S.height()) / 2 - 1);
		COLOR.unbind();
	}

	private static boolean handleColorRowClick(int mx, int my, int x, int y, int component, Path configPath) {
		if (holds(mx, my, x + 28, y, 24, 22)) {
			adjustColor(component, -16, configPath);
			return true;
		}
		if (holds(mx, my, x + 104, y, 24, 22)) {
			adjustColor(component, 16, configPath);
			return true;
		}
		return false;
	}

	private static void adjustColor(int component, int amount, Path configPath) {
		int r = colorR;
		int g = colorG;
		int b = colorB;
		if (component == 0)
			r = clampColor(r + amount);
		else if (component == 1)
			g = clampColor(g + amount);
		else
			b = clampColor(b + amount);
		setColor(r, g, b, configPath);
	}

	private static void setColor(int red, int green, int blue, Path configPath) {
		colorR = clampColor(red);
		colorG = clampColor(green);
		colorB = clampColor(blue);
		persistColor(configPath);
	}

	private static void persistColor(Path configPath) {
		try {
			List<String> lines = Files.exists(configPath) ? Files.readAllLines(configPath, StandardCharsets.UTF_8) : new ArrayList<String>();
			lines = upsertConfigLine(lines, "CURSOR_COLOR_R", Integer.toString(colorR));
			lines = upsertConfigLine(lines, "CURSOR_COLOR_G", Integer.toString(colorG));
			lines = upsertConfigLine(lines, "CURSOR_COLOR_B", Integer.toString(colorB));
			Files.write(configPath, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			CoopLog.warn("Could not save cursor color config: " + e.getMessage());
		}
	}

	private static void persistRender(Path configPath) {
		try {
			List<String> lines = Files.exists(configPath) ? Files.readAllLines(configPath, StandardCharsets.UTF_8) : new ArrayList<String>();
			lines = upsertConfigLine(lines, "CURSOR_RENDER", Boolean.toString(render));
			Files.write(configPath, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			CoopLog.warn("Could not save cursor visibility config: " + e.getMessage());
		}
	}

	private static List<String> upsertConfigLine(List<String> lines, String key, String value) {
		ArrayList<String> out = new ArrayList<>(lines);
		String prefix = key + " =";
		for (int i = 0; i < out.size(); i++) {
			String trimmed = out.get(i).trim();
			if (trimmed.toUpperCase().startsWith(prefix)) {
				out.set(i, key + " = " + value);
				return out;
			}
		}
		out.add(key + " = " + value);
		return out;
	}

	private static int buttonX() {
		return Math.max(12, C.WIDTH() - 138);
	}

	private static int buttonY() {
		return 92;
	}

	private static int panelX() {
		return Math.max(12, C.WIDTH() - PANEL_W - 24);
	}

	private static int panelY() {
		return buttonY() + 38;
	}

	private static boolean holds(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int clampColor(int value) {
		if (value < 0)
			return 0;
		if (value > 255)
			return 255;
		return value;
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
}
