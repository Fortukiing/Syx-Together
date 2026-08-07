package coopmod;

import settlement.main.SETT;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;

final class CoopTileArea implements AREA {

	private final boolean[] map = new boolean[SETT.TAREA];
	private final Rec bounds = new Rec();
	private int area;

	CoopTileArea(String tiles) {
		bounds.set(SETT.TWIDTH, 0, SETT.THEIGHT, 0);
		if (tiles == null || tiles.length() == 0)
			return;
		String[] all = tiles.split(";");
		for (String t : all) {
			int comma = t.indexOf(',');
			if (comma < 0)
				continue;
			int x = Integer.parseInt(t.substring(0, comma));
			int y = Integer.parseInt(t.substring(comma + 1));
			add(x, y);
		}
	}

	private void add(int tx, int ty) {
		if (!SETT.IN_BOUNDS(tx, ty))
			return;
		int i = tx + ty * SETT.TWIDTH;
		if (!map[i]) {
			map[i] = true;
			area++;
			bounds.unify(tx, ty);
		}
	}

	@Override
	public boolean is(int tile) {
		return tile >= 0 && tile < map.length && map[tile];
	}

	@Override
	public boolean is(int tx, int ty) {
		return SETT.IN_BOUNDS(tx, ty) && is(tx + ty * SETT.TWIDTH);
	}

	@Override
	public boolean is(int tx, int ty, snake2d.util.datatypes.DIR d) {
		return is(tx + d.x(), ty + d.y());
	}

	@Override
	public boolean is(COORDINATE c) {
		return is(c.x(), c.y());
	}

	@Override
	public boolean is(COORDINATE c, snake2d.util.datatypes.DIR d) {
		return is(c.x() + d.x(), c.y() + d.y());
	}

	@Override
	public RECTANGLE body() {
		return bounds;
	}

	@Override
	public int area() {
		return area;
	}
}
