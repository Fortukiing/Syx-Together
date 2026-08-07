package settlement.stats.colls;

import static settlement.main.SETT.ROOMS;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.GAME;
import init.race.RACES;
import init.race.Race;
import init.resources.RBIT;
import init.resources.RBIT.RBITImp;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.NEEDS;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.stats.Induvidual;
import settlement.stats.POP;
import settlement.stats.StatsInit;
import settlement.stats.stat.STAT;
import settlement.stats.stat.STATData;
import settlement.stats.stat.STATFakeRace;
import settlement.stats.stat.StatCollection;
import settlement.stats.stat.StatDecree;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.info.INFO;
import util.race.PERMISSION;
import util.text.D;

public class StatsFood extends StatCollection{
	
	public static final int MAX_RATIONS = 4;
	
	public final STAT FOOD_PREFFERENCE;
	public final STAT FOOD_DAYS;
	public final STAT FOOD;
	public final STAT DRINK;
	public final STAT DRINK_PREFFERENCE;
	public final STAT STARVATION;
	
	private final All[] foodAllowed = new All[HCLASSES.ALL().size()];
	private final LIST<PERMISSION> food;
	
	private static CharSequence ¤¤name = "Food";
	private static CharSequence ¤¤desc = "Stats related to food and hunger.";
	
	static {
		D.ts(StatsFood.class);
	}
	
	public StatsFood(StatsInit init){
		super(init, "FOOD", ¤¤name, ¤¤desc);
		D.gInit(this);
		
		STARVATION = new STATData("STARVATION", init, init.count.new DataBit("FOOD_STARVE"));
		FOOD_PREFFERENCE = new STATData("FOOD_PREFFERENCE", init, init.count.new DataNibble("FOOD_PREF"));
		init.onArrivalStats.add(FOOD_PREFFERENCE);
		for (int i = 0; i < foodAllowed.length; i++) {
			foodAllowed[i] = new All();
		}
			
		FOOD_DAYS = new STATFakeRace("FOOD_DAYS", init) {
			
			private double am;
			private int lastT = -1;
			
			@Override
			public int dataDivider() {
				return 24;
			}

			@Override
			protected double getDD(Race race) {
				if (GAME.updateI() == lastT)
					return am;
				
				lastT = GAME.updateI();
				
				double a = 0;
				for (int ei = 0; ei < RESOURCES.EDI().all().size(); ei++) {
					ResG r = RESOURCES.EDI().all().get(ei);
					double aa = ROOMS().STOCKPILE.tally().amountTotal(r.resource);
					a+= aa;
				}
				
				for (int ri = 0; ri < SETT.ROOMS().EATERIES.size(); ri++) {
					ROOM_EATERY e = SETT.ROOMS().EATERIES.get(ri);
					double aa = e.totalFood();
					a += aa;
				}
				
				for (int ri = 0; ri <  SETT.ROOMS().CANTEENS.size(); ri++) {
					ROOM_CANTEEN e = SETT.ROOMS().CANTEENS.get(ri);
					double aa = e.totalFood();
					a += aa;
				}
				
				double needed = 0;
				
				for (int ci = 0; ci < HCLASSES.ALL().size(); ci++) {
					HCLASS c = HCLASSES.ALL().get(ci);
					if (c.player) {
						for (int ri = 0; ri < RACES.all().size(); ri++) {
							Race r = RACES.all().get(ri);
							needed += NEEDS.TYPES().HUNGER.rate.get(c.get(r))*POP.physical(c, r)*FOOD.decree().get(c, r);
							
						}
					}
				}
				
				
				
				if (needed == 0)
					am =  a > 0 ? 1 : 0;
				else
					am = (a / needed);
				am /= dataDivider();
				return am;
			}
		};
		FOOD_DAYS.info().setInt();
		FOOD_DAYS.info().setMatters(true, false);
		
		StatDecree d = new StatDecree("FOOD_RATIONS_DECREE", init, 2, MAX_RATIONS, 2, D.g("RationsT", "Target Food servings."), 1);
		d.setInt();
		

		FOOD = new STATData("FOOD_RATIONS", init, init.count.new DataNibble("FOOD_RATIONS", MAX_RATIONS-1));
		FOOD.addDecree(d);
		init.onArrivalStats.add(FOOD);
		
		d = new StatDecree("DRINK_RATION_DECREE", init, 2, MAX_RATIONS, 2, D.g("DrinkT", "Target Drink servings"), 1);
		d.setInt();
		DRINK = new STATData("DRINK_RATIONS", init, init.count.new DataNibble("DRINK_RATIONS", 5));
		DRINK.addDecree(d);
		DRINK_PREFFERENCE = new STATData("DRINK_PREFFERENCE", init, init.count.new DataNibble("DRINK_PREF"));
		init.onArrivalStats.add(DRINK_PREFFERENCE);
		
		for (All bb : foodAllowed)
			bb.clear();
				
		init .savers.put("FOOD_ALLOWED", new SAVABLE() {
			
			@Override
			public void save(FilePutter file) {
				HCLASSES.MAP().saver().save(foodAllowed, file);
			}
			
			@Override
			public void load(FileGetter file) throws IOException {
				HCLASSES.MAP().loader().load(foodAllowed, file);
			}
			
			@Override
			public void clear() {
				for (All bb : foodAllowed)
					bb.clear();
			}
		});
		
		LIST<RESOURCE> perm = RESOURCES.EDI().res().join(RESOURCES.DRINKS().res());
		ArrayList<PERMISSION> food = new ArrayList<>(perm.size());
		for (RESOURCE res : perm) {
			final int foodIndex = food.size();
			food.add(new PERMISSION() {
				
				@Override
				public void set(HCLASS cl, Race race, boolean value) {
					if (race == null) {
						for (int ri = 0; ri < RACES.all().size(); ri++) {
							set(cl, RACES.all().get(ri), value);
						}
						return;
					}
					if (value)
						foodAllowed[cl.index()].foodAllowed[race.index].or(res);
					else
						foodAllowed[cl.index()].foodAllowed[race.index].clear(res);
					CoopRuntime.foodPermissionChanged(foodIndex, cl, race, value);
				}
				
				@Override
				public INFO info() {
					return res;
				}
				
				@Override
				public boolean get(HCLASS cl, Race race) {
					if (race == null) { 
						for (int ri = 0; ri < RACES.all().size(); ri++) {
							if (get(cl, RACES.all().get(ri)))
								return true;
						}
						return false;
					}
					return (foodAllowed[cl.index()].foodAllowed[race.index].has(res));
				}
			});
		}
		this.food = food;
	}
	
	public PERMISSION foodAllowed(ResG e) {
		return food.get(e.index());
	}
	
	public PERMISSION drinkAllowed(ResG e) {
		return food.get(e.index());
	}
	
	public PERMISSION allowed(int index) {
		return food.get(index);
	}
	
	public RBIT fetchMask(Humanoid h) {
		return foodAllowed[h.indu().hType().parentClass().index()].foodAllowed[h.race().index];
	}

	public void eat(Humanoid a, int level, double preference) {
		Induvidual i = a.indu();
		NEEDS.TYPES().HUNGER.stat().fix(a.indu());
		FOOD.indu().set(i, Math.max(level-1, 0));
		FOOD_PREFFERENCE.indu().setD(i, preference);
	}
	
	public void drink(Humanoid a, int level, double preference) {
		Induvidual i = a.indu();
		
		DRINK.indu().set(i, level);
		DRINK_PREFFERENCE.indu().setD(i, preference);
	}

	private static class All implements SAVABLE {
		
		public final RBITImp[] foodAllowed = new RBITImp[RACES.all().size()];

		All(){
			for (int i = 0; i < foodAllowed.length; i++)
				foodAllowed[i] = new RBITImp();
		}
		
		@Override
		public void save(FilePutter file) {
			RACES.map().saver().save(foodAllowed, file);
		}

		@Override
		public void load(FileGetter file) throws IOException {
			RACES.map().loader().load(foodAllowed, file);
		}

		@Override
		public void clear() {
			for (RBITImp b : foodAllowed)
				b.setAll();
			
		}
		
	}
	
	
}
