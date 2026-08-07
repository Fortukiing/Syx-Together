package game.battle.div;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.GAME;
import game.battle.DivisionBanners.DivisionBanner;
import game.battle.util.DIV_SPEC.DIV_SPECE;
import game.faction.FACTIONS;
import game.faction.Faction;
import init.race.RACES;
import init.race.Race;
import settlement.main.SETT;
import settlement.room.military.training.ROOM_M_TRAINER;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBattle.StatTraining;
import settlement.stats.equip.EquipBattle;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.misc.CLAMP;
import snake2d.util.sprite.text.Str;
import util.data.DOUBLE.DoubleImp;
import util.text.Dic;

public final class DivInfo implements DIV_SPECE{
	
	private final Str name = new Str(32);
	private final Div div;
	private int menTarget;
	private int raceI;
	private int exMin;
	private int symbolI;
	private final DoubleImp[] trains = new DoubleImp[ROOM_M_TRAINER.ALL().size()];
	
	DivInfo(Div div){
		this.div = div;
		raceI = 0;
		symbolI = div.index();
		name.clear().add(Dic.¤¤Division).add(' ').add('#').add(div.index());
		for (int i = 0; i < trains.length; i++)
			trains[i] = new DoubleImp();
	}
	
	public Div div() {
		return div;
	}
	
	@Override
	public int men() {
		return menTarget;
	}
	
	@Override
	public void menSet(int am) {
		if (div.army() == GAME.ARMIES().player()) {
			SETT.BATTLE().info.clearTargets();
			
		}
		menTarget = CLAMP.i(am, 0, div.men.freeSpots() + div.menNrOf());
		CoopRuntime.divisionMenChanged(div, menTarget);
	}
	
	@Override
	public double equip(EquipBattle e) {
		return (double)e.target(div)/e.equipMax;
	}
	
	@Override
	public void equipSet(EquipBattle e, double d) {
		e.targetSet(div, (int) Math.round(d*e.max()));
	}
	
	@Override
	public double training(StatTraining e) {
		return trains[e.room.INDEX_TRAINING].getD();
	}
	
	@Override
	public void trainingSet(StatTraining e, double d) {
		trains[e.room.INDEX_TRAINING].setD(d);
		CoopRuntime.divisionTrainingChanged(div, e, d);
	}
	
	@Override
	public double experience() {
		return STATS.BATTLE().COMBAT_EXPERIENCE.div().getD(div);
	}
	
	@Override
	public Faction faction() {
		return FACTIONS.player();
	}
	
//	public DoubleImp trainingD(ROOM_M_TRAINER<?> room) {
//		return trains[room.INDEX_TRAINING];
//	}
	
	@Override
	public Race race() {
		return RACES.all().get(raceI);
	}
	
	@Override
	public void raceSet(Race race) {
		int men = men();
		menSet(0);
		raceI = race.index;
		menSet(men);
		CoopRuntime.divisionRaceChanged(div, race);
	}
	
	public DivisionBanner banner() {
		return GAME.ARMIES().banners.get(symbolI);
	}
	
	@Override
	public Str name() {
		return name;
	}
	
	@Override
	public int bannerI() {
		return symbolI;
	}
	

	
//	public final GETTERE<Race> race = new GETTERE<Race>() {
//
//		@Override
//		public Race get() {
//			return RACES.all().get(raceI);
//		}
//
//		@Override
//		public void set(Race t) {
//			int i = men.get();
//			men.set(0);
//			raceI = t.index;
//			men.set(i);
//		}
//	
//	};
	
	

	final SAVABLE saver = new SAVABLE() {
		
		@Override
		public void save(FilePutter file) {
			for (DoubleImp t : trains) {
				t.save(file);
			}
				
			
			file.i(menTarget);
			file.i(raceI);
			file.i(exMin);
			file.i(symbolI);
			name.save(file);
		}
		
		@Override
		public void load(FileGetter file) throws IOException {
			for (DoubleImp t : trains) {
				t.load(file);
			}
			menTarget = file.i();
			raceI = file.i();
			exMin = file.i();
			symbolI = file.i();
			name.load(file);
		}
		
		@Override
		public void clear() {
			for (DoubleImp t : trains) {
				t.setD(0);
			}
			menTarget = 0;
			raceI = FACTIONS.player().race().index;
			exMin = 0;
			symbolI = div.index();
			name.clear().add(Dic.¤¤Division).add(' ').add('#').add(div.index());
		}
	};

	@Override
	public void experienceSet(double experience) {
		
	}

	@Override
	public Str nameE() {
		return name;
	}

	@Override
	public void bannerISet(int bannerI) {
		symbolI = bannerI;
		CoopRuntime.divisionBannerChanged(div, bannerI);
	}

	@Override
	public void factionSet(Faction faction) {
		// TODO Auto-generated method stub
		
	}


	
}
