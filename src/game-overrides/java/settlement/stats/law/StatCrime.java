package settlement.stats.law;

import java.io.IOException;
import java.util.Arrays;

import coopmod.CoopRuntime;
import game.VERSION;
import game.boosting.BOOSTABLES;
import game.time.TIME;
import init.race.RACES;
import init.race.Race;
import init.sprite.UI.UI;
import init.type.CRIMES;
import init.type.CRIMES.CRIME;
import init.type.CRIMES.Response;
import init.type.CRIME_PUNISHMENTS;
import init.type.CRIME_PUNISHMENTS.PUNISHMENT;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import settlement.stats.Induvidual;
import settlement.stats.POP;
import settlement.stats.STATS;
import settlement.stats.StatsInit;
import settlement.stats.standing.STANDINGS;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.gui.GUI_BOX;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.info.GFORMAT;
import util.statistics.HISTORY_COLLECTION;
import util.statistics.HistoryObject;
import util.text.D;
import util.text.Dic;

public class StatCrime {

	private static CharSequence ¤¤custody = "In Custody";
	private static CharSequence ¤¤crimes = "Crimes (today/yearly)";
	private static CharSequence ¤¤arrests = "Arrests (today/yearly)";
	private static CharSequence ¤¤current = "Decreed Punishment:";
	private static CharSequence ¤¤effect = "Administering a punishment for this crime has the following max potential effect:";
	private static CharSequence ¤¤currentGood = "The current punishment for this crime is is good will increase net {0}!";
	private static CharSequence ¤¤currentBad = "The current punishment for this crime too harsh, and the net {0} will decrease. Get more guards and other law boost in order to benefit from the current combination.";
	
	static {
		D.ts(StatCrime.class);
	}
	
	private final HistoryObject<HCLASS_RACE> occurence = new HistoryObject<HCLASS_RACE>(STATS.DAYS_SAVED, TIME.days(),
			false, HCLASS_RACE.MAP());
	private final HistoryObject<HCLASS_RACE> caught = new HistoryObject<HCLASS_RACE>(STATS.DAYS_SAVED, TIME.days(),
			false, HCLASS_RACE.MAP());

	int criminalsTot;
	final int[] criminals = Alloc.ii(RACES.all().size());
	
	public final CRIME crime;
	private final int[] autoPunishment = Alloc.ii(HCLASS_RACE.ALL().size());
	private final CrimesData data;
	private final double[] recentPunishments = new double[HCLASS_RACE.ALL().size()*CRIME_PUNISHMENTS.ALL().size()];
	private final double[] freedom = new double[HCLASS_RACE.ALL().size()];
	private final double[] loyalty = new double[HCLASS_RACE.ALL().size()];
	
	StatCrime(StatsInit init, CRIME type, CrimesData data) {
		this.crime = type;
		this.data = data;
		init.savers.put("LAW_CRIME_OCCURENCE_" + type.key, occurence);
		init.savers.put("LAW_CRIME_CAUGHT_" + type.key, caught);
		init.savers.put("LAW_CRIME_DATA_" + type.key, new SAVABLE() {

			@Override
			public void save(FilePutter file) {
				HCLASS_RACE.MAP().saver().save(autoPunishment, file);
				occurence.save(file);
				caught.save(file);
				file.dsE(recentPunishments);
				file.dsE(freedom);
				file.dsE(loyalty);
			}

			@Override
			public void load(FileGetter file) throws IOException {
				clear();
				HCLASS_RACE.MAP().loader().load(autoPunishment, file, 0);
				occurence.load(file);
				caught.load(file);
				file.dsE(recentPunishments);
				file.dsE(freedom);
				file.dsE(loyalty);
				if (VERSION.versionIsBefore(71, 23))
					setPunishments();
			}

			@Override
			public void clear() {
				
				criminalsTot = 0;
				Arrays.fill(criminals, 0);
				occurence.clear();
				caught.clear();
				Arrays.fill(recentPunishments, 0);
				Arrays.fill(freedom, 0);
				Arrays.fill(loyalty, 0);
				setPunishments();
			}
		});
		
		setPunishments();
		
	}
	
	private void setPunishments() {
		
		Arrays.fill(autoPunishment, 0);
		for (Race r : RACES.all()) {
			autoPunishment[HCLASSES.OTHER().get(r).index] = CRIME_PUNISHMENTS.STOCKS().index();
		}
		if (crime == CRIMES.PERSECUTED(HCLASSES.CITIZEN())) {
			for (Race r : RACES.all()) {
				autoPunishment[HCLASSES.CITIZEN().get(r).index] = CRIME_PUNISHMENTS.STOCKS().index();
			}
		}

	}
	
	public double recentPunishment(HCLASS cl, Race race, PUNISHMENT p) {
		if (race == null) {
			double pop = 0;
			double res = 0;
			for (Race r : RACES.all()) {
				double pp = POP.pop(cl, r);
				pop+= pp;
				res += recentPunishment(cl, r, p)*pp;
			}
			if (pop == 0)
				return 0;
			return res/pop;
		}
		return recentPunishments[p.index()*CRIME_PUNISHMENTS.ALL().size() + HCLASS_RACE.clP(race, cl).index()];
	}
	
	public void punish(HCLASS cl, Race race, PUNISHMENT punishment) {
		
		double dd = 1.0/CRIME_PUNISHMENTS.get(cl).size();
		HCLASS_RACE cc = HCLASS_RACE.clP(race, cl);
		recentPunishments[punishment.index()*CRIME_PUNISHMENTS.ALL().size() + cc.index()] ++;
		for (PUNISHMENT p : CRIME_PUNISHMENTS.ALL()) {
			int i = p.index()*CRIME_PUNISHMENTS.ALL().size() + cc.index();
			recentPunishments[i] -= dd;
			if (recentPunishments[i] < 0)
				recentPunishments[i] = 0;
		}
		
		calc(cc);
		
	}
	
	private void calc(HCLASS_RACE cc) {
		double tot = 0;
		for (PUNISHMENT p : CRIME_PUNISHMENTS.get(cc.cl)) {
			int i = p.index()*CRIME_PUNISHMENTS.ALL().size() + cc.index();
			tot += recentPunishments[i];
		}
		double freedom = 0;
		double loyalty = 0;

		if (tot > 0) {
			for (PUNISHMENT p : CRIME_PUNISHMENTS.get(cc.cl)) {
				int i = p.index()*CRIME_PUNISHMENTS.ALL().size() + cc.index();
				double v = recentPunishments[i]/tot;
				freedom += v*p.tyranny(cc.cl, cc.race);
				loyalty += v*p.law(cc.cl, cc.race);
			}
		}
		
		this.freedom[cc.index] = freedom;
		this.loyalty[cc.index] = loyalty;
	}

	public double law(HCLASS cl, Race race) {
		return lawValue(cl, race)*crime.law(cl, race);
	}

	public double tyrrany(HCLASS cl, Race race) {
		return tyrranyValue(cl, race)*crime.tyrrany(cl, race);
	}
	
	public double lawValue(HCLASS cl, Race race) {
		if (race == null) {
			double pop = 0;
			double res = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				double p = STATS.POP().POP.data(cl).get(r);
				pop += p;
				res += p*lawValue(cl, r);
			}
			if (pop == 0)
				return 0;
			else
				return res / pop;
		}
		return loyalty[HCLASS_RACE.clP(race, cl).index()];
	}
	
	public double tyrranyValue(HCLASS cl, Race race) {
		if (race == null) {
			double pop = 0;
			double res = 0;
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				Race r = RACES.all().get(ri);
				double p = STATS.POP().POP.data(cl).get(r);
				pop += p;
				res += p*tyrranyValue(cl, r);
			}
			if (pop == 0)
				return 0;
			else
				return res / pop;
		}
		return freedom[HCLASS_RACE.clP(race, cl).index()];
	}
	
	public StatPunishment punishment(HCLASS cl, Race race) {

//		if (cl == HCLASSES.OTHER())
//			return STATS.LAW().punishments.get(CRIME_PUNISHMENTS.NONE().index());
//		
		
		if (race == null) {
			StatPunishment p = null;
			for (Race r : RACES.all()) {
				StatPunishment pp = punishment(cl, r);
				if (p == null)
					p = pp;
				else if (p != pp)
					return null;
			}
			return p;
		}

		return STATS.LAW().punishments.get(autoPunishment[HCLASS_RACE.clP(race, cl).index]);
	}
	
	public PUNISHMENT punishment(Induvidual i) {
		
		return punishment(STATS.LAW().prisonerType.get(i).cl, i.race()).punish;
	}

	public void punishmentSet(HCLASS cl, Race race, PUNISHMENT p) {

		if (race == null) {
			for (Race r : RACES.all()) {
				punishmentSet(cl, r, p);
			}
			return;
		}

		autoPunishment[HCLASS_RACE.clP(race, cl).index] = p.index();
		CoopRuntime.lawPunishmentChanged(this, cl, race, p);
	}

	public void commit(HCLASS_RACE c, int amount) {
		
		occurence.inc(c, amount);
		data.crimesComitted.inc(c, amount);

		occurence.inc(HCLASS_RACE.clP(null, c.cl), amount);
		data.crimesComitted.inc(HCLASS_RACE.clP(null, c.cl), amount);
		
		occurence.inc(HCLASS_RACE.clP(), amount);
		data.crimesComitted.inc(HCLASS_RACE.clP(), amount);
	}

	public void commit(Induvidual i) {
		commit(i.popCL(), 1);
	}

	public final void catchh(Induvidual i) {
		catchh(i.race());
	}
	
	public void catchh(Race race) {
		caught.inc(HCLASS_RACE.clP(race, crime.cl) , 1);
		caught.inc(HCLASS_RACE.clP(null, crime.cl), 1);
		if (crime.cl.player)
			caught.inc(HCLASS_RACE.clP(), 1);
	}

	public HISTORY_COLLECTION<HCLASS_RACE> occurence() {
		return occurence;
	}

	public HISTORY_COLLECTION<HCLASS_RACE> caught() {
		return caught;
	}

	public int criminals(Race race) {
		if (race == null)
			return criminalsTot;
		return criminals[race.index()];
	}
	


	protected void update(HCLASS_RACE cl, double ds) {
		
		StatPunishment pp = punishment(cl.cl, cl.race);
		
		if (pp != null && pp.punish == CRIME_PUNISHMENTS.PARDON()) {
			double dd = ds/(TIME.secondsPerDay()*16*4);
			
			for (PUNISHMENT p : CRIME_PUNISHMENTS.ALL()) {
				int ii = p.index()*CRIME_PUNISHMENTS.ALL().size() + cl.index();
				if (p == CRIME_PUNISHMENTS.PARDON())
					recentPunishments[ii] += dd;
				else {
					recentPunishments[ii] -= dd;
					if (recentPunishments[ii] < 0)
						recentPunishments[ii] = 0;
				}
			}
			calc(cl);
		}
		
		boolean fix = false;
		for (PUNISHMENT p : CRIME_PUNISHMENTS.ALL()) {
			if (recentPunishments[p.index()*CRIME_PUNISHMENTS.ALL().size() + cl.index()] > 1) {
				fix = true;
				break;
			}
			
		}
		if (fix) {
			for (PUNISHMENT p : CRIME_PUNISHMENTS.ALL()) {
				recentPunishments[p.index()*CRIME_PUNISHMENTS.ALL().size() + cl.index()]*= 0.85;
			}
		}
		
	}
	
	public void hover(GUI_BOX box, HCLASS cl, Race race) {
		GBox b = (GBox) box;

		HCLASS_RACE r = HCLASS_RACE.clP(race, cl);
		b.title(crime.name);
		
		{
			
			b.text(crime.desc);
			b.sep();
		}
		
		Response rr = crime.loyaltyInc(cl, race, punishment(cl, race).punish);
		{
			GText t = b.text();
			
			if (rr.diff >= 0) {
				t.normalify2();
				t.add(¤¤currentGood);
			}else {
				t.errorify();
				t.add(¤¤currentBad);
			}
			
			t.insert(0, STANDINGS.get(cl).bloyalty.name);
			b.add(t);
			b.NL(4);
			
			b.add(STANDINGS.get(cl).happiness.bo.icon);
			b.textLL(STANDINGS.get(cl).happiness.bo.name);
			b.tab(6);
			b.add(GFORMAT.percInc(b.text(), rr.newHap-rr.oldHappiness));
			b.NL();
			b.add(BOOSTABLES.CIVICS().LAW.icon);
			b.textLL(BOOSTABLES.CIVICS().LAW.name);
			b.tab(6);
			b.add(GFORMAT.percInc(b.text(), rr.newLaw-rr.oldLaw));
			b.NL();
			b.add(STANDINGS.get(cl).bloyalty.icon);
			b.textLL(STANDINGS.get(cl).bloyalty.name);
			b.tab(6);
			b.add(GFORMAT.percInc(b.text(), rr.diff));
			
			b.sep();
		}
		


		{
			b.add(UI.icons().s.slave);
			b.textLL(¤¤custody);
			b.tab(7);
			b.add(GFORMAT.i(b.text(), criminals(race)));
			b.NL();

			b.add(UI.icons().s.slave);
			b.textLL(¤¤crimes);
			b.tab(7);
			b.add(GFORMAT.i(b.text(), occurence().get(r)));
			b.text(b.text().add('/'));
			b.add(GFORMAT.i(b.text(), occurence().history(r).getPeriodSum(-16, 0)));
			b.NL();

			b.add(UI.icons().s.sword);
			b.textLL(¤¤arrests);
			b.tab(7);
			b.add(GFORMAT.i(b.text(), caught().get(r)));
			b.text(b.text().add('/'));
			b.add(GFORMAT.i(b.text(), caught().history(r).getPeriodSum(-16, 0)));
			b.NL();

			b.sep();
		}

		{

			b.textLL(¤¤effect);
			b.NL();

			{
				b.add(STANDINGS.get(cl).bhappiness.icon);
				b.textLL(StatsLaw.¤¤tyranny);
				b.tab(7);
				b.add(GFORMAT.percInc(b.text(), -crime.tyrrany(cl, race), 1));
				b.NL();

				b.add(BOOSTABLES.CIVICS().LAW.icon);
				b.textLL(BOOSTABLES.CIVICS().LAW.name);
				b.tab(7);
				b.add(GFORMAT.percInc(b.text(), crime.law(cl, race), 1));
				b.NL();
				
			}

			StatPunishment pp = punishment(cl, race);

			b.add(pp == null ? UI.icons().m.questionmark : pp.punish.icon);
			b.textLL(¤¤current);
			b.tab(7);
			if (pp != null) {
				b.textL(punishment(cl, race).punish.action);
				b.NL();
				b.tab(1);
				b.add(STANDINGS.get(cl).bhappiness.icon);
				b.add(GFORMAT.f1(b.text(), pp.punish.tyranny(cl, race)));
				b.space();
				b.add(BOOSTABLES.CIVICS().LAW.icon);
				b.add(GFORMAT.f1(b.text(), pp.punish.law(cl, race)));
				b.NL(4);
			}
			b.NL();

			b.sep();
			b.tab(5);
			b.textLL(Dic.¤¤Rate);
			b.tab(7);
			b.add(STANDINGS.get(cl).bhappiness.icon);
			b.tab(9);
			b.add(BOOSTABLES.CIVICS().LAW.icon);
			b.NL();
			
			double ptot = 0;
			for (PUNISHMENT p : CRIME_PUNISHMENTS.get(cl)) {
				ptot += recentPunishment(cl, race, p);
			}
			
			for (PUNISHMENT p : CRIME_PUNISHMENTS.get(cl)) {
				b.add(p.icon.small);
				b.textL(p.name);
				b.tab(5);
				double d = ptot <= 0 ? 0 : recentPunishment(cl, race, p) / ptot;

				b.add(GFORMAT.perc(b.text(), d));
				b.tab(7);
				b.add(GFORMAT.f0(b.text(), -p.tyranny(cl, race)*crime.tyrrany(cl, race)));
				b.tab(9);
				b.add(GFORMAT.f(b.text(), p.law(cl, race)*crime.law(cl, race)));
				b.NL(2);
			}
			
			b.textLL(Dic.¤¤Total);
			b.tab(7);
			b.add(GFORMAT.f0(b.text(), -tyrranyValue(cl, race)*crime.tyrrany(cl, race)));
			b.tab(9);
			b.add(GFORMAT.f0(b.text(), lawValue(cl, race)*crime.law(cl, race)));

			
			
			
			b.sep();
		}
		
		
	}
	
	

}
