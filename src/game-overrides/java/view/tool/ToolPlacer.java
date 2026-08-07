package view.tool;


import coopmod.CoopRuntime;
import init.sprite.SPRITES;
import init.sprite.UI.Icon;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.AREA;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LISTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GButtablePanel;
import util.text.D;
import view.keyboard.KEYS;
import view.main.VIEW;
import view.subview.GameWindow;

public final class ToolPlacer extends Tool{

	private boolean pressed;
	
	private placeFunc current;
	private placeFunc normal;

	private PLACABLE placer;
	private PLACABLE origional;
	private PLACABLE undo;

	private boolean buttonsStolen;
	private final GameWindow window;
	
	
	{
		D.gInit(this);
	}
	
	public static AREA area() {
		return PlacerArea.self;
	}
	
	private GButt.Panel buttUndo = new GButt.Panel(SPRITES.icons().m.cancel) {
		
		
		@Override
		protected void clickA() {
			if (placer != undo) {
				placer = undo;
				current = get(placer);
				current.activate(placer, window);
				selectedSet(true);
			}else {
				placer = origional;
				current = get(placer);
				current.activate(placer, window);
				selectedSet(true);
			}
		};
		
		@Override
		protected void renAction() {
			if (placer == undo)
				selectTmp();
		};
		
		@Override
		public void hoverInfoGet(snake2d.util.gui.GUI_BOX text) {
			text.text(undo.name());
			text.text(KEYS.MAIN().UNDO.repr());
		};
	};

	
	private GButt.Panel buttExit = new GButt.Panel(SPRITES.icons().m.exit, D.g("Close")) {
		@Override
		protected void clickA() {
			deactivate();
		};
	};
	
	private final ToolConfig configDefault = new ToolConfig() {
		
		@Override
		public void addUI(LISTE<RENDEROBJ> uis){
			if (!buttonsStolen) {
				addStandardButtons(uis, true);
			}
			
			buttonsStolen = false;
			
			
		}
		
	};
	
	private final GButtablePanel panel = new GButtablePanel();
	
	public void addStandardButtons(LISTE<RENDEROBJ> uis, boolean exitAlso) {
		panel.clear();
		LIST<CLICKABLE> ps = normal.gui();
		
		int w = 0;
		if (origional.getAdditionalButt() != null) {
			for (CLICKABLE b : origional.getAdditionalButt()) {
				w += b.body().width();
			}
		}
		
		if (w > 5*Icon.M) {
			
			if (origional.getAdditionalButt() != null) {
				for (CLICKABLE b : origional.getAdditionalButt()) {
					panel.addButton(b);
				}
			}
			
			panel.nl();
			
			if (ps != null) {
				for (CLICKABLE b : ps)
					panel.addButton(b);
			}
				
			
			if (undo != null) {
				panel.addButton(buttUndo);
			}

		}else {
			if (ps != null) {
				for (CLICKABLE b : ps)
					panel.addButton(b);
			}
				
			
			if (undo != null) {
				panel.addButton(buttUndo);
			}
			if (origional.getAdditionalButt() != null) {
				for (CLICKABLE b : origional.getAdditionalButt()) {
					panel.addButton(b);
				}
			}
				
			
		}
		
		panel.addTitle(placer.name());
		if (exitAlso)
			panel.addButton(buttExit);
		
		if (exitAlso)
			panel.addButton(buttExit);
		
		uis.add(panel);
	}

	

	

	
	private final placeFunc multi = new PlacableMultiTool();
	
	private final placeFunc fixed = new PlacableFixedTool();
	
	private final placeFunc single2 = new PlacableSingleTool();
	
	private final placeFunc simple = new PlacableSimpleTool();

	private final placeFunc simpleTile = new PlacableSimpleTileTool();
	ToolPlacer(ToolManager manager, GameWindow window) {
		super(manager);
		this.window = window;
	}

	
	void activate(PLACABLE placer){

		
		buttUndo.selectedSet(false);
		CoopRuntime.activated(placer);
		
		
		this.placer = placer;
		this.origional = placer;
		this.undo = placer.getUndo();

		normal = get(placer);
		normal.activate(placer, window);
		current = normal;
		
		pressed = false;
		
	}
	
	private placeFunc get(PLACABLE placer) {
		if (placer instanceof PlacableFixed)
			return fixed;
		if (placer instanceof PlacableSingle)
			return single2;
		if (placer instanceof PlacableMulti) {
			return multi;
		}else if (placer instanceof PlacableSimple)
			return simple;
		else if (placer instanceof PlacableSimpleTile)
			return simpleTile;
		else {
			throw new RuntimeException();
		}
	}
	
	@Override
	public void click(GameWindow window) {
		pressed = true;
		current.click(window);
		
	}
	
	@Override
	public void updateHovered(float ds, GameWindow window) {
		

		if (pressed && !MButt.LEFT.isDown()) {
			current.clickRelease(window);
		}
		update(ds, window);
		
		current.updateHovered(ds, window, pressed);
	}

	@Override
	protected void update(float ds, GameWindow window) {
		
		if (KEYS.MAIN().UNDO.isPressed() && placer == origional && placer.getUndo() != null) {
			if (placer instanceof PlacableMulti && placer.getUndo() instanceof PlacableMulti) {
				//PlacableMulti m = (PlacableMulti) placer.getUndo();
				//prevUndoType = m.previous;
				((PlacableMulti) placer.getUndo()).previous = ((PlacableMulti) placer).previous;
			}
			placer = placer.getUndo();
			if (placer == origional)
				throw new RuntimeException("" + placer);
			current = get(placer);
			
			current.activate(placer, window);
		}else if(!buttUndo.selectedIs() && !KEYS.MAIN().UNDO.isPressed() && placer != origional) {
//			if (placer instanceof PlacableMulti && placer.getUndo() instanceof PlacableMulti) {
//				PlacableMulti m = (PlacableMulti) placer.getUndo();
//				m.previous = prevUndoType;
//			}
			placer = origional;
			current = get(placer);
			current.activate(placer, window);
		}
		
		if (placer == origional.getUndo())
			VIEW.mouse().setReplacement(SPRITES.icons().m.cancel);
		
		current.update(ds, window, pressed);
		pressed = pressed & MButt.LEFT.isDown();
		
	}
	
	@Override
	public void renderHovered(SPRITE_RENDERER r, float ds, GameWindow window, GBox box) {
		current.render(r, ds, window);
	}
	
	@Override
	protected void render(SPRITE_RENDERER r, float ds, GameWindow window) {
	
	}
	
	public void stealButtons(GuiSection s) {
		stealButtons(s, true);
		
	}
	
	public void stealButtons(GuiSection s, boolean undo) {
		buttonsStolen = true;
		
		LIST<CLICKABLE> ps = normal.gui();
		if (ps != null)
		for (CLICKABLE c : ps) {
			s.addRightC(0, c);
			c.activeSet(true);
		}
		
		
		
		if (undo && this.undo != null) {
			s.addRightC(0, buttUndo);
		}
//		if (origional.getAdditionalButt() != null) {
//			for (GButt.Panel b : origional.getAdditionalButt()) {
//				s.addRightC(0, b);
//			}
//		}
		
	}
	
	@Override
	public boolean rightClick() {
		if (pressed) {
			pressed = false;
			return false;
		}
		return true;
	}
	
	static abstract class placeFunc {
		
		placeFunc(){
		}
		
		abstract void updateHovered(float ds, GameWindow window, boolean pressed);
		void update(float ds, GameWindow window, boolean pressed) {
			
		}
		abstract void render(SPRITE_RENDERER r, float ds, GameWindow window);
		abstract void click(GameWindow window);
		abstract void clickRelease(GameWindow window);
		abstract void activate(PLACABLE placer, GameWindow window);
		abstract LIST<CLICKABLE> gui();
	}

	public PLACABLE getCurrent() {
		if (!isActivated())
			return null;
		return placer;
	}
	
	@Override
	protected ToolConfig defaultConfig() {
		return configDefault;
	}

}
