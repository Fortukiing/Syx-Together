package settlement.thing;

import static settlement.main.SETT.PATH;
import static settlement.main.SETT.THINGS;

import java.io.IOException;

import coopmod.CoopRuntime;
import game.GAME;
import game.faction.FACTIONS;
import game.faction.FResources.RTYPE;
import init.constant.C;
import init.resources.RBIT;
import init.resources.RBIT.RBITImp;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.settings.S;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import settlement.main.SETT;
import settlement.misc.util.RESOURCE_TILE;
import settlement.thing.THINGS.Thing;
import settlement.thing.THINGS.ThingFactory;
import settlement.thing.ThingsResources.ScatteredResource;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import snake2d.util.sets.LISTE;
import util.GUTIL;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.rendering.ShadowBatch;
import view.sett.SETT_HOVERABLE;

public final class ThingsResources extends ThingFactory<ScatteredResource>{

	public static final int MAX_AMOUNT = 10000;
	private final static int MAX = 4096*2;
	private final ScatteredResource[] all = new ScatteredResource[MAX];
	private final int[] hoverRes = Alloc.ii(RESOURCES.ALL().size()); 
	private final int[] allclaimed = Alloc.ii(RESOURCES.ALL().size()); 

	private RBITImp hasMask = new RBITImp();
	
	ThingsResources(LISTE<ThingFactory<?>> alllllll) {
		super(alllllll, MAX);
		for (int i = 0; i < all.length; i++) {
			all[i] = new ScatteredResource(i);
		}
	}
	
	@Override
	protected ScatteredResource[] all() {
		return all;
	}
	
	@Override
	protected void save(FilePutter file) {
		super.save(file);
	}
	
	@Override
	protected void load(FileGetter file) throws IOException {
		hasMask.clear();
		super.load(file);
		for (ScatteredResource rr : all) {
			if (!rr.isRemoved() && rr.findableReservedCanBe())
				hasMask.or(rr.resource().bit);
		}
	}
	

	public void renderZoomed(Renderer r, RECTANGLE renWin, int offX, int offY) {
		for (ScatteredResource res : all) {
			if (!res.isRemoved() && renWin.holdsPoint(res.body().x1(), res.body().y1())) {
				res.resource().miniC().bind();
				SPRITES.cons().TINY.low.render(r, 0, offX+res.body().x1()-renWin.x1(), offY+res.body().y1()-renWin.y1());
			}
		}
		COLOR.unbind();
		
	}
	
	public void create(int tx, int ty, RESOURCE r, int amount) {

		int i = 0;
		if (r == null)
			throw new RuntimeException();
		if (amount <= 0)
			return;
		
		while(GUTIL.circle().radius(i) < 3 && amount > 0) {
			int x = tx + GUTIL.circle().get(i).x();
			int y = ty + GUTIL.circle().get(i).y();
			if (has(x, y, r.bit)) {
				Thing t = THINGS().getFirst(x, y);

				while(t != null) {
					if (t instanceof ScatteredResource) {
						ScatteredResource res = (ScatteredResource) t;
						if (res.resource() == r) {
							amount = res.increaseAmount(amount);
//							if (amount > 0) {
//								FACTIONS.player().res().inc(r, RTYPE.SPOILAGE, amount);
//							}
							if (amount <= 0)
								return;
							
						}
					}
					t = t.tileNext();
				}
			}
			i++;
		}
		
		
		while (amount > 0) {
//			FACTIONS.player().res().inc(r, RTYPE.SPOILAGE, amount);
			ScatteredResource re = nextInLine();
			amount = re.init(tx, ty, r, amount);
			re.add();
			evaluate(tx, ty);
		}
		
	}

	
	public void createPrecise(int tx, int ty, RESOURCE r, int amount) {

		if (amount <= 0)
			return;
		
		if (r == null) {
			GAME.Notify("");
			return;
		}
		
		Thing t = THINGS().getFirst(tx, ty);
		
		while(t != null) {
			if (t instanceof ScatteredResource) {
				ScatteredResource res = (ScatteredResource) t;
				if (res.resource() == r) {
					amount = res.increaseAmount(amount);
					if (amount > 0) {
						FACTIONS.player().res().inc(r, RTYPE.SPOILAGE, -amount);
					}
					evaluate(tx, ty);
					return;
				}
			}
			t = t.tileNext();
		}
		
		
		if (amount > 0) {
			ScatteredResource re = nextInLine();
			amount = re.init(tx, ty, r, amount);
			re.add();
			if (amount > 0) {
				FACTIONS.player().res().inc(r, RTYPE.SPOILAGE, -amount);
			}
			evaluate(tx, ty);
		}
		
		
		
		
	}
	
	public void create(COORDINATE c, RESOURCE r, int a) {
		create(c.x(), c.y(), r, a);
	}
	
	public RBIT hasMask() {
		return hasMask;
	}
	
	public ScatteredResource getByIndex(short index) {
		return all[index];
	}
	
	public boolean has(int tx, int ty, RBIT resMask) {

		Thing t = THINGS().getFirst(tx, ty);
		return t != null && t.resourcemask.has(resMask);
	}
	
	public ScatteredResource get(int tx, int ty) {
		Thing t = THINGS().getFirst(tx, ty);
		
		while(t != null) {
			if (t instanceof ScatteredResource) {
				if (t instanceof ScatteredResource) {
					return (ScatteredResource) t;
				}
			}
			t = t.tileNext();
		}
		
		return null;
	}
	
	public ScatteredResource getReservable(int tx, int ty, RBIT resMask) {
		if (!has(tx, ty, resMask))
			return null;
		
		Thing t = THINGS().getFirst(tx, ty);
		
		while(t != null) {
			if (t instanceof ScatteredResource) {
				ScatteredResource s = (ScatteredResource) t;
				if (s.findableReservedCanBe() && s.resource().bit.has(resMask))
					return s;
			}
			t = t.tileNext();
		}

		GAME.Notify(tx + " " + ty + " " + resMask.toString());
		return null;
	}
	
	private void evaluate(int tx, int ty) {

		Thing t = THINGS().getFirst(tx, ty);
		if (t != null) {
			Thing t2 = t;
			t2.resourcemask.clear();
			
			while(t != null) {
				if (t instanceof ScatteredResource) {
					ScatteredResource s = (ScatteredResource) t;
					if (s.findableReservedCanBe()) {
						t2.resourcemask.or(s.resource());
					}
					
				}
				t = t.tileNext();
			}
			
			t = THINGS().getFirst(tx, ty).tileNext();
			
			while(t != null) {
				t.resourcemask.clear();
				t.resourcemask.or(t2.resourcemask);
				t = t.tileNext();
			}
			
		}
		
	}
	
	public final class ScatteredResource extends Thing implements COORDINATE, SETT_HOVERABLE, RESOURCE_TILE{

		private final Rec body = new Rec(C.TILE_SIZE);
		private final int random = RND.rInt();
		private int resource;
		private short amount;
		private short claimed;

		
		ScatteredResource(int i) {
			super(i);
		}
		
		@Override
		protected void save(FilePutter f) {
			body.save(f);
			RESOURCES.map().saver().save(resource(), f);
			f.s(amount);
			f.s(claimed);
		}

		@Override
		protected void load(FileGetter f) throws IOException {
			body.load(f);
			resource = RESOURCES.map().loader().loadI(f);
			if (resource < 0)
				resource = RESOURCES.WOOD().index();
			amount = f.s();
			claimed = f.s();
		}

		@Override
		public void render(Renderer r, ShadowBatch shadows, float ds, int offsetX, int offsetY) {
			
			int x = body.x1() + offsetX;
			int y = body.y1() + offsetY;
			if (amount <= 0) {
				UI.icons().s.cancel.render(r, x, y);
				return;
			}
			resource().renderLaying(r, x, y, random, amount);
			shadows.setDistance2Ground(1).setHeight(0);
			resource().renderLaying(shadows, x, y, random, amount);

			
		}

		private int init(int tx, int ty, RESOURCE r, int amount) {
			
			if (amount > MAX_AMOUNT)
				 amount = MAX_AMOUNT;
			
			if (amount <= 0)
				throw new RuntimeException(""+amount);
			
			this.amount = (short) CLAMP.i(amount, 0, MAX_AMOUNT);
			
			resource = r.bIndex();
			body.moveX1Y1(tx<<C.T_SCROLL, ty<<C.T_SCROLL);
			claimed = 0;
			PATH().finders.resource.reportPresence(this);
			return amount -this.amount;
		}
		
		private int increaseAmount(int amount) {
			
			if (amount == 0)
				return 0;
			if (this.amount == MAX_AMOUNT)
				return amount;
			
			boolean res = findableReservedCanBe();
			int ret = 0;
			
			int a = this.amount + amount;
			
			if (a > MAX_AMOUNT) {
				ret = a - MAX_AMOUNT;
				a = MAX_AMOUNT;
			}
			this.amount = (short) a;
			if (!res) {
				PATH().finders.resource.reportPresence(this);
				evaluate(x(), y());
			}
			CoopRuntime.scatteredResourceChanged(this);
			return ret;
		}
		
		@Override
		protected void addAction() {
			evaluate(x(), y());
			CoopRuntime.scatteredResourceChanged(this);
		}
		
		@Override
		public RECTANGLE body() {
			return body;
		}
		
		@Override
		public RESOURCE resource() {
			return RESOURCES.ALL().get(resource);
		}

		@Override
		public int x() {
			return body.cX() >> C.T_SCROLL;
		}

		@Override
		public int y() {
			return body.cY() >> C.T_SCROLL;
		}

		@Override
		public void findableReserve() {
			if (claimed < amount) {
				claimed++;
				if (claimed == amount) {
					PATH().finders.resource.reportAbsence(this);
					evaluate(x(), y());
				}
			}else {
				GAME.Error(debug());
			}
			
		}
		
		private String debug() {
			return resource().name + " " + x() + " " + y() + " amount:" + amount + " claimed:" + claimed;
		}

		@Override
		public void findableReserveCancel() {
			if (claimed == 0)
				return;
			if (claimed == amount) {
				claimed --;
				PATH().finders.resource.reportPresence(this);
				evaluate(ctx(), cty());
			}else {
				claimed --;
			}
		}

		@Override
		public void resourcePickup() {
			if (claimed == 0)
				GAME.Error(debug());
			claimed --;
			amount --;
			if (amount < claimed)
				GAME.Error(debug());
			if (amount == 0) {
				super.remove();
			}else {
				CoopRuntime.scatteredResourceChanged(this);
			}
		}

		public void removeUnreserved(int a) {
			if (claimed < amount) {
				claimed+=a;
				if (claimed > amount)
					throw new RuntimeException();
				if (claimed == amount) {
					PATH().finders.resource.reportAbsence(this);
					evaluate(x(), y());
				}
				amount -= a;
				claimed -= a;
				if (amount == 0)
					super.remove();
				else
					CoopRuntime.scatteredResourceChanged(this);
			}else {
				GAME.Error(debug()); 
			}
		}

		@Override
		public boolean findableReservedIs() {
			return claimed > 0;
		}
		
		public int amountReserved() {
			return claimed;
		}

		@Override
		public int amount() {
			return amount;
		}
		
		@Override
		public int reservable() {
			return amount - claimed;
		}
		
		@Override
		public boolean findableReservedCanBe() {
			return amount > 0 && claimed < amount;
		}

		@Override
		public void hover(GBox box) {
			
			for (RESOURCE r : RESOURCES.ALL()) {
				hoverRes[r.bIndex()] = 0;
				allclaimed[r.bIndex()] = 0;
			}

			for (Thing t : THINGS().get(ctx(), ctx()+1, cty(), cty()+1)) {
				if (t instanceof ScatteredResource) {
					
					ScatteredResource r = (ScatteredResource) t;
					if (r.body.cX() != this.body().cX() || r.body().cY() != this.body().cY())
						continue;
					hoverRes[r.resource().bIndex()] += r.amount();
					allclaimed[r.resource().bIndex()] += r.claimed;
					
				}
			}
			int i = 0;
			for (RESOURCE r : RESOURCES.ALL()) {
				if (hoverRes[r.bIndex()] != 0) {
					i++;
					box.text(r.name);
					box.setResource(r, hoverRes[r.bIndex()]);
					if (S.get().developer) {
						GText text = box.text();
						text.add(' ').add(allclaimed[r.bIndex()]);
						text.add(' ').add(has(x(), y(), r.bit));
						text.add(' ').add(getReservable(x(), y(), r.bit) != null);
						box.add(text);
					}
					box.NL();
				}
				if (i > 5)
					break;
			}
			
		}
		
		@Override
		public boolean canBeClicked() {
			return false;
		}
		
		@Override
		protected void removeAction() {
			if (claimed < amount) {
				int oldX = x();
				int oldY = y();
				RESOURCE oldResource = resource();
				PATH().finders.resource.reportAbsence(this);
				claimed = 0;
				amount = 0;
				
				evaluate(x(), y());
				resource = -1;
				CoopRuntime.scatteredResourceRemoved(oldX, oldY, oldResource);
			}
		}

		@Override
		protected int z() {
			return 99;
		}

		@Override
		public ThingFactory<?> factory() {
			return SETT.THINGS().resources;
		}
		
		@Override
		public boolean isStorage() {
			return false;
		}
		
		@Override
		public boolean isPrio() {
			return false;
		}
		
	}

	
}
