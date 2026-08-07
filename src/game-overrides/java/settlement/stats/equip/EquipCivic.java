package settlement.stats.equip;

import java.io.IOException;

import coopmod.CoopRuntime;
import init.paths.PATH;
import init.race.RACES;
import init.race.Race;
import init.type.HCLASS;
import init.type.HCLASSES;
import settlement.stats.Induvidual;
import settlement.stats.StatsInit;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.LISTE;
import util.keymap.RMapInt.RMapIntTwo;
import util.text.D;

public class EquipCivic extends Equip {
	
	private final RMapIntTwo<HCLASS, Race> tars = new RMapIntTwo<>(HCLASSES.MAP(), RACES.map());
	static CharSequence ¤¤more = "We would like to be allowed to wear more {0}.";
	
	static {
		D.ts(EquipCivic.class);
	}
	
	EquipCivic(String key, PATH path, LISTE<Equip> all, LISTE<EquipCivic> type, StatsInit init) {
		super("CIVIC", key, path, all, init);
		type.add(this);
		
		for (HCLASS cl: HCLASSES.ALL())
			for (Race race: RACES.all())
				tars.get(cl).set(race, targetDefault);
		
		
		
		stat.info().setOpinion(¤¤more, null);
		
		SAVABLE sa = new SAVABLE() {
			
			@Override
			public void save(FilePutter file) {
				tars.save(file);
			}
			
			@Override
			public void load(FileGetter file) throws IOException {
				tars.load(file);
			}
			
			@Override
			public void clear() {
				tars.setAll(targetDefault);
			}
		};
		sa.clear();
		
		init.savers.put(key + "_TAR", sa);
		
	}

	@Override
	public int target(Induvidual h) {
		return CLAMP.i(tars.get(h.clas()).get(h.race()), 0, max());
	}
	
	public int target(HCLASS c, Race type) {
		if (type == null) {
			int m = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				m = Math.max(m, target(c, r));
			}
			return m;
		}
		return CLAMP.i(tars.get(c).get(type), 0, max());
	}
	
	public void targetSet(int target, HCLASS c, Race type) {
		if (type == null) {
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				targetSet(target, c, r);
			}
			return;
		}
		target = CLAMP.i(target, 0, equipMax);
		tars.get(c).set(type, target);
		CoopRuntime.civicEquipmentTargetChanged(this, c, type, target);
	}
	
	
	public int max() {
		return equipMax;
	}

	@Override
	public int max(Induvidual i) {
		return equipMax;
	}

	@Override
	public double bValue(double equipped) {
		return equipped;
	}



	
}
