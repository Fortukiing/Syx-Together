package settlement.stats.colls;

import coopmod.CoopRuntime;
import game.boosting.BOOSTABLES;
import game.boosting.BOOSTING;
import game.boosting.Boostable;
import init.race.RACES;
import init.race.Race;
import init.sprite.UI.UI;
import init.type.HCLASS;
import init.type.HCLASS_RACE;
import settlement.stats.Induvidual;
import settlement.stats.StatsInit;
import settlement.stats.stat.STAT;
import settlement.stats.stat.STATData;
import settlement.stats.stat.STATFake;
import settlement.stats.stat.StatCollection;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.SPRITE;
import snake2d.util.sprite.text.Str;
import util.gui.misc.GBox;
import util.info.GFORMAT;
import util.keymap.RMapInt;
import util.text.D;
import util.text.Dic;

public class StatsEducation extends StatCollection {

	public static final int LIMIT_MAX = 100;
	
	private static CharSequence ¤¤childHood = "¤Childhood";
	private static CharSequence ¤¤adultHood = "¤Adulthood";
	
	private static CharSequence ¤¤currentPolicy = "¤Current Policy";
	private static CharSequence ¤¤currentLimit = "¤Current Limit";
	private static CharSequence ¤¤currentLimitSpeed = "¤Limit Speed";
	private static CharSequence ¤¤LimitMax = "¤Max Limit";
	
	private static CharSequence ¤¤name = "Enlightenment";
	private static CharSequence ¤¤desc = "Stats regarding your education levels, and boosts surrounding it.";
	private static CharSequence ¤¤limit = "¤Study Limit";
	private static CharSequence ¤¤limitD = "The maximum enlightenment allowed. High enlightenment gives diminishing returns, meaning a high limit will take longer to achieve.";
	
	
	static {
		D.ts(StatsEducation.class);
	}
	
	public final LIST<StatEducation> all;
	private final RMapInt<HCLASS_RACE> policy = new RMapInt<HCLASS_RACE>(HCLASS_RACE.MAP(), 0, 100);
	public final AgeType child;
	public final AgeType adult;
	public final LIST<AgeType> allAges;
	
	
	
	
	
	public StatsEducation(StatsInit init) {
		super(init, "EDUCATION", ¤¤name, ¤¤desc);

		child = new AgeType(UI.icons().s.reproduction, ¤¤childHood, init, 0);
		adult = new AgeType(UI.icons().s.human, ¤¤adultHood, init, 1);
		allAges = new ArrayList<StatsEducation.AgeType>(child, adult);
		all = new ArrayList<StatsEducation.StatEducation>(
				new StatEducation("EDUCATION", init, UI.icons().l.book.small, 0),
				new StatEducation("INDOCTRINATION", init, UI.icons().l.work.small, 1)
				);
		
		init.savers.put("EDUCATION_POL", policy);
		
		
		
	}
	
	public StatEducation policy(HCLASS_RACE cl) {
		return policy(cl.cl, cl.race);
	}
	
	public StatEducation policy(HCLASS cl, Race r) {
		if (r == null) {
			StatEducation rr = policy(cl, RACES.all().get(0));
			for (int ri = 0; ri < RACES.all().size(); ri++) {
				if (policy(cl, RACES.all().get(ri)) != rr)
					return null;
			}
			return rr;
		}
		return all.get(policy.get(HCLASS_RACE.clP(r, cl)));
	}
	
	public void policySet(HCLASS cl, Race r, StatEducation e) {
		if (r == null) {
			for (int ri = 0; ri < RACES.all().size(); ri++)
				policySet(cl, RACES.all().get(ri), e);
			return;
		}
		policy.set(HCLASS_RACE.clP(r, cl), e.index);
	}
	
	public void policySet(HCLASS_RACE cl, StatEducation e) {
		policySet(cl.cl, cl.race, e);
	}
	

	
	public class AgeType {
		
		public final CharSequence name;
		public final SPRITE icon;
		private final RMapInt<HCLASS_RACE> limit;
		private final int typeI;
		public final Boostable limitMax;
		
		AgeType(SPRITE icon, CharSequence name, StatsInit init, int allI){
			this.name = name;
			this.icon = icon;
			this.limit = new RMapInt<HCLASS_RACE>(HCLASS_RACE.MAP(), 0, LIMIT_MAX, LIMIT_MAX/6);
			this.typeI = allI;
			init.savers.put("EDU_AGE_TYPE" + allI, limit);
			limitMax = BOOSTING.push("EDUCATION_LIMIT_"+allI, 10, ¤¤limit + ": " + name, ¤¤limitD, icon, BOOSTABLES.CIVICS());
		}
		
		public int limit(HCLASS_RACE cl) {
			if (cl.race == null) {
				int l = 0;
				for (int ri = 0; ri < RACES.all().size(); ri++)
					l = Math.max(l, limit(HCLASS_RACE.clP(RACES.all().get(ri), cl.cl)));
				return l;
			}
			
			return CLAMP.i(limit.get(cl), 0, limitMax(cl));
		}
		
		public int limit(HCLASS cl, Race r) {
			return limit(HCLASS_RACE.clP(r, cl));
		}
		
		public int limitMax(HCLASS_RACE cl) {
			return (int) Math.round(limitMax.get(cl));
		}
		
		public int limitMax(HCLASS cl, Race r) {
			return limitMax(HCLASS_RACE.clP(r, cl));
		}
		
		public void limitSet(HCLASS_RACE cl, int l) {
			if (cl.race == null) {
				for (int ri = 0; ri < RACES.all().size(); ri++)
					limitSet(HCLASS_RACE.clP(RACES.all().get(ri), cl.cl), l);
				return;
			}
			l = CLAMP.i(l, 0, limitMax(cl));
			limit.set(cl, l);
			CoopRuntime.educationLimitChanged(this, cl.cl, cl.race, l);
		}
		
		public void limitSet(HCLASS cl, Race r, int l) {
			limitSet(HCLASS_RACE.clP(r, cl), l);
		}
		
		public double limitSpeed(HCLASS_RACE cl) {
			return limitSpeed(cl.cl, cl.race);
		}
		
		public double limitSpeed(HCLASS cl, Race r) {
			double s = 1.0;
			double e = 1.0-0.9*limit(HCLASS_RACE.clP(r, cl))/LIMIT_MAX;
			return (s + e)/2.0;
		}
		
		public double currentSpeed(Induvidual i) {
			return 1.0-0.9*policy(i.popCL()).allT[typeI].indu().get(i)/LIMIT_MAX;
		}
		
		public void educate(Induvidual i, double amount) {

			StatEducation s = policy(i.clas(), i.race());
			s.educate(i, amount, s.allT[typeI]);	
		}

		public boolean educateCan(Induvidual i) {
			int lim = limit(i.popCL());
			StatEducation s = policy(i.clas(), i.race());
			



			if (lim > s.allT[typeI].indu().get(i))
				return true;
			
			if (lim > 0) {
				for (StatEducation o : all) {
					
					if (o == s)
						continue;
					
					for (STAT ss : o.allT) {
						if (ss.indu().get(i) > 0) {

							return true;
						}
					}
					
				}
			}
			return false;	
		}
		
		public void hoverLimit(GUI_BOX text, HCLASS_RACE cl) {
			GBox b = (GBox) text;
			
			text.title(Str.TMP.clear().add(¤¤limit).add(':').s().add(name));
			b.text(¤¤limitD);
			
			b.NL(4);
			b.textL(¤¤currentLimit);
			b.tab(6);
			b.add(GFORMAT.perc(b.text(), (double)limit(cl)/LIMIT_MAX));
			b.NL();
			b.textL(¤¤currentLimitSpeed);
			b.tab(6);
			b.add(GFORMAT.f1(b.text(), (double)limitSpeed(cl), 2));
			b.NL();
			b.textL(¤¤LimitMax);
			b.tab(6);
			b.add(GFORMAT.percGood(b.text(), (double)limitMax(cl)/LIMIT_MAX));
			b.NL();
			limitMax.hover(b, cl, true);
			b.NL();
		}
		
	}
	
	
	public final class StatEducation {
		
		private double dAmount = 0;
		
		private final STAT[] allT;
		private final int index;
		public STAT total;
		
		StatEducation(String key, StatsInit init, SPRITE icon, int index){
			allT = new STAT[allAges.size()];
			for (int i = 0; i < allT.length; i++) {
				allT[i] = new STATData(null, key+"DC"+i, init, init.count.new DataByte(key+"DC"+i, LIMIT_MAX));
				init.copier.add(allT[i].indu());
			}
			
			this.index = index;
			total = new STATFake(key, init) {
				
				@Override
				protected double getDD(HCLASS s, Race r, int daysBack) {
					double res = 0;
					for (STAT t : allT) {
						res += t.data(s).getD(r, daysBack);
					}
					return res / allT.length;
					
				}
				
				@Override
				public void hover(GUI_BOX text, HCLASS cl, Race type) {
					GBox b = (GBox) text;

					
					for (AgeType t : allAges) {
						b.textLL(t.name);
						b.tab(6);
						b.add(GFORMAT.perc(b.text(), allT[t.typeI].data(cl).getD(type)));
						b.NL();
						b.textL(¤¤currentLimit);
						b.tab(6);
						b.add(GFORMAT.perc(b.text(), (double)t.limit(cl, type)/LIMIT_MAX));
						b.NL();
						b.textL(¤¤currentLimitSpeed);
						b.tab(6);
						b.add(GFORMAT.f1(b.text(), (double)t.limitSpeed(cl, type)/LIMIT_MAX, 2));
						b.sep();
					}
					
					b.textLL(Dic.¤¤Total);
					b.tab(6);
					b.add(GFORMAT.perc(b.text(), getDD(cl, type, 0)));
					b.sep();
					super.hover(text, cl, type);
				}
				
				@Override
				protected double induGet(Induvidual i) {
					double res = 0;
					
					for (STAT t : allT) {
						
						res += t.indu().getD(i);
					}

					return res / allT.length;
				}
				
				@Override
				public void hover(GUI_BOX text, Induvidual indu) {
					GBox b = (GBox) text;
					b.textLL(¤¤currentPolicy);
					b.tab(6);
					b.add(b.text().add(policy(indu.clas(), indu.race()).total.info().name));
					b.NL();
					
					for (AgeType t : allAges) {
						b.textLL(t.name);
						b.tab(6);
						b.add(GFORMAT.perc(b.text(), allT[t.typeI].indu().getD(indu)));
						b.NL();
					}
					
					super.hover(text, indu);
				}
			};
			total.info().icon = icon;
			
		}
		
		private void educate(Induvidual i, double amount, STAT toIncrease) {
			
			double dam = amount + dAmount;
			
			int am = (int) dam;
			dAmount = dam-am;
			if (am == 0)
				return;
			
			am = decrease(i, am);
			
			if (am == 0)
				return;
			
			int max = toIncrease.indu().max(i)-toIncrease.indu().get(i);
			if (am > max) {
				dAmount += am-max;
				am = max;
			}
			toIncrease.indu().inc(i, am);
		}
		
		private int decrease(Induvidual i, int am) {
			if (am == 0)
				return am;
			
			for (StatEducation o : all) {
				if (o == this)
					continue;
				
				for (STAT os : o.allT) {
					int a = os.indu().get(i);
					if (am > a) {
						os.indu().inc(i, -a);
						am-=a;
					}else {
						os.indu().inc(i, -am);
						return 0;
					}
					if (am <= 0)
						return 0;
				}
			}
			
			return am;
		}
	}
			
			
	
}
