package settlement.stats.equip;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.battle.Armies;
import game.battle.div.Div;
import init.constant.C;
import init.constant.Config;
import init.paths.PATH;
import init.paths.PATHS;
import init.race.RACES;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import settlement.entity.animal.AnimalSpecies;
import settlement.main.SETT;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import settlement.stats.StatsInit;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.ColorImp;
import snake2d.util.datatypes.DIR;
import snake2d.util.file.Json;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayListGrower;
import snake2d.util.sets.Bitsmap1D;
import snake2d.util.sets.KeyMap;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LISTE;
import snake2d.util.sprite.SPRITE;
import snake2d.util.sprite.TILE_SHEET;
import util.rendering.ShadowBatch;
import util.spritecomposer.ComposerDests;
import util.spritecomposer.ComposerSources;
import util.spritecomposer.ComposerThings.ITileSheet;
import util.spritecomposer.ComposerUtil;
import util.text.D;

public class EquipBattle extends Equip {

	private final Bitsmap1D tars = new Bitsmap1D(0, 8, Armies.ARMIES * Config.battle().DIVISIONS_PER_ARMY);
	private final int iMil;
	public final int amountInGarrison;
	public final double[] slotUse;
	
	public final DivSprite[] sprites = new DivSprite[RACES.all().size()];
	public static CharSequence ¤¤combineProblem = "Can not be combined with current equipment.";
	public static CharSequence ¤¤raceProblem = "This equipment is not applicable for the selected race.";
	public final HumanSprite sprite;
	public final AnimalSpecies mount;
	public final int formationAdd;
	public final static int SLOTS = 8;
	
	static {
		D.ts(EquipBattle.class);
	}

	EquipBattle(String coll, String key, PATH path, LISTE<Equip> all, LISTE<EquipBattle> mil, StatsInit init,
			KeyMap<TILE_SHEET> spriteMap) throws IOException {
		super(coll, key, path, all, init);

		iMil = mil.add(this);

		Json j = new Json(path.gets(key));
		if (RESOURCES.SUP().get(resource) != null)
			j.error("Can not have an equippable that is also a regular army supply!", resource.key);
		amountInGarrison = j.i("AMOUNT_IN_GARRISON", 0, equipMax);
		init.savers.put(coll + "_" + key + "_tars", tars);
		slotUse = j.ds("SLOT_USAGE", SLOTS);
		stat.info().setMatters(false, true);

		for (int i = 0; i < sprites.length; i++) {
			sprites[i] = new DivSprite();
			sprites[i].read(j.json("DIV_SPRITE"));
		}

		if (j.has("SPRITE")) {
			this.sprite = new HumanSprite(j, spriteMap);
		} else
			this.sprite = null;
		if (j.has("MOUNTED_ANIMAL")) {
			mount = SETT.ANIMALS().map.read("MOUNTED_ANIMAL", j);
		}else
			mount = null;
		
		formationAdd = j.i("ADD_TO_FORMATION_SIZE", 0, 100, 0);
	}
	



	@Override
	public int target(Induvidual h) {
		Div i = STATS.BATTLE().DIV.get(h);
		if (i != null) {
			if (SETT.ROOMS().GUARD.activeDuty.is(i) || i.settings().mustering())
				return target(i);
		}
		
		return 0;
		
	}

	@Override
	public double bValue(double equipped) {
		equipped = CLAMP.d(equipped, 0, 1);
		return equipped;
	}
	
	@Override
	public int max(Induvidual i) {
		return equipMax;
	}

	public int target(Div d) {
		return CLAMP.i(tars.get(d.index()), 0, equipMax);
	}

	public void targetSet(Div d, int t) {
		int target = CLAMP.i(t, 0, equipMax);
		tars.set(d.index(), target);
		CoopRuntime.divisionEquipmentChanged(d, this, target);
	}

	public int max() {
		return equipMax;
	}

	public int indexMilitary() {
		return iMil;
	}

	public int garrisonAmount() {
		return amountInGarrison;
	}

	public double slotUse(int slot) {
		return slotUse[slot];
	}

	@Override
	protected void hoverP(GUI_BOX box) {
		super.hoverP(box);
	}
	


	public static class DivSprite {

		public int ox = 0;
		public int oy = 0;
		public int z = 0;
		public SPRITE icon = UI.icons().s.cancel;
		public LIST<ColorImp> cols = new ArrayListGrower<ColorImp>();

		public void read(Json json) throws IOException {
			ox = json.i("X", -100, 100, 0);
			oy = json.i("Y", -100, 100, 0);
			z = json.i("Z", -100, 100, 0);
			icon = UI.icons().get(json, UI.icons().s.cancel);
			cols = ColorImp.cols(json);
		}
	}

	public final class HumanSprite {
		
		public final double offsetX;
		public final double offsetY;
		public final double animationX;
		public final double animationY;

		private TILE_SHEET sheet;
		public LIST<ColorImp> cols = new ArrayListGrower<ColorImp>();

		private HumanSprite(Json json, KeyMap<TILE_SHEET> map) throws IOException {
			json = json.json("SPRITE");

			offsetX = json.d("OFFSET_X", -100, 100);
			offsetY = json.d("OFFSET_Y", -100, 100);
			animationX = json.d("ANIMATION_DX", -100, 100);
			animationY = json.d("ANIMATION_DY", -100, 100);

			String file = json.value("FILE");
			if (!map.containsKey(file)) {

				TILE_SHEET sheet = new ITileSheet(PATHS.SPRITE().getFolder("race").getFolder("battle").get(file), 132,
						36) {

					@Override
					protected TILE_SHEET init(ComposerUtil c, ComposerSources s, ComposerDests d) {
						s.singles.init(0, 0, 1, 1, 2, 1, d.s24);
						s.singles.setSkip(0, 2).paste(3, true);
						return d.s24.saveGame();
					}
				}.get();
				map.put(file, sheet);

			}
			sheet = map.get(file);
			cols = ColorImp.cols(json);
		}
		
		public void render(Induvidual a, SPRITE_RENDERER r, DIR dir, double forward, int x, int y, ShadowBatch s) {
			double am = get(a);
			if (am == 0)
				return;
			
			ColorImp.TMP.interpolate(cols, am/max());
			ColorImp.TMP.bind();
			
			int t = dir.id();

			x += C.SCALE*12;
			y += C.SCALE*12;
			
			
			double rotY = dir.xN()*offsetX + dir.yN()*offsetY;
			double rotX = -dir.yN()*offsetX + dir.xN()*offsetY;
			
			double aY = dir.xN()*animationX + dir.yN()*animationY;
			double aX = -dir.yN()*animationX + dir.xN()*animationY;
			
			int cx = (int) ((rotX+aX*forward)*(a.race().physics.hitBoxsize()));
			int cy = (int) ((rotY+aY*forward)*(a.race().physics.hitBoxsize()));
			
			sheet.renderC(r, t, x+cx, y+cy);
			s.setHeight(0).setDistance2Ground(a.race().physics.height()/2);
			
			sheet.renderC(s, t, x+cx, y+cy);
		}

	}

}
