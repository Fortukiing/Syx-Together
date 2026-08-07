package settlement.job;

import static settlement.main.SETT.GRASS;
import static settlement.main.SETT.JOBS;
import static settlement.main.SETT.PATH;
import static settlement.main.SETT.TERRAIN;
import static settlement.main.SETT.THINGS;
import static settlement.main.SETT.TWIDTH;

import coopmod.CoopRuntime;
import game.audio.SoundRace;
import init.resources.RBIT;
import init.resources.RESOURCE;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.thing.THINGS.Thing;
import settlement.thing.ThingsResources.ScatteredResource;
import settlement.tilemap.terrain.Terrain.TerrainTile;
import snake2d.Renderer;
import snake2d.util.sprite.SPRITE;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import util.text.D;
import view.tool.PlacableMessages;
import view.tool.PlacableMulti;

public abstract class JobBuild extends Job{

	private static CharSequence ¤¤clearTerrain = "Clearing Terrain";
	private static CharSequence ¤¤clearVegetation = "Clearing Terrain";
	private static CharSequence ¤¤getting = "Getting Materials";
	private static CharSequence ¤¤constructing = "Constructing";
	private static CharSequence ¤¤removing = "Removing Obstacle";
	static {
		D.ts(JobBuild.class);
	}
	
	
	private enum PSTATE {
		
		CLEAR_TERRAIN(¤¤clearTerrain),
		CLEAR_VEG(¤¤clearVegetation),
		REMOVING(¤¤removing),
		FETCHING(¤¤getting),
		CONSTRUCTING(¤¤constructing);
		
		final CharSequence name;
		
		private PSTATE(CharSequence name) {
			this.name = name;
		}
		
	}
	
	private PSTATE state;

	private final boolean solid;
	Placer placer;
	protected boolean needsFerClear = true;
	protected final RESOURCE res;
	protected final int resAmount;
	
	JobBuild(String key, RESOURCE res, int resAmount, boolean solid, CharSequence name, CharSequence desc, SPRITE icon) {
		super(key, name, icon);
		this.res = res;
		if (res == null)
			resAmount = 0;
		this.resAmount = resAmount;
		this.solid = solid;
		placer = new Placer(this, res, resAmount, desc);
	}
	
	@Override
	void init(int tx, int ty) {
		
		JOBS().progress.set(tx+ty*TWIDTH, 0);
		JOBS().wantsRes.set(tx+ty*TWIDTH, false);
		if (res != null) {
			for (Thing t : THINGS().get(tx, ty)) {
				if (t instanceof ScatteredResource) {
					ScatteredResource tt = (ScatteredResource) t;
					if (tt.resource() == res) {
						int a = tt.amount()-tt.amountReserved();
						if (a>= resAmount) {
							tt.removeUnreserved(resAmount);
							JOBS().progress.set(tx+ty*TWIDTH, resAmount);
							break;
						}else if (a > 0){
							JOBS().progress.set(tx+ty*TWIDTH, a);
							tt.removeUnreserved(a);
							break;
						}
					}
				}
			}
		}
		
		JOBS().wantsRes.set(tx+ty*TWIDTH, getStateP(tx, ty) == PSTATE.FETCHING);
	}
	
	@Override
	public RESOURCE res() {
		return res;
	}
	
	@Override
	public RESOURCE resourceCurrentlyNeeded() {
		if (state == PSTATE.FETCHING)
			return res;
		return null;
	}
	
	@Override
	protected CharSequence problem(int tx, int ty, boolean overwrite) {
		
		if (super.problem(tx, ty, overwrite) != null)
			return super.problem(tx, ty, overwrite);
		if (TERRAIN().get(tx, ty).clearing().isStructure())
			return PlacableMessages.¤¤STRUCTURE_BLOCK;
		if (PATH().solidity.is(tx, ty))
			return PlacableMessages.¤¤SOLID_BLOCK;
		TerrainTile t = TERRAIN().get(tx, ty);
		
		if (t.clearing().needs() && !t.clearing().can())
			return PlacableMessages.¤¤MISC;
		
		if (becomesSolid() && !SETT.TERRAIN().MOUNTAIN.isMountain(tx, ty)) {
			return null;
		}else if (t.clearing().isStructure())
			return PlacableMessages.¤¤STRUCTURE_BLOCK;
		
		return null;
	}
	
	private PSTATE getState(int tx, int ty) {
		PSTATE s = getStateP(tx, ty);
		if (s == PSTATE.FETCHING) {
			JOBS().wantsRes.set(tx+ty*TWIDTH, true);
		}
		if (resNeeds(tx, ty) && JOBS().wantsRes.get(tx+ty*TWIDTH)) {
			s = PSTATE.FETCHING;
		}
		return s;
	}
	
	private PSTATE getStateP(int tx, int ty) {
		if (needsFerClear && terrainNeedsClear(tx, ty))
			return PSTATE.CLEAR_TERRAIN;
		else if (needsFerClear && GRASS().current.get(tx, ty) > 0)
			return PSTATE.CLEAR_VEG;
		else if (resNeeds(tx, ty))
			return PSTATE.FETCHING;
		else if (solid && THINGS().resources.has(tx, ty, RBIT.ALL))
			return PSTATE.REMOVING;
		else
			return PSTATE.CONSTRUCTING;
	}
	
	@Override
	protected boolean get(int tx, int ty) {
		state = getState(tx, ty);
		return super.get(tx, ty);
	}
	
	boolean terrainNeedsClear(int tx, int ty) {
		return TERRAIN().get(tx, ty).clearing().needs() && !TERRAIN().get(tx, ty).clearing().isStructure() && TERRAIN().get(tx, ty).clearing().can();
	}
	
	boolean resNeeds(int tx, int ty) {
		return res != null && JOBS().progress.get(tx+ty*TWIDTH) < resAmount;
	}
	
	@Override
	public int jobResourcesNeeded(Humanoid skill) {
		if (res != null)
			return resAmount - JOBS().progress.get(tile); 
		return 0;
	}
	
	@Override
	public void jobStartPerforming() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public double jobPerformTime(Humanoid skill) {
		switch(state) {
		case CLEAR_TERRAIN:
			TerrainTile t = TERRAIN().get(coo);
			if (t.clearing().isEasilyCleared())
				return 7;
			return 15;
		case CLEAR_VEG:
			return 2.0;
		case REMOVING:
			return 0;
		case FETCHING:
			return 0;
		case CONSTRUCTING:
			return constructionTime(skill);
		}
		throw new RuntimeException();
	}

	protected abstract double constructionTime(Humanoid skill);
	
	@Override
	public RESOURCE jobPerform(Humanoid skill, RESOURCE r, int rAm) {
		
		if (!jobReservedIs(r)) {
			throw new RuntimeException();
		}
		RESOURCE res = null;
		switch(state) {
		case CLEAR_TERRAIN:
			TerrainTile t = TERRAIN().get(tile);
			res = t.clearing().clear1(coo.x(), coo.y());
			break;
		case CLEAR_VEG:
			GRASS().currentI.increment(coo.x(), coo.y(), -4);
			break;
		case REMOVING:
			ScatteredResource ress = THINGS().resources.get(coo.x(), coo.y());
			if (ress == null)
				break;
			if (ress.findableReservedCanBe()) {
				ress.findableReserve();
				ress.resourcePickup();
			}else {
				ress.resourcePickup();
			}
			res = ress.resource();
			break;
		case FETCHING:
			JOBS().progress.set(coo.x()+coo.y()*TWIDTH, JOBS().progress.get(coo.x()+coo.y()*TWIDTH)+rAm);
			break;
		case CONSTRUCTING:
			if (!construct(coo.x(), coo.y())) {
				CoopRuntime.jobBuildFinished(key(), coo.x(), coo.y());
				PlacerDelete.place(coo.x(), coo.y());
				return res;
			}
			break;
		}
//		PlacerDelete.place(coo.x(), coo.y());
//		Placer.place(coo.x(), coo.y(), this);
		
		get(coo.x(), coo.y());
		jobReserveCancel(r);
		return res;
		
		
		
	}
	
	@Override
	boolean becomesSolidNext() {
		return solid && state == PSTATE.CONSTRUCTING;
	}
	
	@Override
	public boolean becomesSolid() {
		return solid;
	}
	
	protected abstract boolean construct(int tx, int ty);

	public final boolean coopForceConstruct(int tx, int ty) {
		if (!get(tx, ty))
			return false;
		for (int i = 0; i < 64; i++) {
			if (!construct(tx, ty)) {
				PlacerDelete.place(tx, ty);
				return true;
			}
		}
		get(tx, ty);
		return false;
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
		switch(state) {
		case CLEAR_TERRAIN:
			return TERRAIN().get(coo).clearing().sound(coo.x(), coo.y());
		case CLEAR_VEG:
			return GRASS().clearSound;
		case REMOVING:
			return null;
		case FETCHING:
			return null;
		case CONSTRUCTING:
			return constructSound();
		}
		throw new RuntimeException();
	}
	
	protected abstract SoundRace constructSound();

	
	@Override
	protected void renderBelow(Renderer r, ShadowBatch shadowBatch, RenderIterator i, int state) {
		if (state > 0 && res != null) {
			res.renderLaying(r, i.x(), i.y(), i.ran(), state);
			shadowBatch.setHeight(1).setDistance2Ground(0);
			res.renderLaying(shadowBatch, i.x(), i.y(), i.ran(), state);
		}
	}
	
	@Override
	void cancel(int tx, int ty) {
		if (JOBS().progress.get(tx + ty*TWIDTH) > 0)
			THINGS().resources.create(tx, ty, res, JOBS().progress.get(tx + ty*TWIDTH));
	}
	
	@Override
	public PlacableMulti placer() {
		return placer;
	}
	
	@Override
	public int resAmount() {
		return resAmount;
	}

}
