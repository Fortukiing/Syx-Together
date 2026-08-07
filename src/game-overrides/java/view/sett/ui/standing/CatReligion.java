package view.sett.ui.standing;

import coopmod.CoopRuntime;
import game.boosting.BoostSpec;
import game.boosting.BoostableCat;
import game.boosting.BoosterAbs;
import game.time.TIME;
import init.race.RACES;
import init.race.Race;
import init.religion.RELIGIONS;
import init.religion.Religion;
import init.sprite.UI.UI;
import init.type.HCLASS;
import init.type.HCLASS_RACE;
import init.type.NEEDS;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBurial;
import settlement.stats.colls.StatsBurial.StatGrave;
import settlement.stats.colls.StatsReligion.ReligionTot;
import settlement.stats.colls.StatsReligion.StatReligion;
import settlement.stats.stat.STAT;
import settlement.stats.stat.StatCollection;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.Hoverable.HOVERABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LinkedList;
import util.colors.GCOLOR;
import util.data.GETTER;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GMeter;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.table.GRows;
import util.gui.table.GScrollRows;
import util.gui.table.GStaples;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;
import view.sett.ui.standing.Cats.Cat;

final class CatReligion extends Cat{

	private static CharSequence ¤¤Burrial = "¤Burial";
	private static CharSequence ¤¤AllowRace = "¤Allow/deny access for Species";
	private static CharSequence ¤¤Allow = "¤Allow/deny access for whole class";
	private static CharSequence ¤¤TempleBoost = "¤Temple Boosts";
	private static CharSequence ¤¤Conversion = "¤Conversion";
	static {
		D.ts(CatReligion.class);
	}
	
	
	CatReligion(HCLASS cl, GETTER<Race> race) {
		super(new StatCollection[] { STATS.RELIGION(), STATS.BURIAL()});
		titleSet(cs[0].info.name);
		
		section.add(dvision(cl, race));
		
		LinkedList<RENDEROBJ> rens = new LinkedList<>();
		

		
		for (StatReligion r : STATS.RELIGION().ALL){
			rens.add(temple(r, cl, race));
		}
		
		
		

		rens.add(access(STATS.RELIGION().SHRINE, cl, race));
		rens.add(access(STATS.RELIGION().TEMPLE, cl, race));
		
		{
			rens.add(new GHeader(¤¤TempleBoost));
			GRows rows = new GRows(6);
			GText t = new GText(UI.FONT().S, 8);
			for (BoostSpec ss : STATS.RELIGION().TEMPLE.TOTAL.boosters.all()) {
				rows.add(new HOVERABLE.HoverableAbs(85, 32) {
					
					@Override
					protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
						GButt.ButtPanel.renderBG(r, true, false, isHovered, body);
						ss.boostable.icon.renderCY(r, body().x1()+8, body().cY());
						t.clear();
						GFORMAT.f0(t, ss.inc(HCLASS_RACE.clP(race.get(), cl)));
						t.renderCY(r, body().x1()+28, body().cY());
						GButt.ButtPanel.renderFrame(r, body);
					}
					@Override
					public void hoverInfoGet(GUI_BOX box) {
						GBox bb = (GBox) box;
						
						
						bb.title(ss.tName);
						double d = 0;
						for (Religion rr : RELIGIONS.ALL()) {
							for (BoostSpec sb : rr.boosts.all()) {
								if (sb.boostable == ss.boostable) {
									sb.booster.hover(box, sb.booster.get(HCLASS_RACE.clP(race.get(), cl)));
									BoosterAbs.hoverSpan(bb, sb.booster.from(), sb.booster.to());
									bb.NL();
								}
							};
						}
						bb.NL(8);
						
						bb.textLL(Dic.¤¤Boosts);
						bb.tab(7);
						bb.add(GFORMAT.f0(bb.text(), d));
						
					};
				});
				
				
			}
			rens.add(rows.rows());
		}
		
		{
			STAT s = STATS.RELIGION().OPPOSITION;
			rens.add(new StatRow(s, cl, race));
		}

		{
			StatsBurial s = STATS.BURIAL();
			new StatRowGrave(cl, race, rens);
			for (STAT ss : s.others())
				rens.add(new StatRow(ss, cl, race));
			
			
		}
		section.add(new GScrollRows(rens, HEIGHT-section.body().height()-8, 0).view(), 0, section.body().y2()+4);

	}
	
	private static RENDEROBJ access(ReligionTot tot, HCLASS cl, GETTER<Race> race) {
		return new StatRow(tot.TOTAL, cl, race);
	}
	
	private static RENDEROBJ dvision(HCLASS cl, GETTER<Race> race) {
		GStaples s = new GStaples(STATS.DAYS_SAVED) {
			
			@Override
			protected void hover(GBox box, int stapleI) {
				box.title(STATS.RELIGION().ALL.get(0).followers.info().name);
				int i = STATS.DAYS_SAVED - stapleI - 1;
				box.add(box.text().add(-i).s().add(TIME.days().cycleName()));
				box.NL(8);
				
				for (StatReligion s : STATS.RELIGION().ALL) {
					box.add(s.religion.icon);
					box.textLL(s.religion.info.name);
					box.tab(6);
					box.add(GFORMAT.i(box.text(), s.followers.data(cl).get(race.get(), i)));
					box.tab(8);
					if (i < STATS.DAYS_SAVED-1)
						box.add(GFORMAT.iIncr(box.text(), s.followers.data(cl).get(race.get(), i)-s.followers.data(cl).get(race.get(), i+1)));
					box.NL();
				}
				
				box.add(NEEDS.TYPES().SHRINE.rate.icon);
				box.textLL(STATS.RELIGION(). SHRINE.TOTAL.info().name);
				box.tab(6);
				box.add(GFORMAT.perc(box.text(), STATS.RELIGION().SHRINE.TOTAL.data(cl).getD(race.get()), i));
				if (i < STATS.DAYS_SAVED-1)
					box.add(GFORMAT.percInc(box.text(), STATS.RELIGION().SHRINE.TOTAL.data(cl).getD(race.get(), i)-STATS.RELIGION().SHRINE.TOTAL.data(cl).getD(race.get(), i+1)));
				box.NL();
				
				box.add(NEEDS.TYPES().TEMPLE.rate.icon);
				box.textLL(STATS.RELIGION().TEMPLE.TOTAL.info().name);
				box.tab(6);
				box.add(GFORMAT.perc(box.text(), STATS.RELIGION().TEMPLE.TOTAL.data(cl).getD(race.get()), i));
				if (i < STATS.DAYS_SAVED-1)
					box.add(GFORMAT.percInc(box.text(), STATS.RELIGION().TEMPLE.TOTAL.data(cl).getD(race.get(), i)-STATS.RELIGION().TEMPLE.TOTAL.data(cl).getD(race.get(), i+1)));
				box.NL();
			}
			
			@Override
			protected double getValue(int stapleI) {
				int i = STATS.DAYS_SAVED - stapleI - 1;
				return STATS.POP().POP.data(cl).get(race.get(), i);
			}
			
			@Override
			protected void renderExtra(SPRITE_RENDERER r, COLOR color, int stapleI, boolean hovered, double value,
					int x1, int x2, int y1, int y2) {
				
				
				int i = STATS.DAYS_SAVED-stapleI-1;
				
				int h = y2-y1;
				if (h <= 0)
					h = 1;
				
				for (StatReligion s : STATS.RELIGION().ALL) {
					int hh = (int) Math.ceil(h*s.followers.data(cl).getD(race.get(), i));
					if (hh > 0) {
						ColorImp c = ColorImp.TMP;
						c.set(s.religion.color);
						c.shadeSelf(hovered ? 0.75 : 0.55);
						c.render(r, x1, x2, y2-hh, y2);
						c.set(s.religion.color);
						c.shadeSelf(hovered ? 1 : 0.80);
						c.render(r, x1+1, x2-1, y2-hh+1, y2-1);
						y2-= hh;
					}
				}
			}
		};
		
		s.body().setWidth(StatRow.Width);
		s.body().setHeight(80);
		return s;
	}
	
	private static RENDEROBJ temple(StatReligion ss, HCLASS cl, GETTER<Race> race) {
		GuiSection s = new GuiSection() {
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				if (!isHoveringAHoverElement()) {
					
					GBox b = (GBox) text;
					b.title(ss.info.name);
					b.text(ss.info.desc);
					b.NL(8);
					
					b.textL(ss.followers.info().name);
					b.tab(8);
					b.add(GFORMAT.i(b.text(), ss.followers.data(cl).get(race.get())));
					b.NL();
					
					b.textLL(STATS.RELIGION().TEMPLE.name);
					b.tab(6);
					b.add(GFORMAT.perc(b.text(), STATS.RELIGION().TEMPLE.access(ss.religion).data(cl).getD(race.get())));
					b.add(GFORMAT.perc(b.text(), STATS.RELIGION().TEMPLE.quality(ss.religion).data(cl).getD(race.get())));
					b.NL();
					
					b.textLL(STATS.RELIGION().SHRINE.name);
					b.tab(6);
					b.add(GFORMAT.perc(b.text(), STATS.RELIGION().SHRINE.access(ss.religion).data(cl).getD(race.get())));
					b.add(GFORMAT.perc(b.text(), STATS.RELIGION().SHRINE.quality(ss.religion).data(cl).getD(race.get())));
					b.NL();
					
					b.textLL(¤¤Conversion);
					b.tab(6);
					b.add(GFORMAT.f0(b.text(), ss.religion.conversionCity.get(cl.get(race.get()))));
					
					b.NL(8);
				
					
					ss.religion.boosts.hover(text, HCLASS_RACE.clP(race.get(), cl), BoostableCat.TYPE_SETT);
					
				
				}
				super.hoverInfoGet(text);
			}
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				GCOLOR.UI().border().render(r, body());
				GCOLOR.UI().bg().render(r, body(), -1);
				super.render(r, ds);
			}
		};
		
		s.addRightC(4, new RENDEROBJ.RenderImp(16, 16) {
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				ColorImp.TMP.set(ss.religion.color);
				ColorImp.TMP.shadeSelf(0.75);
				ColorImp.TMP.render(r, body);
				ColorImp.TMP.set(ss.religion.color);
				ColorImp.TMP.render(r, body, -2);
			}
		});
		

		
		s.addRightC(16, ss.religion.icon);
		
		s.addRightC(16, UI.icons().s.human);
		s.addRightC(4, new GStat() {
			
			@Override
			public void update(GText text) {
				GFORMAT.i(text, ss.followers.data(cl).get(race.get()));
			}
		});
		
		s.addRightC(64, NEEDS.TYPES().SHRINE.rate.icon);
		s.addRightC(16, new GButt.Checkbox() {
			@Override
			protected void clickA() {
				ss.permissionShrine.toggle(cl, race.get());
				CoopRuntime.religionPermissionChanged(ss, 0, cl, race.get(), ss.permissionShrine.get(cl, race.get()));
			}
			
			@Override
			protected void renAction() {
				selectedSet(is());
			}
			
			private boolean is() {
				return ss.permissionShrine.get(cl, race.get());
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				if (race.get() != null)
					text.text(¤¤AllowRace);
				else
					text.text(¤¤Allow);
			}
		});
		s.addRightC(4, new RENDEROBJ.RenderImp(100, 16) {
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				double v = STATS.RELIGION().SHRINE.access(ss.religion).data(cl).getD(race.get())*STATS.RELIGION().SHRINE.quality(ss.religion).data(cl).getD(race.get());
				GMeter.render(r, GMeter.C_BLUE, v, body());
			}
		});
		
		s.addRightC(16, NEEDS.TYPES().TEMPLE.rate.icon);
		s.addRightC(16, new GButt.Checkbox() {
			@Override
			protected void clickA() {
				ss.permissionTemple.toggle(cl, race.get());
				CoopRuntime.religionPermissionChanged(ss, 1, cl, race.get(), ss.permissionTemple.get(cl, race.get()));
			}
			
			@Override
			protected void renAction() {
				selectedSet(is());
			}
			
			private boolean is() {
				return ss.permissionTemple.get(cl, race.get());
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				if (race.get() != null)
					text.text(¤¤AllowRace);
				else
					text.text(¤¤Allow);
			}
		});
		s.addRightC(4, new RENDEROBJ.RenderImp(100, 16) {
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				double v = STATS.RELIGION().TEMPLE.access(ss.religion).data(cl).getD(race.get())*STATS.RELIGION().TEMPLE.quality(ss.religion).data(cl).getD(race.get());
				GMeter.render(r, GMeter.C_BLUE, v, body());
			}
		});
		
		s.pad(10, 6);
		s.body().setWidth(520);
		return s;
	}
	
	final static class StatRowGrave {

		private final HCLASS cl;
		private final GETTER<Race> race;
		
		StatRowGrave(HCLASS cl, GETTER<Race> race, LinkedList<RENDEROBJ> rens){
			this.race = race;
			this.cl = cl;
			
			boolean has = false;
			for (StatGrave ss : STATS.BURIAL().graves()) {
				for (Race r : RACES.all()) {
					if (ss.standing().definition(r).get(cl).max > 0) {
						has = true;
						break;
					}
				}
			}
			if (!has)
				return;
			
			GuiSection s = new GuiSection();
			
			s.add(new GText(UI.FONT().H2, ¤¤Burrial).lablify(), 0, 0);
			s.addRightCAbs(StatRow.StatX, new GStat() {
				
				@Override
				public void update(GText text) {
					double d = 0;
					for (StatGrave ss : STATS.BURIAL().graves()) {
						d = Math.max(d, ss.data(cl).getD(race.get()));
					}
					GFORMAT.perc(text,d);
				}
			});
			
			s.addCentredY(new RENDEROBJ.RenderImp(StatRow.MeterW, 20) {
				
				@Override
				public void render(SPRITE_RENDERER r, float ds) {
					double max = 0;
					double now = 0;
					double nor = 0;
					double prev = 0;
					for (StatGrave s : STATS.BURIAL().graves()) {
						max = Math.max(max, s.standing().max(cl, race.get()));
						now = Math.max(now, s.standing().get(cl, race.get()));
						prev = Math.max(prev, s.standing().getPrev(cl, race.get(), 8));
						nor = Math.max(nor, s.standing().normalized(cl, race.get()));
					}
					
					GMeter.renderDelta(r, prev/max, now/max, body.x1(), (int) (body().x1() + body().width()*nor), body().y1(), body().y2());
				}
			}, StatRow.MeterX);
			s.pad(4, 0);
			rens.add(s);
			
			for (StatGrave ss : STATS.BURIAL().graves()) {
				for (Race r : RACES.all()) {
					if (ss.standing().definition(r).get(cl).max > 0) {
						
						rens.add(service(ss));
						break;
					}
				}
				
			}
	
		}

		
		private RENDEROBJ service(StatGrave ss) {
			GuiSection s = new GuiSection() {
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					if (!isHoveringAHoverElement()) {
						ss.hover(text, cl, race.get());
					}
					super.hoverInfoGet(text);
				}
			};
			s.add(new StatRow.Arrow(ss, cl, race));
			s.addRightC(4, new GButt.Checkbox() {
				@Override
				protected void clickA() {
					ss.grave().permission().toggle(cl, race.get());
					CoopRuntime.gravePermissionChanged(ss, cl, race.get(), ss.grave().permission().get(cl, race.get()));
				}
				
				@Override
				protected void renAction() {
					selectedSet(is());
				}
				
				private boolean is() {
					return ss.grave().permission().get(cl, race.get());
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					if (race.get() != null)
						text.text(¤¤AllowRace);
					else
						text.text(¤¤Allow);
				}
			});
			s.addRightC(4, ss.grave().blueprint().iconBig());
			s.addRightC(4, new GText(UI.FONT().S, ss.grave().blueprint().info.names).lablifySub());
			s.addCentredY(new GStat() {
				
				@Override
				public void update(GText text) {
					text.setFont(UI.FONT().S);
					
					StatRow.format(text, ss, ss.data(cl).getD(race.get()), cl, race.get());
				}
			}, StatRow.StatX);
			
			s.addCentredY(new RENDEROBJ.RenderImp(StatRow.MeterW, 12) {
				
				@Override
				public void render(SPRITE_RENDERER r, float ds) {
					double max = ss.standing().max(cl, race.get());
					double now = ss.standing().get(cl, race.get());
					double nor = ss.standing().normalized(cl, race.get());
					GMeter.render(r, GMeter.C_BLUE, now/max, body.x1(), (int) (body().x1() + body().width()*nor), body().y1(), body().y2());
				}
			}, StatRow.MeterX);
			

			return s;
		}
		
	}

}
