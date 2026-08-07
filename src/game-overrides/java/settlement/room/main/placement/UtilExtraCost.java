package settlement.room.main.placement;

import game.GAME;
import settlement.main.SETT;
import settlement.misc.util.TileRayTracer;
import settlement.misc.util.TileRayTracer.Ray;
import settlement.room.main.ROOMA;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.construction.ConstructionData;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.misc.CLAMP;
import util.GUTIL;

public final class UtilExtraCost {

	private double support = 0;
	private double foundation = 0;
	private int tick = -1;
	private final RoomPlacer placer;
	private static final TileRayTracer tracer = new TileRayTracer(4);

	UtilExtraCost(RoomPlacer placer) {
		this.placer = placer;
	}

	public double support() {
		cache();
		return support;
	}

	public double foundation() {
		cache();
		return foundation;
	}

	public double total() {
		cache();
		return support + foundation;
	}

	private void cache() {
		if (GAME.updateI() == tick)
			return;

		tick = GAME.updateI();

		RoomBlueprintImp blueprint = placer.blueprint();
		if (blueprint == null || blueprint.constructor() == null) {
			support = 0;
			foundation = 0;
			return;
		}

		support = psupport(placer.instance, blueprint);
		foundation = pfoundation(placer.instance, blueprint);
	}

	public double get(int tx, int ty) {
		cache();
		return CLAMP.d(GUTIL.marker().v1.get(tx, ty) - 1, 0, 4) / 4.0;
	}

	public boolean is(int tx, int ty) {
		return ConstructionData.dExpensive.is(tx, ty, 1);
	}

	public static double psupport(ROOMA a, RoomBlueprintImp blueprint) {
		if (a == null || blueprint == null || blueprint.constructor() == null)
			return 0;
		if (blueprint.constructor().mustBeIndoors() && blueprint.constructor().usesArea())
			return support(a) * 2;
		return 0;
	}

	public static double pfoundation(ROOMA a, RoomBlueprintImp blueprint) {
		if (a == null || blueprint == null || blueprint.constructor() == null || a.area() == 0)
			return 0;
		if (blueprint.constructor().isHeavy()) {
			double d = 0;
			for (COORDINATE c : a.body()) {
				if (a.is(c))
					d += SETT.ENV().foundation.get(c);
			}
			d /= a.area();
			return foundation(d);
		}
		return 0;
	}

	public static double foundation(Room room, int rx, int ry) {
		int x1 = room.x1(rx, ry);
		int x2 = x1 + room.width(rx, ry);
		int y1 = room.y1(rx, ry);
		int y2 = y1 + room.height(rx, ry);

		double f = 0;
		double a = 0;

		for (int y = y1; y < y2; y++) {
			for (int x = x1; x < x2; x++) {
				if (room.isSame(rx, ry, x, y)) {
					f += SETT.ENV().foundation.get(x, y);
					a++;
				}
			}
		}

		if (a > 0)
			f /= a;
		return foundation(f);
	}

	public static double foundation(double aveFoundation) {
		double d = 0.1 - aveFoundation * 0.2;
		d = (int) (100 * d) / 100.0;
		return d;
	}

	private static double support(ROOMA a) {
		GUTIL.marker().init(UtilExtraCost.class);

		for (COORDINATE c : a.body()) {
			if (a.is(c)) {
				double v = support(a, c.x(), c.y());
				GUTIL.marker().v1.set(c, v);
				GUTIL.marker().v2.set(c, v);
			}
		}

		double total = 0;
		double exp = 0;
		double value = 0;

		for (COORDINATE c : a.body()) {
			if (a.is(c)) {
				double v = GUTIL.marker().v1.get(c);
				total++;
				if (v >= 1)
					ConstructionData.dExpensive.set(a, c, 0);
				else {
					ConstructionData.dExpensive.set(a, c, 1);
					exp++;
				}
			}
		}

		GUTIL.marker().done();

		if (total == 0)
			value = 0;
		else
			value = exp / total;
		value *= 4;
		value = CLAMP.d(value, 0, 1);
		return value;
	}

	private static double support(AREA a, int tx, int ty) {
		double s = 0;
		tracer.checkInit();

		for (Ray r : tracer.rays()) {
			for (int i = 0; i < r.size(); i++) {
				int dx = tx + r.get(i).x();
				int dy = ty + r.get(i).y();
				if (!SETT.IN_BOUNDS(dx, dy))
					break;
				if (!a.is(dx, dy)) {
					if (!SETT.ROOMS().map.is(dx, dy) && tracer.check(r.get(i)))
						s += CLAMP.d((double) (3.5 - i) / 3.5, 0, 1);
					break;
				}
			}
		}
		return s;
	}
}
