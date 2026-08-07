package settlement.stats.colls;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.boosting.BOOSTABLES;
import game.faction.FACTIONS;
import game.faction.FResources.RTYPE;
import init.race.RACES;
import init.race.Race;
import init.race.home.RaceHomeClass;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.RES_AMOUNT;
import init.sprite.UI.UI;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.home.HOME;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import settlement.stats.StatsInit;
import settlement.stats.StatsInit.StatDisposable;
import settlement.stats.StatsInit.StatUpdatableI;
import settlement.stats.equip.WearableResource;
import settlement.stats.stat.STAT;
import settlement.stats.stat.STATData;
import settlement.stats.stat.STATFake;
import settlement.stats.stat.StatCollection;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.data.INT_O.INT_OE;
import util.info.INFO;
import util.keymap.RMapInt.RMapIntTwo;
import util.text.D;
import util.text.Dic;

public class StatsHome extends StatCollection{
	
	private static CharSequence ¤¤desc = "¤This subject's place of residence.";
	private static CharSequence ¤¤name = "Housing";
	private static CharSequence ¤¤descc = "Housing related stats.";
	
	static {
		D.ts(StatsHome.class);
	}
	public final StatHome GETTER;
	
	private final ArrayList<RMapIntTwo<HCLASS, Race>> targets = new ArrayList<RMapIntTwo<HCLASS,Race>>(RESOURCES.ALL().size());
	
//	private final int[][] targets = Alloc.i2(HCLASS.ALL.size()*RACES.all().size(), RESOURCES.ALL().size());
	private final LIST<StatFurniture> currents;
	public final STAT materials;
	
	private final ArrayList<StatFurniture> tmp = new ArrayList<StatsHome.StatFurniture>(8);

	
	public StatsHome(StatsInit init){
		super(init, "HOME", ¤¤name, ¤¤descc);
		GETTER = new StatHome(init);
		
		for (int i = 0; i < RESOURCES.ALL().size(); i++) {
			targets.add(new RMapIntTwo<>(HCLASSES.MAP(), RACES.map()));
		}
		
		ArrayList<StatFurniture> cc = new ArrayList<>(8);
		while(cc.hasRoom()) {
			StatFurniture ss = new StatFurniture(cc.size(), init);
			cc.add(ss);
		}

		currents = cc;
		
		materials = new STATFake("FURNITURE", init) {
			
			@Override
			protected double getDD(HCLASS s, Race r, int daysBack) {
				double am = 0;
				for (StatFurniture ss : currents)
					am += ss.current.data(s).get(r);
				double div = pdivider(s, r, daysBack);
				if (div == 0)
					return 0;
				return am/div;
			}
			
			@Override
			public double induGet(Induvidual t) {
				double am = 0;
				for (StatFurniture ss : currents)
					am += ss.current.indu().get(t);
				
				double max = 0;
				RaceHomeClass cc = t.race().home().clas(t.clas());
				for (RES_AMOUNT a : cc.resources())
					max += a.amount();
				if (max == 0)
					return 1.0;
				return am/max;
			}
			@Override
			public int dataDivider() {
				return 1;
			}
			
			@Override
			public int pdivider(HCLASS c, Race r, int daysback) {
				if (r == null) {
					int am = 0;
					for (Race rr : RACES.all()) {
						am += pdivider(c, rr, daysback);
					}
					return am;
				}
				if (r == null || r.home() == null)
					throw new RuntimeException(c + " "+r + " " + (r == null ? null : r.home()));
				RaceHomeClass cc = r.home().clas(c);
				if (cc == null)
					return STATS.POP().POP.data(c).get(r);
				return cc.amountTotal()*STATS.POP().POP.data(c).get(r);
			}
		};
		materials.info().icon = UI.icons().m.furniture;
		
		
		init.savers.put("HOME_F_TARGETS", new SAVABLE() {
			
			@Override
			public void save(FilePutter file) {
				RESOURCES.map().saver().save(targets, file);
			}
			
			@Override
			public void load(FileGetter file) throws IOException {
				RESOURCES.map().loader().load(targets, file);
			}
			
			@Override
			public void clear() {
				for (RMapIntTwo<HCLASS, Race> t : targets)
					t.clear();
			}
		});
		
		init.updatable.add(new StatUpdatableI() {
			
			@Override
			public void update16(Humanoid h, int updateR, boolean day, int updateI) {
				
				double wearRateI = rate(h.indu()) / 16.0;
				Induvidual i = h.indu();
				
				HOME home = GETTER.get(h.indu(), this);
				if (home != null) {
					wearRateI *= 1 + CLAMP.d((1-home.isolation())*2, 0, 1);
				}
				
				if (RND.rFloat() < wearRateI) {
					for (int ri = 0; ri < currents.size(); ri++) {
						currents.get(ri).update(i);	
					}
					
				}
				
				
			}
		});
		
//		new GAME_LOAD_FIXER() {
//			
//			@Override
//			protected void fix() {
//				new HomeFixer().fixAll();
//			}
//		};
	}
	
	public LIST<StatFurniture> getTmp(Induvidual i){
		return getTmp(i.clas(), i.race());
	}
	
	public StatFurniture furniture(int i){
		return currents.get(i);
	}
	
	public LIST<StatFurniture> getTmp(HCLASS cl, Race ra){
		LIST<RES_AMOUNT> li = ra.home().clas(cl).resources();
		tmp.clearSloppy();
		for (int i = 0; i < li.size(); i++) {
			tmp.add(currents.get(i));
		}
		return tmp;
	}
	
	public double rate(Induvidual i) {
		return CLAMP.d(0.25/(BOOSTABLES.CIVICS().FURNITURE.get(i)), 0, 1);
	}
	
	public double rate(HCLASS cl, Race ra) {
		return CLAMP.d(0.25/(BOOSTABLES.CIVICS().FURNITURE.get(HCLASS_RACE.clP(ra, cl))), 0, 1);
	}
	
	public int current(Humanoid h, int rI) {
		return currents.get(rI).current.indu().get(h.indu());
	}
	
	public int current(HCLASS c, Race type, int resI) {
		if (type == null) {
			RES_AMOUNT ra = RACES.res().homeResMax(c).get(resI);
			if (ra == null)
				return 0;
			int m = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				int i = 0;
				for (RES_AMOUNT rr : RACES.all().get(ri).home().clas(c).resources()) {
					
					
					if (rr.resource() == ra.resource()) {
						m += currents.get(i).current.data(c).get(RACES.all().get(ri));
					}
					i++;
				}
			}
			return m;
		}
		return currents.get(resI).current.data(c).get(type);
	}
	
	public int needed(HCLASS c, Race type, int resI) {
		if (type == null) {
			RES_AMOUNT ra = RACES.res().homeResMax(c).get(resI);
			int m = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				type = RACES.all().get(ri);
				m += type.home().clas(c).amount(ra.resource())*GETTER.stat().data(c).get(type);
			}
			return m;
		}
		return type.home().clas(c).resources().get(resI).amount()*GETTER.stat().data(c).get(type);
	}
	

	
	public int max(HCLASS c, Race type, RESOURCE res) {
		return type.home().clas(c).amount(res);
	}
	
	public int target(Humanoid h, RESOURCE res) {
		return target(h.indu().clas(), h.indu().race(), res);
	}
	
//	public boolean shouldFetch(Humanoid h, int ri) {
//		int tar = target(h, h.race().home().clas(h.indu().clas()).resources().get(ri).resource());
//		int c = current(h, ri);
//		if (c < tar)
//			return true;
//		return false;
//	}
	
//	public void fetchResource(Humanoid h, RESOURCE res) {
//		int ii = 0;
//		FACTIONS.player().res().inc(res, RTYPE.FURNISH, -1);
//		for (RES_AMOUNT aa : h.race().home().clas(h.indu().clas()).resources()) {
//			if (res == aa.resource()) {
//				int tar = target(h, h.race().home().clas(h.indu().clas()).resources().get(ii).resource());
//				int c = currents.get(ii).current.indu().get(h.indu());
//				if (c < tar)
//					currents.get(ii).current.indu().inc(h.indu(), 1);
//				return;
//			}
//			ii++;
//		}
//		GETTER.get(h, this).resUpdate().done();
//		
//	}
	
	public int target(HCLASS c, Race type, RESOURCE res) {
		if (type == null) {
			int m = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				m = Math.max(m, target(c, r, res));
			}
			return m;
		}
		return CLAMP.i(targets.get(res.index()).get(c).get(type), 0, max(c, type, res));
	}
	
	public void dump(Humanoid a) {
		HOME home = STATS.HOME().GETTER.get(a, this);
		Induvidual i = a.indu();
		for (StatFurniture e : STATS.HOME().getTmp(i)) {
			int toDump = -e.needed(a.indu());
			if (toDump > 0) {
				e.inc(i, -toDump);
				if (home != null)
					SETT.THINGS().resources.create(home.serviceX(), home.serviceY(), e.resource(i), toDump);
				else
					SETT.THINGS().resources.create(a.physics.tileC(), e.resource(i), toDump);
					
			}
		}
	}
	
	public void targetSet(int target, HCLASS c, Race type, RESOURCE res) {
		if (type == null) {
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				targetSet(target, c, r, res);
			}
			return;
		}
		target = CLAMP.i(target, 0, max(c, type, res));
		targets.get(res.index()).get(c).set(type, target);
		CoopRuntime.homeFurnitureTargetChanged(res, c, type, target);
	}
	
	public final class StatFurniture implements WearableResource {
		
		private final int resI;
		public final STATData current;
		private final INT_OE<Induvidual> counter;
		
		StatFurniture(int resI, StatsInit init){
			this.resI = resI;
			current = new STATData(null, init, init.count .new DataNibble("HOME_FURNITURE"+resI));
			counter = init.count.new DataByte("HOME_FCOUNTER"+resI);
		}

		void update(Induvidual i) {
			int am = current.indu().get(i)-(counter.get(i)>>4);
			if (am > 0)
				counter.inc(i, am);
		}
		
		@Override
		public RESOURCE resource(Induvidual i) {
			LIST<RES_AMOUNT> li = i.race().home().clas(i.clas()).resources();
			if (resI >= li.size())
				return null;
			return li.get(resI).resource();
		}

		@Override
		public void wearOut(Induvidual i) {
			
			int c = counter.get(i);
			int am = c >> 4;
			c &= 0x0F;
			counter.set(i, c);
			if (am == 0)
				return;
			
			am = CLAMP.i(am, 0, current.indu().get(i));
			current.indu().inc(i, -am);
		}

		@Override
		public int max(Induvidual i) {
			LIST<RES_AMOUNT> li = i.race().home().clas(i.clas()).resources();
			if (resI >= li.size())
				return 0;
			return li.get(resI).amount();
		}

		@Override
		public int target(Induvidual i) {
			if (!GETTER.has(i))
				return 0;
			RESOURCE res = resource(i);
			if (res == null)
				return 0;
			return StatsHome.this.target(i.clas(), i.race(),res);
		}

		@Override
		public double wearPerYear(Induvidual i) {
			return CLAMP.d(0.5/(BOOSTABLES.CIVICS().FURNITURE.get(i)), 0, 1);
		}

		@Override
		public void set(Induvidual i, int am) {
			int old = current.indu().get(i);
			if (am != old) {
				
				current.indu().set(i, CLAMP.i(am, 0, max(i)));
				if (i.player() && i.added()) {
					FACTIONS.player().res().inc(resource(i), RTYPE.FURNISH, old-current.indu().get(i));
				}
			}
			
		}

		@Override
		public int needed(Induvidual i) {
			int am = target(i)-get(i) + (counter.get(i)>>4);
			if (am < 0) {
				wearOut(i);
				am = target(i)-get(i);
				if (am < 0) {
					int c = counter.get(i)&0x0F;
					if (RND.rInt(16) < c)
						current.indu().inc(i, -1);
					counter.set(i, 0);
					return target(i)-get(i);
				}
			}
			return am;
		}
		
		
		@Override
		public int get(Induvidual i) {
			return current.indu().get(i);
		}
		
	}
	
	public final static class StatHome implements StatDisposable{
		
		private final INT_OE<Induvidual> xx; 
		private final INT_OE<Induvidual> yy; 
		private final STATData stat;
		public final STAT hasSearched;
		public final INFO info;
		
		StatHome(StatsInit init){
			info = new INFO(Dic.¤¤Home, ¤¤desc);
			xx = init.count.new DataShort("HOME_XX");
			yy = init.count.new DataShort("HOME_YY");
			
			INT_OE<Induvidual> b = new INT_OE<Induvidual>(){

				@Override
				public int get(Induvidual t) {
					return xx.get(t) != 0 ? 1:0;
				}

				@Override
				public int min(Induvidual t) {
					return 0;
				}

				@Override
				public int max(Induvidual t) {
					return 1;
				}

				@Override
				public void set(Induvidual t, int i) {
					
				}
				
			};
			stat = new STATData("HOUSED", "HOME_HOUSE", init, b);
			stat.info().icon = SETT.ROOMS().HOME.icon.medium;
			
			
			init.disposable.add(this);
			
			hasSearched = new STATData(null, init, init.count.new DataBit("HOME_SEARCH"));

		}
		
		public boolean has(Humanoid h) {
			return (xx.get(h.indu()) != 0);
		}
		
		public boolean has(Induvidual h) {
			return (xx.get(h) != 0);
		}
		
		public HOME get(Induvidual f, Object user) {
			if (xx.get(f) == 0)
				return null;
			int tx = xx.get(f)-1;
			int ty = yy.get(f)-1;
			return HOME.get(tx, ty);
		}
		
		private Coo coo = new Coo();
		
		public COORDINATE hCoo(Humanoid f) {
			if (xx.get(f.indu()) == 0)
				return null;
			coo.set(xx.get(f.indu())-1, yy.get(f.indu())-1);
			return coo;
		}
		
		public HOME get(Humanoid h, Object user) {
			return get(h.indu(), user);
		}
		
		@Override
		public void dispose(Humanoid h) {
			STATS.HOME().dump(h);
			set(h, null);
		}
		
		public void set(Humanoid h, HOME home) {
			if (h.isRemoved() || SETT.ENTITIES().getByID(h.id()) != h)
				throw new RuntimeException(h.isRemoved() + " " +  (SETT.ENTITIES().getByID(h.id()) != h));
			Induvidual f = h.indu();
			hasSearched.indu().set(f, 0);
			stat.removeH(f);
			
			HOME ho = get(h.indu(), this);
			if (ho != null) {
				ho.vacate(h);
			}
			
			if (home != null) {
				
				xx.set(f, home.serviceX()+1);
				yy.set(f, home.serviceY()+1);
				home.occupy(h);
				
			}else {
				xx.set(f, 0);
				yy.set(f, 0);
			}
			
			stat.addH(f);
			
		}
		
		public STAT stat() {
			return stat;
		}
		
	}
	
}
