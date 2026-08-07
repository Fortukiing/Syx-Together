package view.sett.ui.army;

import coopmod.CoopRuntime;
import game.GAME;
import game.battle.div.Div;
import game.faction.FACTIONS;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import settlement.main.SETT;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBattle.StatTraining;
import settlement.stats.equip.EquipBattle;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.text.Str;
import util.data.GETTER;
import util.gui.common.UIPickerArmy;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.text.D;
import util.text.Dic;
import view.keyboard.KEYS;
import view.main.VIEW;
import view.ui.div.UIDivCardWorld;
import world.army.AD;
import world.entity.army.WArmy;

final class Actions extends GuiSection{

	private static CharSequence ¤¤NoValid = "¤The selected division are already attached to a world army. You must recall them first.";
	private static CharSequence ¤¤Recall = "¤Recall";
	private static CharSequence ¤¤RecallD = "¤Recall these divisions from its world armies and have them return to the city. It will take a few days.";
	private static CharSequence ¤¤RecallProblem = "¤No divisions are selected that are currently attached to a world army.";
	private static CharSequence ¤¤SendOut = "¤Send Out";
	private static CharSequence ¤¤SendOutD = "¤Send this division to join an army on the world map. These soldiers will then have to be supplied through your army depots.";
	private static CharSequence ¤¤NotTrained = "¤Some of the soldiers are not fully trained to specification yet, and will continue to train before they join an army.";
	private static CharSequence ¤¤NoArmies = "¤There are no armies to send this division to. Recruit one on the world map.";
	private static CharSequence ¤¤NoDivs = "No divisions are selected.";
	private static CharSequence ¤¤DisbandD = "Are you sure you wish to disband {0} divisions?";
	private static CharSequence ¤¤Closed = "Our city is closed, we can not leave.";
	private static CharSequence ¤¤Transfer = "Soldiers of this division is still on route back to our city. We must wait until they return";
	
	static {
		D.ts(Actions.class);
	}
	
	Actions(ArrayList<Div> list){
		
		int width = 170;
		int height = 32;
		GButt.ButtPanel c;
		
		GuiSection f = new GuiSection();
		
		f.addRightC(0, new GButt.Glow(SPRITES.icons().m.questionmark) {
			
			@Override
			protected void clickA() {
				
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				Str tmp = Str.TMP.clear().add(Dic.¤¤Unitinfo);
				tmp.insert(0, KEYS.MAIN().UNDO.repr());
				tmp.insert(1, KEYS.MAIN().MOD.repr());
				text.text(tmp);
			};
			
			
		});
		
		c = new GButt.ButtPanel(Dic.¤¤Create) {

			
			@Override
			protected void clickA() {
				Div n = GAME.ARMIES().player().getNextEmptyOrdered();
				if (n == null)
					return;
				n.info.raceSet(FACTIONS.player().race());
				n.info.menSet(50);
				for (EquipBattle e : STATS.EQUIP().BATTLE_ALL())
					e.targetSet(n, 0);
				for (StatTraining e : STATS.BATTLE().TRAINING_ALL)
					n.info.trainingSet(e, 0);
				clicked = null;
			}
			
			@Override
			protected void renAction() {
				activeSet(false);
				for (Div d : GAME.ARMIES().player().divisions()) {
					if (d.info.men() == 0) {
						activeSet(true);
						return;
					}
				}
				
			}
			
		};
		c.icon(UI.icons().m.plus);
		c.setDim(width, height);
		f.addRightC(0, c);
		
		c = new GButt.ButtPanel(Dic.¤¤Edit) {
			
			private final Edit edit = new Edit();
			
			@Override
			protected void clickA() {
				for (int di = 0; di < list.size(); di++) {
					if (AD.cityDivs().attachedArmy(list.get(di)) != null) {
						list.remove(di);
						di--;
					}
				}
				if (list.size() > 0)
					VIEW.inters().popup.show(edit.get(list, this), this);
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				isActive = false;
				for (Div d : list) {
					if (AD.cityDivs().attachedArmy(d) == null)
						isActive = true;
				}
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
		};
		c.icon(UI.icons().m.menu);
		c.setDim(width, height);
		f.addRightC(0, c);
		
		
		c = new GButt.ButtPanel(¤¤SendOut) {
			
			UIPickerArmy p = new UIPickerArmy(new GETTER.GETTER_IMP<>(FACTIONS.player()), 400) {
				
				@Override
				protected void pick(WArmy a) {
					for (Div div : list) {
						if (a.divs().canAdd() && AD.cityDivs().attachedArmy(div) == null && UIDivCardWorld.supplyError(div) == null) {
							AD.cityDivs().attach(a, div);
						}
					}
					VIEW.inters().popup.close();
				}
				
				@Override
				protected boolean canBePicked(WArmy a) {
					if (a == null)
						return false;
					return a.divs().canAdd();
				}
			};
			
			@Override
			protected void clickA() {
				if (sendProblem(list) != null)
					return;
				
				VIEW.inters().popup.show(p, this);
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				isActive = sendProblem(list) == null;
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
			@Override
			protected void renAction() {
				
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				
				b.title(¤¤SendOut);
				b.text(¤¤SendOutD);
				
				b.NL(8);
				
				hoverSendOutProblem(list, b);
			}
			
		
			
		};
		c.icon(UI.icons().m.arrow_left);
		c.setDim(width, height);
		addRightC(0, c);
		
		c = new GButt.ButtPanel(UI.icons().m.fast_forw) {
			
			@Override
			protected void clickA() {
				SETT.BATTLE().info.sendOutWithoutTraining(!SETT.BATTLE().info.sendOutWithoutTraining());
			}

			@Override
			protected void renAction() {
				selectedSet(SETT.BATTLE().info.sendOutWithoutTraining());
			}
			

		
			
		};
		c.hoverInfoSet(Dic.¤¤SendOutArmyToggleD);
		c.setDim(height, height);
		addRightC(0, c);
		
		
		c = new GButt.ButtPanel(¤¤Recall) {
			
			@Override
			protected void clickA() {
				if (hardProblem(list) != null)
					return;
				for (Div div : list) {
					if (AD.cityDivs().attachedArmy(div) != null)
						AD.cityDivs().attach(null, div);	
				}
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				isActive = can();
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				
				b.title(¤¤Recall);
				b.text(¤¤RecallD);
				b.NL(8);
				
				if (!can())
					b.error(¤¤RecallProblem);
				
			}
			
			private boolean can() {
				if (list.size() == 0)
					return false;
				for (Div div : list) {
					if (AD.cityDivs().attachedArmy(div) != null) {
						return true;
					}
				}
				return false;
			}
			
			public CharSequence hardProblem(LIST<Div> divs) {
				if (divs.size() == 0)
					return ¤¤NoDivs;
				if (AD.army(FACTIONS.player()).all().size() <= 0)
					return ¤¤NoArmies;
				for (Div div : divs) {
					if (AD.cityDivs().attachedArmy(div) != null)
						return null;
				}
				return ¤¤NoValid;
			}
			
			
		};
		c.icon(UI.icons().m.arrow_right);
		c.setDim(width, height);
		addRightC(0, c);
		
		c = new GButt.ButtPanel(UI.icons().m.b_guard) {
			
			@Override
			protected void clickA() {
				boolean b = !is();
				for (Div div : list) {
					SETT.ROOMS().GUARD.activeDuty.set(div, b);
					CoopRuntime.guardActiveDutyChanged(div, b);
				}
			}
			
			private boolean is() {
				boolean s = false;
				for (Div div : list) {
					if (SETT.ROOMS().GUARD.activeDuty.is(div)) {
						s = true;
					}
				}
				return s;
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				isSelected = is();
				isActive = list.size() > 0;
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
			@Override
			public void hoverInfoGet(GUI_BOX text) {
				GBox b = (GBox) text;
				
				b.title(SETT.ROOMS().GUARD.activeDuty.info().name);
				b.text(SETT.ROOMS().GUARD.activeDuty.info().desc);
				b.NL(8);
				
			}
			
		};
		
		c.body.setHeight(height);
		addRightC(0, c);
		
		
		c = new GButt.ButtPanel(Dic.¤¤Disband) {
			
			final ACTION a = new ACTION() {
				
				@Override
				public void exe() {
					for (Div div : list) {
						if (AD.cityDivs().attachedArmy(div) == null)
							div.info.menSet(0);
					}
				}
			};
			
			@Override
			protected void clickA() {
				VIEW.inters().yesNo.activate(Str.TMP.clear().add(¤¤DisbandD).insert(0, list.size()), a, ACTION.NOP, true);
			}
			
			@Override
			protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected,
					boolean isHovered) {
				isActive = false;
				for (Div div : list) {
					if (AD.cityDivs().attachedArmy(div) == null)
						isActive = true;
				}
				super.render(r, ds, isActive, isSelected, isHovered);
			}
			
		};
		c.icon(UI.icons().m.cancel);
		c.setDim(width, height);
		addRightC(0, c);
		
		addRelBody(0, DIR.N, f);
		
	}
	
	static CharSequence sendProblem(LIST<Div> divs) {
		if (divs.size() == 0)
			return ¤¤NoDivs;
		if (SETT.ENTRY().isClosed())
			return ¤¤Closed;
		
		if (AD.army(FACTIONS.player()).all().size() <= 0)
			return ¤¤NoArmies;
		for (Div div : divs) {
			if (AD.cityDivs().attachedArmy(div) == null && UIDivCardWorld.supplyError(div) == null) {
				if (AD.cityDivs().get(div).men() > 0)
					return ¤¤Transfer;
				return null;
			}
			
		}
		for (Div div : divs) {
			if (UIDivCardWorld.supplyError(div) != null)
				return UIDivCardWorld.supplyError(div);
		}
		return ¤¤NoValid;
	}

	
	static void hoverSendOutProblem(LIST<Div> divs, GUI_BOX box) {
		GBox b = (GBox) box;
		CharSequence h = sendProblem(divs);
		if (h != null) {
			b.error(h);
			b.NL(4);
		}
		
		if (!SETT.BATTLE().info.sendOutWithoutTraining()) {
		
			for (Div div : divs) {
				if (VIEW.UI().div.settCivic.needsTraining(div) > 0) {
					b.add(b.text().warnify().add(¤¤NotTrained));
					b.NL(4);
					break;
				}
			}
		}
		
		UIDivCardWorld.hoverSendOut(divs, box);
		
	}
	
}
