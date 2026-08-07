package view.sett.ui.home;

import coopmod.CoopRuntime;
import init.constant.C;
import init.race.RACES;
import init.race.Race;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import init.type.HCLASSES;
import init.type.HGROUP;
import init.type.HGROUP.HTypeBits;
import init.type.HGROUP.HTypeBitsImp;
import settlement.main.ON_TOP_RENDERABLE;
import settlement.main.SETT;
import settlement.room.home.house.HomeInstance;
import settlement.room.home.house.HomeInstance.State;
import settlement.room.main.ROOMA;
import settlement.room.main.util.RoomState;
import snake2d.Renderer;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GButt;
import util.rendering.RenderData;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import util.text.D;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;

final class UIHomeAssign extends PlacableMulti{

	private static CharSequence ¤¤name = "Assign";
	private static CharSequence ¤¤desc = "Lets you assign homes to specific criteria.";
	private static CharSequence ¤¤prob = "Must be placed on a house or a house construction.";
	
	private static CharSequence ¤¤everyone = "Set permission for everyone";
	private static CharSequence ¤¤none = "Set permission for none";
	private static CharSequence ¤¤permission = "Set permission for:";
	private static CharSequence ¤¤permissionAll = "Set permission for All:";
	
	
	static {
		D.ts(UIHomeAssign.class);
	}
	
	private final HTypeBitsImp data = new HTypeBitsImp(false);

	private final LIST<CLICKABLE> butts;
	
	public UIHomeAssign() {
		super(¤¤name, ¤¤desc, SPRITES.icons().m.citizen);
		
		GuiSection sec = new GuiSection();
		sec.addRightC(0, new GButt.ButtPanel(SPRITES.icons().m.questionmark) {
			
			@Override
			protected void clickA() {
				data.setEveryone();
			}
			
		}.hoverInfoSet(¤¤everyone));
		sec.addRightC(0, new GButt.ButtPanel(HCLASSES.CITIZEN().icon()) {
			
			@Override
			protected void clickA() {
				data.clear();
				for (Race r : RACES.all())
					data.set(HGROUP.get(HCLASSES.CITIZEN(), r));
			}
			
		}.hoverInfoSet(¤¤permissionAll + " " + HCLASSES.CITIZEN().names));
		sec.addRightC(0, new GButt.ButtPanel(HCLASSES.SLAVE().icon()) {
			
			@Override
			protected void clickA() {
				data.clear();
				for (Race r : RACES.all())
					data.set(HGROUP.get(HCLASSES.SLAVE(), r));
			}
			
		}.hoverInfoSet(¤¤permissionAll + " " + HCLASSES.SLAVE().names));
		sec.addRightC(0, new GButt.ButtPanel(SPRITES.icons().m.cancel) {
			
			@Override
			protected void clickA() {
				data.clear();
			}
			
		}.hoverInfoSet(¤¤none));
		
		GuiSection s = new GuiSection();
		for (HGROUP t : HGROUP.all()) {
			CLICKABLE bb = new GButt.ButtPanel(t.icon) {
				
				@Override
				protected void clickA() {
					
					if (selectedIs())
						data.clear(t);
					else
						data.set(t);
				}
				
				@Override
				protected void renAction() {
					selectedSet(data.is(t));
				}
			}.hoverInfoSet(¤¤permission + " " + t.name);
			
			if (s.getLastX2() > 500) {
				bb.body().moveX1(0).moveY1(s.body().y2());
				s.add(bb);
			}else
				s.addRightC(0, bb);
			
		}
		
		sec.addRelBody(8, DIR.S, s);
		
		butts = new ArrayList<CLICKABLE>(sec);
	}

	@Override
	public CharSequence isPlacable(int tx, int ty, AREA area, PLACER_TYPE type) {
		if (room(tx, ty) != null)
			return null;
		return ¤¤prob;
	}

	@Override
	public void place(int tx, int ty, AREA area, PLACER_TYPE type) {
		settingSet(data, tx, ty);
	}
	
	@Override
	public boolean expandsTo(int fromX, int fromY, int toX, int toY) {
		return room(fromX, fromY) != null && room(fromX, fromY) == room(toX, toY);
	}

	@Override
	public LIST<CLICKABLE> getAdditionalButt() {
		
		ren.add();		
		return butts;
	}
	
	public ROOMA room(int tx, int ty) {
		if (availability(tx, ty) != null)
			return (ROOMA) SETT.ROOMS().map.get(tx, ty);
		return null;
	}
	
	public HomeInstance.State state(int tx, int ty){
		RoomState state = SETT.ROOMS().construction.state(tx, ty);
		if (state instanceof HomeInstance.State) {
			return (State) state;
		}
		return null;
	}
	
	public HTypeBits availability(int tx, int ty) {
		HomeInstance h = SETT.ROOMS().HOME.getter.get(tx, ty);
		if (h != null)
			return h.setting();
		State s = state(tx, ty);
		if (s != null)
			return s.egroup;
		return null;
	}
	
	public void settingSet(HTypeBits bits, int tx, int ty) {
		HomeInstance h = SETT.ROOMS().HOME.getter.get(tx, ty);
		if (h != null)
			h.settingSet(bits);
		else {
			State s = state(tx, ty);
			if (s != null)
				s.egroup.copy(bits);
		}
		CoopRuntime.homeSettingChanged(tx, ty, bits);
	}
	
	private final ON_TOP_RENDERABLE ren = new ON_TOP_RENDERABLE() {
		
		@Override
		public void render(Renderer r, ShadowBatch shadowBatch, RenderData data, double ds) {
			RenderIterator it = data.onScreenTiles();
			while(it.has()) {
				ROOMA h = room(it.tx(), it.ty());
				if (h != null && h.body().cX() == it.tx() && h.body().cY() == it.ty()) {
					
					render(r, h, availability(it.tx(), it.ty()), it);
				}
				it.next();
			}
			remove();
		}
		
		private final ArrayList<HGROUP> rens = new ArrayList<>(HGROUP.all().size());
		
		private void render(Renderer r, ROOMA h, HTypeBits t, RenderIterator it) {
			
			double dx = h.body().x1()+h.body().width()*0.5;
			dx = dx -h.body().x1();
			int cx = it.x()-C.TILE_SIZEH;// (int) (it.x() + dx*C.TILE_SIZE);
			
			double dy = h.body().y1()+h.body().height()*0.5;
			dy = dy -h.body().y1();
			int cy = it.y()-C.TILE_SIZEH;// (int) (it.y() + dy*C.TILE_SIZE);
			
//			HTypeBits t = h.setting();
			int am = 0;
			HGROUP single = null;
			for (HGROUP hh : HGROUP.all()) {
				if (t.is(hh)) {
					single = hh;
					am++;
				}
			}
			
			if (am == HGROUP.all().size()) {
				renderSingle(r, cx, cy, UI.icons().m.questionmark);
			}else if (am == 0) {
				renderSingle(r, cx, cy, UI.icons().m.cancel);
			}else if (am == 1) {
				renderSingle(r, cx, cy, single.icon);
			}else if (am < HGROUP.all().size()/2) {
				rens.clearSloppy();
				for (HGROUP hh : HGROUP.all()) {
					if (t.is(hh)) {
						rens.add(hh);
					}
				}
				renderMany(r, cx, cy, h, false);
			}else {
				rens.clearSloppy();
				for (HGROUP hh : HGROUP.all()) {
					if (!t.is(hh)) {
						rens.add(hh);
					}
				}
				if (rens.size() > 0)
					renderMany(r, cx, cy, h, true);
			}
		}
		
		private void renderSingle(Renderer r, int cx, int cy, SPRITE icon) {
			int w = icon.width()*C.SCALE;
			int h = icon.height()*C.SCALE;
			int x1 = cx-w/2;
			int y1 = cy-h/2;
			icon.render(r, x1, x1+w, y1, y1+h);
		}
		
		private void renderMany(Renderer r, int cx, int cy, ROOMA house, boolean anti) {
			
			int width = house.body().width()*C.TILE_SIZE;
			int w = 24*C.SCALE/2;
			int h = rens.get(0).icon.height()*C.SCALE/2;
			
			int dx = (width/rens.size());
			dx = CLAMP.i(dx, 1, w);
			
			int x1 = cx - dx*rens.size()/2;
			int y1 = cy - h;
			
			for (HGROUP t : rens) {
				t.icon.render(r, x1, x1+w, y1, y1+h);
				if (anti)
					UI.icons().m.anti.render(r, x1, x1+w, y1, y1+h);
				x1 += dx;
			}
		}
	};
	
}
