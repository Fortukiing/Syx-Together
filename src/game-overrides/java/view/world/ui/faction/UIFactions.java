package view.world.ui.faction;

import game.faction.Faction;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealDrawfter;
import game.faction.diplomacy.deal.DealSave;
import game.faction.npc.FactionNPC;
import game.faction.royalty.Royalty;
import init.constant.C;
import init.trade.TRADABLE;
import snake2d.util.gui.GUI_BOX;
import util.data.GETTER.GETTER_IMP;
import view.interrupter.ISidePanel;
import view.main.VIEW;
import world.WORLD;

public final class UIFactions {

	private final Deal deal = new Deal();

	
	private final GETTER_IMP<FactionNPC> getter = new GETTER_IMP<FactionNPC>() {
		
		@Override
		public void set(FactionNPC t) {
			super.set(t);
			if (get() != null)
				deal.setFactionAndClear(t, true);
		};
		
	};
	final Hoverer hov = new Hoverer();
	
	private final UIFactionList list = new UIFactionList(ISidePanel.HEIGHT);
	private final UIFaction detail = new UIFaction(getter, deal, C.MIN_WIDTH-list.section().body().width()-16, ISidePanel.HEIGHT);
	
	public UIFactions(){
		
	}
	
	public void open(FactionNPC r) {
		
		VIEW.world().activate();
		getter.set(r);
		
		VIEW.world().panels.add(list, true);
		if (r != null) {
			VIEW.world().panels.add(detail, false);
			deal.setFactionAndClear(getter.get(), true);
			VIEW.world().window.centererTile.set(r.cx(), r.cy());
		}
		
	}
	
	public boolean openIs() {
		return VIEW.world().panels.added(list);
	}
	
	public boolean openIs(Faction f) {
		return VIEW.world().panels.added(detail) && detail.f() == f;
	}
	
	public boolean diploIs(Faction f) {
		return VIEW.world().panels.added(detail) && detail.f() == f && detail.dipIS();
	}

	public boolean coopApplyDealSave(DealSave save) {
		if (save == null)
			return false;
		FactionNPC faction = save.f();
		if (faction == null || !diploIs(faction))
			return false;
		return save.set(deal) == null;
	}
	
	public void openPeace(FactionNPC other) {
		deal.setFactionAndClear(other, true);
		open(other);
		DealDrawfter.draftPeace(deal, other, true);
		detail.dip();
		
	}
	
	public void openTrade(FactionNPC other) {
		open(other);
		deal.setFactionAndClear(other, true);
		deal.bools.TRADE.setOn();
		DealDrawfter.draft(deal, true, true);
		detail.dip();
		
	}
	
	
	public void openBuy(FactionNPC other, TRADABLE res) {
		open(other);
		deal.setFactionAndClear(other, true);
		deal.npc.resources.set(res, 1);
		detail.dip();
		
	}
	
	
	public void openSell(FactionNPC other, TRADABLE res) {
		open(other);
		deal.setFactionAndClear(other, true);
		if (deal.player.resources.max(res) > 0)
			deal.player.resources.set(res, 1);
		detail.dip();
		
	}
	
	public void openDip(FactionNPC other) {
		open(other);
		deal.setFactionAndClear(other, true);
		detail.dip();
		
	}
	
	public void hover(GUI_BOX b, Faction r) {
		hov.hover(b, r);
	}
	
	public void hover(int tx, int ty) {
		
		Faction f = WORLD.REGIONS().faction.get(tx, ty);
		list.hover(f);
	}

	public void hover(GUI_BOX b, Royalty r) {
		Court.hover(b, r);
	}
	
}
