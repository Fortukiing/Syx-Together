package view.ui.goods;

import coopmod.CoopRuntime;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.deal.DealParty;
import game.faction.npc.FactionNPC;
import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import init.trade.TR;
import init.trade.TRADABLE;
import settlement.trade.PBuyer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.Hoverable.HOVERABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.SPRITE;
import util.colors.GCOLOR;
import util.data.GETTER;
import util.data.GETTER.GETTER_IMP;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GInputInt;
import util.gui.misc.GMeter;
import util.gui.slider.GSliderInt;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;
import view.main.VIEW;
import world.region.RD;

public class UIGoodsExport extends GuiSection{

	static CharSequence ¤¤name = "Export Settings";
	static CharSequence ¤¤special = "Special Orders";

	private static CharSequence ¤¤priceCapD = "¤The minimum price you are willing to sell this resource for.";

	public static final COLOR color = new ColorImp(100, 90, 70);
	
	static {
		D.ts(UIGoodsExport.class);
	}
	
	public GETTER_IMP<TRADABLE> res = new GETTER_IMP<>(TR.ALL().get(0));
	
	public UIGoodsExport(boolean title){
		
		addDown(6, amount());
		addDown(6, priceH());
		addDown(6, price());
		//addDown(6, tradeCap(res));
		
		addRelBody(16, DIR.E, new UIGoodsTraders(6) {

			@Override
			protected int price(FactionNPC f) {
				return f.res(res.get()).priceBuyP();
			}

			@Override
			protected int sortValue(FactionNPC f) {
				return -f.res(res.get()).priceBuyP();
			}
			
		});

		addRelBody(8, DIR.S, problem());
		
		if (title) {
			GuiSection h = new GuiSection();
			
			h.add(new HOVERABLE.HoverableAbs(Icon.M) {
				
				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
					res.get().icon().render(r, body);
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					text.title(res.get().name);
				}
			});
			h.addRightC(8, new GHeader(¤¤name));
			addRelBody(8, DIR.N, h);
		}
		
		{
			GuiSection s = new GuiSection();
			
			GButt.ButtPanel b = new GButt.ButtPanel(UIGoodsImport.¤¤Best) {
				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
						boolean isHovered) {
					isActive &= FACTIONS.player().trade.pricesSell.get(res.get()) > 0 && res.get().ps().playerOwned() > 0;
					super.render(r, ds, isActive, isSelected, isHovered);
				}
				
				@Override
				protected void clickA() {
					FactionNPC f = best();
					
					if (f != null) {
						VIEW.inters(). popup.close();
						VIEW.UI().manager.close();
						VIEW.world().UI.factions.openSell(f, res.get());
					}
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					super.hoverInfoGet(text);
					GBox b = (GBox) text;
					b.NL(8);
					CharSequence p = FACTIONS.player().seller(res.get()).problem();
					if (p != null)
						b.error(p);
					b.NL();
					FactionNPC f = best();
					
					if (f != null) {
						b.add(f.banner().MEDIUM);
						b.textLL(f.name);
						b.add(UI.icons().s.money);
						b.add(GFORMAT.i(b.text(), DealParty.manualPriceBuy(f, res.get(), 1)));
						b.NL();
					}
				}
				
				private FactionNPC best() {
					FactionNPC f = null;
					int pp = 0;
					
					for (int fi = 0; fi < FACTIONS.NPCs().size(); fi++) {
						FactionNPC ff = FACTIONS.NPCs().get(fi);
						int p = DealParty.manualPriceBuy(ff, res.get(), 1);
						if (p > 0 && p > pp) {
							pp = p;
							f = ff;
						}
					}
					return f;
				}
			};
			b.hoverInfoSet(UIGoodsImport.¤¤BestD);
			b.icon(UI.icons().s.money);
			b.setDim(180);
			s.addRightC(0, b);
			
			b = new GButt.ButtPanel(UIGoodsImport.¤¤Closest) {
				
				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
						boolean isHovered) {
					isActive &= FACTIONS.player().trade.pricesSell.get(res.get()) > 0 && res.get().ps().playerOwned() > 0;
					super.render(r, ds, isActive, isSelected, isHovered);
				}
				
				@Override
				protected void clickA() {
					FactionNPC f = null;
					int pp = Integer.MAX_VALUE;
					for (Faction fff : DIP.traders()) {
						FactionNPC ff = (FactionNPC) fff;
						int p = RD.DIST().distance(ff);
						if (ff.res(res.get()).priceSellP() > 0 && p < pp) {
							pp = p;
							f = ff;
						}
					}
					if (f != null) {
						VIEW.inters().popup.close();
						VIEW.UI().manager.close();
						VIEW.world().UI.factions.openSell(f, res.get());
					}
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					super.hoverInfoGet(text);
					GBox b = (GBox) text;
					b.NL(8);
					CharSequence p = FACTIONS.player().seller(res.get()).problem();
					if (p != null)
						b.error(p);
				}
				
			};
			b.hoverInfoSet(UIGoodsImport.¤¤ClosestD);
			b.icon(UI.icons().s.wheel);
			b.setDim(180);
			s.addRightC(0, b);
			
			addRelBody(8, DIR.S, s);
		}
		
		
	
		
		
	}
	
	public static RENDEROBJ mini(TRADABLE res, UIGoodsExport export) {
		
		GuiSection s = new GuiSection() {
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				FACTIONS.player().seller(res).hover(text);
			}
			
		};
		
		GETTER_IMP<TRADABLE> get = new GETTER.GETTER_IMP<TRADABLE>(res);
		
		GButt.ButtPanel b = new GButt.ButtPanel(UI.icons().s.cog) {
			
			@Override
			protected void clickA() {
				export.res.set(res);
				VIEW.inters().popup.show(export, this);
			}
		};	
		b.setDim(48, 48);
		
		s.addRelBody(0, DIR.E, b);
		s.addRelBody(0, DIR.S, UIGoodsExport.capBar(get, 48, 14));
		
		
		RENDEROBJ oo = new RENDEROBJ.RenderImp(s.body().width(), s.body().height()) {
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				if (res.ps().exporting() != null) {
					OPACITY.O50.bind();
					COLOR.BLACK.render(r, body);
					OPACITY.unbind();
				}else {
					if (FACTIONS.player().seller(res).problem() != null) {
						GCOLOR.UI().BAD.hovered.bind();
						UI.icons().s.alert.renderC(r, body().x2()-8, body().y1());
					}else if (FACTIONS.player().seller(res).warning() != null) {
						GCOLOR.UI().SOSO.hovered.bind();
						UI.icons().s.alert.renderC(r, body().x2()-8, body().y1());
					}
					COLOR.unbind();
				}
			}
		};
		
		oo.body().centerIn(s);
		s.add(oo);
		
		
		return s;
		
	}
	
	private GuiSection priceH() {
		GuiSection s = new GuiSection();
		s.add(UIGoodsImport.priceChart(FACTIONS.player().trade.pricesSell, Dic.¤¤sellPrice, res, 8, 64));
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.coins));
		return s;
	}
	
	private GuiSection price() {
		GuiSection s = new GuiSection() {
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				text.title(PBuyer.¤¤PriceCap);
				text.text(¤¤priceCapD);
			}
			
		};
		
		INTE in = new INTE() {
			
			@Override
			public int min() {
				return 1;
			}
			
			@Override
			public int max() {
				return FACTIONS.player().seller(res.get()).priceCapsI.max();
			}
			
			@Override
			public int get() {
				return FACTIONS.player().seller(res.get()).priceCapsI.get();
			}
			
			@Override
			public void set(int t) {
				FACTIONS.player().seller(res.get()).priceCapsI.set(t);
				CoopRuntime.tradeExportSettingsChanged(res.get());
			}
		};
		
		GInputInt sl = new GInputInt(in, true, true);
		
		s.addRightC(2, sl);
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.coins.twin(UI.icons().s.arrowUp, DIR.NE, 2)));
		return s;
	}
	
	private GuiSection amount() {
		
		GuiSection sl = new GuiSection() {
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				FACTIONS.player().seller(res.get()).hoverCapacity(text);
			}
		};
		
		INTE in = new INTE() {
			
			@Override
			public int min() {
				return 0;
			}
			
			@Override
			public int max() {
				return FACTIONS.player().seller(res.get()).limit.max();
			}
			
			@Override
			public int get() {
				return FACTIONS.player().seller(res.get()).limit.get();
			}
			
			@Override
			public void set(int t) {
				FACTIONS.player().seller(res.get()).limit.set(t);
				CoopRuntime.tradeExportSettingsChanged(res.get());
			}
		};
		
		
		sl.add(new GSliderInt(in, 200, true, true));
		sl.addDown(0, new HOVERABLE.HoverableAbs(300, 24) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				double cap = res.get().ps().capacityValue();;
				GMeter.render(r, GMeter.C_ORANGE, cap, body());
			}
		});
		
		
		sl.addRelBody(12, DIR.W, UI.icons().m.cog_big);

		return sl;
	}

	
//	static GSliderInt slider(GETTER<RESOURCE> res, int width, int height) {
//		INTE limit = new INTE() {
//			
//			@Override
//			public int get() {
//				return SETT.ROOMS().EXPORT.tally.exportWhenUnder.get(res.get());
//			}
//
//			@Override
//			public int min() {
//				return SETT.ROOMS().EXPORT.tally.exportWhenUnder.min(res.get());
//			}
//
//			@Override
//			public int max() {
//				return SETT.ROOMS().EXPORT.tally.exportWhenUnder.max(res.get());
//			}
//
//			@Override
//			public void set(int t) {
//				SETT.ROOMS().EXPORT.tally.exportWhenUnder.set(res.get(), t);
//			}
//		};
//
//		return new GSliderInt(limit, width, height, false) {
//			
//			
//			
//			@Override
//			protected void renderMidColor(SPRITE_RENDERER r, int x1, int width, int widthFull, int y1, int y2) {
//				
//				COLOR col = width != widthFull ? colorDark : color;
//				col.render(r, x1, x1+width, y1, y2);
//			}
//			
//			@Override
//			public void hoverInfoGet(GUI_BOX text) {
//				
//				GBox b = (GBox) text;
//				b.title(¤¤ExportLevel);
//
//				int space = (int) SETT.ROOMS().STOCKPILE.tally().space.total(res.get());
//				int amount = (int) SETT.ROOMS().STOCKPILE.tally().amountTotal(res.get());
//				if (limit.getD() == 1) {
//					b.textL(¤¤LevelEverything);
//				}else if (limit.getD() == 0) {
//					b.textL(¤¤LevelNothing);
//				}else {
//					GText t = b.text();
//					t.add(¤¤LevelCurrent);
//					
//					double lim = limit.get()/(limit.max()-1.0);
//					lim = 1.0-lim;
//					t.insert(0, (int)(Math.round(100*lim)));
//					t.insert(1, (int)(lim*space));
//					b.add(t);
//					b.NL();
//					t = b.text();
//					t.add(¤¤LevelCurrentE);
//					
//					int export = (int)CLAMP.d(amount - lim*space, 0, amount);
//					t.insert(0, export);
//					
//					b.add(t);
//					b.NL(4);
//				}
//				b.NL(4);
//				
//				b.NL(8);
//				b.textLL(UIGoodsImport.¤¤Stockpile);
//				b.tab(7);
//				b.add(GFORMAT.iofkNoColor(b.text(), amount, space));
//				
//				b.NL(8);
//				
//				b.text(¤¤LevelDesc);
//				
//				
//			}
//		};
//	}
	
	static HOVERABLE capBar(GETTER<TRADABLE> res, int width, int height) {
		return new HOVERABLE.HoverableAbs(width, height) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				double cap = res.get().ps().capacityValue();
				GMeter.render(r, GMeter.C_ORANGE, cap, body());
				
			}

		};
	}
	
	
	private HOVERABLE problem() {
		GuiSection s = new GuiSection();
		s.add(new HOVERABLE.HoverableAbs(564, 80) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				GCOLOR.UI().bg().render(r, body);
				GCOLOR.UI().borderH(r, body, 0);
				
				CharSequence p = FACTIONS.player().seller(res.get()).problem();
				
				if (p != null) {
					GCOLOR.UI().BAD.hovered.bind();
					UI.FONT().S.render(r, p, body().x1()+8, body().y1()+8, body().width()-16, 1.0);
					COLOR.unbind();
				}
					
				else {
					p = FACTIONS.player().seller(res.get()).warning();
					GCOLOR.UI().SOSO.hovered.bind();
					if (p != null)
						UI.FONT().S.render(r, p, body().x1()+8, body().y1()+8, body().width()-16, 1.0);
					COLOR.unbind();
				}
				
				
			}
		});
		s.hoverInfoSet(Dic.¤¤Problem);
		return s;
	}

	
	private static HOVERABLE icon(SPRITE icon) {
		
		return new HOVERABLE.HoverableAbs(32, 32) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				icon.renderC(r, body());
			}
		};
		
	}
	
//	static HOVERABLE tradeCap(GETTER<TRADABLE> res) {
//		HOVERABLE cc = new HOVERABLE.HoverableAbs(300, 32) {
//			
//			@Override
//			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
//				double has = 0;
//				double tot = 0;
//				for (FactionNPC f : RD.DIST().neighs()) {
//					if (DIP.get(f).trades) {
//						tot += f.res(res.get()).playerTradeLimit();
//						has += Math.max(f.res(res.get()).playerTraded(), 0);
//					}
//				}
//				if (tot == 0) {
//					GMeter.render(r, GMeter.C_ORANGE, 0, body());
//				
//				}else {
//					GMeter.render(r, GMeter.C_ORANGE, has/tot, body);
//					
//				}
//			}
//			
//		
//			
//			@Override
//			public void hoverInfoGet(GUI_BOX text) {
//				GBox b = (GBox) text;
//				text.title(¤¤CapacityN);
//				text.text(¤¤CapacityNDesc);
//				text.NL(8);
//				
//				double has = 0;
//				double tot = 0;
//				for (FactionNPC f : RD.DIST().neighs()) {
//					if (DIP.get(f).trades) {
//						int t = (int) f.res(res.get()).playerTradeLimit();
//						int h = (int) Math.max(f.res(res.get()).playerTraded(), 0);
//						tot += t;
//						has += h;
//						b.add(f.banner().MEDIUM);
//						b.textL(f.name);
//						b.tab(7);
//						b.add(GFORMAT.iofk(b.text(), h, t));
//						b.NL();
//					}
//				}
//				
//				b.textLL(Dic.¤¤Total);
//				b.tab(7);
//				b.add(GFORMAT.iofk(b.text(), (int)has, (int)tot));
//				b.NL();
//				
//
//			}
//		};
//		
//		GuiSection s = new GuiSection() {
//			@Override
//			public void hoverInfoGet(GUI_BOX text) {
//				cc.hoverInfoGet(text);
//			}
//		};
//		s.add(cc);
//		
//		s.addRelBody(8, DIR.W, icon(UI.icons().m.wheel));
//		return s;
//	}
	
}
