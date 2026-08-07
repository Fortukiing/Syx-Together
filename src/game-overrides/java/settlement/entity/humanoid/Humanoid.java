package settlement.entity.humanoid;

import static settlement.main.SETT.GRASS;
import static settlement.main.SETT.IN_BOUNDS;
import static settlement.main.SETT.PATH;
import static settlement.main.SETT.ROOMS;

import java.io.IOException;

import coopmod.CoopLog;
import coopmod.CoopRuntime;
import game.GAME;
import game.battle.div.Div;
import game.battle.thread.trajectory.BattleTrajectories;
import game.boosting.BOOSTABLES;
import game.nobility.Noble;
import game.time.TIME;
import init.constant.C;
import init.constant.Config;
import init.race.Race;
import init.settings.S;
import init.type.CAUSE_ARRIVE;
import init.type.CAUSE_ARRIVES;
import init.type.CAUSE_LEAVE;
import init.type.CAUSE_LEAVES;
import init.type.CRIME_PUNISHMENTS;
import init.type.HTYPE;
import init.type.HTYPES;
import settlement.entity.ECollision;
import settlement.entity.ENTITY;
import settlement.entity.ResolverTile;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.HAI;
import settlement.entity.humanoid.ai.types.prisoner.AIModule_Prisoner;
import settlement.entity.humanoid.spirte.HSprite;
import settlement.main.SETT;
import settlement.path.AVAILABILITY;
import settlement.room.main.RoomInstance;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import settlement.thing.DRAGGABLE;
import snake2d.LOG;
import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import util.gui.misc.GBox;
import util.info.GFORMAT;
import util.rendering.ShadowBatch;
import util.text.Dic;
import view.main.VIEW;

public class Humanoid extends ENTITY implements DRAGGABLE{
	
	public final static int WORK_TICKS = 16*safeWorkHours()/safeHoursPerDay();
	public final static double WORK_PER_DAY = WORK_TICKS/16.0;
	public final static double WORK_PER_DAYI = 1.0/WORK_PER_DAY;
	
	final AIManager ai;
	private final Induvidual induvidual;
	public byte spriteoff = (byte) RND.rInt(255);
	public float spriteTimer = 0;
	public float relTimer = 0;
	
	
	private float updateTimer = (float) (RND.rFloat()*HumanoidResource.updateDelta);
	private byte dayOfYear = (byte) TIME.days().bitCurrent();

	private byte updateI = (byte) RND.rInt(255);
	private byte dayRan = (byte) RND.rInt(256);;
	public float moveBonus;
	public boolean inWater;
	private short nobleI = -1;
	private byte leaveCause = -1;
	private byte mark;
	public static int TARGET_MAX = 10;

	private static int safeHoursPerDay() {
		try {
			if (Config.sett() != null && Config.sett().hoursPerDay > 0)
				return Config.sett().hoursPerDay;
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read configured hours per day; using the vanilla fallback.");
		}
		return 24;
	}

	private static int safeSecondsPerHour() {
		try {
			if (Config.sett() != null && Config.sett().secondsPerHour > 0)
				return Config.sett().secondsPerHour;
		} catch (RuntimeException e) {
			CoopLog.warn("Could not read configured seconds per hour; using the vanilla fallback.");
		}
		return 48;
	}

	private static int safeWorkHours() {
		int hours = safeHoursPerDay();
		return hours*8/16;
	}
	
	public Humanoid(int x, int y, Race spec, HTYPE type, CAUSE_ARRIVE cause){
		
		induvidual = new Induvidual(type, spec);
		
		physics.initPosition(x, y, spec.physics.hitBoxsize(), spec.physics.hitBoxsize()); 
		
		physics.setRestitution(0.2f);
		physics.setHeight(spec.physics.height() + RND.rFloat0(spec.physics.height()/4.0f));
		
		physics.setMass(BOOSTABLES.PHYSICS().MASS.get(induvidual));
		speed.accelerationInit(BOOSTABLES.PHYSICS().ACCELERATION.get(induvidual)*C.TILE_SIZE);
		speed.magnitudeMaxInit(BOOSTABLES.PHYSICS().SPEED.get(induvidual)*C.TILE_SIZE);
		speed.turnRandom();
		
		ai = new AIManager(this);
		
		initTile(-1, -1);
		
		add(false);
		
		if (isRemoved()) {
			//removeAction();
			return;
		}
		
		((HumanoidResource) induvidual).add(this, cause);
		((HumanoidResource) ai).add(this, cause);
		
		
		if (induvidual.player())
			GAME.count().SUBJECTS.inc(1);

		if (cause == CAUSE_ARRIVES.IMMIGRATED()) {
			STATS.POP().TYPE.IMMIGRANT.set(induvidual);
		}

		STATS.REL().setHumanoid(this);
		
	}
	
	public Humanoid(FileGetter file) throws IOException{
		super.load(file);
		ai = new AIManager(this, file);
		induvidual = new Induvidual(file);
		spriteoff = file.b();
		spriteTimer = file.f();
		relTimer = file.f();
		updateTimer = file.f();
		dayOfYear = file.b();
		updateI = file.b();
		dayRan = file.b();
		moveBonus = file.f();
		inWater = file.bool();
		nobleI = file.s();
		leaveCause = file.b();
	}
	
	@Override
	protected void save(FilePutter file) {
		super.save(file);
		ai.save(file);
		induvidual.save(file);
		file.b(spriteoff);
		file.f(spriteTimer);
		file.f(relTimer);
		file.f(updateTimer);
		file.b(dayOfYear);
		file.b(updateI);
		file.b(dayRan);
		file.f(moveBonus);
		file.bool(inWater);
		file.s(nobleI);
		file.b(leaveCause);
	}
	
	@Override
	public void render(Renderer r, ShadowBatch s, float ds, int offsetX, int offsetY) {

		int x = body().x1()+offsetX - race().appearance().off;
		int y = body().y1()+offsetY - race().appearance().off;
		
		x += -4 + (spriteoff & 0b0111);
		y += -4 + ((spriteoff>>3) & 0b0111);
		HSprite sprite = ai.sprite(this);
		sprite.render(this, ai, r, s, ds, x, y);
		
		if (division() != null && division().settings().mustering() && (id()&0x1F) == 0) {
			SETT.BATTLE().bannerR.regBannerman(this);
		}
		
		
	}
	
	@Override
	public void renderSimple(Renderer r, ShadowBatch shadows, float ds, int offsetX, int offsetY) {
		int x = body().x1()+offsetX - race().appearance().off;
		int y = body().y1()+offsetY - race().appearance().off;
		
		x += -4 + (spriteoff & 0b0111);
		y += -4 + ((spriteoff>>3) & 0b0111);
		HSprite sprite = ai.sprite(this);
		sprite.renderSimple(this, ai, r, shadows, ds, x, y);
		if (division() != null && division().settings().mustering() && (id()&0x1F) == 0) {
			SETT.BATTLE().bannerR.regBannerman(this);
		}
//		if (hovered && GSettings.get().devMode.isOn()) {
//			ai.path().render(r, offsetX, offsetY);
//		}
	}

	public Race race(){
		return induvidual.race();
	}
	
	private void initTile(int ox, int oy) {
		inWater = SETT.ENTITIES().submerged.is(physics.tileC().x(), physics.tileC().y());
		initSpeed();
	}
	
	private void initSpeed() {
		moveBonus = (float) (ai.resourceA() > 0 ? 0.6 : 1);
		
		AVAILABILITY a = PATH().availability.get(physics.tileC());
		if (a != null)
			moveBonus *= (a.movementSpeed * (1.0 - 0.5*STATS.NEEDS().INJURIES.COUNT.indu().getD(indu())));
	}
	
	@Override
	protected boolean update(double ds) {
		
		if (CoopRuntime.remoteControlsHumanoid(this))
			return CoopRuntime.remoteHumanoidUpdate(this, ds);
		
		int cx = body().cX()>>C.SCALE;
		int cy = body().cY()>>C.SCALE;
		
		int ox = physics.tileC().x();
		int oy = physics.tileC().y();
//		int nx = ((int)ai.X) >> C.T_SCROLL;
//		int ny = ((int)ai.Y) >> C.T_SCROLL;
//		AISUB s = ai.plansub();
//		AISTATE ss = ai.state();
//		int sb = ai.subByte;
		
		
		physics.move(this, speed, ds*moveBonus);
		
		((HumanoidResource) ai).update(this, ds);

		if (ResolverTile.collide(this)) {
		}
		if (isRemoved())
			return false;
		
		if (!physics.tileC().isSameAs(ox,oy)) {
			initTile(ox, oy);
			if (RND.oneIn(18) && !ROOMS().map.is(physics.tileC())) {
				int x = physics.tileC().x() + RND.rInt0(9)/8;
				int y = physics.tileC().y() + RND.rInt0(9)/8;
				if (IN_BOUNDS(x, y) && !ROOMS().map.is(x,y) && SETT.FLOOR().getter.get(x, y) == null) {
					SETT.TILE_MAP().growth.tear(x, y);
//					if (TERRAIN().clearing.get(x, y).isEasilyCleared())
//						TERRAIN().clearing.get(x, y).clearAll(x, y);
					GRASS().currentI.increment(x, y, -1);
				}
			}
		}


		if (AIManager.dead != null) {
			kill(false, AIManager.dead);
			AIManager.dead = null;
			return false;
		}
		
		int uS = (int) updateTimer;
		updateTimer -= ds;
		int uSN = (int) updateTimer;
		
		if (uS != uSN) {
			if (!inWater && (uS & 0b001) == 0) {
				if (STATS.NEEDS().INJURIES.COUNT.indu().get(induvidual) > RND.rInt(STATS.NEEDS().INJURIES.COUNT.indu().max(induvidual))) {
					SETT.THINGS().gore.bleed(this, race().appearance().colors.blood);
				}
			}
			if ((uS & 0b0111) == 0) {
				HEvent.Handler.exhaust(this);
			}else if((uS & 0b0111) == 1) {
				HEvent.Handler.checkMorale(this);
			}
			Div d = division();
			if (d != null && d.settings().mustering()) {
				BattleTrajectories.register(this, d);
			}
			physics.setMass(BOOSTABLES.PHYSICS().MASS.get(induvidual));
			speed.accelerationInit(Math.max(0.2, BOOSTABLES.PHYSICS().ACCELERATION.get(induvidual))*C.TILE_SIZE);
			speed.magnitudeMaxInit(Math.max(0.2, BOOSTABLES.PHYSICS().SPEED.get(induvidual))*C.TILE_SIZE);
		}
		
		if (updateTimer <= 0) {
			boolean day = false;
			
			if (dayOfYear != TIME.days().bitCurrent()) {
				
				int now = (int) (TIME.days().bitPartOf()*HumanoidResource.updatesPerDay);
				int db = getDayBreakTick();
				if (now >= db) {
					day = true;
					dayOfYear = (byte) TIME.days().bitCurrent();
				}
			}
			
			updateTimer += HumanoidResource.updateDelta;
			
			updateI++;
			if (day)
				dayRan = (byte) RND.rInt(256);
			((HumanoidResource) ai).update(this, updateI, day);
			if (isRemoved())
				return true;
			((HumanoidResource) induvidual).update(this, updateI&0x0FF, day);
			mark -= 1;
			mark = (byte) CLAMP.i(mark, 0, 100);
			
			
		}
		
		
		if (AIManager.dead != null) {
			kill(AIManager.deadGore, AIManager.dead);
			AIManager.dead = null;
		}else if ((body().cX()>>C.SCALE != cx || body().cY()>>C.SCALE != cy)) {
			Div d = division();
			if (d != null)
				d.reporter.reportPosition(divSpot(), body().cX(), body().cY());
		}
		return true;
	}

	public double partOfDay() {
		int now = (int) (TIME.days().bitPartOf()*HumanoidResource.updatesPerDay);
		int db = getDayBreakTick();
		if (now == db) {
			return 1.0;
		}else if (now < db) {
			return  1-0-HumanoidResource.updatesPerDayI*(db-now);
		}else {
			return 1.0-HumanoidResource.updatesPerDayI* (HumanoidResource.updatesPerDay-(now-db));
		}
	}
	
	@Override
	public void hover(GBox text) {
		VIEW.s().ui.subjects.hoverInfo(this, text);
		
		if (S.get().developer) {
			text.NL();
			text.add(GFORMAT.f(text.text(), ai.stateTimer));
			text.NL();
			text.text(""+tc());
		}
		
		
	}
	
	
	@Override
	public void click() {
		VIEW.s().ui.subjects.showSingle(this);
	}
	
	@Override
	public boolean canBeClicked() {
		return VIEW.s().ui.subjects.canShow(this);
	}
//	
//	static double de,da;
//	static int daAm;
//	
	public boolean inflictDamage(double d, CAUSE_LEAVE cause) {
		
		leaveCause = (byte) cause.index();
		
		if (d <= 0)
			return false;
		
		if (d > 0.1) {
			SETT.THINGS().gore.bleed(this, race().appearance().colors.blood);
		}
		
		if (d > 0.2) {
			SETT.THINGS().gore.cloud(this, race().appearance().colors.blood);
		}
		
		if (d*RND.rFloat() > 1) {
			SETT.HUMANOIDS().sound.rnd(this);
			SETT.THINGS().gore.explode(this, race().appearance().colors.blood);
			STATS.NEEDS().INJURIES.COUNT.indu().setD(induvidual, 1.0);
			if (division() != null) {
				GAME.ARMIES().factors.reportCasulty(division());
			}
			
			kill(true, CAUSE_LEAVES.ALL().get(leaveCause));
			return false;
		}else {
			
			int m = STATS.NEEDS().INJURIES.COUNT.indu().max(induvidual);
			d *= m;
			int ii = (int) d;
			
			if (RND.rFloat() < (d-ii))
				ii++;
			
			int am = STATS.NEEDS().INJURIES.COUNT.indu().get(induvidual) + ii;
			
			if (am >= m) {
				STATS.NEEDS().INJURIES.COUNT.indu().inc(induvidual, (int) (d*RND.rFloat()));
				if (division() != null) {
					GAME.ARMIES().factors.reportCasulty(division());
				}
				kill(true, CAUSE_LEAVES.ALL().get(leaveCause));
				return false;
				
				
			}else {
				STATS.NEEDS().INJURIES.COUNT.indu().set(induvidual, am);
			}
		}
		
		
		
		return true;
	}
	
	public void kill(boolean gore, CAUSE_LEAVE cause) {
		if (isRemoved())
			return;

		if (indu().hType() == HTYPES.PRISONER()) {
			if (cause.leavesCorpse && AIModule_Prisoner.punishment(this, ai) == CRIME_PUNISHMENTS.STOCKS())
				STATS.LAW().punish(indu(), CRIME_PUNISHMENTS.EXECUTE());
			if (cause == CAUSE_LEAVES.EXECUTED()) {
				GAME.count().EXECUTIONS.inc(1);
				STATS.LAW().punish(indu(), CRIME_PUNISHMENTS.EXECUTE());
			}
		}
		
		
//		if (indu().hType() == HTYPES.ENEMY()) {
//			GAME.count().ENEMIES_KILLED.inc(1);
//		}
		
//		if (cause == CAUSE_LEAVE.SLAYED)
//			ai.debug(this, "");
		
		STATS.POP().COUNT.reg(indu(), cause);
		
		if (cause.leavesCorpse) {
			if (speed.isZero()) {
				SETT.THINGS().corpses.create(
						this, 
						!gore, cause);
			}else {
				
				SETT.HALFENTS().corpses.make(this, gore, cause);
			}
			if (!VIEW.b().isActive()) {
				if (indu().player() || RND.oneIn(5))
					STATS.EQUIP().drop(this);
				
			}
		}
		
		helloMyNameIsInigoMontoyaYouKilledMyFatherPrepareToDie();
		STATS.REL().setDeath(induvidual, cause);
	}

	@Override
	public void collide(ECollision coll){
		HEvent.Handler.collide(this, ai, coll);
	}
	
	@Override
	protected void meet(ENTITY other) {
		HEvent.Handler.meet(this, ai, other);
	}
	
	@Override
	protected boolean collidesWithOthers(ENTITY e) {
		return HPoll.Handler.collides(this, ai, e);
	}
	
	@Override
	protected boolean willCollideWith(ENTITY other) {
		return HPoll.Handler.willCollideWith(this, ai, other);
		//return ai.collider(this).isColliding(ai, this, other);
	}
	
	@Override
	public boolean collideTile(boolean broken, double norX, double norY, double force, int tx, int ty) {
		return HEvent.Handler.collideTile(this, ai, norX, norY, force, broken, tx, ty);
		//return ai.collider(this).collideTile(this, ai, norX, norY, force, broken, tx, ty);
	}
	
	@Override
	public void collideUnconnected() {
		HEvent.Handler.collisionUnreachable(this);
	}

	@Override
	protected void setCollideDamage(ECollision coll, ECollision result) {
		HPoll.Handler.collideDamage(this, ai, coll, result);
		
	}

	
	
	@Override
	protected void removeAction() {
		((HumanoidResource) ai).cancel(this);
		((HumanoidResource) induvidual).cancel(this);
		if (noble() != null) {
			GAME.NOBLE().vacateOnlyCallFromHumanoid(this, nobleI);
		}
		STATS.REL().setDeath(induvidual, CAUSE_LEAVES.OTHER());

//		if (ai.subPathByte >= 0 && ai.subPathByte < FinderRoomService.all().size() && 
//				FinderRoomService.all().get(ai.subPathByte) != null 
//				&& FinderRoomService.all().get(ai.subPathByte) != Settlement.ROOMS().SERVICE.BATH.finder 
//				&& FinderRoomService.all().get(ai.subPathByte).get(ai.path.destX(), ai.path.destY()) != null 
//				&& FinderRoomService.all().get(ai.subPathByte).get(ai.path.destX(), ai.path.destY()).findableReservedIs())
//			ai.debug(this, FinderRoomService.all().get(ai.subPathByte) + " " + ai.path.toDebugString());

	}


	
	@Override
	public double getDefenceSkill(double dirDot, double adx, double ady) {
		double d = HPoll.Handler.defenseSkill(this, dirDot, adx, ady);
		return d;
	}
	
	public Induvidual indu() {
		return induvidual;
	}
	
	public HAI ai() {
		return ai;
	}
	
	public CAUSE_LEAVE lastLeaveCause() {
		if (leaveCause == -1)
			return null;
		return CAUSE_LEAVES.ALL().get(leaveCause);
	}
	
	private int getDayBreakTick() {
		int db = (spriteoff&0b011)-1;
		
		RoomInstance w = STATS.WORK().EMPLOYED.get(induvidual);
		if (w != null) {
			db += (int) (w.blueprintI().employment().getShiftStart()*0x0F);
			if ((STATS.RAN().get(induvidual, 0) & 1) == 1 && w.blueprintI().employment().worksNights()) {
				db += 8;
				db &= 0x0F;
			}
		}else{
			db += 0.325*0x0F;
		}
		return db & 0x0F;
	}
	
	public int getNewDayHour() {
		return 24*getDayBreakTick()/16;
	}

	
	public void setDivision(Div div) {
		
		STATS.BATTLE().DIV.set(this, div);
		if (div != null)
			division().reporter.reportPosition(divSpot(), body().cX(), body().cY());
	}
	
	public void teleportAndInitInDiv() {

		if (division() == null)
			return;
		if (!division().settings().mustering())
			return;
		COORDINATE de = division().reporter.getPixel(this);
		if (de == null)
			return;
		physics.body().moveC(de);
		ai.muster(this);
		SETT.ENTITIES().move(this);
		if (division() == null)
			return;
		speed.setDirCurrent(division().dir());
		division().reporter.reportPosition(divSpot(), body().cX(), body().cY());
		
	}
	
	public Div division() {
		return STATS.BATTLE().DIV.get(this);
	}
	
	public static abstract class HumanoidResource {

		public final static int updatesPerDay = 16;
		public final static double updatesPerDayI = 1.0/updatesPerDay;
		public final static double updateDelta = (safeSecondsPerHour()*safeHoursPerDay() / (double)updatesPerDay);
		public final static int byteDelta = 256 / updatesPerDay;
		public static CAUSE_LEAVE dead;
		public static boolean deadGore;

		protected abstract void update(Humanoid h, int updateI, boolean newDay);
		protected abstract void update(Humanoid h, double ds);
		protected abstract void cancel(Humanoid h);
		protected abstract void add(Humanoid h, CAUSE_ARRIVE a);
		
		protected abstract void save(FilePutter file);
		
	}

	@Override
	protected double height() {
		return physics.getHeight()*ai.sprite(this).height;
	}

	public short divSpot() {
		return (short)STATS.BATTLE().position(indu());
	}
	

	public int dayRan() {
		return dayRan;
	}
	
	public Noble noble() {
		if (nobleI == -1) {
			return null;
		}
		return GAME.NOBLE().ALL().get(nobleI);
	}
	
	public void nobleSet() {
		if (noble() != null)
			throw new RuntimeException();
		HTypeSet(HTYPES.NOBILITY(), CAUSE_LEAVES.OTHER(), null);
		nobleI = GAME.NOBLE().assignOnlyCallFromHumanoid(this);
	}
	
	public void HTypeSet(HTYPE t, CAUSE_LEAVE leave, CAUSE_ARRIVE arr) {
		boolean added = !isRemoved();
		if (added) {
			SETT.ENTITIES().moveIntoTheTheUnknown(this);
			if (noble() != null) {
				GAME.NOBLE().vacateOnlyCallFromHumanoid(this, nobleI);
			}

		}
		ai.changeType(this, t, leave, arr);
		double m = BOOSTABLES.PHYSICS().MASS.get(induvidual)*RND.rFloat1(0.1);
		physics.setMass(t == HTYPES.CHILD() ? m/2 : m);
		speed.accelerationInit(Math.max(0.2, BOOSTABLES.PHYSICS().ACCELERATION.get(induvidual))*C.TILE_SIZE);
		speed.magnitudeMaxInit(Math.max(0.2, BOOSTABLES.PHYSICS().SPEED.get(induvidual))*C.TILE_SIZE);
		if (added)
			SETT.ENTITIES().returnFromTheTheUnknown(this);
	}
	
	public void interrupt() {
		ai.overwrite(this, AI.plans().NOP);
	}
	
	public void knockOut() {
		ai.overwrite(this, AI.plans().KNOCKED_OUT);
	}
	
	public CharSequence title() {
		if (noble() != null)
			return noble().title();
		
		if (indu().hType().isWorks()) {
			if (STATS.WORK().EMPLOYED.get(indu()) == null)
				return Dic.¤¤Oddjobber;
			return STATS.WORK().EMPLOYED.get(indu()).blueprintI().employment().title;
		}else if (indu().hType() == HTYPES.PRISONER()) {
			return STATS.LAW().prisonerType.get(indu()).title;
		}
		
		else {
			return indu().hType().name;
		}
	}

	public void target(int amount) {
		mark += amount;
	}
	
	public int targets() {
		return mark;
	}

	@Override
	public void drag(DIR d, int cx, int cy, int fromDist) {
		
		speed.setDirCurrent(d);
		physics.body().moveC(cx-fromDist*d.xN(), cy-fromDist*d.yN());
		if (physics.body().cX() < 0)
			physics.body().moveCX(0);
		if (physics.body().cX() >= SETT.PIXEL_BOUNDS.x2())
			physics.body().moveCX( SETT.PIXEL_BOUNDS.x2()-1);
		if (physics.body().cY() < 0)
			physics.body().moveCY(0);
		if (physics.body().cY() >= SETT.PIXEL_BOUNDS.y2())
			physics.body().moveCY( SETT.PIXEL_BOUNDS.y2()-1);
		
		if (COORDINATE.tileDistance(cx, cy, body().cX(), body().cY()) > 3*C.TILE_SIZE)
			LOG.ln(cx + " " + cy + " " + fromDist);
		
	}

	@Override
	public void drag(DIR d, int cx, int cy) {
		drag(d, cx, cy, body().width());
	}

	@Override
	public boolean canBeDragged() {
		return ai.plan() == AI.plans().KNOCKED_OUT;
	}

}
