package view.tool;

import coopmod.CoopRuntime;
import init.constant.C;
import init.sprite.SPRITES;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.text.Dic;
import view.keyboard.KEYS;
import view.keyboard.KeyButt;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.ToolPlacer.placeFunc;

final class PlacableFixedTool extends placeFunc {

	private PlacableFixed placable;

	private final ArrayList<CLICKABLE> butts = new ArrayList<>(3);
	private final CLICKABLE bIncrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.plus) {
		{hoverInfoSet("" + KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd);}
		@Override
		protected void renAction() {
			activeSet(placable.size() < placable.sizes()-1);
		};
		@Override
		protected void clickA() {
			if (placable.size() < placable.sizes()-1)
				placable.sizeSet(placable.size()+1);
		};
	}, KEYS.MAIN().GROW);
	private final CLICKABLE bDecrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.minus) {
		{hoverInfoSet("" + KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd);}
		@Override
		protected void renAction() {
			activeSet(placable.size() > 0);
			
		};
		@Override
		protected void clickA() {
			if (placable.size() > 0)
				placable.sizeSet(placable.size()-1);
		};
	}, KEYS.MAIN().SHRINK);
	private final CLICKABLE bRotate = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.rotate) {
		@Override
		protected void clickA() {
			int r = placable.rot()+1;
			r %= placable.rotations();
			placable.rotSet(r);
		};
	}, KEYS.MAIN().ROTATE);
	
	@Override
	void updateHovered(float ds, GameWindow window, boolean pressed) {
		
		double s = MButt.peekWheel();
		if (KEYS.MAIN().MOD.isPressed() && s != 0) {
			
			if (s > 0 && placable.size() < placable.sizes()-1) {
				placable.sizeSet(placable.size()+1);
			}else if (s < 0 && placable.size() > 0){
				placable.sizeSet(placable.size()-1);
			}
			MButt.clearWheelSpin();
		}
		
		if (KEYS.MAIN().GROW.consumeClick() && placable.size() < placable.sizes()-1) {
			placable.sizeSet(placable.size()+1);
		}else if (KEYS.MAIN().SHRINK.consumeClick() && placable.size() > 0){
			placable.sizeSet(placable.size()-1);
		}
		
		if (KEYS.MAIN().ROTATE.consumeClick()) {
			int r = placable.rot()+1;
			r %= placable.rotations();
			placable.rotSet(r);
		}
		
		if (pressed) {
			click(window);
		}
		
	}
	
	@Override
	void update(float ds, GameWindow window, boolean pressed) {
		placable.updateRegardless(window);
		super.update(ds, window, pressed);
	}

	@Override
	void render(SPRITE_RENDERER r, float ds, GameWindow window) {
		
		placable.init(window.tile().x(), window.tile().y());
		
		int w = placable.width();
		int h = placable.height();
		
		int x1 = window.tile().x()-w/2;
		int y1 = window.tile().y()-h/2;
		
		
		CharSequence pError = placable.placableWhole(x1, y1);
		CharSequence e = null;
		
		for (int dy = 0; dy<h; dy++) {
			for (int dx = 0; dx<w; dx++) {
				CharSequence e2 = placable.placable(x1+dx, y1+dy, dx, dy);
				if (e2 != null) {
					e = e2;
				}
			}
		}
		
		COLOR normal = pError == null && e == null ? GCOLOR.MAP().OK : GCOLOR.MAP().SOSO;
		
		for (int dy = 0; dy<h; dy++) {
			for (int dx = 0; dx<w; dx++) {
				CharSequence e2 = placable.placable(x1+dx, y1+dy, dx, dy);
				if (e2 != null) {
					GCOLOR.MAP().BAD.bind();
				}else {
					normal.bind();
				}
				int x = window.tile().rel().x()+(-w/2 + dx)*C.TILE_SIZE;
				int y = window.tile().rel().y()+(-h/2 + dy)*C.TILE_SIZE;;
				
				int m = 0;
				if (dx == 0)
					m |= DIR.W.mask();
				if (dx == w-1)
					m |= DIR.E.mask();
				if (dy == 0)
					m |= DIR.N.mask();
				if (dy == h-1)
					m |= DIR.S.mask();
				
				m = ~m;
				m &= 0x0F;
				
				placable.renderPlaceHolder(r, m, x, y, x1+dx, y1+dy, dx, dy, e2 == null, pError == null);
				
			}
		}
		COLOR.unbind();
		int dist = (int) Math.ceil(h/2.0 + 1);
		dist *= C.TILE_SIZE;
		dist = dist >> CORE.renderer().getZoomout();
		VIEW.hoverBoxDistance(dist);
		if (pError != null && pError.length() > 0)
			VIEW.hoverBox().error(pError);
		else if (e != null && e.length() > 0)
			VIEW.hoverBox().error(e);
		else
			placable.placeInfo(VIEW.hoverBox(), x1, y1);
	}

	@Override
	void click(GameWindow window) {
		
		placable.init(window.tile().x(), window.tile().y());
		
		
		final int w = placable.width();
		final int h = placable.height();
		
		final int x1 = window.tile().x()-w/2;
		final int y1 = window.tile().y()-h/2;
		
		if (placable.placableWhole(x1, y1) != null)
			return;
		
		for (int dy = 0; dy<h; dy++) {
			for (int dx = 0; dx<w; dx++) {
				if (placable.placable(x1+dx, y1+dy, dx, dy) != null)
					return;
			}
		}
		
		for (int dy = 0; dy<h; dy++) {
			for (int dx = 0; dx<w; dx++) {
				placable.place(x1+dx, y1+dy, dx, dy);
			}
		}
		
		placable.afterPlaced(x1, y1);
		CoopRuntime.placedFixed(placable, window.tile().x(), window.tile().y());
		
	}

	@Override
	void activate(PLACABLE placer, GameWindow window) {
		placable = (PlacableFixed) placer;
	}

	@Override
	void clickRelease(GameWindow window) {
		
	}

	@Override
	LIST<CLICKABLE> gui() {
		butts.clear();
		if (placable.sizes() > 1) {
			butts.add(bDecrease);
			butts.add(bIncrease);
			
		}
		
		if (placable.rotations() > 1)
			butts.add(bRotate);
		
		return butts;
	}

	
	
	
};
