package view.sett.ui.standing;

import init.race.Race;
import init.sprite.UI.Icon;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import init.type.HTYPES;
import settlement.main.SETT;
import settlement.room.infra.elderly.ROOM_RESTHOME;
import settlement.room.knowledge.school.ROOM_SCHOOL;
import settlement.room.knowledge.university.ROOM_UNIVERSITY;
import settlement.stats.STATS;
import settlement.stats.colls.StatsEducation;
import settlement.stats.colls.StatsEducation.AgeType;
import settlement.stats.colls.StatsEducation.StatEducation;
import settlement.stats.standing.STANDINGS;
import settlement.stats.stat.STAT;
import settlement.stats.stat.StatCollection;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LinkedList;
import snake2d.util.sprite.SPRITE;
import util.data.GETTER;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.gui.slider.GSliderInt;
import util.gui.table.GRows;
import util.gui.table.GScrollRows;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;
import view.main.VIEW;
import view.sett.ui.standing.Cats.Cat;

class CatOccupation extends Cat {
	
	private static CharSequence ¤¤workPrio = "Work Priorities";

	
	static {
		D.ts(CatOccupation.class);
	}

	CatOccupation(HCLASS cl, GETTER<Race> race){
		super(new StatCollection[] {STATS.WORK(), STATS.EDUCATION()});
		titleSet(Dic.¤¤Occupation);
		LinkedList<RENDEROBJ> rens = new LinkedList<>();

		
		rens.add(new StatRow.Title(STATS.WORK().info));
		for (STAT s : STATS.WORK().workStats) {
			rens.add(new StatRow(s, cl, race));
		}
		
		rens.add(new GButt.ButtPanel(¤¤workPrio) {
			@Override
			protected void clickA() {
				VIEW.s().ui.rooms.prio(cl, race.get(), this);
			}
		}.pad(16, 2));
		
		if (cl != HCLASSES.SLAVE()) {
			rens.add(new StatRow(STATS.WORK().RET.RETIREMENT_AGE, cl, race));
			rens.add(new StatRow(STATS.WORK().RET.RETIREMENT_HOME, cl, race));
		}
		
		if (cl == HCLASSES.CITIZEN()) {
			
			rens.add(new StatRow.Title(STATS.EDUCATION().info));
			for (StatEducation e : STATS.EDUCATION().all) {
				RENDEROBJ ch = new GButt.CheckboxSelect() {
					
					@Override
					protected void clickA() {
						STATS.EDUCATION().policySet(cl, race.get(), e);
						super.clickA();
					}
					
					@Override
					protected void renAction() {
						selectedSet(STATS.EDUCATION().policy(cl, race.get()) == e);
					}
					
				};
				
				rens.add(new StatRow(e.total, ch, cl, race));
			}
			

			
			for (AgeType aa : STATS.EDUCATION().allAges){
				GuiSection s = new GuiSection() {
					
					@Override
					public void hoverInfoGet(GUI_BOX text) {
						aa.hoverLimit(text, HCLASS_RACE.clP(race.get(), cl));
					}
					
				};
				s.body().incrW(8).incrH(1);
				
				s.addRightC(0, aa.icon);
				s.addRightC(0, new GHeader(aa.name));
				
				INTE ii = new INTE() {
					
					@Override
					public int min() {
						return 0;
					}
					
					@Override
					public int max() {
						return StatsEducation.LIMIT_MAX;
					}
					
					@Override
					public int get() {
						return aa.limit(cl, race.get());
					}
					
					@Override
					public void set(int t) {
						aa.limitSet(cl, race.get(), t);
					}
				};
				
				
				
				s.addRightCAbs(200, new GSliderInt(ii, 200, true));
				s.pad(8, 4);
				rens.add(s);
			}
			
			
			
			
			
			
			{
				GuiSection s = new GuiSection();

				
				GRows rows = new GRows(2);
				
				rows.add(s);
				
				for (ROOM_UNIVERSITY u : SETT.ROOMS().UNIVERSITIES) {
										
					s = new GuiSection();
					
					s.add(new GStat() {
						
						@Override
						public void update(GText text) {
							GFORMAT.iofk(text, u.employment().employed(), u.employment().employedMax());
						}
					}, 0, 0);

					
					s.addRelBody(8, DIR.W, u.iconBig());
					s.body().setWidth(135);
					s.body().pad(0, 2);
					
					GButt.ButtPanel b = new GButt.ButtPanel(s.asSprite()) {
						
						@Override
						public void hoverInfoGet(GUI_BOX box) {
							GBox b = (GBox) box;
							b.title(u.info.names);
							b.text(u.info.desc);
							b.NL();
							
							b.textLL(u.employment().title);
							b.tab(6);
							b.add(GFORMAT.iofk(b.text(), u.employment().employed(), u.employment().employedMax()));
							b.NL();
							
							
						}
						
						@Override
						protected void clickA() {
							VIEW.s().panels.add(VIEW.s().ui.rooms.open(u), true);
						}
					};
					rows.add(b);
				}
				
				rens.add(rows.rows());
				
				
			}
			
			{
				GuiSection s = new GuiSection();
				s.add(new GHeader(HTYPES.CHILD().names));
				s.addRightC(8, new GStat() {
					
					@Override
					public void update(GText text) {
						GFORMAT.i(text, STATS.POP().pop(HTYPES.CHILD()));
					}
				});
				rens.add(s);
				
				GRows rows = new GRows(2);
				
				for (ROOM_SCHOOL u : SETT.ROOMS().SCHOOLS) {
					s = new GuiSection();
					
					s.addRightC(0, u.iconBig());
					
					s.addRightC(8, new GStat() {
						
						@Override
						public void update(GText text) {
							GFORMAT.percInv(text, u.service().load());
						}
					});
					
					s.body().setWidth(120);
					s.body().pad(16, 0);
					
					GButt.ButtPanel b = new GButt.ButtPanel(s.asSprite()) {
						@Override
						public void hoverInfoGet(GUI_BOX box) {
							GBox b = (GBox) box;
							
							b.textLL(Dic.¤¤Employees);
							b.tab(6);
							b.add(GFORMAT.i(b.text(), u.employment().employed()));
							b.NL();
							
							b.textLL(Dic.¤¤load);
							b.tab(6);
							b.add(GFORMAT.perc(b.text(), u.service().load()));

							b.NL();

						}
						
						@Override
						protected void clickA() {
							VIEW.s().panels.add(VIEW.s().ui.rooms.open(u), true);
						}
					};
					
					rows.add(b);
					
				}
				
				rens.add(rows.rows());
				
				
			}

		}
		
		
		section.add(new GScrollRows(rens, HEIGHT, 0).view());
		
	}
	
	GuiSection makeHomes(HCLASS c, GETTER<Race> race) {
		GuiSection s = new GuiSection();
		
		int i = 0;
		
		for (ROOM_RESTHOME hh : SETT.ROOMS().RESTHOMES) {
			
			final ROOM_RESTHOME h = hh;
			
			
			SPRITE icon = new SPRITE.Imp(Icon.L) {
				
				@Override
				public void render(SPRITE_RENDERER r, int X1, int X2, int Y1, int Y2) {
					if (race.get() != null && race.get().pref().getWork(h.employment()) <= 0) {
						OPACITY.O50.bind();
						COLOR.BLACK.render(r, X1, X2, Y1, Y2);
						OPACITY.unbind();
					}
					h.iconBig().render(r, X1, X2, Y1, Y2);
				}
			};
			
			RENDEROBJ r = new GStat() {
				
				@Override
				public void update(GText text) {
					GFORMAT.iIncr(text, h.employment().neededWorkers()-h.employment().employed());
				}
				
				@Override
				public void hoverInfoGet(GBox b) {
					b.title(h.info.names);
					b.text(h.info.desc);
					b.NL(8);
					b.textLL(HTYPES.RETIREE().names);
					b.tab(5);
					b.add(GFORMAT.iofk(b.text(), h.employment().employed(), h.employment().neededWorkers()));
					b.NL();
					b.textLL(Dic.¤¤Quality);
					b.tab(5);
					b.add(GFORMAT.perc(b.text(), h.quality()));
					b.NL();
					if (race.get() != null) {
						b.textLL(STANDINGS.get(c).fullfillment.info().name);
						b.tab(5);
						b.add(GFORMAT.perc(b.text(), race.get().pref().getWork(h.employment())));
					}
						
				};
				
			}.hh(icon);
			
			s.add(r, 150*(i%4), 40*(i/4));
			i++;
			
		}
		
		s.pad(8);
		return s;
		
	}
	
	
	
}
