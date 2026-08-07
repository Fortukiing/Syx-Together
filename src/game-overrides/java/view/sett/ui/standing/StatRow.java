package view.sett.ui.standing;

import init.race.Race;
import init.sprite.SPRITES;
import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import init.type.HCLASS;
import settlement.stats.stat.STAT;
import settlement.stats.stat.StatDecree;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.SPRITE;
import util.colors.GCOLOR;
import util.data.GETTER;
import util.data.INT.INTE;
import util.data.INT_O.INT_OE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GButt.Checkbox;
import util.gui.misc.GMeter;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.slider.GSliderInt;
import util.gui.slider.GTarget;
import util.info.GFORMAT;
import util.info.INFO;

public class StatRow extends GuiSection{

	private final STAT s;
	private final HCLASS cl;
	private final GETTER<Race> race;
	
	static final int StatX = 268;
	static final int MeterX = 328;
	static final int MeterW = 200;
	static final int Width = MeterX + MeterW + 4;
	
	StatRow(STAT s, HCLASS cl, GETTER<Race> race){
		this(s, null, cl, race);
	}
	
	StatRow(STAT s, RENDEROBJ ins, HCLASS cl, GETTER<Race> race){
		this.s = s;
		this.cl = cl;
		this.race = race;
		add(new Arrow(s, cl, race));
		
		if (ins != null)
			addRightC(2, ins);
		
		SPRITE icon = s.info().icon;
		if (icon != null) {
			addRightC(2, icon.resized(Icon.L));
		}
		GText t = new GText(UI.FONT().H2, s.info().name);
		t.setMultipleLines(false);
		t.setMaxWidth(210- (icon != null ? Icon.L+4 : 0));
		addRightC(2, t.lablify());
		add(new GStat() {
			
			@Override
			public void update(GText text) {
				format(text, s, value(cl, 0), cl, race.get());
			}
		}, StatX, 0);
		add(new Meter(s, cl, race), MeterX, 0);
		degree(cl, race);
		pad(2, 4);
	}
	
	static class Arrow extends RENDEROBJ.RenderImp{

		private final STAT s;
		private final HCLASS cl;
		private final GETTER<Race> race;
		
		Arrow(STAT s, HCLASS cl, GETTER<Race> race){
			super(Icon.S);
			this.s = s;
			this.cl = cl;
			this.race = race;
		}
		
		@Override
		public void render(SPRITE_RENDERER r, float ds) {
			int v = (int) (s.standing().getHistoric(cl, race.get(), 1)*256);
			int n = (int) (s.standing().get(cl, race.get())*256);
			if (n > v) {
				GCOLOR.UI().goodFlash().bind();
				SPRITES.icons().s.arrow_right.render(r, body);
			}else if(n < v) {
				GCOLOR.UI().badFlash().bind();
				SPRITES.icons().s.arrow_left.render(r, body);
			}
			COLOR.unbind();
		}
		
	}
	
	static class Meter extends RENDEROBJ.RenderImp{

		private final STAT s;
		private final HCLASS cl;
		private final GETTER<Race> race;
		
		Meter(STAT s, HCLASS cl, GETTER<Race> race){
			this.s = s;
			body().setDim(200, 16);
			this.cl = cl;
			this.race = race;
		}
		
		@Override
		public void render(SPRITE_RENDERER r, float ds) {
			double now = s.standing().get(cl, race.get());
			double max = s.standing().max(cl, race.get());
			double prev = s.standing().getPrev(cl, race.get(), 8);
			int w = (int) (200*Math.sqrt(s.standing().normalized(cl, race.get())));
			if (w > 0) {
				GMeter.renderDelta(r, prev/max, now/max, body().x1(), body().x1()+w, body().y1(), body().y2());
			}
		}
		
	}
	
	void degree(HCLASS cl, GETTER<Race> race) {
		StatDecree c = s.decree();
		
		if (c == null)
			return;
		INT_OE<Race> rr = c.getI(cl);
		if (rr.max(null) == 1) {
			Checkbox b = new GButt.Checkbox((SPRITE)(new GText(UI.FONT().S, c.name).lablifySub())) {
				@Override
				protected void renAction() {
					
					selectedSet(rr.get(race.get()) == 1);
				}
				
				@Override
				protected void clickA() {
					rr.set(race.get(), (rr.get(race.get())+1)&1);
				}
			};
			b.hoverSet(c);
			b.body().moveX1Y1(64, getLastY2()+8);
			add(b);
		}else if (rr.max(null) > 25){
			INTE d = new INTE() {
				
				@Override
				public int min() {
					return rr.min(race.get());
				}
				
				@Override
				public int max() {
					return rr.max(race.get());
				}
				
				@Override
				public int get() {
					return rr.get(race.get());
				}
				
				@Override
				public void set(int t) {
					rr.set(race.get(), t);
				}
			};
			
			GSliderInt in = new GSliderInt(d, 100, true, true) {
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					GBox b = (GBox) text;
					b.add(GFORMAT.f(b.text(), c.get(cl, race.get())));
				}
			};
//			m.hideInfo();
//			m.hoverTitleSet(c.name);
//			m.hoverInfoSet(c.desc);
			add(new GText(UI.FONT().S, c.name).lablifySub(), 64, getLastY2()+8);
			addRightC(8, in);
			
		}else {
			INTE d = new INTE() {
				
				@Override
				public int min() {
					return rr.min(race.get());
				}
				
				@Override
				public int max() {
					return rr.max(race.get());
				}
				
				@Override
				public int get() {
					return rr.get(race.get());
				}
				
				@Override
				public void set(int t) {
					rr.set(race.get(), t);
				}
			};
			
			GStat ss = new GStat() {
				
				@Override
				public void update(GText text) {
					GFORMAT.f(text, c.get(cl, race.get()), 1);
				}
			};
			
			GTarget m = new GTarget(64, false, true, ss, d);
			m.hoverTitleSet(c.name);
			m.hoverInfoSet(c.desc);
			add(new GText(UI.FONT().S, c.name).lablifySub(), 64, getLastY2()+8);
			addRightC(8, m);
		}
	}
	

	
	static GText format(GText t, STAT s, double v, HCLASS cl, Race race) {
		if (race == null) {
			if (s.info().isInt()) {
				return GFORMAT.f(t, v*s.dataDivider());
			}else {
				return GFORMAT.perc(t, v).normalify();
			}
		}else {
			
			double d = race.stats().def(s.standing()).get(cl).to - race.stats().def(s.standing()).get(cl).from;
			if (s.info().isInt()) {
				double m = s.dataDivider();
				double n = (double)v*s.dataDivider();
				
				if (d>0) {
					return GFORMAT.f0(t, n, m);
				}else if(d<0) {
					return GFORMAT.f0Inv(t, n, m);
				}else {
					return GFORMAT.f(t, n);
				}
			}else {
				if (d>0)
					return GFORMAT.perc(t, v);
				else if (d < 0) {
					return GFORMAT.percInv(t, v);
				}else {
					return GFORMAT.perc(t, v).normalify();
				}
			}
		}
	}
	
	@Override
	public void render(SPRITE_RENDERER r, float ds) {
		super.render(r, ds);
		GCOLOR.UI().border().render(r, body().x1(), body().x2(), body().y2()-1, body().y2());
	}
	
	protected double value(HCLASS c, int daysBack) {
		
		return s.data(c).getD(race.get(), daysBack);
	}
	
	@Override
	public void hoverInfoGet(GUI_BOX text) {
		if (isHoveringAHoverElement()) {
			super.hoverInfoGet(text);
			return;
		}
		
		s.hover(text, cl, race.get());
	}

	
	static class Title extends HoverableAbs{
		
		private final GText t;
		
		Title(INFO info){
			this(info.name, info.desc);
		}
		
		Title(CharSequence name, CharSequence desc){
			t = new GText(UI.FONT().H2, name).lablify();
			body().setWidth(500);
			body().setHeight(t.height()*2);
			hoverTitleSet(name);
			hoverInfoSet(desc);
		}

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
			
			t.renderC(r, body().cX(), body().cY()+t.height()/2 - 6);
			COLOR.WHITE30.render(r, body().x1(), body().x2(), body().y2()-1, body().y2());
		}
		
	}
	
}
