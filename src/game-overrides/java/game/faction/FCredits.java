package game.faction;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.boosting.BOOSTABLES;
import game.time.TIME;
import game.time.TIMECYCLE;
import init.trade.TRADABLE;
import init.type.HCLASSES;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import util.data.DOUBLE;
import util.statistics.HISTORY_INT;
import util.statistics.HistoryInt;
import util.text.D;
import util.text.Dic;

public class FCredits extends FactionResource implements DOUBLE{

	protected double credits;


	
	private static CharSequence ¤¤Treasury = "¤Treasury";
	private static CharSequence ¤¤TreasuryD = "¤The amount of Denari at disposal.";
	
	private static CharSequence ¤¤TRADE = "Trade";
	private static CharSequence ¤¤TRADED = "Money that flow through imports and exports.";
	
	private static CharSequence ¤¤INFLATION = "Inflation";
	private static CharSequence ¤¤INFLATIOND = "Over time, Inflation adds credits to a negative treasury and removes credits from a positive one."; 
	private static CharSequence ¤¤MISC = "Misc"; 
	private static CharSequence ¤¤MISCD = "Special sources";
	private static CharSequence ¤¤TRIBUTE = "Tribute";
	private static CharSequence ¤¤TRIBUTED = "Denari spent/gained from paying off other armies and factions.";
	
	private static CharSequence ¤¤DIPLOMACYD = "Denari spent/gained from diplomacy with other factions.";
	private static CharSequence ¤¤MERCINARIES = "Mercenaries";
	private static CharSequence ¤¤MERCINARIESD = "Mercenaries can be conscripted into your armies and cost credits to upkeep each day.";
	private static CharSequence ¤¤TOURISM = "Tourism";
	private static CharSequence ¤¤TOURISMD = "Tourists that visit your city will give you some money at the end of their stay.";
	private static CharSequence ¤¤CONSTRUCTION = "Construction";
	private static CharSequence ¤¤CONSTRUCTIOND = "Construction of buildings in your kingdom.";
	private static CharSequence ¤¤TAX = "Tax";
	private static CharSequence ¤¤TAXD = "Taxation from the realm.";
	private static CharSequence ¤¤SLAVESD = "Transactions from slave trade.";
	;
	
	static {
		D.ts(FCredits.class);
	}
	
	private final HistoryInt creditsH;

	
	
	public FCredits(int saved, TIMECYCLE time) {

		creditsH = new HistoryInt(¤¤Treasury, ¤¤TreasuryD, saved, time, true);
		
		
	}
	
	@Override
	protected void save(FilePutter file) {
		file.d(credits);
		creditsH.save(file);
	}


	@Override
	protected void load(FileGetter file) throws IOException {
		credits = file.d();
		creditsH.load(file);
	}


	@Override
	protected void clear() {
		credits = 0;
		creditsH.clear();
	}

	@Override
	protected void update(double ds, Faction f) {
		
		double inf = credits*0.2*ds/(TIME.years().bitSeconds()*BOOSTABLES.CIVICS().DEFALTION.get(f));
		int i = (int) inf;
		
		if (Math.abs(inf-i) > RND.rFloat()) {
			i+= Math.signum(i);
		}
		
		inc(-i, CTYPE.INFLATION);
		
	}
	
	public HISTORY_INT creditsH() {
		return creditsH;
	}
	
	public double credits() {
		return credits;
	}
	
	@Override
	public double getD() {
		return credits;
	}
	
	protected void inccc(double amount) {
		credits += amount;
		creditsH.set((int) credits);
		CoopRuntime.playerCreditsChanged(this, credits);
	}
	
	public void set(double amount) {
		credits = amount;
		CoopRuntime.playerCreditsChanged(this, credits);
	}
	
	public void inc(double amount, CTYPE t) {
		inccc(amount);
	}
	
	public void inc(double amount, CTYPE t, TRADABLE res, int resAm) {
		inccc(amount);
	}
	

	
	public enum CTYPE {
		
		
		TRADE(¤¤TRADE, ¤¤TRADED),
		INFLATION(¤¤INFLATION, ¤¤INFLATIOND),
		MISC(¤¤MISC, ¤¤MISCD),
		TRIBUTE(¤¤TRIBUTE, ¤¤TRIBUTED),
		
		DIPLOMACY(Dic.¤¤Diplomacy, ¤¤DIPLOMACYD),
		MERCINARIES(¤¤MERCINARIES, ¤¤MERCINARIESD),
		TOURISM(¤¤TOURISM, ¤¤TOURISMD),
		CONSTRUCTION(¤¤CONSTRUCTION, ¤¤CONSTRUCTIOND),
		TAX(¤¤TAX, ¤¤TAXD),
		SLAVES(HCLASSES.SLAVE().name, ¤¤SLAVESD),
		;
		
		public final CharSequence name;
		public final CharSequence desc;
		
		CTYPE(CharSequence name, CharSequence desc){
			this.name = name;
			this.desc = desc;
		}
		
	}
	
}
