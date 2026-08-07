package view.sett.ui.standing;

import init.race.RACES;
import init.race.Race;
import init.resources.RBIT.RBITImp;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.RES_AMOUNT;
import init.sprite.UI.Icon;
import init.type.HCLASS;
import settlement.stats.STATS;
import settlement.stats.colls.StatsFood;
import settlement.stats.colls.StatsHome;
import settlement.stats.equip.EquipCivic;
import settlement.stats.equip.StatsEquip;
import settlement.stats.stat.STAT;
import settlement.stats.stat.StatCollection;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LinkedList;
import snake2d.util.sprite.SPRITE;
import util.colors.GCOLOR;
import util.data.DOUBLE;
import util.data.GETTER;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt.Checkbox;
import util.gui.misc.GMeter;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.slider.GAllocator;
import util.gui.slider.GTarget;
import util.gui.table.GScrollRows;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;
import view.sett.ui.standing.Cats.Cat;

class CatAccess extends Cat {
	
	private static CharSequence ¤¤PreferedBy = "¤Preferred By:";
	private static CharSequence ¤¤Allowed = "¤Allowed to consume";
	private static CharSequence ¤¤AllowedNot = "¤Not allowed to consume";
	private static CharSequence ¤¤Yearly = "¤{0} per item per year. Estimation: -{1} with current furnishing, and -{2} if the target is fulfilled.";
	private static CharSequence ¤¤FurnitureD = "The amount allowed to furnish a subject's home. More allowed and available will increase happiness from furnishing.";
	private static CharSequence ¤¤desc = "¤Stats and settings related to consumable resources";
	static {
		D.ts(CatAccess.class);
	}
	
	CatAccess(HCLASS c, GETTER<Race> race){
		super(new StatCollection[] {
			STATS.FOOD(), STATS.EQUIP(), STATS.HOME()
		});
		titleSet(Dic.¤¤Access);
		name = Dic.¤¤Access;
		desc = ¤¤desc;
		LinkedList<RENDEROBJ> rens = new LinkedList<>();
		
		{
			StatsFood s = STATS.FOOD();
			
			rens.add(new StatRow.Title(s.info));
			
			for (STAT st : s.all()) {
				rens.add(new StatRow(st, c, race));
			}
			
			
			
			GuiSection ss = new GuiSection();

			
			int ww = 7;
			
			LIST<RESOURCE> ll = RESOURCES.EDI().res().join(RESOURCES.DRINKS().res());
			RBITImp bbb = new RBITImp();
			for (int i = 0; i < ll.size(); i++) {
				RESOURCE e = ll.get(i);
				if (bbb.has(e))
					continue;
				bbb.or(e);
				final int k = i;
				Checkbox cl = new Checkbox(e.icon()) {
					
					@Override
					protected void clickA() {
						s.allowed(k).toggle(c, race.get());
					}
					
					@Override
					protected void renAction() {
						selectedSet(s.allowed(k).get(c, race.get()));
					}
					
					@Override
					protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
						
						if (race.get() != null && race.get().pref().foodMask.has(e)) {
							COLOR.WHITE100.render(r, body, 1);
							COLOR.WHITE15.render(r, body, 0);
						}
						super.render(r, ds, isActive, isSelected, isHovered);
						
						
					};
					
					@Override
					public void hoverInfoGet(GUI_BOX text) {
						GBox b = (GBox) text;
						text.title(e.name);
						if (selectedIs())
							b.text(¤¤Allowed);
						else
							b.error(¤¤AllowedNot);
						b.NL(4);
						b.textLL(¤¤PreferedBy);
						b.NL();
						if (RESOURCES.EDI().is(e)) {
							for (Race r : RACES.all()) {
								if (r.pref().food.contains(RESOURCES.EDI().get(e))){
									b.add(r.appearance().iconBig);
								}
							}
						}
						if (RESOURCES.DRINKS().is(e)) {
							for (Race r : RACES.all()) {
								if (r.pref().drink.contains(RESOURCES.DRINKS().get(e))){
									b.add(r.appearance().iconBig);
								}
							}
						}
							
					};
					
					
				};
				cl.hoverTitleSet(e.name);
				cl.pad(8, 2);
				
				ss.add(cl, cl.body().width()*(i%ww), (i/ww)*cl.body().height());
			}
			
			rens.add(ss);
		}
		
		{
			StatsEquip s = STATS.EQUIP();
			rens.add(new StatRow.Title(s.info));
			
			for (EquipCivic ss : s.civics()) {
				rens.add(new StatRowEquip(ss, c, race));
			}
		}
		
		{

			LIST<RES_AMOUNT> rr = RACES.res().homeResMax(c);
			
			
			StatsHome s = STATS.HOME();
			rens.add(new StatRow.Title(s.info));
			
			for (STAT st : s.all()) {
				if (st.key() != null)
					rens.add(new StatRow(st, c, race));
					
			}
			
			GuiSection ss = null;
			for (int ri = 0; ri < rr.size(); ri++) {
				if (ri % 3 == 0) {
					ss = new GuiSection();
					rens.add(ss);
				}
				ss.add(new StatHomeFurniture(ri, c, race, rr), (ri%3)*170, 0);

				
			}
			
		}
		
		
		
		
		
		
		
		section.add(new GScrollRows(rens, HEIGHT, 0).view());
		
	}
	
	private static class StatHomeFurniture extends GuiSection {
		
		private final int ri;
		private final HCLASS cl;
		private final LIST<RES_AMOUNT> rr;
		private final GETTER<Race> race;
		
		public StatHomeFurniture(int resource, HCLASS cl,  GETTER<Race> race, LIST<RES_AMOUNT> rr) {
			ri = resource;
			this.race = race;
			this.cl = cl;
			this.rr = rr;
			final INTE tar = new INTE() {
				
				@Override
				public int min() {
					return 0;
				}
				
				@Override
				public int max() {
					if (res() == null)
						return 1;
					return res().amount();
				}
				
				@Override
				public int get() {
					return STATS.HOME().target(cl, race.get(), res().resource());
				}
				
				@Override
				public void set(int t) {
					STATS.HOME().targetSet(t, cl, race.get(), res().resource());
				}
			};
			
			GAllocator al = new GAllocator(COLOR.ORANGE100, tar, 6, 16, 16);
			
			add(new SPRITE.Imp(Icon.M) {
				
				@Override
				public void render(SPRITE_RENDERER r, int X1, int X2, int Y1, int Y2) {
					res().resource().icon().render(r, X1, Y1);
					
				}
			}, 0, 0);
			
			DOUBLE d = new DOUBLE() {
				
				@Override
				public double getD() {
					double tot = STATS.HOME().needed(cl, race.get(), ri);
					if (tot == 0)
						return 0;
					double am = STATS.HOME().current(cl, race.get(), ri);
					return am/tot;
				}
			};
			
			add(new GMeter.GMeterSprite(GMeter.C_REDGREEN, d, 130, 16), body().x2()+4, 0);
			
			add(al, getLastX1(), getLastY2()+2);
			
			pad(8, 8);
			
		}
		
		private RES_AMOUNT res(){
			return res(race.get());
		}
		
		private RES_AMOUNT res(Race race){
			if (race == null) {
				if (ri >= rr.size())
					return null;
				return rr.get(ri);
			}
			
			if (race.home().clas(cl).resources().size() <= ri)
				return null;
			
			return race.home().clas(cl).resources().get(ri);
		}
		
		@Override
		public void render(SPRITE_RENDERER r, float ds) {
			activeSet(res() != null);
			if (activeIs())
				super.render(r, ds);
		}
		
		@Override
		public void hoverInfoGet(GUI_BOX text) {
			RES_AMOUNT res = res();
			if (res == null)
				return;
			GBox b = (GBox) text;
			b.title(res().resource().name);
			
			b.text(¤¤FurnitureD);
			b.NL(8);
			
			
			int tot = STATS.HOME().needed(cl, race.get(), ri);
			int am = STATS.HOME().current(cl, race.get(), ri);
			b.add(GFORMAT.iofkInv(b.text(), am, tot));
			b.NL(8);
			
			b.textL(Dic.¤¤ConsumptionRate);
			b.NL();
			GText t = b.text();
			t.add(¤¤Yearly);
			t.insert(0, STATS.HOME().rate(cl, race.get()), 2);
			t.insert(1, (int)(STATS.HOME().rate(cl, race.get())*STATS.HOME().current(cl, race.get(), ri)));
			t.insert(2, (int)(STATS.HOME().rate(cl, race.get())*STATS.POP().POP.data(cl).get(race.get()))*STATS.HOME().target(cl, race.get(), res.resource()) );
			b.add(t);
		
			if (race.get() == null) {
				b.NL(8);
				b.textL(Dic.¤¤Used);
				for (Race r : RACES.all()) {
					
					
					
					if (r.home().clas(cl).amount(res.resource()) > 0)
						b.add(r.appearance().icon);
				}
				b.NL();
			}
			
			super.hoverInfoGet(text);
		}
		
	}
	
	static class StatRowEquip extends GuiSection{

		private final EquipCivic ss;
		private final HCLASS cl;
		private final GETTER<Race> race;
		
		public StatRowEquip(EquipCivic ss, HCLASS cl, GETTER<Race> race) {
			this.ss = ss;
			this.cl = cl;
			this.race = race;
			add(new StatRow.Arrow(ss.stat(), cl, race));
			addRightC(4, ss.resource.icon());
			
			EquipCivic s = ss;
			INTE in = new INTE() {
				
				@Override
				public int min() {
					return 0;
				}
				
				@Override
				public int max() {
					return s.max();
				}
				
				@Override
				public int get() {
					return s.target(cl, race.get());
				}
				
				@Override
				public void set(int t) {
					s.targetSet(t, cl, race.get());
				}
			};
			
			addRightC(16, new GTarget(40, false, true, in).hoverInfoSet(ss.sTarget));
			
			add(new GStat() {
				
				@Override
				public void update(GText text) {
					StatRow.format(text, ss.stat(), ss.stat().data(cl).getD(race.get()), cl, race.get());
				}
			}, 230, 0);
			

			
			add(new StatRow.Meter(ss.stat(), cl, race), 320, 0);
			pad(2, 4);
		}
		
		@Override
		public void hoverInfoGet(GUI_BOX text) {
			if (!isHoveringAHoverElement()) {
				//StatRow.hoverStat(text, ss.stat(), cl);
				ss.hover(text, cl, race.get());
				text.NL();
				ss.stat().hover(text, cl, race.get());
			}else {
				super.hoverInfoGet(text);
			}
		}
		
		@Override
		public void render(SPRITE_RENDERER r, float ds) {
			super.render(r, ds);
			GCOLOR.UI().border().render(r, body().x1(), body().x2(), body().y2()-1, body().y2());
		}
		
	}

}