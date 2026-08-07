package settlement.stats.stat;

import coopmod.CoopRuntime;
import init.race.RACES;
import init.race.Race;
import init.type.HCLASS;
import init.type.HCLASSES;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.StatsInit;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import snake2d.util.sets.ArrayList;
import util.data.INT_O.INT_OE;
import util.keymap.RMapInt.RMapIntTwo;

public class StatDecree extends StatInfo {

	private final int def;
	
	private final RMapIntTwo<HCLASS, Race> levels = new RMapIntTwo<HCLASS, Race>(HCLASSES.MAP(), RACES.map()) {
		@Override
		public void clear() {
			super.clear();
			setAll(def);
		};
	};
	private final ArrayList<INT_OE<Race>> tars;
	private final double ii;
	private final double iii;
	public StatDecree (String dkey, StatsInit i, int min, int max, int fractions, CharSequence name, int def){
		super(name, "");
		this.def = def;
		i.savers.put(dkey + "_DEC", levels);
		levels.setAll(def);
		tars = new ArrayList<>(HCLASSES.ALL().size());
		
		final int m = max*fractions;
		ii = 1.0/(m);
		iii = 1.0/fractions;
		for (HCLASS c : HCLASSES.ALL()) {
			tars.add(new INT_OE<Race>() {

				@Override
				public int min(Race t) {
					return min;
				}
				
				@Override
				public int max(Race t) {
					return m;
				}
				
				@Override
				public int get(Race t) {
					if (t == null) {
						int m = 0;
						for (int ri = 0; ri < RACES.all().size(); ri++) {
							Race r = RACES.all().get(ri);
							m = Math.max(m, get(r));
						}
						return m;
					}
					return CLAMP.i(levels.get(c).get(t), min, m);
				}

				@Override
				public void set(Race t, int i) {
					if (t == null) {
						for (int ri = 0; ri < RACES.all().size(); ri++) {
							Race r = RACES.all().get(ri);
							set(r, i);
						}
						return;
					}
					int value = CLAMP.i(i, min, m);
					levels.get(c).set(t, value);
					CoopRuntime.statDecreeChanged(StatDecree.this, c, t, value);
				}
			
			
			});
		}
	}
	
//	public INT_OE<Race> get(HCLASS c){
//		return tars.get(c.index());
//	}
	
	public INT_OE<Race> getI(HCLASS c){
		return tars.get(c.index());
	}
	
	public double getD(HCLASS c, Race race) {
		return tars.get(c.index()).get(race)*ii;
	}
	
	public double get(HCLASS c, Race race) {
		return tars.get(c.index()).get(race)*iii;
	}
	
	
	public int get(Humanoid h) {
		double d = tars.get(h.indu().clas().index()).get(h.indu().race())*iii;
		int i = (int) d;
		if (RND.rFloat() < d-i)
			i++;
		return i;
	}
	
//	public int get(Humanoid h) {
//		return tars.get(h.indu().clas().index()).get(h.indu().race());
//	}
}
