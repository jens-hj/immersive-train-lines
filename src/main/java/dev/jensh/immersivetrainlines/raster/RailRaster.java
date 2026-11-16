package dev.jensh.immersivetrainlines.raster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints railways at one pixel per block, the same resolution as the map underneath, so lines look drawn onto
 * the map rather than laid over it. Produces a surface layer and a cave layer, split into 512x512 regions.
 */
public final class RailRaster {
	public static final int REGION_POWER = 9;
	public static final int REGION = 1 << REGION_POWER;

	// Vanilla map palette tones, so the lines sit in the same colour world as the terrain.
	public static final int INK = 0xFF24160F;
	public static final int PARCHMENT = 0xFFDDCFA2;
	public static final int RAIL = 0xFF909090;
	public static final int SLEEPER = 0xFF6F4A2A;
	public static final int GIRDER = 0xFF565656;
	public static final int PLATFORM = 0xFFC7C7C7;
	public static final int FLAG = 0xFF8E3C2E;

	/** Blocks of cover above the rails before a stretch counts as a tunnel. */
	static final int TUNNEL_COVER = 4;
	/** How far the ground must drop on both sides before a stretch counts as a bridge. */
	static final int BRIDGE_DROP = 3;
	static final int MIN_RUN = 3;

	enum Kind { SURFACE, TUNNEL, BRIDGE }

	public final Map<Long, int[]> surface = new HashMap<>();
	public final Map<Long, int[]> cave = new HashMap<>();

	private final Terrain terrain;
	private final LineStyle style;
	private final boolean chartedOnly;
	// Painted back to front: sleepers/ticks/casings, then the line itself, then stations.
	private final List<List<int[]>> passes = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

	private RailRaster(Terrain terrain, LineStyle style, boolean chartedOnly) {
		this.terrain = terrain;
		this.style = style;
		this.chartedOnly = chartedOnly;
	}

	public static RailRaster paint(List<TrackPath> paths, List<StationMark> stations, Terrain terrain, LineStyle style, boolean chartedOnly) {
		RailRaster raster = new RailRaster(terrain, style, chartedOnly);
		for (TrackPath path : paths) raster.paintPath(path);
		for (StationMark station : stations) raster.paintStation(station);
		for (List<int[]> pass : raster.passes) {
			for (int[] op : pass) raster.apply(op[0] == 0 ? raster.surface : raster.cave, op[1], op[2], op[3]);
		}
		return raster;
	}

	public static long regionKey(int regionX, int regionZ) {
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}

	public static int regionX(long key) {
		return (int) (key >> 32);
	}

	public static int regionZ(long key) {
		return (int) key;
	}

	/** ARGB of a painted pixel, 0 if empty. */
	public int pixel(boolean caveLayer, int x, int z) {
		int[] region = (caveLayer ? cave : surface).get(regionKey(x >> REGION_POWER, z >> REGION_POWER));
		return region == null ? 0 : region[(z & (REGION - 1)) * REGION + (x & (REGION - 1))];
	}

	private void apply(Map<Long, int[]> layer, int x, int z, int argb) {
		if (chartedOnly && !terrain.charted(x >> 4, z >> 4)) return;
		int[] region = layer.computeIfAbsent(regionKey(x >> REGION_POWER, z >> REGION_POWER), k -> new int[REGION * REGION]);
		region[(z & (REGION - 1)) * REGION + (x & (REGION - 1))] = argb;
	}

	private void put(int pass, boolean caveLayer, int x, int z, int argb) {
		passes.get(pass).add(new int[]{caveLayer ? 1 : 0, x, z, argb});
	}

	private static int alpha(int argb, int alpha) {
		return (argb & 0xFFFFFF) | (alpha << 24);
	}

	private void paintPath(TrackPath path) {
		// Thin 8-connected pixel walk along the centreline.
		int n = 0;
		int[] xs = new int[path.size()];
		int[] zs = new int[path.size()];
		double[] ys = new double[path.size()];
		for (int i = 0; i < path.size(); i++) {
			int x = (int) Math.floor(path.x()[i]);
			int z = (int) Math.floor(path.z()[i]);
			if (n > 0 && xs[n - 1] == x && zs[n - 1] == z) continue;
			// Drop staircase corners so diagonals stay one pixel wide.
			if (n > 1 && Math.abs(xs[n - 2] - x) <= 1 && Math.abs(zs[n - 2] - z) <= 1) n--;
			xs[n] = x;
			zs[n] = z;
			ys[n] = path.y()[i];
			n++;
		}
		if (n == 0) return;
		if (chartedOnly) {
			boolean any = false;
			for (int i = 0; i < n && !any; i++) any = terrain.charted(xs[i] >> 4, zs[i] >> 4);
			if (!any) return;
		}

		int[] px = new int[n];
		int[] pz = new int[n];
		Kind[] kinds = new Kind[n];
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 2);
			int b = Math.min(n - 1, i + 2);
			double dx = xs[b] - xs[a];
			double dz = zs[b] - zs[a];
			double length = Math.max(1e-6, Math.hypot(dx, dz));
			// Perpendicular, snapped to the eight pixel directions.
			px[i] = Math.abs(dz / length) > 0.38 ? (int) -Math.signum(dz) : 0;
			pz[i] = Math.abs(dx / length) > 0.38 ? (int) Math.signum(dx) : 0;
			kinds[i] = classify(xs[i], zs[i], ys[i], px[i], pz[i]);
		}
		smooth(kinds);

		for (int i = 0; i < n; i++) {
			Kind kind = kinds[i];
			boolean runStart = i == 0 || kinds[i - 1] != kind;
			boolean runEnd = i == n - 1 || kinds[i + 1] != kind;
			// A tunnel mouth sits on the last open-air pixel before the line goes under.
			boolean mouth = kind != Kind.TUNNEL && ((i > 0 && kinds[i - 1] == Kind.TUNNEL) || (i < n - 1 && kinds[i + 1] == Kind.TUNNEL));
			if (style == LineStyle.INK) inkStep(i, xs[i], zs[i], px[i], pz[i], kind, runStart || runEnd, mouth);
			else railStep(i, xs[i], zs[i], px[i], pz[i], kind, mouth);
		}
	}

	private Kind classify(int x, int z, double y, int px, int pz) {
		int surface = terrain.surfaceY(x, z);
		if (surface != Terrain.UNKNOWN && surface >= y + TUNNEL_COVER) return Kind.TUNNEL;
		if (px == 0 && pz == 0) return Kind.SURFACE;
		for (int reach = 2; reach <= 3; reach++) {
			if (low(x + px * reach, z + pz * reach, y) && low(x - px * reach, z - pz * reach, y)) return Kind.BRIDGE;
		}
		return Kind.SURFACE;
	}

	private boolean low(int x, int z, double y) {
		if (terrain.water(x, z)) return true;
		int surface = terrain.surfaceY(x, z);
		return surface != Terrain.UNKNOWN && surface <= y - BRIDGE_DROP;
	}

	/** Folds runs too short to read at map scale into their neighbour. */
	private static void smooth(Kind[] kinds) {
		int start = 0;
		while (start < kinds.length) {
			int end = start;
			while (end < kinds.length && kinds[end] == kinds[start]) end++;
			if (end - start < MIN_RUN && !(start == 0 && end == kinds.length)) {
				Kind fill = start > 0 ? kinds[start - 1] : kinds[end];
				for (int i = start; i < end; i++) kinds[i] = fill;
			}
			start = end;
		}
	}

	private void inkStep(int i, int x, int z, int px, int pz, Kind kind, boolean runEdge, boolean mouth) {
		switch (kind) {
			case SURFACE -> {
				put(1, false, x, z, INK);
				put(1, true, x, z, alpha(INK, 0x55));
				if (i % 4 == 2 || mouth) sides(0, false, x, z, px, pz, INK);
			}
			case BRIDGE -> {
				put(1, false, x, z, INK);
				put(1, true, x, z, alpha(INK, 0x55));
				sides(0, false, x, z, px, pz, runEdge || mouth ? INK : PARCHMENT);
			}
			case TUNNEL -> {
				if (i % 4 < 2) put(1, false, x, z, alpha(INK, 0x99));
				put(1, true, x, z, INK);
				if (i % 4 == 2) sides(0, true, x, z, px, pz, INK);
			}
		}
	}

	private void railStep(int i, int x, int z, int px, int pz, Kind kind, boolean mouth) {
		switch (kind) {
			case SURFACE -> {
				put(1, false, x, z, RAIL);
				put(1, true, x, z, alpha(RAIL, 0x55));
				if (mouth) sides(0, false, x, z, px, pz, GIRDER);
				else if (i % 2 == 0) sides(0, false, x, z, px, pz, SLEEPER);
			}
			case BRIDGE -> {
				put(1, false, x, z, RAIL);
				put(1, true, x, z, alpha(RAIL, 0x55));
				sides(0, false, x, z, px, pz, GIRDER);
			}
			case TUNNEL -> {
				put(1, true, x, z, RAIL);
				if (i % 2 == 0) sides(0, true, x, z, px, pz, SLEEPER);
			}
		}
	}

	private void sides(int pass, boolean caveLayer, int x, int z, int px, int pz, int argb) {
		if (px == 0 && pz == 0) return;
		put(pass, caveLayer, x + px, z + pz, argb);
		put(pass, caveLayer, x - px, z - pz, argb);
	}

	private void paintStation(StationMark station) {
		int x = (int) Math.floor(station.x());
		int z = (int) Math.floor(station.z());
		if (chartedOnly && !terrain.charted(x >> 4, z >> 4)) return;
		int ring = style == LineStyle.INK ? INK : PLATFORM;
		int centre = style == LineStyle.INK ? PARCHMENT : FLAG;
		for (int layer = 0; layer < 2; layer++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(2, layer == 1, x + dx, z + dz, dx == 0 && dz == 0 ? centre : ring);
				}
			}
		}
	}
}
