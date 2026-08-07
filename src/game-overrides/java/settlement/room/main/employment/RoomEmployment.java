package settlement.room.main.employment;

import static settlement.main.SETT.ROOMS;

import java.io.IOException;
import java.util.Arrays;

import coopmod.CoopRuntime;
import game.GameDisposable;
import game.time.TIME;
import init.race.RACES;
import init.type.WGROUP;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.util.RoomInitData;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayListResize;
import snake2d.util.sets.INDEXED;
import util.data.INT.INTE;
import util.data.INT_O.INT_OE;
import util.statistics.HISTORY_INT;
import util.statistics.HistoryInt;

public final class RoomEmployment extends RoomEmploymentSimple implements INDEXED{

	static ArrayListResize<RoomEmployment> WORK = new ArrayListResize<>(10, 512);
	static {
		new GameDisposable() {
			@Override
			protected void dispose() {
				WORK.clear();
			}
		};
	}
	
	private final int index = WORK.add(this);
	private final HistoryInt history = new HistoryInt(32, TIME.days(), true);
	public final Target target = new Target(this);
	public final Priority priority = new Priority(this);
	public final GRoupInt priorities = new GRoupInt(0, 5) {
		
		@Override
		public void set(WGROUP t, int i) {
			int o = get(t);
			super.set(t, i);
			if (o != get(t)) {
				SETT.ROOMS().employment.employer.updateAll();
				CoopRuntime.roomEmploymentGroupPriorityChanged(RoomEmployment.this, t, get(t));
			}
		};
	};
	
	public RoomEmployment(RoomBlueprintIns<?> p, RoomInitData init) {
		super("WORK", p, init);
		
	}
	
	
	public HISTORY_INT history() {
		return history;
	}
	
	@Override
	void employ(Humanoid h, int delta) {
		SETT.ROOMS().employment.history.inc(-employed());
		super.employ(h, delta);
		ROOMS().employment.changeCurrent(delta, WGROUP.get(h.indu()));
		SETT.ROOMS().employment.history.inc(employed());
		history.set(employed());
	}
	
	@Override
	void register(RoomEmploymentIns ins, int delta) {
		ROOMS().employment.changeNeeded(blueprint(), -workersNeeded);
		super.register(ins, delta);
		ROOMS().employment.changeNeeded(blueprint(), workersNeeded);
	}
	
//	@Override
//	void fixFuckup() {
//		ROOMS().employment.changeNeeded(blueprint(), -workersNeeded);
//		super.fixFuckup();
//	}
	

	@Override
	void save(FilePutter file) {
		super.save(file);
		target.save(file);
		priority.save(file);
		priorities.save(file);
		history.save(file);
	}

	@Override
	void load(FileGetter file) throws IOException {
		super.load(file);
		target.load(file);
		priority.load(file);
		priorities.load(file);
		history.load(file);
	}

	@Override
	void clear() {
		super.clear();
		target.clear();
		priority.clear();
		priorities.clear();
		history.clear();
		setPrioOnSkill();
		
	}
	
	public void setPrioOnSkill() {
		for (WGROUP g : WGROUP.all()) {
			setPrioOnSkill(g);
		}
	}
	
	public void setPrioOnFullfillment() {
		for (WGROUP g : WGROUP.all()) {
			setPrioOnFullfillment(g);
		}
	}
	
	public void setPrioOnSkill(WGROUP g) {
		int p = CLAMP.i((int)Math.round(RACES.boosts().getNorSkill(g.race, this)*priorities.max), 1, priorities.max);
		priorities.set(g, p);
	}
	
	public void setPrioOnFullfillment(WGROUP g) {
		int p = CLAMP.i((int)Math.round(g.race.pref().getWork(this)*priorities.max), 1, priorities.max);
		priorities.set(g, p);
	}

	@Override
	public int index() {
		return index;
	}
	
	@Override
	public double efficiency() {
		// TODO Auto-generated method stub
		return super.efficiency();
	}
	
	public static class Priority implements INTE{
		
		final RoomEmployment p;
		private int prio = 10;

		public Priority(RoomEmployment p) {
			this.p = p;
		}
		
		void save(FilePutter file) {
			file.i(prio);
		}

		void load(FileGetter file) throws IOException {
			prio = file.i();
		}

		void clear() {
			prio = 10;
		}
		
		@Override
		public int max() {
			return 30;
		};
		
		@Override
		public int min() {
			return 0;
		}

		@Override
		public int get() {
			return prio;
		}

		@Override
		public void set(int i) {
			i = CLAMP.i(i, min(), max());
			if (prio != i) {
				prio = i;
				SETT.ROOMS().employment.employer.updateAll();
				CoopRuntime.roomEmploymentPriorityChanged(p, prio);
			}
		};
	}
	
	public static class GRoupInt implements INT_OE<WGROUP>{
		
		private int total;

		private final int[] racePrio = Alloc.ii(WGROUP.all().size());
		private final int min;
		private final int max;
		
		public GRoupInt(int min, int max) {
			this.min = min;
			this.max = max;
		}
		
		void save(FilePutter file) {
			file.is(racePrio);
			file.i(total);
		}

		void load(FileGetter file) throws IOException {
			file.is(racePrio);
			total = file.i();
		}

		void clear() {
			Arrays.fill(racePrio, 0);
			total = 0;
		}
		
		@Override
		public int max(WGROUP t) {
			return max;
		};
		
		@Override
		public int min(WGROUP t) {
			return min;
		}

		@Override
		public int get(WGROUP t) {
			if (t == null)
				return total;
			return racePrio[t.index()];
		}

		@Override
		public void set(WGROUP t, int i) {
			i = CLAMP.i(i, min(t), max(t));
			if (racePrio[t.index()] != i) {
				
				total -= racePrio[t.index()];
				racePrio[t.index()] = i;
				total += racePrio[t.index()];
				
			}
		};
		
	}
	
	public static class Target {
		
		private int target;
		private final int[] perGroup = Alloc.ii(WGROUP.all().size());

		
		public Target(RoomEmployment p) {

		}
		
		public int get() {
			return target;
		}
		
		public int group(WGROUP g) {
			return perGroup[g.index()];
		}
		
		void clear() {
			target = 0;
			for (WGROUP e : WGROUP.all()) {
				SETT.ROOMS().employment.changeTarget(-perGroup[e.index()], e);
				perGroup[e.index()] = 0;
			}
			
		}
		
		void add(WGROUP g, int amount) {
			SETT.ROOMS().employment.changeTarget(amount, g);
			target+= amount;
			perGroup[g.index()] += amount;
		}
		

		
		void save(FilePutter file) {
			file.i(target);
			file.is(perGroup);
		}

		void load(FileGetter file) throws IOException {
			target = file.i();
			file.is(perGroup);
		}

	
	}

	
}
