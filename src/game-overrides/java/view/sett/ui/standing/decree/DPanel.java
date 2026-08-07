package view.sett.ui.standing.decree;

import coopmod.CoopRuntime;
import init.race.Race;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import settlement.stats.POP;
import settlement.stats.STATS;
import settlement.stats.muls.StatsMultipliers.StatMultiplier;
import settlement.stats.muls.StatsMultipliers.StatMultiplierAction;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.LinkedList;
import util.data.GETTER;
import util.data.INT;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.slider.GSliderInt;
import util.gui.table.GScrollRows;
import util.info.GFORMAT;
import util.text.D;
import view.interrupter.ISidePanel;
import view.main.VIEW;

final class DPanel extends ISidePanel{

	private static CharSequence ¤¤Cancel = "Click to cancel action for {0} subjects.";
	private static CharSequence ¤¤Set = "Set action for:";
	private static CharSequence ¤¤Projected = "Projected fulfillment increase";
	private static CharSequence ¤¤AutoPer = "Automatically execute this decree for {0} % of your population.";
	private static CharSequence ¤¤AutoAm = "Automatically execute this decree when population is above {0} people.";
	
	static {
		D.ts(DPanel.class);
	}
	
	DPanel(HCLASS cl, GETTER<Race> race){
		titleSet(UIDecreeButt.¤¤title);
		
		section = new GuiSection() {
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				if (cl == HCLASSES.CITIZEN() && race.get() == null) {
					VIEW.s().panels.remove(DPanel.this);
					return;
				}
				super.render(r, ds);
			}
		};
		
		LinkedList<RENDEROBJ> rows = new LinkedList<>();
		
		for (StatMultiplier m : STATS.MULTIPLIERS().get(cl)) {
			if (m.available(cl) && m instanceof StatMultiplierAction) {
				StatMultiplierAction dec = (StatMultiplierAction) m;
				other(rows, dec, cl, race);
					
			}
			
		}
		
		
		section.add(new GScrollRows(rows, HEIGHT-16).view());
		
	}
	
	private void other(LinkedList<RENDEROBJ> rows, StatMultiplierAction dec, HCLASS cl, GETTER<Race> race) {

		GuiSection s = new GuiSection();
		
		s.add(new Header(dec, cl, race));
		II ii = new II(cl, race, dec);
		GSliderInt sl = slider(dec, cl, race, ii);
		s.addDown(2, sl);
		
		s.addRelBody(16, DIR.E, marker(dec, cl, race, ii).pad(4, 4));
		if (dec.canUnmark()) {
			s.addRelBody(2, DIR.E, unmarker(dec, cl, race).pad(4, 4));
		}
		
		s.add(UI.icons().s.cog, 0, s.body().y2()+2);
		INTE ee = new INTE() {
			
			@Override
			public int min() {
				return dec.auto(cl, race.get()).min;
			}
			
			@Override
			public int max() {
				return dec.auto(cl, race.get()).max;
			}
			
			@Override
			public int get() {
				return dec.auto(cl, race.get()).get();
			}
			
			@Override
			public void set(int t) {
				dec.auto(cl, race.get()).set(t);
				CoopRuntime.statMultiplierAutoChanged(dec, cl, race.get(), t);
			}
		};
		s.addRightC(8, new GSliderInt(ee, 100, true) {
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				GText t = b.text();
				if (dec == STATS.MULTIPLIERS().EMANCIPATE || dec == STATS.MULTIPLIERS().PROSECUTION) {
					t.add(¤¤AutoAm).insert(0, ee.get());
				}else
					t.add(¤¤AutoPer).insert(0, ee.get());
				b.add(t);
				super.hoverInfoGet(text);
			}
		});
		
		s.pad(8, 10);
		
		rows.add(s);
	}
	
	private GButt.ButtPanel unmarker(StatMultiplierAction dec, HCLASS cl, GETTER<Race> rr) {
		return new GButt.ButtPanel(SPRITES.icons().m.cancel) {
			@Override
			protected void clickA() {
				dec.unmark(cl, rr.get());
				CoopRuntime.statMultiplierUnmarkChanged(dec, cl, rr.get());
			}
			
			@Override
			protected void renAction() {
				activeSet(dec.unmarkable(cl, rr.get()) > 0);
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				GText t = b.text();
				t.add(¤¤Cancel);
				t.insert(0, dec.unmarkable(cl, rr.get()));
				b.add(t);
			}
			
		};
	}
	
	private GSliderInt slider(StatMultiplierAction dec, HCLASS cl, GETTER<Race> rr, INTE ii) {
		return new GSliderInt(ii, 280, true) {
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				hov(text, cl, rr.get(), dec, ii.get());
			}
		};
	}
	
	private GButt.ButtPanel marker(StatMultiplierAction dec, HCLASS cl, GETTER<Race> rr, INT ii) {
		return new GButt.ButtPanel(SPRITES.icons().m.ok) {
			@Override
			protected void clickA() {
				dec.mark(cl, rr.get(), ii.get());
				CoopRuntime.statMultiplierMarkChanged(dec, cl, rr.get(), ii.get());
			}
			
			@Override
			protected void renAction() {
				activeSet(ii.get() != 0);
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				hov(text, cl, rr.get(), dec, ii.get());
				
			}
			
		};
	}

	static void hov(GUI_BOX text, HCLASS cl, Race race, StatMultiplierAction dec, int am) {
		
		GBox b = (GBox) text;
		b.title(dec.name);
		
		b.textLL(¤¤Set);
		b.NL();
		b.add(GFORMAT.i(b.text(), am));
		b.text(race.info.names);
		
		b.NL(8);
		
		GText t = b.text();
		t.add(¤¤Projected);
		t.lablify();
		b.add(t);
		b.NL(2);
		
		
		
		double d = (double)am/POP.pop(cl, race);
		dec.boosters.hover(text, d, null, -1);
		
//		for (BoostSpec s : dec.boosters.all()) {
//			double to = s.booster.to();
//			double cur = s.booster.get(s.boostable, RACES.clP(race, cl));
//			to -= cur;
//			if (s.booster.isMul)
//				to += 1;
//			
//			
//			s.booster.hover(b, to);
//			b.NL();
//			
//		}
//		
		b.NL(8);
		
		dec.info(b, am);
		
	}

	
	private static class Header extends GuiSection{

		private final HCLASS cl;
		private final GETTER<Race> race;
		private final StatMultiplierAction dec;
		
		Header(StatMultiplierAction dec, HCLASS cl, GETTER<Race> race){
			this.cl = cl;
			this.race = race;
			this.dec = dec;
			add(dec.icon, 0, 0);
			
			
			addCentredY(new GHeader(dec.verb), 48);
			
			addCentredY(new GStat() {

				@Override
				public void update(GText text) {
					GFORMAT.f0(text, dec.value(cl, race.get(), 0));
				}
				
			}, 260);
			
			
			
		}
		
		@Override
		public void hoverInfoGet(GUI_BOX text) {
			GBox b = (GBox) text;
			b.title(dec.name);
			b.text(dec.desc);
			b.NL(8);
			
			dec.boosters.hover(text, HCLASS_RACE.clP(race.get(), cl));
			
		}
		
	}
	
	private final static class II implements INTE {
		
		int i = 0;
		private final HCLASS cl;
		private final GETTER<Race> race;
		private final StatMultiplierAction dec;
		
		II(HCLASS cl, GETTER<Race> race, StatMultiplierAction dec){
			this.cl = cl;
			this.race = race;
			this.dec = dec;
		}
		
		@Override
		public int min() {
			return 0;
		}
		
		@Override
		public int max() {
			return dec.maxAmount(cl, race.get());
		}
		
		@Override
		public int get() {
			return CLAMP.i(i, 0, max());
		}
		
		@Override
		public void set(int t) {
			i = t;
		}
	}

	
}
