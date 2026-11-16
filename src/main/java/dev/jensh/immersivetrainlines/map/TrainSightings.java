package dev.jensh.immersivetrainlines.map;

import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapSync;
import com.simibubi.create.compat.trainmap.TrainMapSync.TrainMapSyncEntry;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;
import com.simibubi.create.content.trains.entity.Train;
import dev.jensh.immersivetrainlines.Config;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Which trains the map is allowed to know about. Positions come from Create's own train-map sync, which any
 * client may ask the server for; this decides which of them a map-reader could plausibly have.
 */
public final class TrainSightings {
	private static int wantedTicks;

	/** A carriage as the segment between its two bogeys. */
	public record Carriage(double x1, double z1, double x2, double z2) {
	}

	public record Sighting(Train train, TrainMapSyncEntry entry, List<Carriage> carriages) {
		public boolean atStation() {
			return entry.targetStationDistance == 0 && !entry.targetStationName.isBlank();
		}
	}

	private TrainSightings() {
	}

	/** Called while a map is on screen; keeps position reports coming for a couple of seconds. */
	public static void keepWatching() {
		wantedTicks = 40;
	}

	public static void tick() {
		if (wantedTicks <= 0 || Minecraft.getInstance().level == null) return;
		wantedTicks--;
		if (Config.TRAINS.get() != Config.TrainVisibility.NONE) TrainMapSyncClient.requestData();
	}

	public static List<Sighting> visible(ResourceKey<Level> dimension, RailLayers layers) {
		Config.TrainVisibility mode = Config.TRAINS.get();
		if (mode == Config.TrainVisibility.NONE) return List.of();
		double time = Mth.clamp((AnimationTickHolder.getTicks() + AnimationTickHolder.getPartialTicks() - TrainMapSyncClient.lastPacket)
			/ TrainMapSync.lightPacketInterval, 0, 1);
		LocalPlayer player = Minecraft.getInstance().player;
		boolean playerHere = player != null && player.level().dimension() == dimension;
		double range = Config.NEARBY_RANGE.get();

		List<Sighting> sightings = new ArrayList<>();
		for (Train train : CreateClient.RAILWAYS.trains.values()) {
			TrainMapSyncEntry entry = TrainMapSyncClient.currentData.get(train.id);
			if (entry == null || entry.dimensions == null) continue;
			List<Carriage> carriages = new ArrayList<>();
			boolean near = false;
			boolean charted = false;
			for (int i = 0; i < entry.dimensions.size(); i++) {
				if (entry.dimensions.get(i) != dimension) continue;
				Vec3 front = entry.getPosition(i, true, time);
				Vec3 back = entry.getPosition(i, false, time);
				carriages.add(new Carriage(front.x, front.z, back.x, back.z));
				if (playerHere && Math.hypot(front.x - player.getX(), front.z - player.getZ()) <= range) near = true;
				if (layers.charted(front.x, front.z)) charted = true;
			}
			if (carriages.isEmpty()) continue;
			Sighting sighting = new Sighting(train, entry, carriages);
			boolean known = mode == Config.TrainVisibility.ALL ? charted : near || (charted && sighting.atStation());
			if (known) sightings.add(sighting);
		}
		return sightings;
	}
}
