package settlement.room.main.employment;

import java.io.Serializable;

import coopmod.CoopRuntime;
import game.GAME;
import game.faction.FACTIONS;
import game.faction.FResources.RTYPE;
import game.time.TIME;
import init.type.WGROUP.HTypeBits;
import init.type.WGROUP.HTypeBitsImp;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.RoomInstance.SecretEmployment;
import settlement.stats.STATS;
import snake2d.util.file.Alloc;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import snake2d.util.sets.ArrayListResize;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LISTE;
import util.text.D;

public final class RoomEmploymentIns extends SecretEmployment implements Serializable{
	
	public static CharSequence ¤¤Workload = "¤Work-load";
	public static CharSequence ¤¤WorkloadD = "How busy your employees are. If workload is low, it means some of the workers have nothing to do and are wasting their time. Possible causes of this are that there simply isn't enough work, or that the room is missing resources to work with. If workload is 100%, your workers might have too much to do and the room might need to have more workers allocated.";
	public static CharSequence ¤¤Proximity = "¤Commute";
	public static CharSequence ¤¤ProximityD = "The commute the employees need to undertake each day. Usually this is the distance to the employees home, but some workers need to leave the city during their workday, and these rooms are best placed near the city's exits.";
	
	public static CharSequence ¤¤ProximityInput = "¤Hauling";
	public static CharSequence ¤¤¤¤ProximityInputD = "Industry workers can fetch the input for the industry without problems if the distance is short. If long, then productivity will suffer.";
	
	
	static {
		D.ts(RoomEmploymentIns.class);
	}
	
	public final static int FETCH_FREE_TILES = 36;
	/**
	 * boostable average, walking speed, walking speed when carrying resource, walking speed when colliding
	 */
	private final static double FETCH_AVERAGE_SPEED = 4.5*0.6*(0.5 + 0.5*0.6)*0.55;	
	private final static int FETCH_FREE_SECONDS = (int) (2.0*FETCH_FREE_TILES/FETCH_AVERAGE_SPEED + 1.0);
	private final static int FETCH_FREE_MAX_COMP = (int) (2.0*(Room.MAX_DIM+10)/FETCH_AVERAGE_SPEED + 1);
	
	
	public static int DIST_AVERAGE_TIME;
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private short workersEmployed = 0;
	private short workersTarget = 0;
	private short workersTargetMax = 500;
	private final RoomInstance ins;
	
	private float eff = 0;
	private float effTot = 0;
	private byte EffLast = 100;

	private byte lastProximity = 100;
	private byte proxCount = 0;
	private float walkSeconds;
	
	private byte lastFetch = 100;
	private float surplousFetch = 0;
	private byte fetchCount = 0;
	private float fetchSeconds;
	private int fetchBonus;
	
	private static double EffLastI = 1.0/100;
	
	private static ArrayListResize<Humanoid> employees = new ArrayListResize<>(512, 512*16);
	private static int employeesI = -1;
	private static Object employeesO = null;
	
	private boolean active = false;
	
	private final HTypeBitsImp preferred = new HTypeBitsImp(false);
	
	public RoomEmploymentIns(RoomInstance ins){
		if (DIST_AVERAGE_TIME == 0)
			DIST_AVERAGE_TIME = (int) (TIME.workSeconds()*0.06);
		this.ins = ins;
	}
	
	public HTypeBits preffered() {
		return preferred;
	}
	
	public void prefferedSet(HTypeBits other) {
		remove();
		preferred.copy(other);
		add();
	}
	
	@Override
	protected void update(boolean active, boolean day, boolean auto, double seconds) {
		if (blueprint().employment() == null)
			return;
		
		if (workersTarget > max()) {
			neededSet(max());
		}
		
		if (day) {
			remove();
			for (RoomEquip w : SETT.ROOMS().employment.equip.ALL) {
				updateTools(w, true);
			}
			if (proxCount > 0) {
				double p = (walkSeconds-DIST_AVERAGE_TIME)/(TIME.workSeconds());
			
				
				walkSeconds = 0;
				proxCount = 0;
				p = 1-p;
				p = Math.ceil(p*100)/100.0;
				p = CLAMP.d(p, 0, 10);
				double d = p;
				
				lastProximity = (byte) (100*d);
			}
			
			if (fetchCount > 0) {
				double p = fetchSeconds/(TIME.workSeconds());
				fetchSeconds = 0;
				fetchCount = 0;
				p = 1-p;
				p = CLAMP.d(p, 0, 1);
				double d = 0.75*p + 0.25*lastFetch/100.0;
				
				lastFetch = (byte) (100*d);
			}
			
			if (effTot > 0) {
				double p = eff/effTot;
				eff = 0;
				effTot = 0;
				
				int next = CLAMP.i((int) (p*100), 0, 100);
				if (next > EffLast)
					;
				else
					next = (int) (0.5*EffLast + 0.5*next);
				double last = efficiency();
				EffLast = (byte) CLAMP.i(next, 0, 100);
				add();
				if (active && auto)
					adjustAuto(efficiency(), last);
			}else {
				add();
			}
			
			
			
		}
			
		
	}
	
	private void updateTools(RoomEquip w, boolean expire) {
		
		if (expire) {
			int exp = toolsToExpire(w);
			exp = CLAMP.i(exp, 0, tools(w));
			int am = tools(w)-exp;
			toolISet(w, toolI, am);
			toolISet(w, toolToExpireI, 0);
		}
		
		
		int nn = toolsNeeded(w);
		if (nn < 0) {
			int newAm = tools(w)+nn;
			SETT.THINGS().resources.create(ins.mX(), ins.mY(), w.resource, -nn);
			FACTIONS.player().res().inc(w.resource, RTYPE.EQUIPPED, nn);
			toolISet(w, toolI, newAm);
		}
		
		if (expire) {
			double am = tools(w);
			am *= w.degradePerDay;
			int a = (int) am;
			am -= a;
			if (RND.rFloat() < am)
				a++;
			toolISet(w, toolToExpireI, a);
		}
	}
	
	private void adjustAuto(double workload, double last) {
		double am = needed();
		if (am == 0) {
			neededSetAdjustWorkload(1);
		}else if (last >= 1.0 && workload >= 1.0 && needed()-employed() <= 1) {
			int aa = 1;
			if (workload >= 1.0) {
				aa = (int) Math.ceil(employed()/10.0);
			}
			
			neededSetAdjustWorkload(needed()+aa);

		}else if(am > 1 && last < 1.0 && workload < 1.0 && employed()-needed() <= 1) {
			double p = (workload+last)/2.0;
			int needed = (int) Math.ceil(p*(employed()+1.0));
			
			int fire = CLAMP.i(employed()-needed, 0, (int)Math.ceil(employed()/10.0));
			if (fire > 0)
				neededSetAdjustWorkload(needed()-fire);
			
		}	
	}

	@Override
	protected void activate(boolean active) {
		if (active == this.active) {
			return;
		}
		
		remove();
		
		if (!active) {
			
			
			EffLast = 100;
			eff = 0;
			effTot = 0;
			proxCount = 0;
			walkSeconds = 0;
			lastProximity = 100;
			lastFetch = 100;
			fetchSeconds = 0;
			fetchCount = 0;
		}
		this.active = active;
		
		add();
		
		
		
		
	}
	
	@Override
	protected void dispose() {
		if (blueprint().employment() != null) {
			if (employed() > 0) {
				int rem = 0;
				int added = 0;
				for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
					if (e instanceof Humanoid && STATS.WORK().EMPLOYED.get(((Humanoid) e).indu()) == ins) {
						STATS.WORK().EMPLOYED.set(((Humanoid) e), null);
						rem++;
						if (!e.isRemoved())
							added ++;
					}
				}
				if (employed() != 0) {
					throw new RuntimeException(rem + " " + added + " "  + employed());
				}
					
			}
			for (RoomEquip w : SETT.ROOMS().employment.equip.ALL) {
				updateTools(w, false);
			}
		}
	}

	private void remove() {
		if (this.active && blueprint().employment() != null) {
			blueprint().employment().register(this, -1);
		}
	}
	
	void add() {
		
		if (this.active && blueprint().employment() != null) {
			blueprint().employment().register(this, 1);
		}
	}

	
	public void maxSet(int max) {
		if (max < 0 || max > Short.MAX_VALUE)
			throw new RuntimeException(ins().name(0,0) + " " + max + " " + ins().mX() + " " + ins().mY());
		this.workersTargetMax = (short) max;
		if (workersTarget > this.workersTargetMax)
			neededSet(this.workersTargetMax);
	}
	
	public int max() {
		return workersTargetMax;
	}
	
	public final void reportWorkSuccess(int seconds, boolean success) {
		
		if (employed() <= 0)
			return;
		
		double v = (double)seconds/(employed());
		
		effTot += v;
		if (success)
			eff += v;
		
	}
	
	public void neededSet(int target) {
		
		target = CLAMP.i(target, 0, max());
		if (target != workersTarget) {
			remove();
			workersTarget = (short) target;
			add();
			CoopRuntime.roomEmploymentNeededChanged(ins, workersTarget);
		}
	}
	
	private void neededSetAdjustWorkload(int target) {
		
		target = CLAMP.i(target, 0, max());
		if (target != workersTarget) {
			remove();
			if (target > workersTarget) {
				double d = 0.75*workersTarget/target;
				EffLast = (byte) CLAMP.i(EffLast-(int)(d*100), 0, 100);
			}else {
				EffLast = 100;
			}
			workersTarget = (short) target;
			add();
		}
	}
	
	public int needed() {
		if (!ins.active())
			return 0;
		return workersTarget;
	}
	
	public int hardTarget() {
		return workersTarget;
	}
	
	public int target() {
		return (int) Math.ceil(workersTarget*blueprint().employment().getFill());
	}
	
	public final boolean isOverstaffed() {
		return (!ins().active() && workersEmployed > 0) || workersEmployed > target();
	}
	
	public final int employed() {
		return workersEmployed;
	}
	
	/**
	 * Only called from stat
	 * @param h
	 */
	public void employ(Humanoid h) {
		
		remove();
		workersEmployed ++;
		if (ins.blueprint().employment() != null) {
			ins.blueprint().employment().employ(h, 1);
		}
		add();
	}
	
	/**
	 * Only called from stat
	 * @param h
	 */
	public void fire(Humanoid h) {
		remove();
		workersEmployed --;
		if (ins.blueprint().employment() != null) {
			ins.blueprint().employment().employ(h, -1);
		}
		add();
	}
	
	
	public double efficiencySoFar() {
		if (effTot == 0)
			return EffLast*EffLastI;
		return eff/effTot;
		
	}
	
	public double efficiency() {
		return EffLast*EffLastI;
	}
	
	public final RoomInstance ins(){
		return ins;
	}
	
	public final RoomBlueprintIns<?> blueprint(){
		return (RoomBlueprintIns<?>)(ins.blueprint());
	}
	
	public LIST<Humanoid> employees(){
		return employees(this.ins);
	}
	
	public LIST<Humanoid> employees(LISTE<Humanoid> res){
		return employees(this.ins, res);
	}
	
	public static LIST<Humanoid> employees(RoomInstance ins){
		if (echeck(ins)) {
			return employees(ins, employees);
		}
		return employees;
	}
	
	public static LIST<Humanoid> employess(RoomBlueprint imp){
		if (echeck(imp)) {
			return employees(imp, employees);
		}
		return employees;
	}
	
	private static boolean echeck(Object o) {
		if (employeesI != GAME.updateI() || o != employeesO) {
			employeesI = GAME.updateI();
			employeesO = o;
			employees.clearSoft();
			return true;
		}
		return false;
	}
	
	public static LIST<Humanoid> employees(RoomInstance ins, LISTE<Humanoid> res){
		for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (!res.hasRoom())
				break;
			if (e instanceof Humanoid) {
				Humanoid a = (Humanoid) e;
				if (STATS.WORK().EMPLOYED.get(a) == ins) {
					res.add(a);
					if (!res.hasRoom())
						break;
					
				}
				
			}
		}
		return res;
	}
	
	public static LIST<Humanoid> employees(RoomBlueprint bb, LISTE<Humanoid> res){
		for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (e instanceof Humanoid) {
				Humanoid a = (Humanoid) e;
				if (STATS.WORK().EMPLOYED.get(a) != null && STATS.WORK().EMPLOYED.get(a).blueprint() == bb) {
					res.add(a);
					if (!res.hasRoom())
						break;
					
				}
				
			}
		}
		return res;
	}
	
	private int[] equipData = Alloc.ii(0);
	private static final int toolI = 0;
	private static final int toolToExpireI = 1;
	private static final int toolReservedI = 2;
	
	public int tools(RoomEquip w) {
		return toolI(w, toolI);
	}
	
	public int toolsTarget(RoomEquip w) {
		return employed()*w.target(ins.blueprintI().employment()).get();
	}
	
	public int toolsTargetMax(RoomEquip w) {
		return employed()*w.target(ins.blueprintI().employment()).max();
	}
	
	public double toolD(RoomEquip w) {
		double e = toolsTargetMax(w);
		if (e == 0)
			return 0;
		double t = CLAMP.i(tools(w), 0 , toolsTarget(w));
		return CLAMP.d(t/e, 0, 1);
	}
	
	public double toolsPerPerson(RoomEquip w) {
		double e = employed();
		if (e == 0)
			return 0;
		double t = CLAMP.i(tools(w), 0 , toolsTarget(w));
		return CLAMP.d(t/e, 0, toolsTargetMax(w));
	}
	
	public int toolsToExpire(RoomEquip w) {
		return toolI(w, toolToExpireI);
	}
	
	public int toolsNeeded(RoomEquip w) {
		return toolsTarget(w) - tools(w) + toolsToExpire(w) - toolReserved(w);
	}
	
	public int toolReserved(RoomEquip w) {
		return toolI(w, toolReservedI);
	}

	public void toolReserve(RoomEquip w, int am) {
		int aa = toolI(w, toolReservedI) + am;
		if (aa < 0)
			throw new RuntimeException();
		toolISet(w, toolReservedI, aa);
	}
	
	public void toolDeliver(RoomEquip w, int am) {
		if (am < 0)
			throw new RuntimeException();
		remove();
		FACTIONS.player().res().inc(w.resource, RTYPE.EQUIPPED, -am);
		int aa = toolI(w, toolI) + am;
		toolISet(w, toolI, aa);
		add();
	}
	
	private int toolI(RoomEquip w, int ii) {
		if (equipData.length != SETT.ROOMS().employment.equip.ALL.size()*3)
			equipData = Alloc.ii(SETT.ROOMS().employment.equip.ALL.size()*3);
		return equipData[w.index()*3+ii];
	}
	
	private void toolISet(RoomEquip w, int ii, int value) {
		if (equipData.length != SETT.ROOMS().employment.equip.ALL.size()*3)
			equipData = Alloc.ii(SETT.ROOMS().employment.equip.ALL.size()*3);
		equipData[w.index()*3+ii] = value;
	}

	public void reportWalkSeconds(int seconds) {
		if (employed() <= 0)
			return;
		
//		if (seconds < DIST_FREE_SECONDS) {
//			//fetchBonus += seconds;
//			seconds = 0;
//		}else {
//			//fetchBonus += DIST_FREE_SECONDS;
//			seconds -= DIST_FREE_SECONDS;
//		}
		
//		int max = (int) (employed()*TIME.secondsPerDay()*0.75);
//		
//		if (fetchBonus >= max)
//			fetchBonus = max;
		
		walkSeconds += (double)seconds/(employed());
		proxCount = 1;
	}
	
	public double proximity() {
		return lastProximity/100.0;
	}
	
	public double proximitySoFar() {
		if (proxCount == 0)
			return lastProximity/100.0;
		return 1.0 - (walkSeconds-DIST_AVERAGE_TIME)/(TIME.workSeconds());
	}
	
	public void reportFetchSeconds(int seconds) {
		if (employed() <= 0)
			return;
		
		if (seconds < FETCH_FREE_SECONDS) {
			fetchBonus += seconds;
			surplousFetch += FETCH_FREE_SECONDS-seconds;
			seconds = 0;
		}else {
			double over = seconds-FETCH_FREE_SECONDS;
			if (over > surplousFetch)
				over = surplousFetch;
			if (over > FETCH_FREE_MAX_COMP)
				over = FETCH_FREE_MAX_COMP;
			surplousFetch -= over;
			seconds -= over;
			fetchBonus += FETCH_FREE_SECONDS;
			seconds -= FETCH_FREE_SECONDS;
		}
		
		int max = (int) (employed()*TIME.secondsPerDay()*0.75);

		
		if (fetchBonus >= max)
			fetchBonus = max;
		
		fetchSeconds += (double)seconds/(employed());
		fetchCount = 1;
	}
	
	public double fetchProximity() {
		return lastFetch/100.0;
	}
	
	public double fetchProximitySoFar() {
		if (fetchCount == 0)
			return lastFetch/100.0;
		return 1.0 - fetchSeconds/(TIME.workSeconds());
	}
	
	public int fetchSecondsPerPerson() {
		if (employed() == 0)
			return 0;
		return fetchBonus/employed();
	}
	
	public int fetchBonus(int time) {
		int extra = time*4;
		extra = Math.min(extra, fetchBonus);
		fetchBonus -= extra;
		
		return (int) (time + extra*0.8);
	}
	
	public int fetchBonus() {
		return fetchBonus;
	}
	
	public boolean fetchBonusConsume(int seconds) {
		if (seconds <= fetchBonus) {
			fetchBonus-= seconds;
			return true;
		}
		return false;
	}
	
	public double totEfficiency() {
		double d = efficiency()*proximity();
		if (ins.blueprintI().employment().countInput())
			d *= fetchProximity();
		return d;
	}
	
	public boolean active() {
		return active;
	}
	
}
