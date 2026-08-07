package view.sett.ui.standing;

import java.util.Arrays;

import coopmod.CoopRuntime;
import init.race.RACES;
import init.race.Race;
import init.sprite.UI.UI;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import settlement.main.SETT;
import settlement.stats.STATS;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.Hoverable.HOVERABLE;
import util.data.GETTER;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GInputInt;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.info.GFORMAT;
import util.text.D;

public abstract class CatPopulationGrowth extends GuiSection{

	private static CharSequence ¤¤immi = "¤Aspiring Immigrants";
	private static CharSequence ¤¤Athorize = "¤Authorize";
	private static CharSequence ¤¤Auto = "¤Auto";
	private static CharSequence ¤¤AutoDesc = "¤Automatically authorize new immigrants up until this amount.";
	private static CharSequence ¤¤Children = "Children";
	private static CharSequence ¤¤Limit = "¤Limit";
	private static CharSequence ¤¤LimitD = "¤Sets the max population desired from breeding. When breeding stops based on this limit, your population might be unhappy about it.";
	
	private static CharSequence ¤¤ForcedBreeding = "¤Forces your subjects to breed harder and faster.";
	
	static {
		D.ts(CatPopulationGrowth.class);
	}
	
	
	private final HCLASS cl;
	private final GETTER<Race> race;
	
	public CatPopulationGrowth(HCLASS cl, GETTER<Race> race){
		this.cl = cl;
		this.race = race;
		
		GuiSection b = breed();
		int w = b.body().width();
		if (cl == HCLASSES.CITIZEN()) {
			GuiSection a = immi();
			w = Math.max(w, a.body().width());
			a.body().setWidth(w);
			add(a);
		}
		b.body().setWidth(w);
		addDown(2, b);
		
		
	}
	
	@Override
	public void render(SPRITE_RENDERER r, float ds) {

		visableSet(race.get() != null);
		if (race.get() != null)
			super.render(r, ds);
	}
	
	private GuiSection immi() {
		GuiSection s = new GuiSection() {

			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GButt.ButtPanel.renderBG(r, cl == HCLASSES.CITIZEN(), false, hoveredIs(), body());
				super.render(r, ds);
				GButt.ButtPanel.renderFrame(r, body());
			}
			
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				super.hoverInfoGet(text);
				if (text.emptyIs())	{
					SETT.ENTRY().immi().hoverImmigrants(text, HCLASS_RACE.clP(race.get(), cl));
				}
			}
		};
		
		s.add(new GStat() {
			
			@Override
			public void update(GText text) {
	
				if (cl == HCLASSES.CITIZEN())
					GFORMAT.i(text, SETT.ENTRY().immi().wanted(race.get()));
				if (race.get() != null) {
					text.s().add('/').add(SETT.ENTRY().immi().auto(race.get()).get());
				}
				else
					text.add(0);
			}
			
		}.hh(¤¤immi));
		
		INTE m = new INTE() {
			
			double[] d = new double[RACES.all().size()];
			{
				Arrays.fill(d, 1.0);
			}
			@Override
			public int min() {
				return 0;
			}
			
			@Override
			public int max() {
				if (cl == HCLASSES.CITIZEN())
					return SETT.ENTRY().immi().wanted(race.get());
				return 0;
			}
			
			@Override
			public int get() {
				if (race.get() != null)
					return (int) (max()*d[race.get().index]);
				return 0;
			}
			
			@Override
			public void set(int t) {
				d[race.get().index] = (double) t / max();
			}
		};
		s.addDown(4, new GInputInt(m, true, true));
		
		s.addRightC(8, new GButt.ButtPanel(¤¤Athorize) {
			
			@Override
			protected void clickA() {
				if (m.get() > 0)
					SETT.ENTRY().immi().admit(race.get(), m.get());
			}
			
			@Override
			protected void renAction() {
				activeSet(m.get() > 0);
				super.renAction();
			}
		});
		
		s.add(new HOVERABLE.Sprite(new GText(UI.FONT().S, ¤¤Auto)).hoverInfoSet(¤¤AutoDesc),0, s.body().y2()+10);
		
		INTE a = new INTE() {
			
			
			
			@Override
			public int min() {
				return 0;
			}
			
			@Override
			public int max() {
				if (cl == HCLASSES.CITIZEN() && race.get() != null)
					return SETT.ENTRY().immi().auto(race.get()).max();
				return 0;
			}
			
			@Override
			public int get() {
				if (cl == HCLASSES.CITIZEN() && race.get() != null)
					return SETT.ENTRY().immi().auto(race.get()).get();
				return 0;
			}
			
			@Override
			public void set(int t) {
				if (cl == HCLASSES.CITIZEN()) {
					SETT.ENTRY().immi().auto(race.get()).set(t);
					CoopRuntime.immigrationAutoChanged(race.get(), t);
				}
			}
		};
		s.addRightCAbs(80, new GInputInt(a, true, true));
		
		s.addRelBody(16, DIR.W, UI.icons().s.arrow_right.scaled(2.0));
		
		s.pad(16, 8);
		
		return s;
	}
	
	private GuiSection breed() {
		GuiSection s = new GuiSection() {

			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GButt.ButtPanel.renderBG(r, STATS.POP().reproduction.propagates(cl, race.get()), false, hoveredIs(), body());
				super.render(r, ds);
				GButt.ButtPanel.renderFrame(r, body());
			}
			
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				super.hoverInfoGet(text);
				GBox b = (GBox) text;
				if (text.emptyIs())	{
					STATS.POP().reproduction.hover(b, cl, race.get());
				}
			}
		};
		
		s.add(new GStat() {
			
			@Override
			public void update(GText text) {
	
				GFORMAT.i(text, STATS.POP().reproduction.kidsIncoming(cl, race.get()));

				text.s().add('+').add(STATS.POP().reproduction.kidsPerYear(cl, race.get()), 2);
			}
			
			
			
		}.hh(¤¤Children));
		
		s.add(new HOVERABLE.Sprite(new GText(UI.FONT().S, ¤¤Limit).lablify()).hoverInfoSet(¤¤LimitD),0, s.body().y2()+10);
		
		INTE a = new INTE() {
			
			
			
			@Override
			public int min() {
				return 0;
			}
			
			@Override
			public int max() {
				return STATS.POP().reproduction.propagates(cl, race.get()) ? STATS.POP().reproduction.limit.max(HCLASS_RACE.clP(race.get(), cl)) : 0;
			}
			
			@Override
			public int get() {
				return STATS.POP().reproduction.propagates(cl, race.get()) ? STATS.POP().reproduction.limit.get(HCLASS_RACE.clP(race.get(), cl)) : 0;
			}
			
			@Override
			public void set(int t) {
				STATS.POP().reproduction.limit.set(HCLASS_RACE.clP(race.get(), cl), t);
				CoopRuntime.reproductionLimitChanged(cl, race.get(), t);
			}
		};
		s.addRightCAbs(80, new GInputInt(a, true, true));
		
		s.addRightC(8, new GButt.ButtPanel(UI.icons().m.plus) {
			
			@Override
			protected void clickA() {
				int index = HCLASS_RACE.clP(race.get(), cl).index;
				int value = STATS.POP().reproduction.settings.get(index) == 50 ? 100 : 50;
				STATS.POP().reproduction.settings.set(index, value);
				CoopRuntime.reproductionForcedChanged(cl, race.get(), value);
				super.clickA();
			}
			
			@Override
			protected void renAction() {
				selectedSet(STATS.POP().reproduction.settings.get(HCLASS_RACE.clP(race.get(), cl).index) != 50);
				super.renAction();
			}
			
		}.hoverInfoSet(¤¤ForcedBreeding));
		
		s.addRelBody(16, DIR.W, UI.icons().s.reproduction.scaled(2.0));
		
		s.pad(16);
		
		return s;
	}
	
	public abstract HCLASS_RACE pop();
	
}
