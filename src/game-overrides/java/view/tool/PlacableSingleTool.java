package view.tool;

import coopmod.CoopRuntime;
import init.constant.C;
import settlement.main.SETT;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sets.LIST;
import util.GUTIL;
import util.colors.GCOLOR;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.ToolPlacer.placeFunc;

final class PlacableSingleTool extends placeFunc {

	private PlacableSingle placable;
	@Override
	void updateHovered(float ds, GameWindow window, boolean pressed) {
		
		
	}
	

	@Override
	void render(SPRITE_RENDERER r, float ds, GameWindow window) {
		
		int tx = window.tile().x();
		int ty = window.tile().y();
		
		placable.init(tx, ty);
		
		CharSequence problem = placable.isPlacable(tx, ty);
		
		if (problem == null) {
			int t = 0;
			GUTIL.filler().init(this);
			GUTIL.filler().fill(tx, ty);
			while(GUTIL.filler().hasMore()) {
				COORDINATE c = GUTIL.filler().poll();
				t++;
				int mask = 0;
				for (DIR d : DIR.ORTHO) {
					int dx = c.x()+d.x();
					int dy = c.y()+d.y();
					if (!SETT.IN_BOUNDS(dx, dy))
						continue;
					if (dx == tx && dy == ty) {
						mask |= d.mask();
					}else if (GUTIL.filler().isFilled(dx, dy) || (placable.isPlacable(dx, dy) == null && placable.expandsTo(c.x(), c.y(), dx, dy))){
						mask |= d.mask();
						GUTIL.filler().fill(dx, dy);
					}
				}
				render(r, mask, c.x(), c.y(), true, window);
			}
			GUTIL.filler().done();
			placable.placeInfo(VIEW.hoverBox(), t);
		}else {
			render(r, 0, tx, ty, false, window);
			VIEW.hoverBox().error(problem);
		}
		COLOR.unbind();
	}
	
	private void render(SPRITE_RENDERER r, int mask, int tx, int ty, boolean placable, GameWindow window) {
		if (placable)
			GCOLOR.MAP().OK.bind();
		else
			GCOLOR.MAP().BAD.bind();
		int x = (tx-window.tile().x())*C.TILE_SIZE+window.tile().rel().x();
		int y = (ty-window.tile().y())*C.TILE_SIZE+window.tile().rel().y();
		this.placable.renderPlaceHolder(r, mask, x, y, tx, ty, placable);
	}

	@Override
	void click(GameWindow window) {
		int tx = window.tile().x();
		int ty = window.tile().y();
		
		CharSequence problem = placable.isPlacable(tx, ty);
		if (problem != null)
			return;
		
		placable.placeFirst(tx, ty);
		
		
		GUTIL.filler().init(this);
		GUTIL.filler().fill(tx, ty);
		while(GUTIL.filler().hasMore()) {
			COORDINATE c = GUTIL.filler().poll();
			placable.placeExpanded(c.x(), c.y());
			for (DIR d : DIR.ORTHO) {
				int dx = c.x()+d.x();
				int dy = c.y()+d.y();
				if (!SETT.IN_BOUNDS(dx, dy))
					continue;
				if (dx == tx && dy == ty)
					continue;
				if ((placable.isPlacable(dx, dy) == null && placable.expandsTo(c.x(), c.y(), dx, dy))){
					GUTIL.filler().fill(dx, dy);
				}
			}
		}
		GUTIL.filler().done();
		CoopRuntime.placedSingle(placable, tx, ty);
	}

	@Override
	void activate(PLACABLE placer, GameWindow window) {
		placable = (PlacableSingle) placer;
	}

	@Override
	void clickRelease(GameWindow window) {
		
	}

	@Override
	LIST<CLICKABLE> gui() {
		return null;
	}

	
};
