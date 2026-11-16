package dev.jensh.immersivetrainlines.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jensh.immersivetrainlines.Config;
import dev.jensh.immersivetrainlines.ImmersiveTrainLines;
import dev.jensh.immersivetrainlines.raster.LineStyle;
import dev.jensh.immersivetrainlines.raster.RailRaster;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Per-dimension railway drawings, kept as textures aligned with Hoofprint's own 512-block map regions. */
public final class RailLayers {
	private static final Map<ResourceKey<Level>, RailLayers> LAYERS = new HashMap<>();
	private static final long REFRESH_MILLIS = 1000;
	private static int nextTextureId;

	public record Sheet(DynamicTexture texture, ResourceLocation location) {
	}

	private final ResourceKey<Level> dimension;
	private HoofprintTerrain terrain;
	private RailNetwork network = RailNetwork.EMPTY;
	private final Map<Long, Sheet> surface = new HashMap<>();
	private final Map<Long, Sheet> cave = new HashMap<>();
	private long lastCheck = Long.MIN_VALUE;
	private boolean baked;
	private int version;
	private int charted;
	private LineStyle style;
	private boolean chartedOnly;

	private RailLayers(ResourceKey<Level> dimension) {
		this.dimension = dimension;
		this.terrain = new HoofprintTerrain(dimension);
	}

	public static RailLayers get(ResourceKey<Level> dimension) {
		RailLayers layers = LAYERS.computeIfAbsent(dimension, RailLayers::new);
		layers.refresh();
		return layers;
	}

	public static void clear() {
		LAYERS.values().forEach(RailLayers::close);
		LAYERS.clear();
	}

	public Map<Long, Sheet> sheets(boolean caveMode) {
		return caveMode ? cave : surface;
	}

	public RailNetwork network() {
		return network;
	}

	public boolean charted(double x, double z) {
		return !Config.CHARTED_ONLY.get() || terrain.charted((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
	}

	private void refresh() {
		long now = Util.getMillis();
		if (baked && now - lastCheck < REFRESH_MILLIS) return;
		lastCheck = now;
		try {
			int newVersion = RailNetwork.version();
			int newCharted = terrain.chartedCount();
			LineStyle newStyle = Config.LINE_STYLE.get();
			boolean newChartedOnly = Config.CHARTED_ONLY.get();
			if (baked && newVersion == version && newCharted == charted && newStyle == style && newChartedOnly == chartedOnly) return;
			// Track changes usually come with terraforming, so re-read the ground too.
			if (newVersion != version) terrain = new HoofprintTerrain(dimension);
			version = newVersion;
			charted = newCharted;
			style = newStyle;
			chartedOnly = newChartedOnly;
			network = RailNetwork.read(dimension);
			RailRaster raster = RailRaster.paint(network.paths(), network.stations(), terrain, style, chartedOnly);
			upload(raster.surface, surface);
			upload(raster.cave, cave);
		} catch (Throwable error) {
			if (!baked) ImmersiveTrainLines.LOGGER.error("Could not draw railways onto the map", error);
		}
		baked = true;
	}

	private static void upload(Map<Long, int[]> pixels, Map<Long, Sheet> sheets) {
		for (Iterator<Map.Entry<Long, Sheet>> iterator = sheets.entrySet().iterator(); iterator.hasNext(); ) {
			Map.Entry<Long, Sheet> entry = iterator.next();
			if (pixels.containsKey(entry.getKey())) continue;
			Minecraft.getInstance().getTextureManager().release(entry.getValue().location());
			iterator.remove();
		}
		pixels.forEach((key, argb) -> {
			Sheet sheet = sheets.computeIfAbsent(key, k -> {
				DynamicTexture texture = new DynamicTexture(RailRaster.REGION, RailRaster.REGION, true);
				ResourceLocation location = ResourceLocation.fromNamespaceAndPath(ImmersiveTrainLines.ID, "rails/" + nextTextureId++);
				Minecraft.getInstance().getTextureManager().register(location, texture);
				return new Sheet(texture, location);
			});
			NativeImage image = sheet.texture().getPixels();
			if (image == null) return;
			for (int i = 0; i < argb.length; i++) {
				int color = argb[i];
				// NativeImage wants ABGR.
				int abgr = (color & 0xFF00FF00) | ((color & 0xFF) << 16) | ((color >> 16) & 0xFF);
				image.setPixelRGBA(i & (RailRaster.REGION - 1), i >> RailRaster.REGION_POWER, abgr);
			}
			sheet.texture().upload();
		});
	}

	private void close() {
		for (Map<Long, Sheet> sheets : new Map[]{surface, cave}) {
			sheets.values().forEach(sheet -> Minecraft.getInstance().getTextureManager().release(sheet.location()));
			sheets.clear();
		}
	}
}
