package dev.jensh.immersivetrainlines.raster;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailRasterTest {
	static final int WIDTH = 256;
	static final int HEIGHT = 160;
	static final int RAIL_Y = 65;

	/** Plain at y=64, a river at x 96..111, a ridge at x 150..199, and nothing charted east of x=224. */
	static final Terrain SCENE = new Terrain() {
		@Override
		public boolean charted(int chunkX, int chunkZ) {
			return chunkX < 14;
		}

		@Override
		public int surfaceY(int x, int z) {
			if (water(x, z)) return 58;
			if (x >= 150 && x < 200) return 64 + Math.min(Math.min(x - 149, 200 - x) * 3, 30);
			return 64;
		}

		@Override
		public boolean water(int x, int z) {
			return x >= 96 && x < 112;
		}
	};

	static TrackPath line(double x1, double z1, double x2, double z2) {
		int steps = (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1) * 2);
		double[] x = new double[steps + 1], y = new double[steps + 1], z = new double[steps + 1];
		for (int i = 0; i <= steps; i++) {
			x[i] = x1 + (x2 - x1) * i / steps;
			z[i] = z1 + (z2 - z1) * i / steps;
			y[i] = RAIL_Y;
		}
		return new TrackPath(x, y, z);
	}

	static TrackPath arc(double cx, double cz, double radius, double from, double to) {
		int steps = (int) Math.ceil(Math.abs(to - from) * radius * 2);
		double[] x = new double[steps + 1], y = new double[steps + 1], z = new double[steps + 1];
		for (int i = 0; i <= steps; i++) {
			double angle = from + (to - from) * i / steps;
			x[i] = cx + Math.cos(angle) * radius;
			z[i] = cz + Math.sin(angle) * radius;
			y[i] = RAIL_Y;
		}
		return new TrackPath(x, y, z);
	}

	static List<TrackPath> paths() {
		List<TrackPath> paths = new ArrayList<>();
		paths.add(line(8.5, 40.5, 250.5, 40.5));
		paths.add(line(40.5, 40.5, 90.5, 90.5));
		paths.add(line(90.5, 90.5, 250.5, 90.5));
		paths.add(arc(60.5, 110.5, 30, Math.PI, Math.PI * 1.5));
		paths.add(line(30.5, 110.5, 30.5, 150.5));
		paths.add(line(8.5, 120.5, 80.5, 150.5));
		return paths;
	}

	static List<StationMark> stations() {
		return List.of(new StationMark("Riverside", 70.5, RAIL_Y, 40.5), new StationMark("Uncharted Halt", 240.5, RAIL_Y, 40.5));
	}

	static RailRaster paint(LineStyle style) {
		return RailRaster.paint(paths(), stations(), SCENE, style, true);
	}

	@Test
	void surfaceLineIsInkWithCrossTicks() {
		RailRaster raster = paint(LineStyle.INK);
		int ticks = 0;
		for (int x = 12; x < 36; x++) {
			assertEquals(RailRaster.INK, raster.pixel(false, x, 40), "line at x=" + x);
			if (raster.pixel(false, x, 39) == RailRaster.INK) {
				assertEquals(RailRaster.INK, raster.pixel(false, x, 41));
				ticks++;
			}
		}
		assertEquals(6, ticks);
	}

	@Test
	void diagonalStaysOnePixelWide() {
		RailRaster raster = RailRaster.paint(List.of(line(40.5, 40.5, 90.5, 90.5)), List.of(), SCENE, LineStyle.RAILS, true);
		for (int z = 45; z < 85; z++) {
			int rails = 0;
			for (int x = 30; x < 100; x++) if (raster.pixel(false, x, z) == RailRaster.RAIL) rails++;
			assertEquals(1, rails, "rail pixels in row " + z);
		}
	}

	@Test
	void bridgeIsCasedOverWater() {
		RailRaster raster = paint(LineStyle.INK);
		assertEquals(RailRaster.INK, raster.pixel(false, 104, 40));
		assertEquals(RailRaster.PARCHMENT, raster.pixel(false, 104, 39));
		assertEquals(RailRaster.PARCHMENT, raster.pixel(false, 104, 41));
		assertEquals(RailRaster.GIRDER, paint(LineStyle.RAILS).pixel(false, 104, 39));
	}

	@Test
	void tunnelIsDashedOnTheSurfaceAndSolidInCaveMode() {
		RailRaster ink = paint(LineStyle.INK);
		int drawn = 0;
		for (int x = 160; x < 192; x++) {
			if (ink.pixel(false, x, 40) != 0) drawn++;
			assertEquals(RailRaster.PARCHMENT, ink.pixel(true, x, 40));
			assertEquals(0, ink.pixel(false, x, 39), "no ticks over a tunnel");
		}
		assertEquals(16, drawn);

		RailRaster rails = paint(LineStyle.RAILS);
		for (int x = 160; x < 192; x++) {
			assertEquals(0, rails.pixel(false, x, 40));
			assertEquals(RailRaster.RAIL, rails.pixel(true, x, 40));
		}
	}

	@Test
	void unchartedGroundStaysBlank() {
		RailRaster raster = paint(LineStyle.INK);
		assertNotEquals(0, raster.pixel(false, 223, 40));
		for (int x = 224; x < 256; x++) {
			for (int z = 36; z < 45; z++) assertEquals(0, raster.pixel(false, x, z));
		}
		RailRaster everything = RailRaster.paint(paths(), stations(), SCENE, LineStyle.INK, false);
		assertEquals(RailRaster.INK, everything.pixel(false, 245, 40));
		assertEquals(RailRaster.PARCHMENT, everything.pixel(false, 240, 40));
	}

	@Test
	void stationsAreMarked() {
		assertEquals(RailRaster.PARCHMENT, paint(LineStyle.INK).pixel(false, 70, 40));
		assertEquals(RailRaster.INK, paint(LineStyle.INK).pixel(false, 71, 41));
		assertEquals(RailRaster.FLAG, paint(LineStyle.RAILS).pixel(false, 70, 40));
		assertEquals(RailRaster.PLATFORM, paint(LineStyle.RAILS).pixel(true, 69, 39));
	}

	/** Not an assertion so much as a proof sheet: both styles over a stand-in map, for eyeballing. */
	@Test
	void writesPreviewSheet() throws Exception {
		String out = System.getProperty("immersivetrainlines.preview");
		if (out == null) return;
		int scale = 4;
		BufferedImage sheet = new BufferedImage(WIDTH * scale, HEIGHT * scale * 4, BufferedImage.TYPE_INT_RGB);
		int row = 0;
		for (LineStyle style : LineStyle.values()) {
			RailRaster raster = paint(style);
			for (boolean caveLayer : new boolean[]{false, true}) {
				for (int x = 0; x < WIDTH; x++) {
					for (int z = 0; z < HEIGHT; z++) {
						int ground = ground(x, z, caveLayer);
						int ink = raster.pixel(caveLayer, x, z);
						int color = blend(ground, ink);
						for (int sx = 0; sx < scale; sx++) {
							for (int sz = 0; sz < scale; sz++) {
								sheet.setRGB(x * scale + sx, (row * HEIGHT + z) * scale + sz, color);
							}
						}
					}
				}
				row++;
			}
		}
		File file = new File(out, "styles.png");
		file.getParentFile().mkdirs();
		assertTrue(ImageIO.write(sheet, "png", file));
	}

	static int ground(int x, int z, boolean caveLayer) {
		if (!SCENE.charted(x >> 4, z >> 4)) return ((x / 8 + z / 8) & 1) == 0 ? 0xD6BE96 : 0xCDB48B;
		if (caveLayer) return ((x * 7 + z * 13) % 5 == 0) ? 0x4C4C4C : 0x565656;
		if (SCENE.water(x, z)) return 0x4040FF - ((z & 1) == 0 ? 0x0A0A20 : 0);
		int y = SCENE.surfaceY(x, z);
		if (y > 64) return x < 175 ? 0x7C7C7C : 0x606060;
		return ((x * 31 + z * 17) % 7 == 0) ? 0x6DA136 : 0x7FB238;
	}

	static int blend(int rgb, int argb) {
		int a = argb >>> 24;
		int result = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			int under = (rgb >> shift) & 0xFF;
			int over = (argb >> shift) & 0xFF;
			result |= ((over * a + under * (255 - a)) / 255) << shift;
		}
		return result;
	}
}
