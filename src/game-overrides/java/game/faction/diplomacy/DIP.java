package game.faction.diplomacy;

import java.io.IOException;
import java.util.Arrays;

import coopmod.CoopRuntime;
import game.GAME;
import game.GameDisposable;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.FactionResource;
import game.faction.diplomacy.deal.Deal;
import game.faction.npc.FactionNPC;
import game.time.TIME;
import init.sprite.UI.UI;
import snake2d.util.color.ColorImp;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.ACTION;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.ArrayListGrower;
import snake2d.util.sets.Bitsmap2D;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LinkedList;
import util.Debugger;
import util.text.D;
import view.interrupter.IDebugPanel;

public final class DIP extends FactionResource{

	static DIP s;
	
	private final ArrayListGrower<DipStance> all = new ArrayListGrower<>();

	public final DipStance neutral;
	public final DWar enemies;
	public final DipStance traders;
	public final DipStance pact;
	public final DipStance allied;
	public final DipStance vassal;
	public final DipStance overlord;
	public final DipWarPlayer warPlayer = new DipWarPlayer();
	
	private final UpVassal uv = new UpVassal();
	private final Deal dealTmp;
	private final Deal dealTmp2;

	private final Bitsmap2D data = new Bitsmap2D(0, 4, FACTIONS.MAX(), FACTIONS.MAX());
	private final int[] overlords = Alloc.ii(FACTIONS.MAX());
//	private double[] secondOfPlayer = new double[FACTIONS.MAX];
	private final int[] secondOfStance = Alloc.ii(FACTIONS.MAX()*FACTIONS.MAX()); 
	public double playerWarSecond = -1;
	
	int stateI = -1;
	
	@Override
	protected void save(FilePutter file) {
		data.save(file);
		uv.save(file);
		file.isE(overlords);
		file.is(secondOfStance);
		file.d(playerWarSecond);
		warPlayer.teamName.save(file);
		warPlayer.warName.save(file);
	}

	@Override
	protected void load(FileGetter file) throws IOException {
		data.load(file);
		uv.load(file);
		file.isE(overlords);
		file.is(secondOfStance);
		playerWarSecond = file.d();
		stateI = -1;
		warPlayer.teamName.load(file);
		warPlayer.warName.load(file);
	}

	@Override
	protected void clear() {
		data.clear();
		uv.clear();
		Arrays.fill(secondOfStance, 0);
		Arrays.fill(overlords, 0);
		stateI = -1;
		playerWarSecond = -1;
	}

	public DIP(FACTIONS ff) {
		s = this;
		D.gInit(this);
		
		double tarif = 0.25;
		
		neutral = new DipStance(all, "NEUTRAL", 0, 0, tarif, false, false, false,
				D.g("NEUTRAL", "Neutral"), 
				D.g("NEUTRALD", "No relations at all"), 
				UI.icons().s.flag
						);
		enemies = new DWar(all);
		traders = new DipStance(all, "TRADE", 0.1, 1.5, tarif*0.8, true, false, false, 
				D.g("TRADE", "Trade Partners"), 
				D.g("TRADED", "Trade partners can exchange goods automatically at favourable rates. Requires 1 in opinion to be considered."), 
				UI.icons().s.trade.createColored(new ColorImp(128, 128, 20))
				);
		pact = new DipStance(all, "PACT", 0.4, 2.5, tarif*0.6, true, true, false, 
				D.g("PACT", "Colleagues"), 
				D.g("PACTD", "A colleague not only trades at a discounted rate, but allow transit through their lands. Requires 2.5 in opinion to be considered."), 
				UI.icons().s.flag.createColored(new ColorImp(90, 128, 90))
				);
		allied = new DipStance(all, "ALLIED", 1.0, 6, tarif*0.4, true, true, true, 
				D.g("ALLY", "Allies"), 
				D.g("ALLYD", "An ally is your sworn bannerman. You share the same enemies, trade and swear to never question each other's rule. Requires 6 in opinion to be considered."), 
				UI.icons().s.shield.createColored(new ColorImp(90, 128, 128))
				);
		vassal = new DipStance(all, "VASSAL", 1.0, 6, tarif*0.4, true, true, true,
				D.g("VASSAL", "Vassal"), 
				D.g("VASSALD", "A Vassal serves its overlord with tribute. In return, they are protected."), 
				UI.icons().s.slave.createColored(new ColorImp(90, 128, 90))
				);
		
		overlord = new DipStance(all, "OVERLORD", 1.0, 6, tarif*0.4, true, true, true,
				D.g("PROTECTOR", "Protector"), 
				D.g("PROTECTORD", "A Protector is the overlord of a vassal. Their task is to protect, and in return they get to enjoy some tribute at the end of each year."), 
				UI.icons().s.noble.createColored(new ColorImp(128, 20, 128))
				) {
		};
		
		new Faction.FactionActivityListener() {
			
			@Override
			public void remove(FactionNPC f) {
				clear(f);
			}

			@Override
			public void add(FactionNPC f) {
				clear(f);
			}
			

		};
		
		IDebugPanel.add("Total War", new ACTION() {
			
			@Override
			public void exe() {
				for (FactionNPC f : FACTIONS.NPCs()) {
					enemies.set(f);
				}
			}
		});
		
		dealTmp = new Deal();
		dealTmp2 = new Deal();
	}
	
	public static DipStance get(Faction faction, Faction other) {
		return s.all.get(s.data.get(faction.index(), other.index()));
	}
	
	public static DipStance get(FactionNPC faction) {
		return s.all.get(s.data.get(faction.index(), FACTIONS.player().index()));
	}
	
	public static double secondSinceStance(Faction a, Faction b) {
		if (a.index() < b.index()) {
			Faction c = a;
			a = b;
			b = c;
		}
		return TIME.playedGame()-s.secondOfStance[a.index()*FACTIONS.MAX()+b.index()];
	}
	
	public static void secondSincestartInc(Faction a, Faction b, double d) {
		if (a.index() < b.index()) {
			Faction c = a;
			a = b;
			b = c;
		}
		s.secondOfStance[a.index()*FACTIONS.MAX()+b.index()] += d*16*8*TIME.secondsPerDay();
	}
	
	public static double secondSinceStance(FactionNPC f) {
		return secondSinceStance(FACTIONS.player(), f);
	}
	
//	public static double secondOfPlayer(FactionNPC f) {
//		return s.secondOfPlayer[f.index()];
//	}
//	
//	public static double timeOfPlayer(FactionNPC f) {
//		return TIME.currentSecond()-s.secondOfPlayer[f.index()];
//	}
	
	public static double secondsOFPlayerWar() {
		if (s.playerWarSecond < 0)
			return 0;
		return TIME.currentSecond()-s.playerWarSecond;
	}
	
	public static Faction overlord(Faction f) {
		int i = s.overlords[f.index()];
		if (i > 0) {
			return FACTIONS.getByIndex(i-1);
		}
		return null;
	}
	
	private void clear(Faction f) {
		for (int i = 0; i < FACTIONS.MAX(); i++) {
			data.set(f.index(), i, 0);
			data.set(i, f.index(), 0);
			if (overlords[i] == f.index()+1) {
				overlords[i] = 0;
			}
		}
		overlords[f.index()] = 0;
		
		stateI++;
	}
	
	private void clearAllButTrade(Faction f) {
		
		for (int i = 0; i < FACTIONS.MAX(); i++) {
			if (i != f.index() && get(f, FACTIONS.getByIndex(i)).trades) {
				data.set(f.index(), i, TRADE().index());
				data.set(i, f.index(), TRADE().index());
			}else {
				data.set(f.index(), i, 0);
				data.set(i, f.index(), 0);
			}
			
			if (overlords[i] == f.index()+1)
				overlords[i] = 0;
		}
		overlords[f.index()] = 0;
		
		stateI++;
	}
	
	void set(Faction instigator, Faction accepter, DipStance dip) {
		
		DipStance old = get(instigator, accepter);
		if (old == dip)
			return;
		CoopRuntime.diplomacyChanged(instigator, accepter, dip);

		boolean pemeny = enemies.any(FACTIONS.player());

		
		stateI++;
		{
			Faction a = instigator;
			Faction b = accepter;
			Faction c = a;
			a = b;
			b = c;
			s.secondOfStance[a.index()*FACTIONS.MAX()+b.index()] = (int) TIME.playedGame();
		}
		
		if (dip == VASSAL()) {
			clearAllButTrade(instigator);
			
			overlords[instigator.index()] = accepter.index()+1;
			
			data.set(instigator.index(), accepter.index(), VASSAL().index());
			data.set(accepter.index(), instigator.index(), OVERLORD().index());

		}else if (dip == OVERLORD()) {
			clearAllButTrade(accepter);
			overlords[accepter.index()] = instigator.index()+1;
			
			data.set(instigator.index(), accepter.index(), OVERLORD().index());
			data.set(accepter.index(), instigator.index(), VASSAL().index());

		}else {
			if(overlord(accepter) == instigator)
				overlords[accepter.index()] = 0;
			if(overlord(instigator) == accepter)
				overlords[instigator.index()] = 0;
			data.set(instigator.index(), accepter.index(), dip.index());
			data.set(accepter.index(), instigator.index(), dip.index());
		}
		
//		LOG.ln("");
//		LOG.ln(old.name + " " + dip.name + " " + get(instigator, accepter).name + " " + get(accepter, instigator).name) ;
//		LOG.ln(overlord(FACTIONS.player()) + " " + VASSAL().all(FACTIONS.player()).size());
//		LOG.ln(overlord(accepter));
//		LOG.ln("");
		
		
		for (DipActivityListener li : DipActivityListener.all) {
			li.change(instigator, accepter, old, dip);
		}
		
		ally(accepter, old);
		ally(instigator, old);
		
		if (!enemies.any(FACTIONS.player())) {
			playerWarSecond = -1;
		}else if (!pemeny) {
			playerWarSecond = TIME.currentSecond();
		}
		
		stateI++;
	}
	
	private void ally(Faction f, DipStance old) {
		for (int ai = 0; ai < FACTIONS.MAX(); ai++) {
			Faction f2 = FACTIONS.all().get(ai);
			
			if (get(f2, f).ally) {
				for (int ei = 0; ei < FACTIONS.MAX(); ei++) {
					Faction f3 = FACTIONS.all().get(ei);
					if (get(f, f3) == enemies || get(f2, f2) == enemies) {
						set(f, f3, enemies);
						set(f2, f3, enemies);
					}
				}	
			}
		}
		
		if (f == FACTIONS.player() && old == enemies) {
			
			for (int ai = 0; ai < FACTIONS.MAX(); ai++) {
				Faction f2 = FACTIONS.all().get(ai);
				
				if (get(f2, f).ally) {
					for (int ei = 0; ei < FACTIONS.MAX(); ei++) {
						Faction f3 = FACTIONS.all().get(ei);
						if (get(f2, f3) == enemies && get(f, f3) != enemies) {
							set(f, f3, neutral);
							set(f2, f3, neutral);
						}
					}	
				}
				
				
			}
		}
	}
	
	boolean is(Faction a, Faction b, DipStance dip) {
		if (dip == overlord)
			return overlord(b) == a;
		if (dip == vassal) {
			return overlord(a) == b;
		}
		return all.get(data.get(a.index(), b.index())) == dip;
	}
	
	
	
	@Override
	protected void update(double ds, Faction f) {
		uv.update();
	}



	public static DipStance NEUTRAL() {
		return s.neutral;
	}
	
	public static DWar WAR() {
		return s.enemies;
	}
	
	public static DipStance TRADE() {
		return s.traders;
	}
	
	public static Deal TMP() {
		return s.dealTmp;
	}

	public static Deal TMP2() {
		return s.dealTmp2;
	}
	
//	public static boolean isPlayerBitch(FactionNPC f) {
//		return ALLY().is(f) || VASSAL().overlord(f) == FACTIONS.player();
//	}

	public static DipStance ALLY() {
		return s.allied;
	}
	
	public static DipStance PACT() {
		return s.pact;
	}
	
	public static DipStance VASSAL() {
		return s.vassal;
	}
	
	public static DipStance OVERLORD() {
		return s.overlord;
	}
	
	public static DipWarPlayer WAR_PLAYER() {
		return s.warPlayer;
	}
	
	public static abstract class DipActivityListener {
		
		static final LinkedList<DipActivityListener> all = new LinkedList<>();
		static {
			new GameDisposable() {
				
				@Override
				protected void dispose() {
					all.clear();
				}
			};
		}
		
		public DipActivityListener() {
			all.add(this);
		}
		
		public abstract void change(Faction faction, Faction other, DipStance old, DipStance nn);
		
	}

	private final ArrayList<FactionNPC> tmp = new ArrayList<FactionNPC>(FACTIONS.MAX());
	private int ffi =-1;
	
	private static final ACTION increase = new ACTION() {
		
		@Override
		public void exe() {
			for (int fi = 0; fi < FACTIONS.MAX(); fi++)
				s.secondOfStance[fi*FACTIONS.MAX()+FACTIONS.player().index()] -= (int) TIME.secondsPerDay();
		}
	};
	
	public static LIST<FactionNPC> traders (){
		if (GAME.updateI() == s.ffi)
			return s.tmp;
		
		s.ffi = GAME.updateI();
		s.tmp.clearSloppy();
		for (FactionNPC f : FACTIONS.NPCs()) {
			if (f.isActive() && get(f).trades)
				s.tmp.add(f);
				
		}
		return s.tmp;
	}
	
	public static void debug(Debugger d, FactionNPC f) {
		d.title(DIP.class.getSimpleName());
		
		d.debug("stance").add(get(f).name);
		d.debug("since Days").add(secondSinceStance(f)*TIME.secondsPerDayI());
		d.debug("increase time", increase);
		d.debug("war days").add(s.playerWarSecond*TIME.secondsPerDayI());
		s.warPlayer.debug(d, f);
		s.warPlayer.debug(d);
		
		
	}
	
}
