package game.event.engine;

import coopmod.CoopRuntime;
import game.GAME;
import game.event.actions.EventAction;
import game.faction.FACTIONS;
import init.settings.S;
import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LinkedList;
import snake2d.util.sprite.SPRITE;
import snake2d.util.sprite.text.Str;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GTextR;
import util.gui.table.GRows;
import util.gui.table.GScrollRows;
import util.text.D;
import view.main.VIEW;
import view.ui.message.MessageSection;

class EventMessage extends MessageSection {

	private static CharSequence ¤¤active = "This event is no longer relevant.";
	static {
		D.ts(EventMessage.class);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String eKey;
	private final int ei;
	private final EContext data;
	private final int iteration;
	private int choice = -1;

	private final String[] mess;

	public EventMessage(Event abs, EContext data) {
		super(abs.info.name);
		eKey = abs.key;
		ei = abs.allIndex;
		mess = new String[abs.info.messages.length];
		for (int i = 0; i < mess.length; i++) {
			Str s = new Str(128);
			s.add(abs.info.messages[i]);
			EContext.insert.set(s, data);
			mess[i] = "" + s;
		}

		this.data = new EContext(data);
		iteration = GAME.EVENT().occ(abs);
	}

	@Override
	protected void make(GuiSection section) {

		Event e = GAME.EVENT().read(ei, eKey);
		if (e == null)
			return;

		LinkedList<RENDEROBJ> rr = new LinkedList<>();

		for (CharSequence par : mess) {
			
			for (CharSequence s : UI.FONT().M.getRows(par, 800)) {
				if (s.length() > 0)
					rr.add(new GTextR(UI.FONT().M, s));
			}
			
			rr.add(new RENDEROBJ.RenderDummy(10, 8));
		}

		if (data.coo.x() >= 0) {
			GButt.ButtPanel b = new GButt.ButtPanel(UI.icons().m.crossair) {

				@Override
				protected void clickA() {
					VIEW.s().getWindow().centererTile.set(data.coo);
				};
			};
			b.body.setDim(100);
			section.addRelBody(8, DIR.S, b);
			rr.add(new RENDEROBJ.RenderDummy(10, 8));
		}

		for (EventAction a : e.on_spawn) {
			if (!a.hideUI) {
				a.addToMessageBody(rr, e, data, section.body());
				rr.add(new RENDEROBJ.RenderDummy(10, 8));
			}
		}

		GRows butts = new GRows(2);

		int i = 0;
		for (EChoice c : e.choices) {
			final int k = i++;
			GButt.ButtPanel b = new GButt.ButtPanel(c.name) {

				@Override
				protected void clickA() {
					VIEW.messages().hide();
					for (EventAction a : c.actions) {
						if (a.problem(e, data) != null) {
							return;
						}
					}
					if (!c.request.passes(FACTIONS.player()))
						return;
					if (CoopRuntime.eventChoiceRequest(e, data, k)) {
						super.clickA();
						return;
					}
					GAME.EVENT().choiceSelect(e, k);
					
					
					choice = k;
					for (EventAction a : c.actions) {
						a.exe(e, data);
					}
					if (GAME.EVENT().current() == e)
						GAME.EVENT().set(null, false, false, false, false);

					super.clickA();
				}

				@Override
				protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
						boolean isHovered) {
					super.render(r, ds, isActive, isSelected, isHovered);
					if (!c.request.passes(FACTIONS.player())) {
						OPACITY.O50.bind();
						COLOR.WHITE50.render(r, body, -2);
						OPACITY.unbind();
						return;
					}
					for (EventAction a : c.actions) {
						if (a.problem(e, data) != null) {
							OPACITY.O50.bind();
							COLOR.WHITE50.render(r, body, -2);
							OPACITY.unbind();
							return;
						}
					}

				}

				@Override
				protected void renAction() {
					selectedSet(choice == k);
					activeSet(iteration == GAME.EVENT().occ(e) && GAME.EVENT().current() == e);
				}

				@Override
				public void hoverInfoGet(GUI_BOX text) {
					GBox b = (GBox) text;
					b.title(c.name);
					
					if (iteration != GAME.EVENT().occ(e) || GAME.EVENT().current() != e) {
						b.error(¤¤active);
						b.NL();
						if (!S.get().developer)
							return;
						
					}
					
					
					for (EventAction a : c.actions) {
						if (!a.hideUI) {
							a.hover(b, e, data);
							b.NL(8);
						}
					}
					b.NL();
					for (EventAction a : c.actions) {
						CharSequence p = a.problem(e, data);
						if (p != null) {
							b.error(p);
							b.NL();
						}

					}
					if (c.request.all().size() > 0) {
						c.request.hover(text, FACTIONS.player());
					}

					if (S.get().developer) {
						for (EventAction a : c.actions)
							b.text(a.key);
					}
					
					super.hoverInfoGet(text);
					
					
				}

			};

			b.body.setWidth((WIDTH - 100) / 2);

			butts.add(b);

		}

		
		SPRITE icon = null;
		if (icon == null && e.selection.indu.useAsIcon)
			icon = data.indu.sprite();
		if (icon == null && e.selection.reg.useAsIcon)
			icon = data.regs.sprite();
		if (icon == null && e.selection.faction.useAsIcon)
			icon = data.faction.sprite();
		if (icon == null && e.selection.royalty.useAsIcon)
			icon = data.royalty.sprite();
		if (icon == null)
			icon = e.info.icon;
		
	
		
		int hmax = 700 - icon.height() - butts.height() - 16;
		for (RENDEROBJ r : rr) {
			hmax -= r.body().height();
		}

		if (hmax > 0) {
			for (RENDEROBJ o : rr)
				section.addDown(0, o);
		} else {
			GScrollRows sr = new GScrollRows(rr, 700 - icon.height() - butts.height() - 16);
			section.addRelBody(8, DIR.S, sr.view());
		}

		SPRITE ic = icon;
		if (icon != null) {
			int sc = 1;
			if (icon.width() < Icon.HUGE) {
				sc = 2;
			}

			SPRITE sp = new SPRITE.Imp(icon.width() * sc, icon.height() * sc) {

				@Override
				public void render(SPRITE_RENDERER r, int X1, int X2, int Y1, int Y2) {
					ic.render(r, X1, X2, Y1, Y2);

				}
			};
			section.addRelBody(8, DIR.N, sp);
		}

		for (RENDEROBJ o : butts.rowsCentered(WIDTH))
			section.addRelBody(0, DIR.S, o);

	}

}
