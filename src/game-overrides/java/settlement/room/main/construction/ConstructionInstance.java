package settlement.room.main.construction;

import static settlement.main.SETT.GRASS;
import static settlement.main.SETT.JOBS;
import static settlement.main.SETT.PATH;
import static settlement.main.SETT.ROOMS;
import static settlement.main.SETT.TERRAIN;
import static settlement.main.SETT.THINGS;
import static settlement.main.SETT.TWIDTH;
import static settlement.room.main.construction.ConstructionData.dBroken;
import static settlement.room.main.construction.ConstructionData.dConstructed;
import static settlement.room.main.construction.ConstructionData.dData;
import static settlement.room.main.construction.ConstructionData.dFloored;
import static settlement.room.main.construction.ConstructionData.dResAllocated;
import static settlement.room.main.construction.ConstructionData.dResourceNeeded;
import static settlement.room.main.construction.ConstructionData.dResourceNeededAll;
import static settlement.room.main.construction.ConstructionData.dWorkAmount;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.GAME;
import game.faction.FACTIONS;
import game.faction.FResources.RTYPE;
import init.resources.RESOURCE;
import settlement.job.ROOM_JOBBER;
import settlement.main.SETT;
import settlement.maintenance.ROOM_DEGRADER;
import settlement.path.AVAILABILITY;
import settlement.room.main.MapDataF;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.TmpArea;
import settlement.room.main.furnisher.Furnisher;
import settlement.room.main.furnisher.FurnisherItem;
import settlement.room.main.furnisher.FurnisherItemTile;
import settlement.room.main.furnisher.FurnisherStat;
import settlement.room.main.placement.UtilExtraCost;
import settlement.room.main.util.RoomInit;
import settlement.room.main.util.RoomState;
import settlement.room.sprite.RoomSprite;
import settlement.tilemap.terrain.TBuilding;
import snake2d.LOG;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;
import snake2d.util.datatypes.DIR;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import snake2d.util.sprite.SPRITE;
import snake2d.util.sprite.text.Str;
import util.GUTIL;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.rendering.RenderData;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import util.text.D;

class ConstructionInstance extends Room.RoomInstanceImp implements ROOM_JOBBER {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	transient Furnisher blueprint;

	private final Rec tiles;
	private final int size;
	boolean active = true;
	private final short fx, fy;
	
	private int resourcedNeeded;
	private int resTot;
	int builtNeeded;
	int builtTotal;
	private int structuresNeeded;
	private int clearingNeeded;
	private int floorsNeeded;
	boolean broken;
	final int structureI;
	boolean constructing = false;
	private final double resMul;
	private int degrade;
	RoomState state;
	private final int upgrade;
	
	ConstructionInstance(RoomBlueprint daddy, TmpArea a, ConstructionInit init) {
		super(ROOMS(), daddy, false);
		this.resMul = 1 + UtilExtraCost.psupport(a, init.b.blue()) + UtilExtraCost.pfoundation(a, init.b.blue());
		
		tiles = new Rec(a.body());
		this.size = a.area();
		a.replaceAndClear(this);
		blueprint = init.b;
		
		int up = 0;
		if (blueprint == SETT.ROOMS().HOME.constructor())
			up = 0;
		else
			up = Math.min(init.upgrade, blueprint.blue().upgrades().max());
		
		while (up > 0 && blueprint.blue().upgrades().requires(up) != null && !blueprint.blue().upgrades().requires(up).passes(FACTIONS.player())) {
			up--;
		}
		
		this.upgrade = up;
		
		this.state = init.state;
		this.degrade = init.degrade;
		int fx = -1,fy = -1;
		boolean b = false;
		boolean m = false;
		for (COORDINATE c : tiles) {
			if (is(c)) {
				JOBS().clearer.set(c);
				da().candle.set(c, false);
				b |= ConstructionData.dBroken.get(c) == 1;
				m |= !SETT.MAINTENANCE().disabled.is(c);
				if (fx == -1) {
					fx = c.x();
					fy = c.y();
				}
			}
		}

		this.broken = b;
		
		
		
		if (fx == -1)
			throw new RuntimeException(daddy + " " + tiles);
		
	
		this.fx = (short) fx;
		this.fy = (short) fy;
		
		active = (!broken || m) && !JOBS().planMode.is();
		TBuilding structure = init.structure;
		if (!blueprint.mustBeIndoors())
			structure = null;
		structureI = structure == null ? -1 : structure.structure.index();
		
		GUTIL.coos().set(0);
		
		for (COORDINATE c : tiles) {
			if (is(c)) {
				FurnisherItemTile t = da().tile.get(c);
				if (t != null && t.canGoCandle) {
					GUTIL.coos().get().set(c);
					GUTIL.coos().inc();
				}
			}
		}
		
		int cm = GUTIL.coos().getI();
		GUTIL.coos().shuffle(cm);
		for (int i = 0; i < cm; i++) {
			COORDINATE c = GUTIL.coos().set(i);
			candle(c.x(), c.y());
		}
		
		init();
		
		if (is(fx, fy)) {
			SETT.ROOMS().map.init(this);
			if (broken)
				SETT.ROOMS().stats.broken().add(mX(), mY());
		}else {
			SETT.ROOMS().stats.finished().remove(fx, fy);
		}
		
		

	}
	
	private void candle(int tx, int ty) {

		int radius = 4;

		int i = 0;
		while (GUTIL.circle().radius(i) <= radius) {
			
			COORDINATE c = GUTIL.circle().get(i);
			i++;
			int x = c.x()+tx;
			int y = c.y()+ty;
			if (!is(x, y))
				continue;
			if (da().candle.is(x, y)) {
				return;
			}
			
		}
		da().candle.set(tx, ty, true);

	}
	
	private void init() {
		
		builtNeeded = 0;
		builtTotal = 0;
		resourcedNeeded = 0;
		floorsNeeded = 0;
		structuresNeeded = 0;
		
		double[] resNeededD = new double[blueprint.resources()];
		
		int availableResourceTiles = 0;
		
		{
			
			int floored = 0;
			
			for (COORDINATE c : body()) {
				if (is(c)) {
					jobClear(c.x(), c.y());
					ROOMS().data.set(this, c, dData.get(c));
					if (dFloored.is(c, 1)) {
						blueprint.putFloor(c.x(), c.y(), upgrade, this);
						floored += 1;
						constructor().putFloor(c.x(), c.y(), upgrade, this);
					}
					
					FurnisherItem it = da().item.get(c);
					
					if (it != null && da().isMaster.is(c)) {
						
						if ((dConstructed.is(c, 0) || dBroken.is(c, 1))) {
							
							for (int y = 0; y < it.height(); y++) {
								for (int x = 0; x < it.width(); x++) {
									if (it.get(x, y) == null)
										continue;
									if (dConstructed.is(c, 0))
										dConstructed.set(this, x+c.x()-it.firstX(), y+c.y()-it.firstY(), 0);
									else
										dBroken.set(this, c, 1);
									builtTotal++;
								}
							}
							for (int i = 0; i < blueprint.resources(); i++) {
								resNeededD[i] += it.cost2(i, upgrade);
							}
						}else {
							for (int i = 0; i < blueprint.resources(); i++) {
								resNeededD[i] += it.cost2(i, upgrade)*getDegrade();
							}
						}
					}
					
					
					if (dFloored.is(c, 0) || (da().item.is(c) && (dConstructed.is(c, 0) || dBroken.is(c, 1))))
						availableResourceTiles++;
				}
			}
			
			for (int i = 0; i < blueprint.resources(); i++) {
				resNeededD[i] += Math.ceil(blueprint.areaCost(i, upgrade)*(area()-floored*(1-getDegrade())));
				resNeededD[i] = (int) Math.ceil(resNeededD[i]*resMul);
				resourcedNeeded += resNeededD[i];
			}
			
			floorsNeeded = area()-floored;
			builtNeeded = builtTotal;
		}
		
		
		
		
		if (resourcedNeeded == 0) {
			setClear();
			return;
		}
		
		int[] resNeeded = Alloc.ii(resNeededD.length);
		for (int i = 0; i < resNeeded.length; i++) {
			resNeeded[i] = (int) Math.ceil(resNeededD[i]);
		}

		int[] pileAmount = Alloc.ii(blueprint.resources());		
		double[] pileDistance = new double[blueprint.resources()];
		double[] pileD = new double[blueprint.resources()];
		boolean[] placedD = new boolean[blueprint.resources()];
		for (int i = 0; i < blueprint.resources(); i++) {
			int max = 4;
			int am = resNeeded[i];
			int piles = am/max;
			piles += am%max > 0 ? 1 : 0;
			
			if (piles > availableResourceTiles) {
				max = dResourceNeeded[0].max;
				piles = am/max;
				piles += am%max > 0 ? 1 : 0;
			}
			
//			
			pileAmount[i] = max;
			if (piles == 0)
				continue;
			pileDistance[i] = (double)(availableResourceTiles-1) / piles;
			if (pileDistance[i] < 1)
				pileDistance[i] = 1;
			pileD[i] = (availableResourceTiles - pileDistance[i]*(piles))/2;
		}
		for (COORDINATE c : body()) {
			if (is(c)) {
				if (dFloored.is(c, 0) || (da().item.is(c) && (dConstructed.is(c, 0) || dBroken.is(c, 1)))) {
					for (int i = 0; i < blueprint.resources(); i++) {
						if (resNeeded[i] > 0) {

							
							if (!placedD[i] && RND.oneIn(pileD[i])) {
								int am = pileAmount[i];
								am = CLAMP.i(am, 0, resNeeded[i]);
								resNeeded[i] -= am;
								dResourceNeeded[i].set(this, c, am);
								placedD[i] = true;
							}
							if (pileD[i] <= 0) {
								pileD[i] += pileDistance[i];
								placedD[i] = false;
								
							}	
						}
						pileD[i] --;
					}
				}
				
			}
		}
		for (COORDINATE c : body()) {
			if (is(c)) {
				if (!(da().item.is(c))) {
					for (int i = 0; i < blueprint.resources(); i++) {
						if (resNeeded[i] > 0) {

							
							if (!placedD[i] && RND.oneIn(pileD[i])) {
								int am = pileAmount[i];
								am = CLAMP.i(am, 0, resNeeded[i]);
								resNeeded[i] -= am;
								dResourceNeeded[i].set(this, c, am);
								placedD[i] = true;
							}
							if (pileD[i] <= 0) {
								pileD[i] += pileDistance[i];
								placedD[i] = false;
								
							}	
						}
						pileD[i] --;
					}
				}
				
			}
		}
		
		
		resTot = resourcedNeeded;
		for (int i = 0; i < blueprint.resources(); i++) {
			if (resNeeded[i] > 0) {
				GAME.Notify(blueprint.resource(i).name + " " + resNeeded[i]);
				resourcedNeeded-= resNeeded[i];
			}
		}

		setClear();
	}
	
	private void setClear() {
		
		for (COORDINATE c : body()) {
			if (needsClear(c)) {
				clearingNeeded++;
				jobSet(c.x(), c.y(), active, null);
			}
		}
		if (clearingNeeded == 0)
			setBuildRoof();
	}
	
	boolean needsClear(COORDINATE c) {
		if (is(c) && (dFloored.is(c, 0) || dConstructed.is(c, 0) || dBroken.is(c, 1))) {
			if (blueprint.removeFertility() && GRASS().current.get(c) > 0) {
				return true;
			}
			return needsTerrainToBeCleared(c.x(), c.y());
//			
//			else if(!SETT.TERRAIN().NADA.is(c) && (structureI == -1 || !SETT.TERRAIN().BUILDINGS.getAt(structureI).roof.is(c)) && blueprint.removeTerrain(c.x(), c.y())) {
//				if (!TERRAIN().CAVE.is(c))
//					return true;
//			}
		}
		return false;
	}
	
	private void setBuildRoof() {
		
		if (structureI == -1) {
			setFetch();
			return;
		}
		
		TBuilding st = TERRAIN().BUILDINGS. all().get(structureI);
		
		for (COORDINATE c : body()) {
			if (is(c)) {
				if (TERRAIN().CAVE.is(c))
					continue;
				if (!st.roof.is(c) && getAvailability(c.x()+c.y()*SETT.TWIDTH).player > 0) {
					jobSet(c.x(), c.y(), active, st.structure.resource);
					structuresNeeded ++;
				}
			}
		}
		
		if (structuresNeeded == 0) {
			setFetch();
			return;
		}
	
	}
	
	private void setFetch() {
		
		if (resourcedNeeded == 0) {
			setBuildFloor();
			return;
		}

		for (COORDINATE c : body()) {
			if (is(c)) {
				for (int i = 0; i < blueprint.resources(); i++) {
					if (dResourceNeeded[i].get(c) > 0) {
						jobSet(c.x(), c.y(), active, blueprint.resource(i));
						break;
					}
				}
			}
		}
	}
	
	private void setBuildFloor() {
		if (floorsNeeded == 0) {
			setFurnish();
			return;
		}
		
		for (COORDINATE c : body()) {
			if (is(c)) {
				if (dFloored.is(c, 0)) {
					jobSet(c.x(), c.y(), active, null);
				}
			}
		}
	}
	
	private void setFurnish() {
		if (builtNeeded == 0) {
			finish();
			return;
		}
		
		for (COORDINATE c : body()) {
			if (is(c)) {
				FurnisherItem it = da().item.get(c);
				if (it != null && da().isMaster.is(c) && (dConstructed.is(c, 0) || dBroken.is(c, 1))) {
					int x1 = c.x()-it.firstX();
					int y1 = c.y()-it.firstY();
					for (int y = 0; y < it.height(); y++) {
						for (int x = 0; x < it.width(); x++) {
							if (!it.is(x, y))
								continue;
							if (!is(x1+x, y1+y)) {
								LOG.ln(blueprint.blue().info.name + " " + it.group.name + " " + it.rotation + " " + it.width() + " " + it.height());
							}
							jobSet(x1+x, y1+y, active, null);
						}
					}
				}
			}
		}
	}
	
	void finish() {
		CoopRuntime.roomConstructionFinished(mX(), mY());
		
		RoomInit init = new RoomInit(blueprint.blue(), 0);
		
		constructing = true;
		
		double[] resources = new double[blueprint.resources()];
		
		for (COORDINATE c : body()) {
			if (!is(c))
				continue;
			blueprint.putFloor(c.x(), c.y(), upgrade, this);
			ROOMS().data.set(this, c, 0);
			if (da().candle.is(c)) {
				SETT.LIGHTS().candle(c.x(), c.y(), 0);
			}
			FurnisherItem it = da().item.get(c);
			if (it != null && da().isMaster.is(c)) {
				for (FurnisherStat s : blueprint.stats())
					init.stats[s.index()] += it.stat(s);
				for (int i = 0; i < blueprint.resources(); i++)
					resources[i] += it.costFlat(i);
			}
			
				
		}
		
		for (FurnisherStat s : blueprint.stats())
			init.stats[s.index()] = s.get(this, init.stats);
		
		for (int i = 0; i < blueprint.resources(); i++) {
			init.res[i] = Math.ceil((resources[i]+ blueprint.areaCostFlat(i)*area())*resMul);
		}

		init.resMul = resMul;
		((ConstructionBlueprint)blueprint()).remove(this);
		TmpArea a = super.delete(mX(), mY(), this);
		CONSTRUCTION.ppCreate(a, init, blueprint, upgrade, state);
		if (broken && state != null) {
			Room r = SETT.ROOMS().map.get(mX(), mY());
			if (r != null && r.blueprint() == blueprint.blue()) {
				state.apply(r, builtTotal, builtNeeded);
			}
		}
		
		if (blueprint.blue() instanceof RoomBlueprintIns<?>) {
			GAME.count().ROOMS_BUILT.inc(1);
			SETT.ROOMS().stats.finished().add(fx, fy);
		}
		
		
	}
	
	@Override
	public void jobFinsih(int tx, int ty, RESOURCE r, int ram) {
		CoopRuntime.roomJobFinished(tx, ty, r, ram);
		
		if (clearingNeeded > 0) {
			clearingNeeded--;
			if (clearingNeeded == 0)
				setBuildRoof();
			return;
		}
		
		if (structuresNeeded > 0) {
			
			int am = dWorkAmount.get(tx, ty);
			TBuilding t = TERRAIN().BUILDINGS.all().get(structureI);
			if (am == t.structure.resAmount) {
				t.roof.placeFixed(tx, ty);
				if (t.structure.resource != null)
					GAME.player().res().inc(t.structure.resource, RTYPE.CONSTRUCTION, -t.structure.resAmount);
				dWorkAmount.set(this, tx, ty, 0);
				structuresNeeded --;
				if (structuresNeeded == 0)
					setFetch();
			}else {
				am ++;
				dWorkAmount.set(this, tx, ty, am);
				if (am == t.structure.resAmount) {
					jobSet(tx, ty, active, null);
				}else {
					jobSet(tx, ty, active, t.structure.resource);
				}
			}
			
			return;
		}
		
		if (resourcedNeeded > 0) {
			if (r == null)
				throw new RuntimeException();
			
			GAME.player().res().inc(r, RTYPE.CONSTRUCTION, -ram);
			int needed = 0;
			for (int i = 0; i < blueprint.resources(); i++) {
				needed += dResourceNeeded[i].get(tx, ty);
			}
			
			ram = CLAMP.i(ram, 0, needed-dResAllocated.get(tx, ty));

			dResAllocated.inc(this, tx, ty, ram);
			
			resourcedNeeded -= ram; 
			if (resourcedNeeded == 0) {
				setBuildFloor();
				return;
			}
			
			if (dResAllocated.get(tx, ty) < needed) {
				int am = dResAllocated.get(tx, ty);
				for (int i = 0; i < blueprint.resources(); i++) {
					am -= dResourceNeeded[i].get(tx, ty);
					if (am < 0) {
						jobSet(tx, ty, active, blueprint.resource(i));
						break;
					}
				}
			}

			return;
		}
		
		if (r != null)
			throw new RuntimeException();
		
		if (floorsNeeded > 0) {
			blueprint.putFloor(tx, ty, upgrade, this);
			dFloored.set(this, tx, ty, 1);
			floorsNeeded --;
			if (floorsNeeded == 0)
				setFurnish();
			return;
		}
		
		builtNeeded --;
		dConstructed.set(this, tx, ty, 1);
		dBroken.set(this, tx, ty, 0);
		PATH().availability.updateAvailability(tx, ty);
		
		
		
		if (builtNeeded == 0) {
			finish();
		}

	}
	
	@Override
	protected void saveExtra(FilePutter file) {
		SETT.ROOMS().collection.saver().save(blueprint.blue(), file);
		super.saveExtra(file);
	}
	
	@Override
	protected boolean loadExtra(FileGetter file) throws IOException {
		RoomBlueprint p = SETT.ROOMS().collection.loader().load(file);
		if (p == null)
			return false;
		RoomBlueprintImp b = (RoomBlueprintImp)p;
		blueprint = b.constructor();
		return super.loadExtra(file);
	}
	
	private static final Coo tmp = new Coo();

	@Override
	protected boolean render(Renderer r, ShadowBatch shadowBatch, RenderData.RenderIterator it) {

		
		FurnisherItemTile tile = da().tile.get(it.tile());

		if (tile == null || tile.sprite() == null) {
			int m = 0;
			for (DIR d : DIR.ORTHO) {
				if (is(it.tx(), it.ty(), d))
					m |= d.mask();
			}
			if (active) {
				GCOLOR.MAP().JOB_ACTIVE.bind();
			} else {
				GCOLOR.MAP().JOB_DORMANT.bind();
			}
			blueprint.renderEmbryo(r, m, it, dFloored.is(it.tile(), 1), this, active);
			
			
		}else {
			if (dConstructed.is(it.tile(), 0)) {
				if (active) {
					GCOLOR.MAP().JOB_ACTIVE.bind();
				} else {
					GCOLOR.MAP().JOB_DORMANT.bind();
				}
				FurnisherItem itt = da().item.get(it.tile());
				da().itemX1Y1(it.tx(), it.ty(), tmp);
				int rx = it.tx()-(tmp.x());
				int ry = it.ty()-(tmp.y());
				if (itt.get(rx, ry) == null)
					throw new RuntimeException(itt.group.blueprint.blue() + " " + rx + " " + ry + " " + tmp + " " + it.tx() + " " + it.ty());
				
				tile.sprite().renderPlaceholder(r, it.x(), it.y(), da().spriteData.get(it.tile()), it.tx(), it.ty(), rx, ry, itt);
				//tile.sprite().renderPlaceholder(r, it.x(), it.y(), da().spriteData.get(it.tile()), it.tx(), it.ty(), tile);
			}else if(dBroken.is(it.tile(), 1)) {
				tile.sprite().renderBroken(r, shadowBatch, it.x(), it.y(), it, da().item.get(it.tile()));
			}else {
				return tile.sprite().render(r, shadowBatch, da().spriteData.get(it.tile()), it, deg(), false);
			}
		}
		COLOR.unbind();
		if(structuresNeeded > 0) {
			int am = dWorkAmount.get(it.tile());
			if (am > 0)
				TERRAIN().BUILDINGS.all().get(structureI).structure.resource.renderLaying(r, it.x(), it.y(), it.ran(), am);
		}else if (dResourceNeededAll.get(it.tile()) != 0) {
			int am = dResAllocated.get(it.tile());
			int tot = area()+builtTotal;
			double d = tot-(floorsNeeded+builtNeeded);
			d /= tot;
			
			if ((it.ran() & 0x0FFFF) >= 0x0FFFF*d) {
			
				for (int i = 0; i < blueprint.resources() && am > 0; i++) {
					int a = CLAMP.i(dResourceNeeded[i].get(it.tile()), 0, am);
					if (a > 0) {
						blueprint.resource(i).renderLaying(r, it.x(), it.y(), it.ran(), a);
					}
					am -= a;
				}
			}
		}

		

		return false;
	}
	
	@Override
	protected boolean renderAbove(Renderer r, ShadowBatch shadowBatch, RenderIterator it) {
		
		if (dConstructed.is(it.tile(), 1) && dBroken.is(it.tile(), 0)) {
			RoomSprite sp = da().sprite.get(it.tile());
			if (sp != null)
				sp.renderAbove(r, shadowBatch, da().spriteData.get(it.tile()), it, deg());
		}
		return false;
	}

	@Override
	protected boolean renderBelow(Renderer r, ShadowBatch shadowBatch, RenderIterator it) {
		if (dConstructed.is(it.tile(), 1) && dBroken.is(it.tile(), 0)) {
			RoomSprite sp = da().sprite.get(it.tile());
			if (sp != null)
				sp.renderBelow(r, shadowBatch, da().spriteData.get(it.tile()), it, deg());
		}
		blueprint.renderTileBelow(r, shadowBatch, it, dFloored.is(it.tile(), 1));
		return false;
	}

	private double deg() {
		return getDegrade()*CLAMP.d(1.0-(double)(resTot-resourcedNeeded)/resTot, 0, 1);
	}
	
	@Override
	protected AVAILABILITY getAvailability(int tile) {
		FurnisherItemTile t = da().tile.get(tile);
		
		if (t != null) {
			if (dConstructed.is(tile, 1) && dBroken.is(tile, 0)) {
				return t.availability;
			}else if (t.isBlocker()) {
				return AVAILABILITY.PENALTY4;
			}
		}
		return AVAILABILITY.ROOM;
	}



	private static Str name = new Str(32);
	private static CharSequence ¤¤Construction = "¤Construction";
	private static CharSequence ¤¤Broken = "¤Broken";
	
	static {
		D.ts(ConstructionInstance.class);
	}
	
	@Override
	public CharSequence name(int tx, int ty) {
		name.clear();
		if (broken) {
			name.add(¤¤Broken).add(' ').add(blueprint.blue().info.name);
		}else
			name.add(blueprint.blue().info.name).add(' ').add(¤¤Construction);
		return name;
	}

	@Override
	public void destroyTile(int tx, int ty) {
		FurnisherItem it = ROOMS().fData.item.get(tx, ty);
		COORDINATE ff = ROOMS().fData.itemX1Y1(tx, ty, Coo.TMP);
		int x1 = ff.x();
		int y1 = ff.y();
		for (int y = 0; y < it.height(); y++) {
			for (int x = 0; x < it.width(); x++) {
				if (it.is(x, y)) {
					dBroken.set(this, x1+x, y1+y, 1);
					PATH().availability.updateAvailability(x1+x, y1+y);
				}
			}
		}
		if (!broken)
			SETT.ROOMS().stats.broken().add(mX(), mY());
		broken = true;
		active = false;
		broken = true;
		PATH().availability.updateAvailability(tx, ty);
		init();
	}

	@Override
	public boolean destroyTileCan(int tx, int ty) {
		return getAvailability(tx+ty*TWIDTH).player < 0 && ROOMS().fData.item.get(tx, ty) != null;
	}

	@Override
	public int area() {
		return size;
	}

	@Override
	public RECTANGLE body() {
		return tiles;
	}

	@Override
	public int mX() {
		return fx;
	}

	@Override
	public int mY() {
		return fy;
	}

	public TBuilding structure() {
		if (structureI >= 0)
			return SETT.TERRAIN().BUILDINGS.all().get(structureI);
		return null;
	}
	
	void releaseResources(boolean all) {
		
		
		
		if (structuresNeeded > 0 || resourcedNeeded > 0) {
			for (COORDINATE c : body()) {
				if (!is(c))
					continue;
				if (structuresNeeded > 0) {
					int am = dWorkAmount.get(c);
					if (am > 0) {
						GAME.player().res().inc(TERRAIN().BUILDINGS.all().get(structureI).structure.resource, RTYPE.CONSTRUCTION, am);
						THINGS().resources.create(c, TERRAIN().BUILDINGS.all().get(structureI).structure.resource, am);
					}
					dWorkAmount.set(this, c, 0);
				}else if(resourcedNeeded > 0){
					int am = dResAllocated.get(c);
					if (am == 0)
						continue;
					for (int i = 0; i < blueprint.resources() && am > 0; i++) {
						int a = CLAMP.i(am, 0, dResourceNeeded[i].get(c));
						if (a > 0) {
							THINGS().resources.create(c, blueprint.resource(i), a);
							GAME.player().res().inc(blueprint.resource(i), RTYPE.CONSTRUCTION, a);
						}
						am -= a;
						
					}
					dResourceNeededAll.set(this, c, 0);
					dResAllocated.set(this, c, 0);
				}
				
				
			}
		}else {
			int ff = 0;
			for (COORDINATE c : body()) {
				if (!is(c))
					continue;
				releaseItem(c.x(), c.y(), all);
				if (dFloored.get(c) == 0) {
					ff++;
				}
				
			}
			for (int i = 0; i < blueprint.resources(); i++) {
				int am = (int) Math.ceil(blueprint.areaCost(i, upgrade)*ff);
				if (am > 0)
					THINGS().resources.create(mX(), mY(), blueprint.resource(i), am);
			}
		}
		
		if (!all)
			return;
		
		int ff = 0;
		
		for (COORDINATE c : body()) {
			if (!is(c))
				continue;
			
			if (dFloored.get(c) == 1) {
				ff++;
			}
			
		}
		for (int i = 0; i < blueprint.resources(); i++) {
			int am = (int) (blueprint.areaCost(i, upgrade)*ff*(1-getDegrade()));
			if (am > 0)
				THINGS().resources.create(mX(), mY(), blueprint.resource(i), am);
		}
	}
	
	private void releaseItem(int tx, int ty, boolean all) {
		
		FurnisherItem it = da().item.get(tx, ty);
		if (it == null || !da().isMaster.is(tx, ty))
			return;
		
		
		boolean constructed = true;
		
		int x1 = tx-it.firstX();
		int y1 = ty-it.firstY();
		
		for (int y = 0; y < it.height() && constructed; y++) {
			for (int x = 0; x < it.width(); x++) {
				if (!it.is(x, y))
					continue;
				if (!is(x1+x, y1+y)) {
					LOG.ln(blueprint.blue().info.name + " " + it.group.name + " " + it.rotation + " " + it.width() + " " + it.height());
				}
				if (dConstructed.get(x1+x, y1+y) == 0) {
					constructed = false;
					break;
				}
			}
		}
		
		if (all || !constructed) {
			for (int y = 0; y < it.height(); y++) {
				for (int x = 0; x < it.width(); x++) {
					if (!it.is(x, y))
						continue;
					dConstructed.set(this, x1+x, y1+y, 0);
				}
			}

			for (int i = 0; i < blueprint.resources(); i++) {
				double a = resMul*it.cost2(i, upgrade)*(1-getDegrade());
				int am = (int) a;
				if (RND.rFloat() < a-am)
					am++;
				if (am > 0) {
					THINGS().resources.create(tx, ty, blueprint.resource(i), am);
					GAME.player().res().inc(blueprint.resource(i), RTYPE.CONSTRUCTION, am);
				}
				
			}
		}
		
		
	}
	
	@Override
	public TmpArea remove(int tx, int ty, boolean scatter, Object iser, boolean force) {

		SETT.ROOMS().stats.broken().remove(mX(), mY());
		releaseResources(true);
		for (COORDINATE c : body()) {
			if (!is(c))
				continue;
			jobClear(c.x(), c.y());
		}
		
		TmpArea a = delete(tx, ty, iser);
		//a.setRemoveFloor();
		for (COORDINATE c : a.body()) {
			if (a.is(c)) {
				int d = ConstructionData.dData.get(c);
				SETT.ROOMS().data.set(a, c, d);
			}
		}
		((ConstructionBlueprint)blueprint()).remove(this);
		return a;


	}
	
	public double getDegrade() {
		degI = this;
		return deg.get();
	}

	@Override
	public void jobToggle(boolean toggle) {
		active = toggle;
	}

	@Override
	public boolean jobToggleIs() {
		return active;
	}

	@Override
	public ROOM_DEGRADER degrader(int tx, int ty) {
//		degI = this;
//		return deg;
		return null;
	}

	@Override
	public boolean needsFertilityToBeCleared(int tx, int ty) {
		if (dFloored.get(tx, ty) == 1)
			return false;
		
		return blueprint.removeFertility();
	}
	
	@Override
	public boolean needsTerrainToBeCleared(int tx, int ty) {
		if (TERRAIN().CAVE.is(tx, ty))
			return false;
		if (SETT.TERRAIN().get(tx, ty).roofIs()) {
			if (blueprint.mustBeOutdoors())
				return true;
			return false;
		}
		
		if (structureI != -1 && SETT.TERRAIN().BUILDINGS.all().get(structureI).roof.is(tx, ty))
			return false;
		if (SETT.TERRAIN().NADA.is(tx, ty))
			return false;
		return blueprint.removeTerrain(tx, ty);
	}
	
	@Override
	public Furnisher constructor() {
		return blueprint;
	}
	
	private static MapDataF da() {
		return SETT.ROOMS().fData;
	}
	
	void debug(GBox box) {
		box.add(box.text().add("nRes: ").add(resourcedNeeded));
		box.add(box.text().add("nBuild: ").add(builtNeeded).add('/').add(builtTotal));
		box.add(box.text().add("nStruc: ").add(structuresNeeded));
		box.add(box.text().add("nClear: ").add(clearingNeeded));
		box.add(box.text().add("nFloors: ").add(floorsNeeded));
	}

	@Override
	public SPRITE icon() {
		return blueprint.blue().iconBig();
	}

	@Override
	public boolean becomesSolid(int tx, int ty) {
		if (clearingNeeded == 0 && structuresNeeded == 0 && resourcedNeeded == 0 && floorsNeeded == 0) {
			FurnisherItemTile t = da().tile.get(tx, ty);
			if (t != null && t.availability.player < 0)
				return true;
		}
		return false;
	}

	@Override
	public int totalResourcesNeeded(int tx, int ty) {
		if (clearingNeeded != 0) {
			return 1;
		}
		
		if (structuresNeeded > 0) {
			TBuilding t = TERRAIN().BUILDINGS.all().get(structureI);
			return t.structure.resAmount - dWorkAmount.get(tx, ty);
		}
		
		if (resourcedNeeded > 0) {
			int am = dResAllocated.get(tx, ty);
			for (int i = 0; i < blueprint.resources(); i++) {
				am -= dResourceNeeded[i].get(tx, ty);
				if (am < 0) {
					return -am;
				}
			}
		}
		
		return 1;
	}
	
	public RoomState getState() {
		return state;
	}

	public RoomState makeState(int tx, int ty, boolean preserve) {
		return state;
	}
	
	
	private static ConstructionInstance degI;
	
	private static ROOM_DEGRADER deg = new ROOM_DEGRADER() {
		
		@Override
		protected void setData(int v, boolean realChange) {
			degI.degrade = v;
		}
		
		@Override
		public int resSize() {
			return degI.blueprint.resources();
		}
		
		@Override
		public int resAmount(int i) {
			return 1;
		}
		
		@Override
		public RESOURCE res(int i) {
			return degI.blueprint.resource(i);
		}
		
		@Override
		public int getData() {
			return degI.degrade;
		}
		
		@Override
		public double degRate() {
			return 0;
		}

		@Override
		public int roomArea() {
			return degI.area();
		}

		@Override
		public double base() {
			return 0;
		}

		@Override
		public double expenseRate() {
			return 0;
		}

		@Override
		public double rate(double bonus) {
			return 0;
		}

		@Override
		public int jobs() {
			return 0;
		}
	};

	@Override
	public int resAmount(int ri, int upgrade) {
		return 0;
	}

	@Override
	public boolean is(int tile) {
		return SETT.ROOMS().map.indexGetter.get(tile) == roomI;
	}

	@Override
	public int upgrade() {
		return upgrade;
	}

	@Override
	public boolean isJobActive() {
		return active;
	}

}
