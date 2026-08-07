package settlement.stats.service;

import java.io.IOException;

import coopmod.CoopRuntime;
import init.race.RACES;
import init.race.Race;
import init.type.HCLASSES;
import init.type.HCLASS_RACE;
import init.type.HTYPES;
import init.type.NEED;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.StatsInit;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.sets.Bitmap1D;
import snake2d.util.sets.LISTE;
import snake2d.util.sprite.SPRITE;
import util.data.BOOLEANO.BOOLEAN_OE;
import util.info.INFO;
import util.text.D;

public abstract class StatServiceImp extends StatService{

	public final static int TARGET_MAX = 16;
	private final Perm permission;

	static CharSequence ¤¤Access = "¤Access";
	static CharSequence ¤¤AcessDesc = "¤The level of access this subject has to a service. Can be improved by building more service facilities and making sure they are close enough for your people to utilize.";
	static CharSequence ¤¤Quality = "¤Quality";
	static CharSequence ¤¤QualityDesc = "¤The quality of a subject's last visit to this facility. Often improved by placing special items in the rooms in question.";
	static CharSequence ¤¤Distance = "¤Proximity";
	static CharSequence ¤¤DistanceDesc = "¤Whenever a subject wants to use a service, this is the distance the subject has had to walk to reach it. This value only reaches 100% if subjects are right next to the service at all times. You must yourself find a good balance.";
	static CharSequence ¤¤TotalDesc = "¤The access and quality this subject group has. Can be improved by building more facilities, keeping them maintained, and also in some cases building them well.";
	static CharSequence ¤¤UpDesc = "¤The current upgrade boost from the rooms visited. Some rooms don't have upgrades.";
//	static CharSequence ¤¤More = "¤We want better access to {0}, of good quality.";
	private static CharSequence ¤¤perm = "¤Permission";
	static {
		D.ts(StatServiceImp.class);
	}
	
	StatServiceImp(String key, LISTE<StatServiceImp> all, StatsInit init, CharSequence name, CharSequence desc, SPRITE icon, NEED need) {
		super(name, desc, icon, need);
		all.add(this);
		permission = new Perm(¤¤perm, ¤¤perm + ": " + name);
		init.savers.put("SER_PERM_" + key, permission);
		
		
	}
	
	public BOOLEAN_OE<HCLASS_RACE> permission() {
		return permission;
	}

	
	public double getBasePriority(Humanoid h) {
		return h.indu().race().stats().defNormalized(h.indu().hType().CLASS, total().standing());
	}
	
	/**
	 * @param h
	 * @return
	 */
	public boolean accessRequest(Humanoid h) {
		if (h.indu().hType() == HTYPES.NOBILITY())
			return true;
		if (h.indu().hType() == HTYPES.TOURIST())
			return true;
		if (h.indu().hType().parent() != h.indu().hType()) {
			if (!total().standing.definition(h.race()).child)
				return false;
			return permission.is(HCLASS_RACE.clP(h.race(), h.indu().hType().parent().parentClass()));
		}
		return permission.is(h.indu().popCL());
	}
	
	private class Perm implements BOOLEAN_OE<HCLASS_RACE>, SAVABLE{
		
		private final Bitmap1D access = new Bitmap1D(HCLASS_RACE.ALL().size(), false);
		private final INFO info;
		
		public Perm(CharSequence name, CharSequence desc){
			this.info = new INFO(name, desc);
		}

		@Override
		public INFO info() {
			return info;
		}

		@Override
		public void save(FilePutter file) {
			access.save(file);
		}

		@Override
		public void load(FileGetter file) throws IOException {
			access.load(file);
		}

		@Override
		public void clear() {
			for (HCLASS_RACE p : HCLASS_RACE.ALL()) {
				if (StatServiceImp.this instanceof StatServiceChild)
					access.set(p.index, true);
				if (p.cl != null && p.race != null) {
					boolean b = p.cl != HCLASSES.SLAVE() && total().boosters.all().size() > 0;
					b |= total().standing().max(p.cl, p.race) != 0;
					access.set(p.index, !b);
				}
			}
		}

		@Override
		public boolean is(HCLASS_RACE t) {
			if (t.race == null) {
				boolean m = false;
				for (Race r : RACES.all()) {
					m |= is(t.cl.get(r));
				}
				return m;
			}
			return !access.get(t.index());
		}

		@Override
		public BOOLEAN_OE<HCLASS_RACE> set(HCLASS_RACE t, boolean b) {
			if (t.race == null) {
				for (Race r : RACES.all()) {
					set(t.cl.get(r), b);
				}
			}else {
				access.set(t.index(), !b);
				CoopRuntime.servicePermissionChanged(StatServiceImp.this, t, b);
			}
			return this;
		}
		
	}
	
}
