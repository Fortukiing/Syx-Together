package settlement.job;

import static settlement.main.SETT.GRASS;
import static settlement.main.SETT.JOBS;
import static settlement.main.SETT.ROOMS;
import static settlement.main.SETT.TERRAIN;
import static settlement.main.SETT.THINGS;
import static settlement.main.SETT.TWIDTH;

import game.GAME;
import game.audio.AUDIO;
import game.audio.SoundRace;
import game.faction.FResources.RTYPE;
import init.resources.RESOURCE;
import init.sprite.SPRITES;
import settlement.entity.humanoid.Humanoid;
import settlement.job.StateManager.State;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.thing.ThingsResources.ScatteredResource;
import settlement.tilemap.terrain.Terrain.TerrainTile;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.DIR;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import util.text.D;
import view.tool.PlacableMulti;

class JobRoom extends Job {

	private static CharSequence ¤¤clearTerrain = "Clearing Terrain";
	private static CharSequence ¤¤clearVegetation = "Clearing Terrain";
	private static CharSequence ¤¤getting = "Getting Materials";
	private static CharSequence ¤¤constructing = "Constructing";
	private static CharSequence ¤¤removing = "Removing Obstacle";
	static {
		D.ts(JobRoom.class);
	}
	
	
	private enum PSTATE {

		CLEAR_TERRAIN(¤¤clearTerrain), CLEAR_VEG(¤¤clearVegetation), REMOVING(¤¤removing), FETCHING(
				¤¤getting), DOING(¤¤constructing);

		final CharSequence name;

		private PSTATE(CharSequence name) {
			this.name = name;
		}

	}

	private PSTATE state;
	private ROOM_JOBBER r;
	private final RESOURCE res;
	private final SoundRace sound = AUDIO.race("BUILD");
	
	
	JobRoom(RESOURCE res) {
		super("ROOM_"+ (res == null ? "NONE" : res.key), "work", SPRITES.icons().m.questionmark);
		this.res = res;
	}

	@Override
	void init(int tx, int ty) {
		JOBS().progress.set(tx+ty*TWIDTH, 0);
		ROOM_JOBBER jobber = jobber(tx, ty);
		JOBS().wantsRes.set(tx+ty*TWIDTH, jobber != null && getState(jobber, tx, ty) == PSTATE.FETCHING);
	}

	@Override
	protected boolean get(int tx, int ty) {
		Room room = ROOMS().map.get(tx, ty);
		if (room == null || !(room instanceof ROOM_JOBBER)) {
			r = null;
			return false;
		}
		r = (ROOM_JOBBER) room;
		state = getState(r, tx, ty);
		super.get(tx, ty);
		if (JOBS().wantsRes.get(tile))
			state = PSTATE.FETCHING;
		else if (state == PSTATE.FETCHING)
			state = PSTATE.CLEAR_VEG;
			
		return true;
	}
	
	private PSTATE getState(int tx, int ty) {
		ROOM_JOBBER jobber = jobber(tx, ty);
		if (jobber == null)
			return PSTATE.DOING;
		return getState(jobber, tx, ty);
	}
	
	private PSTATE getState(ROOM_JOBBER jobber, int tx, int ty) {
		if (jobber.needsTerrainToBeCleared(tx, ty) && terrainNeedsClear(tx, ty))
			return PSTATE.CLEAR_TERRAIN;
		else if (jobber.needsFertilityToBeCleared(tx, ty) && !GRASS().current.is(tx, ty, 0))
			return PSTATE.CLEAR_VEG;
		else if (res != null)
			return PSTATE.FETCHING;
		else if (jobber.becomesSolid(tx, ty) && THINGS().resources.get(tx, ty) != null)
			return PSTATE.REMOVING;
		else
			return PSTATE.DOING;
	}
	
	private ROOM_JOBBER jobber(int tx, int ty) {
		Room room = ROOMS().map.get(tx, ty);
		if (room instanceof ROOM_JOBBER)
			return (ROOM_JOBBER) room;
		return null;
	}

	boolean terrainNeedsClear(int tx, int ty) {
		return TERRAIN().get(tx, ty).clearing().needs() && TERRAIN().get(tx, ty).clearing().can();
	}

//	@Override
//	public long jobResourceBitToFetch() {
//		if (state == PSTATE.FETCHING && res != null)
//			return res.bit;
//		return 0;
//	}

	@Override
	public RESOURCE resourceCurrentlyNeeded() {
		if (state == PSTATE.FETCHING)
			return res;
		return null;
	}

	@Override
	public void jobStartPerforming() {
		// TODO Auto-generated method stub

	}

	@Override
	public double jobPerformTime(Humanoid skill) {
		switch (state) {
		case CLEAR_TERRAIN:
			if (SETT.TERRAIN().MOUNTAIN.is(coo))
				return JOBS().clearss.tunnel.jobPerformTime(skill);
			
			TerrainTile t = TERRAIN().get(coo);
			if (t.clearing().isEasilyCleared())
				return 2;
			return 20; 
		case CLEAR_VEG:
			return 2.0;
		case REMOVING:
			return 0;
		case FETCHING:
			return 0;
		case DOING:
			return 10;
		}
		throw new RuntimeException();
	}
	
	@Override
	public RESOURCE jobPerform(Humanoid skill, RESOURCE r, int ram) {

		if (!jobReservedIs(r)) {
			throw new RuntimeException(JOBS().state.is(coo, State.RESERVED) + " " + r + " " + resourceCurrentlyNeeded());
		}

		RESOURCE res = null;
		switch (state) {
		case CLEAR_TERRAIN:
			if (SETT.TERRAIN().MOUNTAIN.is(coo)) {
				res = JOBS().clearss.tunnelPerform(coo);
			}else {
				TerrainTile t = TERRAIN().get(tile);
				res = t.clearing().clear1(coo.x(), coo.y());
			}
			
			break;
		case CLEAR_VEG:
			GRASS().current.increment(coo.x(), coo.y(), -4);
			break;
		case REMOVING:
			ScatteredResource ress = THINGS().resources.get(coo.x(), coo.y());
			if (ress == null)
				break;
			if (ress.findableReservedCanBe()) {
				ress.findableReserve();
				ress.resourcePickup();
			} else {
				ress.resourcePickup();
			}
			res = ress.resource();
			break;
		default:
			int tx = coo.x();
			int ty = coo.y();
			ROOM_JOBBER j = jobber(tx, ty);
			if (j == null)
				return null;
			PlacerDelete.place(tx, ty);
			j.jobFinsih(tx, ty, r, ram);
			if (!SETT.JOBS().getter.is(tx, ty)) {
				for (int di = 0; di < DIR.ORTHO.size(); di++) {
					DIR d = DIR.ORTHO.get(di);
					if (SETT.JOBS().getter.is(tx, ty, d))
						SETT.JOBS().state.set(SETT.JOBS().state.get(tx, ty, d), SETT.JOBS().getter.get(tx, ty, d));
				}
			}
			return null;
		}
		
		JOBS().wantsRes.set(tile, getState(coo.x(), coo.y()) == PSTATE.FETCHING);

		get(coo.x(), coo.y());
		jobReserveCancel(r);
		if (res != null)
			GAME.player().res().inc(res, RTYPE.PRODUCED, 1);
		return res;

	}

	@Override
	boolean becomesSolidNext() {
		ROOM_JOBBER jobber = jobber(coo.x(), coo.y());
		return jobber != null && jobber.becomesSolid(coo.x(), coo.y());
	}
	
	@Override
	public boolean becomesSolid() {
		ROOM_JOBBER jobber = jobber(coo.x(), coo.y());
		return jobber != null && jobber.becomesSolid(coo.x(), coo.y());
	}
	
	@Override
	public int jobResourcesNeeded(Humanoid skill) {
		ROOM_JOBBER jobber = jobber(coo.x(), coo.y());
		if (jobber == null)
			return 0;
		return jobber.totalResourcesNeeded(coo.x(), coo.y());
	}

	@Override
	public CharSequence jobName() {
		return state.name;
	}

	@Override
	public boolean jobUseTool() {
		return true;
	}

	
	
	@Override
	public SoundRace jobSound() {
		switch (state) {
		case CLEAR_TERRAIN:
			return TERRAIN().get(coo).clearing().sound(coo.x(), coo.y());
		case CLEAR_VEG:
			return GRASS().clearSound;
		case REMOVING:
			return null;
		case FETCHING:
			return null;
		case DOING:
			return sound;
		}
		throw new RuntimeException();
	}

	@Override
	protected void renderBelow(Renderer r, ShadowBatch shadowBatch, RenderIterator i, int state) {
		
	}

	@Override
	public PlacableMulti placer() {
		return null;
	}

	@Override
	void renderAbove(SPRITE_RENDERER r, int x, int y, int mask, int tx, int ty) {
		if (Debug.showRoom || getState(tx, ty) == PSTATE.CLEAR_TERRAIN)
			SPRITES.cons().ICO.repair.render(r, x, y);
	}
	
	@Override
	public int resAmount() {
		return res != null ? 1 :0;
	}

	@Override
	public RESOURCE res() {
		return res;
	}

	@Override
	public TerrainTile becomes(int tx, int ty) {
		return TERRAIN().NADA;
	}

}
