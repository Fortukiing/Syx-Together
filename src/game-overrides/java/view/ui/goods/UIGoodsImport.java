package view.ui.goods;

import coopmod.CoopRuntime;
import game.GAME;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.deal.DealParty;
import game.faction.npc.FactionNPC;
import init.resources.RESOURCES;
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
import snake2d.util.sprite.text.Str;
import util.colors.GCOLOR;
import util.data.GETTER;
import util.data.GETTER.GETTER_IMP;
import util.data.INT.INTE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GHeader;
import util.gui.misc.GInputInt;
import util.gui.misc.GMeter;
import util.gui.misc.GText;
import util.gui.slider.GSliderInt;
import util.gui.table.GStaples;
import util.info.GFORMAT;
import util.statistics.HistoryTradable;
import util.text.D;
import util.text.Dic;
import util.text.DicTime;
import view.main.VIEW;
import world.region.RD;

public class UIGoodsImport extends GuiSection{

	static CharSequence ¤¤name = "Import Settings";
	static CharSequence ¤¤Best = "¤Best";
	static CharSequence ¤¤BestD = "¤Make a custom order of this resource from the trade partner with the most favourable price.";
	static CharSequence ¤¤Closest = "¤Closest";
	static CharSequence ¤¤ClosestD = "¤Make a speedy custom order of this resource from the trade partner that is closest.";
	static CharSequence ¤¤Stockpile = "¤Current warehouse stock:";
	

	//private static CharSequence ¤¤CapacityNDesc = "¤The combined sell quota of your trade partners per day, and how much that you have currently bought. Once the capacity is exceeded, the factions will add extra tariffs to your purchases.";

	private static CharSequence ¤¤priceCapD = "¤The maximum price you are willing to pay for this resource.";
	private static CharSequence ¤¤minCredsD = "¤The minimum credits needed before a purchase is undertaken.";


	public static final COLOR color = new ColorImp(80, 80, 100);
	private static final COLOR colorDark = color.shade(0.75); 
	
	static {
		D.ts(UIGoodsImport.class);
	}
	
	public GETTER_IMP<TRADABLE> res = new GETTER_IMP<>(TR.ALL().get(0));
	
	public UIGoodsImport(){
		
		addDown(12, amount());
		addDown(12, priceH());
		addDown(12, price());
		addDown(12, credlim());
		//addDown(12, tradeCap(res));

		addRelBody(16, DIR.E, new UIGoodsTraders(6) {

			@Override
			protected int price(FactionNPC f) {
				return f.res(res.get()).priceSellP();
			}

			@Override
			protected int sortValue(FactionNPC f) {
				return f.res(res.get()).priceSellP();
			}
			
		});

		addRelBody(8, DIR.S, problem());
		
		
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
		
		{
			GuiSection s = new GuiSection();
			
			GButt.ButtPanel b = new GButt.ButtPanel(¤¤Best) {
				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
						boolean isHovered) {
					isActive &= FACTIONS.player().trade.pricesBuy.get(res.get()) > 0;
					super.render(r, ds, isActive, isSelected, isHovered);
				}
				
				@Override
				protected void clickA() {
					FactionNPC f = best();
					
					if (f != null) {
						VIEW.inters(). popup.close();
						VIEW.UI().manager.close();
						VIEW.world().UI.factions.openBuy(f, res.get());
					}
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					super.hoverInfoGet(text);
					GBox b = (GBox) text;
					b.NL(8);
					CharSequence p = FACTIONS.player().buyer(res.get()).problem();
					if (p != null)
						b.error(p);
					
					b.NL();
					
					FactionNPC f = best();
					
					if (f != null) {
						b.add(f.banner().MEDIUM);
						b.textLL(f.name);
						b.add(UI.icons().s.money);
						b.add(GFORMAT.i(b.text(), DealParty.manualPriceSell(f, res.get(), 1)));
						b.NL();
					}
				}
				
				private FactionNPC best() {
					FactionNPC f = null;
					int pp = Integer.MAX_VALUE;
					
					for (int fi = 0; fi < FACTIONS.NPCs().size(); fi++) {
						FactionNPC ff = FACTIONS.NPCs().get(fi);
						int p = DealParty.manualPriceSell(ff, res.get(), 1);
						if (p > 0 && ff.seller(res.get()).removeMax() > 0  && p < pp) {
							pp = p;
							f = ff;
						}
					}
					return f;
				}
			};
			b.hoverInfoSet(¤¤BestD);
			b.icon(UI.icons().s.money);
			b.setDim(180);
			s.addRightC(0, b);
			
			
			
			b = new GButt.ButtPanel(¤¤Closest) {
				
				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
						boolean isHovered) {
					isActive &= FACTIONS.player().trade.pricesBuy.get(res.get()) > 0;
					super.render(r, ds, isActive, isSelected, isHovered);
				}
				
				@Override
				protected void clickA() {
					FactionNPC f = null;
					int pp = Integer.MAX_VALUE;
					for (Faction fff : DIP.traders()) {
						FactionNPC ff = (FactionNPC) fff;
						int p = RD.DIST().distance(ff);
						if (ff.res(res.get()).priceSellP() > 0 && ff.seller(res.get()).removeMax() > 0 && p < pp) {
							pp = p;
							f = ff;
						}
					}
					if (f != null) {
						VIEW.inters().popup.close();
						VIEW.UI().manager.close();
						VIEW.world().UI.factions.openBuy(f, res.get());
					}
				}
				
				@Override
				public void hoverInfoGet(GUI_BOX text) {
					super.hoverInfoGet(text);
					GBox b = (GBox) text;
					b.NL(8);
					CharSequence p = FACTIONS.player().buyer(res.get()).problem();
					if (p != null)
						b.error(p);
				}
				
			};
			b.hoverInfoSet(¤¤ClosestD);
			b.icon(UI.icons().s.wheel);
			b.setDim(180);
			s.addRightC(0, b);
			s.addRelBody(10, DIR.N, new GHeader(UIGoodsExport.¤¤special, UI.FONT().S));
			addRelBody(8, DIR.S, s);
		}
		
		
	
		
		
	}
	

	
	private GuiSection priceH() {
		GuiSection s = new GuiSection();
		s.add(priceChart(FACTIONS.player().trade.pricesBuy, Dic.¤¤buyPrice, res, 8, 64));
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.coins));
		return s;
	}

	
	static HOVERABLE priceChart(HistoryTradable hi, CharSequence title, GETTER<TRADABLE> res, int ww, int height) {
		
		final int amount = hi.get(0).historyRecords();
		GStaples s = new GStaples(amount, false) {

			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				super.render(r, ds, hoveredIs());
				Str.TMP.clear();
				Str.TMP.add(hi.get(res.get()));
				int w = UI.FONT().S.getDim(Str.TMP).x();
				OPACITY.O50.bind();
				COLOR.BLACK.render(r, body().x1(),  body().x1()+w+4, body.y1(), body.y1()+18);
				OPACITY.unbind();
				UI.FONT().S.render(r, Str.TMP, body().x1()+2, body.y1()+1);
				
			}

			@Override
			protected double getValue(int stapleI) {
				double v = hi.history(res.get()).get(amount-1-stapleI);
				if (res.get() == null)
					v /= RESOURCES.ALL().size();
				return v;
			}

			@Override
			protected void hover(GBox b, int stapleI) {
				int si = amount-stapleI-1;
				b.title(Dic.¤¤buyPrice);
				GText t = b.text();
				t.lablify();
				DicTime.setAgo(t, si*GAME.player().res().time.bitSeconds());
				b.add(t);
				b.NL(4);
				
				b.add(GFORMAT.i(b.text(), (long) getValue(stapleI)));
				
			}

		};
		s.body().setWidth(ww*amount).setHeight(height);
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
				return FACTIONS.player().buyer(res.get()).priceCapsI.max();
			}
			
			@Override
			public int get() {
				return FACTIONS.player().buyer(res.get()).priceCapsI.get();
			}
			
			@Override
			public void set(int t) {
				FACTIONS.player().buyer(res.get()).priceCapsI.set(t);
				CoopRuntime.tradeImportSettingsChanged(res.get());
			}
		};
		
		GInputInt sl = new GInputInt(in, true, true);
		
		s.addRightC(2, sl);
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.coins.twin(UI.icons().s.cog, DIR.NE, 2)));
		return s;
	}
	
	private GuiSection credlim() {
		GuiSection s = new GuiSection() {
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				text.title(PBuyer.¤¤TreasuryLim);
				text.text(¤¤minCredsD);
			}
			
		};
		
		INTE in = new INTE() {
			
			@Override
			public int min() {
				return 1;
			}
			
			@Override
			public int max() {
				return FACTIONS.player().buyer(res.get()).minMoney.max();
			}
			
			@Override
			public int get() {
				return FACTIONS.player().buyer(res.get()).minMoney.get();
			}
			
			@Override
			public void set(int t) {
				FACTIONS.player().buyer(res.get()).minMoney.set(t);
				CoopRuntime.tradeImportSettingsChanged(res.get());
			}
		};
		
		GInputInt sl = new GInputInt(in, true, true);
		
		s.addRightC(2, sl);
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.coins.twin(UI.icons().s.arrowUp, DIR.NE, 2)));
		return s;
	}
	
	private GuiSection amount() {
		
		INTE limit = new INTE() {
			
			@Override
			public int get() {
				return FACTIONS.player().buyer(res.get()).limit.get();
			}

			@Override
			public int min() {
				return 0;
			}

			@Override
			public int max() {
				return FACTIONS.player().buyer(res.get()).limit.max();
			}

			@Override
			public void set(int t) {
				FACTIONS.player().buyer(res.get()).limit.set(t);
				CoopRuntime.tradeImportSettingsChanged(res.get());
			}
		};
		GSliderInt sl = new GSliderInt(limit, 200, true, true) {
			
			
			
			@Override
			protected void renderMidColor(SPRITE_RENDERER r, int x1, int width, int widthFull, int y1, int y2) {
				
				COLOR col = width != widthFull ? colorDark : color;
				col.render(r, x1, x1+width, y1, y2);
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				b.title(Dic.¤¤ImportLevel);
				
				
				b.NL(4);
				
				FACTIONS.player().buyer(res.get()).hoverCapacity(b);
				
			}
		};
		
		GuiSection s = new GuiSection() {
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				sl.hoverInfoGet(text);
			}
			
		};
		s.add(sl);
		
		
		s.addDown(0, new HOVERABLE.HoverableAbs(300, 24) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				double cap = res.get().pb().capacityValue();
				GMeter.render(r, GMeter.C_ORANGE, cap, body());
				
			}
		});
		
		s.addRelBody(8, DIR.W, icon(UI.icons().m.cog_big));
		
		return s;
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
//						has += Math.max(-f.res(res.get()).playerTraded(), 0);
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
//				text.title(PBuyer.¤¤TradeQuota);
//				text.text(¤¤CapacityNDesc);
//				text.NL(8);
//				
//				double has = 0;
//				double tot = 0;
//				for (FactionNPC f : RD.DIST().neighs()) {
//					if (DIP.get(f).trades) {
//						int t = (int) f.res(res.get()).playerTradeLimit();
//						int h = (int) Math.max(-f.res(res.get()).playerTraded(), 0);
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
	

	
	private HOVERABLE problem() {
		GuiSection s = new GuiSection();
		s.add(new HOVERABLE.HoverableAbs(550, 80) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				GCOLOR.UI().bg().render(r, body);
				GCOLOR.UI().borderH(r, body, 0);
				
				CharSequence p = FACTIONS.player().buyer(res.get()).problem();
				
				if (p != null) {
					GCOLOR.UI().BAD.hovered.bind();
					UI.FONT().S.render(r, p, body().x1()+8, body().y1()+8, body().width()-16, 1.0);
					COLOR.unbind();
				}
					
				else {
					p = FACTIONS.player().buyer(res.get()).warning();
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
	
	public static RENDEROBJ miniControl(TRADABLE res, UIGoodsImport setting) {
		
		GuiSection s = new GuiSection() {
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				
				FACTIONS.player().buyer(res).hover(text);
				text.title(¤¤name);
			}
		};
		
		GButt.ButtPanel b = new GButt.ButtPanel(UI.icons().s.cog) {
			
			@Override
			protected void clickA() {
				setting.res.set(res);
				VIEW.inters().popup.show(setting, this);
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
		};	
		b.setDim(48, 48);
		
		s.addRelBody(0, DIR.E, b);
		
		HOVERABLE h = new HOVERABLE.HoverableAbs(48, 14) {
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
				

				double cap = res.pb().capacityValue();
				GMeter.render(r, GMeter.C_ORANGE, cap, body());
				
				
			}
			
			
		};
		
		s.addRelBody(0, DIR.S, h);
		
		RENDEROBJ oo = new RENDEROBJ.RenderImp(s.body().width(), s.body().height()) {
			
			@Override
			public void render(SPRITE_RENDERER r, float ds) {
				if (FACTIONS.player().buyer(res).importing()) {
					if (FACTIONS.player().buyer(res).problem() != null) {
						GCOLOR.UI().BAD.hovered.bind();
						UI.icons().s.alert.renderC(r, body().x2()-8, body().y1());
					}else if (FACTIONS.player().buyer(res).warning() != null) {
						GCOLOR.UI().SOSO.hovered.bind();
						UI.icons().s.alert.renderC(r, body().x2()-8, body().y1());
					}
					COLOR.unbind();
				}else {
					OPACITY.O75.bind();
					COLOR.BLACK.render(r, body);
					OPACITY.unbind();
				}
			}
		};
		oo.body().centerIn(s);
		s.add(oo);
		
		return s;
		
	}
	
}
