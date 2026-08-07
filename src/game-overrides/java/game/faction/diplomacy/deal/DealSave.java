package game.faction.diplomacy.deal;

import java.io.Serializable;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.npc.FactionNPC;
import init.trade.TR;
import init.trade.TRADABLE;
import snake2d.util.file.Alloc;
import snake2d.util.sprite.text.Str;
import util.text.D;
import util.text.Dic;
import world.WORLD;
import world.map.regions.Region;

public final class DealSave implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final int fi;
	private final int fii;
	public final boolean[] bools;
	public final Party player;
	public final Party npc;
	
	public DealSave(Deal deal){
		fi = deal.npc.npc().index();
		fii = deal.npc.npc().iteration();
		bools = new boolean[deal.bools.all().size()];
		for (int i = 0; i < bools.length; i++) {
			bools[i] = deal.bools.all().get(i).is();
		}
		player = new Party(deal.player);
		npc = new Party(deal.npc);
		
	}
	
	private static CharSequence ¤¤Faction = "The faction of this agreement does no longer exist.";
	private static CharSequence ¤¤You = "You currently do not have the means to fulfill this agreement. ({0})";
	private static CharSequence ¤¤Other = "The faction currently does not have the means to fulfill this agreement.";
	
	static {
		D.ts(DealSave.class);
	}
	
	public CharSequence set(Deal deal) {
		FactionNPC npc = f();
		if (npc == null)
			return ¤¤Faction;
		deal.setFactionAndClear(npc);
		for (int i = 0; i < bools.length; i++) {
			deal.bools.all().get(i).set(bools[i]);
		}
		if (player.set(deal.player) != null)
			return Str.TMP.clear().add(¤¤You).insert(0, player.set(deal.player));
		if (this.npc.set(deal.npc) != null)
			return ¤¤Other;
		return null;
	}
	
	public FactionNPC f() {
		Faction f = FACTIONS.getByIndex(fi);
		if (f == null || !f.isActive() || !(f instanceof FactionNPC))
			return null;
		FactionNPC npc = (FactionNPC) f;
		if (npc.iteration() != fii)
			return null;
		return npc;
	}
	
	public static final class Party implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public final int creditsP;
		public final int[] regsP;
		public int[] resP;
		
		Party(DealParty p){
			creditsP =p.credits.get();
			regsP = Alloc.ii(p.regs.all().size());
			for (int i = 0; i < regsP.length; i++) {
				regsP[i] = p.regs.all().get(i).is() ? p.regs.all().get(i).reg().index() : -1;
			}
			resP = Alloc.ii(TR.ALL().size());
			for (int i = 0; i < resP.length; i++) {
				resP[i] = p.resources.get(TR.ALL().get(i));
			}
		}
		
		private Object readResolve()  {
			int[] resP = Alloc.ii(TR.ALL().size());
			for (int i = 0; i < this.resP.length; i++) {
				TRADABLE res = TR.MAP().loader().get(i);
				if (res != null)
					resP[res.index()] = this.resP[i];
			}
			this.resP = resP;
		    return this;
		}
		
		CharSequence set(DealParty p) {
			p.credits.set(0);
			for (TRADABLE res : TR.ALL())
				p.resources.set(res, 0);
			p.regs.clear();
			
			
			if (creditsP > p.credits.max())
				return Dic.¤¤Curr;
			p.credits.set(creditsP);
			for (int i : regsP) {
				if (i != -1) {
					Region reg = WORLD.REGIONS().getByIndex(i);
					if (!reg.active() || reg.faction() != p.f())
						return Dic.¤¤Region;
					p.regs.add(reg);
				}
			}
			for (int i = 0; i < resP.length; i++) {
				if (resP[i] > 0 && resP[i] > p.resources.max(TR.ALL().get(i))) {
					return TR.ALL().get(i).names;
				}
				p.resources.set(TR.ALL().get(i), resP[i]);
			}
			
			return null;
		}
		
	}
	
}