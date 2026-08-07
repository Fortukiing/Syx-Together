package view.tool;

import coopmod.CoopRuntime;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sets.LIST;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.ToolPlacer.placeFunc;

final class PlacableSimpleTool extends placeFunc {

	private PlacableSimple placable;
	private boolean clicked = false;
	int cx, cy;
	@Override
	void updateHovered(float ds, GameWindow window, boolean pressed) {
		clicked = MButt.LEFT.isDown();
		if (clicked && !window.pixel().isSameAs(cx, cy)) {
			cx = window.pixel().x();
			cy = window.pixel().y();
			if (placable.isPlacable(cx, cy) == null) {
				placable.place(cx, cy);
				CoopRuntime.placedSimple(placable, cx, cy);
			}
			
		}else {
			placable.placeInfo(VIEW.hoverBox(), window.pixel().x(), window.pixel().y());
		}
		
	}
	
	@Override
	void update(float ds, GameWindow window, boolean pressed) {
		// TODO Auto-generated method stub
		super.update(ds, window, pressed);
	}

	@Override
	void render(SPRITE_RENDERER r, float ds, GameWindow window) {
		
		int tx = window.pixel().x();
		int ty = window.pixel().y();
		
		placable.renderOverlay(tx, ty, r, ds, window);
		CharSequence problem = placable.isPlacable(tx, ty);
		
		if (problem == null) {
			placable.renderPlaceHolder(r, window.pixel().rel().x(), window.pixel().rel().y(), false);
		}else {
			placable.renderPlaceHolder(r, window.pixel().rel().x(), window.pixel().rel().y(), true);
			VIEW.hoverBox().error(problem);
		}
		placable.renderAction(tx, ty);
		COLOR.unbind();
	}

	@Override
	void click(GameWindow window) {
		int tx = window.pixel().x();
		int ty = window.pixel().y();
		
		CharSequence problem = placable.isPlacable(tx, ty);
		if (problem != null)
			return;
		
		placable.place(tx, ty);
		CoopRuntime.placedSimple(placable, tx, ty);
		clicked = true;
		cx = tx;
		cy = ty;
		
	}

	@Override
	void activate(PLACABLE placer, GameWindow window) {
		placable = (PlacableSimple) placer;
	}

	@Override
	void clickRelease(GameWindow window) {
		
	}

	@Override
	LIST<CLICKABLE> gui() {
		return null;
	}

	
};
