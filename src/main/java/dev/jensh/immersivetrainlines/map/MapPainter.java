package dev.jensh.immersivetrainlines.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.jensh.immersivetrainlines.Config;
import dev.jensh.immersivetrainlines.ImmersiveTrainLines;
import dev.jensh.immersivetrainlines.raster.RailRaster;
import dev.jensh.immersivetrainlines.raster.StationMark;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Draws railways, stations and trains into a map view. Shared by the Hoofprint world map and the minimap. */
public final class MapPainter {
	/** Liveries in vanilla dye tones; trains keep the colour Create assigned them. */
	private static final int[] LIVERIES = {0xFFB02E26, 0xFF3C44AA, 0xFF5E7C16, 0xFFF9801D, 0xFF8932B8, 0xFF169C9C, 0xFFFED83D, 0xFF835432};
	private static boolean warned;

	/**
	 * A window onto the world: {@code scale} GUI pixels per block, centred on a world position, occupying a
	 * rectangle of the GUI.
	 */
	public record View(ResourceKey<Level> dimension, double centreX, double centreZ, float scale, float left, float top, float width, float height, boolean caveMode) {
		double screenX(double worldX) {
			return left + width / 2.0 + (worldX - centreX) * scale;
		}

		double screenY(double worldZ) {
			return top + height / 2.0 + (worldZ - centreZ) * scale;
		}

		boolean contains(double screenX, double screenY) {
			return screenX >= left && screenX < left + width && screenY >= top && screenY < top + height;
		}
	}

	private MapPainter() {
	}

	/**
	 * @param decorations whether to draw trains and labels on top of the lines themselves
	 * @param labels whether station names are written out
	 * @return tooltip for whatever railway feature is under the mouse, or null
	 */
	public static List<Component> draw(GuiGraphics graphics, View view, boolean decorations, boolean labels, double mouseX, double mouseY) {
		if (view.dimension() == null) return null;
		try {
			TrainSightings.keepWatching();
			RailLayers layers = RailLayers.get(view.dimension());
			drawLines(graphics, view, layers);
			if (!decorations) return null;
			List<TrainSightings.Sighting> sightings = TrainSightings.visible(view.dimension(), layers);
			drawTrains(graphics, view, sightings);
			return drawStations(graphics, view, layers, sightings, labels, mouseX, mouseY);
		} catch (Throwable error) {
			if (!warned) {
				warned = true;
				ImmersiveTrainLines.LOGGER.error("Could not draw railways onto the map", error);
			}
			return null;
		}
	}

	private static void drawLines(GuiGraphics graphics, View view, RailLayers layers) {
		PoseStack pose = graphics.pose();
		double reach = RailRaster.REGION;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		pose.pushPose();
		pose.translate(view.left() + view.width() / 2.0, view.top() + view.height() / 2.0, 0);
		pose.scale(view.scale(), view.scale(), 1);
		double halfWidth = view.width() / 2.0 / view.scale();
		double halfHeight = view.height() / 2.0 / view.scale();
		for (Map.Entry<Long, RailLayers.Sheet> entry : layers.sheets(view.caveMode()).entrySet()) {
			// Relative to the view centre before going to floats, so far-out coordinates stay sharp.
			double x = RailRaster.regionX(entry.getKey()) * reach - view.centreX();
			double z = RailRaster.regionZ(entry.getKey()) * reach - view.centreZ();
			if (x > halfWidth || z > halfHeight || x + reach < -halfWidth || z + reach < -halfHeight) continue;
			pose.pushPose();
			pose.translate(x, z, 0);
			graphics.blit(entry.getValue().location(), 0, 0, 0f, 0f, RailRaster.REGION, RailRaster.REGION, RailRaster.REGION, RailRaster.REGION);
			pose.popPose();
		}
		pose.popPose();
	}

	private static void drawTrains(GuiGraphics graphics, View view, List<TrainSightings.Sighting> sightings) {
		PoseStack pose = graphics.pose();
		for (TrainSightings.Sighting sighting : sightings) {
			int livery = LIVERIES[Math.floorMod(sighting.train().mapColorIndex, LIVERIES.length)];
			for (TrainSightings.Carriage carriage : sighting.carriages()) {
				double dx = carriage.x2() - carriage.x1();
				double dz = carriage.z2() - carriage.z1();
				// Half-block units: a carriage body is three blocks wide and overhangs its bogeys.
				int halfLength = (int) Math.round(Math.hypot(dx, dz)) + 3;
				pose.pushPose();
				pose.translate(view.screenX((carriage.x1() + carriage.x2()) / 2), view.screenY((carriage.z1() + carriage.z2()) / 2), 0);
				pose.scale(view.scale() / 2, view.scale() / 2, 1);
				pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dz, dx)));
				graphics.fill(-halfLength, -3, halfLength, 3, RailRaster.INK);
				graphics.fill(-halfLength + 1, -2, halfLength - 1, 2, livery);
				pose.popPose();
			}
		}
	}

	private static List<Component> drawStations(GuiGraphics graphics, View view, RailLayers layers, List<TrainSightings.Sighting> sightings, boolean labels, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		StationMark hovered = null;
		double best = Math.max(4, 2.5 * view.scale());
		for (StationMark station : layers.network().stations()) {
			double x = view.screenX(station.x());
			double y = view.screenY(station.z());
			if (!view.contains(x, y) || !layers.charted(station.x(), station.z())) continue;
			double distance = Math.max(Math.abs(mouseX - x), Math.abs(mouseY - y));
			if (distance < best) {
				best = distance;
				hovered = station;
			}
			if (labels || Config.STATION_LABELS.get()) {
				graphics.drawString(font, station.name(), (int) x - font.width(station.name()) / 2, (int) (y + 2 * view.scale()) + 2, 0xFFFFFF, true);
			}
		}
		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.name()));
			for (TrainSightings.Sighting sighting : sightings) {
				if (sighting.atStation() && sighting.entry().targetStationName.equals(hovered.name())) {
					lines.add(Component.translatable("immersive_train_lines.map.at_platform", sighting.train().name).withStyle(ChatFormatting.GRAY));
				}
			}
			return lines;
		}
		for (TrainSightings.Sighting sighting : sightings) {
			for (TrainSightings.Carriage carriage : sighting.carriages()) {
				double x = view.screenX((carriage.x1() + carriage.x2()) / 2);
				double y = view.screenY((carriage.z1() + carriage.z2()) / 2);
				double length = Math.hypot(carriage.x2() - carriage.x1(), carriage.z2() - carriage.z1()) / 2 + 2;
				if (Math.max(Math.abs(mouseX - x), Math.abs(mouseY - y)) > Math.max(4, length * view.scale())) continue;
				List<Component> lines = new ArrayList<>();
				lines.add(sighting.train().name.copy());
				String station = sighting.entry().targetStationName;
				if (!station.isBlank()) {
					lines.add(Component.translatable(sighting.atStation() ? "immersive_train_lines.map.standing_at" : "immersive_train_lines.map.bound_for", station).withStyle(ChatFormatting.GRAY));
				}
				return lines;
			}
		}
		return null;
	}
}
