package view.sett.ui.room;

import static settlement.main.SETT.ROOMS;

import init.race.RACES;
import init.race.Race;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HTYPES;
import settlement.environment.SettEnvMap.SettEnv;
import settlement.main.SETT;
import settlement.room.infra.gate.ROOM_GATE;
import settlement.room.infra.monument.ROOM_MONUMENT;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEquip;
import settlement.room.service.module.RoomFinderHaser;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.ACTION;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.text.D;
import view.interrupter.ISidePanel;
import view.interrupter.Interrupter;
import view.main.VIEW;

public final class UIRooms {


	final UIRoom[] rooms = new UIRoom[ROOMS().all().size()];
	private UIPanelMain main;
	
	private final GuiSection pop2 = new GuiSection();
	private static CharSequence ¤¤reconstructPrompt = "¤Do you wish to refurnish this room? Some progress of the current construction will be lost.";
	static {
		D.ts(UIRooms.class);
	}
	
	public UIRooms() {
		
		Init init = new Init();
		Modules mm = new Modules(init);
		
		D.t(UIRoom.class);
		
		for (RoomBlueprint p : ROOMS().all()) {
			rooms[p.index()] = new UIRoom(p, mm.get(p));
		}
		main = new UIPanelMain(rooms);
		
		SETT.addGeneratorHook(new ACTION() {
			
			@Override
			public void exe() {
				main = new UIPanelMain(rooms);
			}
		});
		
		for (int i = 0; i < RACES.all().size(); i++) {
			Race r = RACES.all().get(i);

			CLICKABLE c = new GButt.ButtPanel(r.appearance().iconBig.big) {
				@Override
				protected void clickA() {
					VIEW.inters().popup.close();
					main.work.set(r, HTYPES.SLAVE());
					VIEW.s().panels.add(main.work, true);
				};
			}.hoverInfoSet(r.info.names);
			c.body().moveX1Y1((i % 5) * c.body().width(), (i / 5) * c.body().height());
			pop2.add(c);
		}
		


	}
	
	
	
	public ISidePanel main() {
		
		return main;
	}

	private static boolean isInvalidForRoomUi(Room r) {
		try {
			return r == null || r.blueprint() == null || r.icon() == null;
		} catch (Exception e) {
			return true;
		}
	}
	
	public void hover(GBox box, Room r, int rx, int ry) {
		if (isInvalidForRoomUi(r))
			return;
		rooms[r.blueprint().index()].hover(box, r, rx, ry);
	}
	
	public boolean problem(Room r, int rx, int ry) {
		if (isInvalidForRoomUi(r))
			return false;
		return rooms[r.blueprint().index()].problem(r, rx, ry);
	}
	
	public boolean warning(Room r, int rx, int ry) {
		if (isInvalidForRoomUi(r))
			return false;
		return rooms[r.blueprint().index()].warning(r, rx, ry);
	}
	
	public void open(RoomEquip w) {
		main.open(w);
	}
	
	final Coo rRoom = new Coo();
	
	private ACTION recon = new ACTION() {
		
		@Override
		public void exe() {
			VIEW.s().ui.placer.reconstruct(rRoom.x(), rRoom.y());
		}
	};
	
	public void click(Room room, int tx, int ty) {
		if (isInvalidForRoomUi(room))
			return;
		if (room.blueprint() instanceof ROOM_GATE) {
			((ROOM_GATE)room.blueprint()).lock(tx, ty, !((ROOM_GATE)room.blueprint()).locked(tx, ty));
		}else if (room.blueprint() == ROOMS().HOME) {
			VIEW.s().panels.add(VIEW.s().ui.home, true);
		}else if (room.blueprint() == ROOMS().THRONE) {
			VIEW.UI().level.activate();
		}else if (room instanceof RoomInstance)
			open((RoomInstance) room); 
		else if (SETT.ROOMS().placement.canReconstruct(tx, ty)) {
			rRoom.set(tx, ty);
			VIEW.inters().yesNo.activate(¤¤reconstructPrompt, recon, ACTION.NOP, true);
		}else if (rooms[room.blueprint().index()].table == null) {
			inter.show(room, tx, ty);
		}else if (room instanceof RoomInstance)
			open((RoomInstance)room);
			
	}
	
	public void open(RoomInstance r) {
		if (rooms[r.blueprint().index()].table == null) {
			return;
		}
		VIEW.s().panels.add(rooms[r.blueprint().index()].table.get(), true);
		VIEW.s().panels.add(rooms[r.blueprint().index()].detail(r), false);
	}
	
	public ISidePanel open(RoomBlueprint r) {
		if (rooms[r.index()].table == null)
			return null;
		return rooms[r.index()].table.get();
	}
	
	public boolean openIs(RoomBlueprint r) {
		if (rooms[r.index()].table == null)
			return false;
		return VIEW.s().panels.added(rooms[r.index()].table) && VIEW.s().panels.added(rooms[r.index()].detail);
	}
	
	public void prio(HCLASS c, Race r, CLICKABLE trigger) {
		if (c == HCLASSES.SLAVE()) {
			VIEW.inters().popup.show(pop2, trigger);
		}else if (c == HCLASSES.CITIZEN() && r != null) {
			main.work.set(r, HTYPES.SUBJECT());
			VIEW.s().panels.add(main.work, true);
		}else {
			VIEW.s().panels.add(main.work, true);
		}
		
	}
	
	private final Inter inter = new Inter();
	private final class Inter extends Interrupter {
		
		Room room;
		int tx;
		int ty;
		
		@Override
		protected boolean update(float ds) {
			return true;
		}
		
		@Override
		protected boolean render(Renderer r, float ds) {
			if (room.blueprint() instanceof ROOM_MONUMENT) {
				SETT.OVERLAY().monument((ROOM_MONUMENT) room.blueprint());
			}else if (room.blueprint() instanceof RoomFinderHaser) {
				SETT.OVERLAY().service((RoomFinderHaser) room.blueprint());
			}else {
				for (SettEnv e : SETT.ENV().map.all()) {
					if (room.constructor().envValue(e)) {
						SETT.OVERLAY().envThing(e).add();
						break;
					}
				}
			}
			SETT.OVERLAY().add(tx, ty);
			
			return true;
		}
		
		public void show(Room room, int tx, int ty){
			
			if (!has(room))
				return;
			this.room = room;
			this.tx = tx;
			this.ty = ty;
			
			show(VIEW.s().uiManager);
		}
		
		private boolean has(Room room) {
			if (room.blueprint() instanceof ROOM_MONUMENT) {
				return true;
			}
			if (room.blueprint() instanceof RoomFinderHaser) {
				return true;
			}else {
				for (SettEnv e : SETT.ENV().map.all()) {
					if (room.constructor().envValue(e))
						return true;
				}
			}
			return false;
		}
		
		@Override
		protected void mouseClick(MButt button) {
			if (button == MButt.RIGHT)
				hide();
		}
		
		@Override
		protected boolean otherClick(MButt button) {
			if (button != MButt.WHEEL_SPIN)
				hide();
			return false;
		}
		
		@Override
		protected void hide() {
			super.hide();
		}
		
		@Override
		protected void hoverTimer(GBox text) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		protected boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
			return false;
		}
	};

}
