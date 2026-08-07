package view.sett.ui.standing;

import init.race.RACES;
import init.race.Race;
import init.sprite.UI.UI;
import init.type.HCLASS;
import settlement.stats.STATS;
import settlement.stats.service.StatServiceImp;
import settlement.stats.service.StatsService;
import settlement.stats.stat.StatCollection;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.ArrayList;
import util.colors.GCOLOR;
import util.data.GETTER;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GMeter;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.table.GScrollRows;
import util.text.D;
import view.sett.ui.standing.Cats.Cat;

final class CatServices extends Cat {
	
	private static CharSequence ¤¤Other = "¤Other Services";
	
	static {
		D.ts(CatServices.class);
	}
	
	CatServices(HCLASS cl, GETTER<Race> race){
		super(new StatCollection[] {STATS.SERVICE()});
		StatsService s = STATS.SERVICE();
		
		titleSet(s.info.name);
		ArrayList<RENDEROBJ> rens = new ArrayList<>(s.all().size() + 1);
		
		
		{
			GuiSection sec = new GuiSection();
			sec.add(new GHeader(¤¤Other));
			for (StatServiceImp ss : s.ALL) {
				
				boolean has = false;
				for (Race r : RACES.all()) {
					if (ss.total().standing().definition(r).get(cl).max > 0) {
						has = true;
						break;
					}
				}
				has = true;
				if (!has)
					continue;
//				
				rens.add(new StatRowService(ss, cl, race));
			}
			//rens.add(hospital(cl));
			
			
		}
		
		section.add(new GScrollRows(rens, HEIGHT, 0).view());
		
	}
	
	private static final class StatRowService extends GuiSection{

		private final HCLASS cl;
		private final GETTER<Race> race;
		
		StatRowService(StatServiceImp g, HCLASS cl, GETTER<Race> race){
			this.race = race;
			this.cl = cl;
			
			add(service(g), 20, 0);
			pad(2,5);
		}
		
		@Override
		public void render(SPRITE_RENDERER r, float ds) {
			super.render(r, ds);
			GCOLOR.UI().border().render(r, body().x1(), body().x2(), body().y2()-1, body().y2());
		}
		
		@Override
		public void hoverInfoGet(GUI_BOX text) {
			if (isHoveringAHoverElement()) {
				super.hoverInfoGet(text);
				return;
			}
			super.hoverInfoGet(text);
		}
		
		private RENDEROBJ service(StatServiceImp ss) {
			GuiSection s = new GuiSection() {
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					if (!isHoveringAHoverElement()) {
						ss.total().hover(text, cl, race.get());
					}
					super.hoverInfoGet(text);
				}

				

			};
			s.add(new StatRow.Arrow(ss.total(), cl, race));
			
			s.addRightC(4, new GButt.Checkbox() {
				
				@Override
				protected void clickA() {
					ss.permission().toggle(cl.get(race.get()));
					super.clickA();
				}
				
				@Override
				protected void renAction() {
					selectedSet(ss.permission().is(cl.get(race.get())));
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					GBox b = (GBox) text;
					b.title(ss.permission().info().name);
				}
				
			});
			
			s.addRightC(4, ss.icon);
			s.addRightC(4, new GText(UI.FONT().S, ss.name).lablifySub());
			s.addCentredY(new GStat() {
				
				@Override
				public void update(GText text) {
					text.setFont(UI.FONT().S);
					StatRow.format(text, ss.total(), ss.total().data(cl).getD(race.get()), cl, race.get());
				}
			}, StatRow.StatX-20);
			
			
			s.addCentredY(new RENDEROBJ.RenderImp(StatRow.MeterW, 12) {
				
				@Override
				public void render(SPRITE_RENDERER r, float ds) {
					double max = ss.total().standing().max(cl, race.get());
					double now = ss.total().standing().get(cl, race.get());
					double nor = ss.total().standing().normalized(cl, race.get());
					GMeter.render(r, GMeter.C_REDGREEN, now/max, body.x1(), (int) (body().x1() + body().width()*nor), body().y1(), body().y2());
				}
			}, StatRow.MeterX-20);
			

			return s;
		}
		
	}

//	private RENDEROBJ hospital(HCLASS cl) {
//		
//		ROOM_HOSPITAL hh = SETT.ROOMS().HOSPITAL;
//		SERVICE_PROVIDER ss = hh.service();
//		
//		GuiSection s = new GuiSection() {
//			@Override
//			public void hoverInfoGet(GUI_BOX text) {
//				if (!isHoveringAHoverElement()) {
//					StatRow.hoverStat(text, ss.stats().total(), cl);
//					GBox b = (GBox) text;
//					
//					b.NL(8);
//					b.textLL(Dic.¤¤Total);
//					b.tab(6);
//					b.add(GFORMAT.i(b.text(), hh.service().total()));
//					b.NL();
//					b.textLL(Dic.¤¤Available);
//					b.tab(6);
//					b.add(GFORMAT.i(b.text(), hh.service().available()));
//					
//					b.NL(8);
//					
//					StatRow.hoverStanding(b, ss.stats().total(), cl);
//				}
//				super.hoverInfoGet(text);
//			}
//			
//			
//		};
//		s.add(new StatRow.Arrow(ss.stats().total(), cl));
//		
//		s.addRightC(4, new GButt.Checkbox() {
//			
//			@Override
//			protected void clickA() {
//				ss.stats().permission().toggle(cl.get(race.get()));
//				super.clickA();
//			}
//			
//			@Override
//			protected void renAction() {
//				selectedSet(ss.stats().permission().is(cl.get(race.get())));
//			}
//			
//			@Override
//			public void hoverInfoGet(GUI_BOX text) {
//				GBox b = (GBox) text;
//				b.title(ss.stats().permission().info().name);
//			}
//			
//		});
//		
//		s.addRightC(4, hh.iconBig());
//		s.addRightC(4, new GText(UI.FONT().S, hh.info.names).lablifySub());
//		s.addCentredY(new GStat() {
//			
//			@Override
//			public void update(GText text) {
//				text.setFont(UI.FONT().S);
//				StatRow.format(text, ss.stats().total(), ss.stats().total().data(cl).getD(race.get()), cl);
//			}
//		}, StatRow.StatX-20);
//		
//		
//		s.addCentredY(new RENDEROBJ.RenderImp(StatRow.MeterW, 16) {
//			
//			@Override
//			public void render(SPRITE_RENDERER r, float ds) {
//				double max = ss.stats().total().standing().max(cl, race.get());
//				double now = ss.stats().total().standing().get(cl, race.get());
//				double nor = ss.stats().total().standing().normalized(cl, race.get());
//				GMeter.render(r, GMeter.C_REDGREEN, now/max, body.x1(), (int) (body().x1() + body().width()*nor), body().y1(), body().y2());
//			}
//		}, StatRow.MeterX-20);
//		
//
//		return s;
//	}
	
}