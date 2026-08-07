package game.faction.player;

import java.io.IOException;
import java.util.Arrays;

import coopmod.CoopRuntime;
import game.boosting.BOOSTING;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.BoostSpec;
import game.boosting.BoostSpecs;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import game.time.TIME;
import init.sprite.UI.UI;
import init.tech.TECH;
import init.tech.TECH.TechRequirement;
import init.tech.TECHS;
import init.tech.TechCost;
import init.tech.TechCurrency;
import init.type.HCLASS_RACE;
import settlement.main.SETT;
import settlement.room.infra.admin.AdminData;
import settlement.room.infra.admin.AdminData.ROOM_ADMIN_HOLDER;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomBlueprintIns;
import settlement.stats.STATS;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.misc.ACTION;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.KeyMap;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.text.Str;
import util.gui.misc.GBox;
import util.info.GFORMAT;
import util.statistics.HISTORY_INT;
import util.statistics.HistoryInt;
import util.text.D;
import util.text.Dic;
import util.updating.IUpdater;
import view.interrupter.IDebugPanel;
import view.ui.message.MessageText;

public class PTech {

	public static CharSequence ¤¤name = "Technology";
	private static CharSequence ¤¤allocated = "Allocated";
	private static CharSequence ¤¤frozen = "Frozen";
	private static CharSequence ¤¤available = "Available";	
	private static CharSequence ¤¤penalty = "Penalty";

	private static CharSequence ¤¤low = "¤{0} low";
	private static CharSequence ¤¤lowBody = "¤There is not enough {0} to maintain our current technologies. As a result, all bonuses from technologies using these points are receiving a penalty, and some unlocked mechanics are now re-locked. Make sure your {1} producing facilities are fully operational, or build more of them.";
	
	static{
		D.ts(PTech.class);
	}

	private final double frozenRate = 100.0/TIME.days().bitSeconds();
	private int[] level = Alloc.ii(TECHS.ALL().size());
	private float[] penalties = new float[TECHS.ALL().size()];
	public static final double FORGET_THRESHOLD = 0.8;
	
	public final BoostSpecs boosters = new BoostSpecs(TECHS.¤¤name, UI.icons().s.vial, true);
	private final BoostCompound<TECH> bos;
	private double[] npcAmount;
	private final ArrayList<TechCurr> currs = new ArrayList<>(TECHS.COSTS().size());
	private double costsTmp[] = new double[TECHS.COSTS().size()];
	private boolean dirty = true;
	
	PTech(){

		for (TechCurrency c : TECHS.COSTS())
			currs.add(new TechCurr(c));
		
		IDebugPanel.add("unlockRooms", new ACTION() {
			
			@Override
			public void exe() {
				
				for (TechCurr c : currs) {
					BValue v = new BValue.BValuePlayerOnly() {
						
						@Override
						public double vGet(Player f) {
							return 1;
						}

						@Override
						public double vGet(FactionNPC f) {
							return 0;
						}
					};
					new BoosterValue(v, new BSourceInfo("cheat", UI.icons().s.expand), 1000000, false).add(c.cu.bo);
				}
				
				for (int ti = 0; ti < TECHS.ALL().size(); ti++) {
					TECH t = TECHS.ALL().get(ti);
					if (t.lockers.all().size() > 0) {
						levelSet(t, t.levelMax);
					}
				}
			}
		});
		
		BOOSTING.connecter(new ACTION() {
			
			@Override
			public void exe() {
				int ss = 0;
				for (TECH t : TECHS.ALL()) {
					for (BoostSpec s : t.boosters.all()) {
						ss = Math.max(s.boostable.index(), ss);
					}
				}
				ss++;
				double[] totMul = new double[ss]; 
				double[] totAdd = new double[ss]; 
				double[] mul = new double[ss]; 
				double[] add = new double[ss]; 
				Arrays.fill(totMul, 1);
				Arrays.fill(mul, 1);
				
				for (TECH t : TECHS.ALL()) {
					for (BoostSpec s : t.boosters.all()) {
						if (s.booster.isMul && s.booster.to() > 1) {
							totMul[s.boostable.index()] *= s.booster.to()*t.levelMax;
							mul[s.boostable.index()] *= s.booster.to()*t.AIAmount*t.levelMax;
						}else if (!s.booster.isMul && s.booster.to() > 0) {
							totAdd[s.boostable.index()] += s.booster.to()*t.levelMax;
							add[s.boostable.index()] += s.booster.to()*t.AIAmount*t.levelMax;
						}
					}
				}
				
				npcAmount = new double[ss];
				
				for (int i = 0; i < ss; i++) {
					double tot = totMul[i] * (totAdd[i]);
					double t = mul[i] * (add[i]);
					npcAmount[i] = CLAMP.d((t) / (tot), 0, 1);
				}
				
			}
		});
		
		bos = new BoostCompound<TECH>(boosters, TECHS.ALL()) {

			@Override
			protected BoostSpecs bos(TECH t) {
				BoostSpecs bos = new BoostSpecs(t.boosters.info.name, t.boosters.info.icon, false);
				for (BoostSpec s : t.boosters.all()) {
					double to = s.booster.isMul ? ((s.booster.to()-1)*t.levelMax + 1) : s.booster.to()*t.levelMax;
					BoosterValue b = new BoosterValue(BValue.VALUE1, t.boosters.info, s.booster.from(), to, s.booster.isMul);
					bos.push(b, s.boostable);
				}
				
				
				
				return bos;
			}

			@Override
			protected double getValue(TECH t) {
				return (1.0-penalties[t.index()])*level(t)/t.levelMax;
			}

			@Override
			protected double get(Boostable bo, FactionNPC f, boolean isMul) {
				return super.get(bo, f, isMul)*npcAmount[bo.index()%npcAmount.length];
			}			
		
		};
		
	}
	
	public boolean isPenaltyLocked(TECH tech) {
		if (dirty)
			setPenalty();
		return penalties[tech.index()] > 0;
	}
	
	private void setBonuses() {
		for (TechCurr c : currs) {
			c.allocated = 0;
		}
		
		for (TECH t : TECHS.ALL()) {
			int l = level(t);
			if (l > 0) {
				for (TechCost c : t.costs) {
					currs.get(c.cu.index).allocated += costTotal(c, t, l);
				}
			}
		}
		bos.clearChache();
		setPenalty();
	}
	
	final SAVABLE saver = new SAVABLE() {
		
		@Override
		public void save(FilePutter file) {
			file.i(TECHS.ALL().size());
			for (TECH t : TECHS.ALL()) {
				file.chars(t.key);
				file.i(level[t.index()]);
			}
			
			file.i(currs.size());
			for (TechCurr c : currs) {
				file.chars(c.cu.bo.key);
				c.save(file);
			}
			uper.save(file);
		}
		
		@Override
		public void load(FileGetter file) throws IOException {
			int tS = file.i();

			Arrays.fill(level, 0);
			Arrays.fill(penalties, 0f);
			KeyMap<TECH> map = new KeyMap<>();
			for (TECH t : TECHS.ALL())
				map.put(t.key, t);
			for (int i = 0; i < tS; i++) {
				String k = file.chars();
				int l = file.i();
				if (map.containsKey(k)) {
					level[map.get(k).index()] = CLAMP.i(l, 0, map.get(k).levelMax);
				}
			}
			
			KeyMap<TechCurr> cmap = new KeyMap<>();
			for (TechCurr c : currs) {
				c.clear();
				cmap.put(c.cu.bo.key, c);
			}
			tS = file.i();
			
			for (int i = 0; i < tS; i++) {
				String k = file.chars();
				if (cmap.containsKey(k)) {
					cmap.get(k).load(file);
				}else {
					new TechCurr(TECHS.COSTS().get(0)).load(file);
				}
			}
			uper.load(file);
			setBonuses();
			bos.clearChache();
			dirty = true;
		}
		
		@Override
		public void clear() {

		}
	};
	


	
	private void setPenalty() {
		if (FACTIONS.player() == null || FACTIONS.player().capitolRegion() == null) {
			for (TechCurr c : currs)
				c.penalty = 0;
			Arrays.fill(penalties, 0f);
			return;
		}
		dirty = false;
		boolean changed = false;
		
		for (TechCurr c : currs) {
			double old = c.penalty;
			c.penalty = 0;
			
			double tot = c.total();
			double all = (c.frozen() + c.allocated())*FORGET_THRESHOLD;
			
			if (tot == 0) {
				c.penalty = c.available() < 0 ? 1 : 0;
			}else if (all > tot) {
				c.penalty = 1.0 - tot/all;
				c.penalty *= c.penalty;
			}else {
				c.penalty = 0;
			}
			if (old != c.penalty)
				changed = true;
		}
		
		if (changed) {
			for (TECH t : TECHS.ALL()) {
				double p = 0;
				for (TechCost c : t.costs) {
					p += (double)currs.get(c.cu.index).penalty*c.amount/t.costTotal;
				}
				penalties[t.index()] = (float) p;
			}
			bos.clearChache();
		}
		
		
		
		
	}
	
	private final IUpdater uper = new IUpdater(TECHS.COSTS().size(), 10) {
		
		@Override
		protected void update(int i, double ds) {
			TechCurr c = currs.get(i);
			c.total.set(c.total());
			if (c.frozen > 0) {
				double dfrocen = c.frozen/(TIME.secondsPerDay()*4);
				dfrocen = Math.max(dfrocen, frozenRate);
				
				c.frozen -= dfrocen*ds;
				if (c.frozen < 0)
					c.frozen = 0;
			}
			setPenalty();
			
			c.forgetTimer += ds;
			if (c.penalty > 0) {
				if (!c.forgetting && c.forgetTimer > 30) {
					c.forgetting = true;
					new MessageText(Str.TMP.clear().add(¤¤low).insert(0, c.cu.bo.name), Str.TMP2.clear().add(¤¤lowBody).insert(0, c.cu.bo.name).insert(1, c.cu.bo.name)).send();
					c.forgetTimer = 0;
				}else {
					
				}
			}else {
				c.forgetting = false;
			}
			
		}
	};
	
	void update(double ds) {
		uper.update(ds);
	}
	
	public int level(TECH tech) {
		return level[tech.index()];
	}
	
	public void levelSet(TECH tech, int level) {
		level = CLAMP.i(level, 0, tech.levelMax);
		if (level < level(tech)) {
			for (TechCost c : tech.costs)
				currs.get(c.cu.index).frozen += 0.5*(costTotal(c, tech)-costTotal(c, tech, level));
		}
		
		this.level[tech.index()] = level;
		setBonuses();
		CoopRuntime.techLevelChanged(tech, level);
		
	}
	
	public int costLevel(double am, TECH tech) {
		return costLevel(am, tech, level(tech));
	}
	
	public int costLevelNext(double am, TECH tech) {
		return costLevel(am, tech, level(tech)+1);
	}
	
	public int costLevel(double am, TECH tech, int level) {
		if (am == 0)
			return 0;
		if (level > 1) {
			am += Math.round(tech.levelCostInc*CLAMP.i(level-1, 0, level));
		}
		
		return (int) Math.ceil(am);
	}
	
	public int costTotal(TechCost cost, TECH tech) {
		return costTotal(cost, tech, level(tech));
	}
	
	public static int costTotal(TechCost cost, TECH tech, int level) {
		
		int L = level;
		int A = (int) cost.amount;
		int B = (int) tech.levelCostInc;
		
		int am = A*L;
		
		if (L > 1) {
			L--;
			int sum = B*L*(L+1)/2;
			am += sum;
		}
		
		return am;
	}
	
	public int costOfNextWithRequired(TechCurrency cost, double am, TECH tech) {
		return costLevelNext(am, tech) + costOfRequired(cost, tech);
	}
	
	private boolean passes(TECH tech) {
		
		if (!tech.requires.passes(null))
			return false;
		for (int ti = 0; ti < tech.requires().size(); ti++) {
			TechRequirement r = tech.requires().get(ti);
			if (!passes(r.tech))
				return false;
		}
		return true;
	}
	
	public int costOfRequired(TechCurrency cost, TECH tech) {
		int am = 0;
		for (TechRequirement r : tech.requires()) {
			for (TechCost c : r.tech.costs) {
				if (c.cu == cost)
					am += Math.max(costTotal(c, r.tech, r.level) - costTotal(c, r.tech, level(r.tech)), 0);
			}
			
		}
		return am;
	}
	
	
	
	public boolean canUnlockNext(TECH tech) {
		if (level[tech.index()] >= tech.levelMax)
			return false;
		if (!passes(tech))
			return false;
		return canAffordNext(tech);
		
	}
	
	
	public boolean canAffordNext(TECH tech) {
		Arrays.fill(costsTmp, 0);
		for (TechCost c : tech.costs) {
			costsTmp[c.cu.index] = c.amount;
		}
		for (int ti = 0; ti < TECHS.COSTS().size(); ti++) {
			if (costOfNextWithRequired(TECHS.COSTS().get(ti), costsTmp[ti], tech) > Math.max(0, currs.get(ti).available()))
				return false;
		}
		return true;
	}

	
	public LIST<TechCurr> currs(){
		return currs;
	}
	
	public static class TechCurr {
		
		public final TechCurrency cu;
		private int allocated;
		private double frozen = 0;
		private double penalty = 0;
		private boolean forgetting = false;
		private double forgetTimer = 50;
		private double askTimer = -10;
		private final HistoryInt total = new HistoryInt(STATS.DAYS_SAVED, TIME.days(), true);
		
		TechCurr(TechCurrency cu){
			this.cu = cu;
		}
		
		public int allocated() {
			return allocated;
		}
		
		public int frozen() {
			return (int) Math.ceil(frozen);
		}
		
		public int total() {
			return (int) cu.bo.get(HCLASS_RACE.clP());
		}
		
		public int available() {
			return total()-frozen()-allocated();
		}
		
		public double penalty() {
			return penalty;
		}
		
		public HISTORY_INT produced() {
			total.set(total());
			return total;
		}
		
		public void hover(GUI_BOX box) {
			GBox b = (GBox) box;
			box.title(cu.bo.name);
			
	
			cu.bo.hoverDetailed(box, HCLASS_RACE.clP(), Dic.¤¤Produced, true);
			b.NL();
			
			b.textLL(¤¤allocated);
			b.tab(6);
			b.add(GFORMAT.iIncr(b.text(), -allocated));
			b.NL();
			
			b.textLL(¤¤frozen);
			b.tab(6);
			b.add(GFORMAT.iIncr(b.text(), -frozen()));
			b.NL();
			
			b.sep();
			
			b.textLL(¤¤available);
			b.tab(6);
			b.add(GFORMAT.iIncr(b.text(), available()));
			b.NL();
			
			b.textLL(¤¤penalty);
			b.tab(6);
			b.add(GFORMAT.percInv(b.text(), penalty));
			b.NL();
			
			b.sep();
			
			for (RoomBlueprint bb : SETT.ROOMS().all()) {
				
				
				if (bb instanceof RoomBlueprintIns<?> && bb instanceof ROOM_ADMIN_HOLDER) {
					AdminData d = ((ROOM_ADMIN_HOLDER)bb).admin();
					if (d.target == cu.bo) {
						b.title(((RoomBlueprintIns<?>)bb).info.names);
						

						b.textL(Dic.¤¤Produced);
						b.tab(6);
						b.add(GFORMAT.f0(b.text(), d.value()));
						b.NL();
						
						b.textL(Dic.¤¤Target);
						b.tab(6);
						b.add(GFORMAT.f0(b.text(), d.projection()));
						b.NL();
						
						b.NL();
						b.textL(Dic.¤¤Employees);
						b.tab(6);
						b.add(GFORMAT.i(b.text(), bb.employment().employed()));
						b.NL();
						
						b.textL(Dic.¤¤ProductionRate);
						b.tab(6);
						b.add(GFORMAT.f0(b.text(), d.perEmployee()));
						b.NL(6);
					}
						
				}
			}
			

		}

		void save(FilePutter file) {
			file.i(allocated);
			file.d(frozen);
			file.d(penalty);
			file.bool(forgetting);
			file.d(forgetTimer);
			file.d(askTimer);
			total.save(file);
			
		}

		void load(FileGetter file) throws IOException {
			allocated = file.i();
			frozen = file.d();
			penalty = file.d();
			forgetting = file.bool();
			forgetTimer = file.d();
			askTimer = file.d();
			total.load(file);;
			
		}

		void clear() {
			allocated = 0;
			frozen = 0;
			penalty = 0;
			forgetting = false;
			forgetTimer = 50;
			askTimer = -10;
			total.clear();
		}
		
		
	}
	
}
