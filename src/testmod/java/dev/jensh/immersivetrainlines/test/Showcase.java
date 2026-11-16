package dev.jensh.immersivetrainlines.test;

import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapSync.TrainMapSyncEntry;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.jensh.immersivetrainlines.Config;
import dev.jensh.immersivetrainlines.map.RailLayers;
import dev.jensh.immersivetrainlines.raster.LineStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lays a railway with a tunnel, a bridge, a crossing and a station in a fresh world, charts it with Hoofprint
 * and photographs the world map and the minimap in both line styles.
 * Enabled with -Dimmersivetrainlines.showcase=<output dir>; needs the map mods in the run's mods folder.
 */
public final class Showcase {
	private static final int SITE_X = 2000, SITE_Z = 2000, LENGTH = 220;
	private final Path out = Path.of(System.getProperty("immersivetrainlines.showcase"));
	private final Deque<Step> steps = new ArrayDeque<>();
	private final List<String> report = new ArrayList<>();
	private final UUID fakeTrain = UUID.randomUUID();
	private boolean started, planned, done, trainRunning;
	private int ticks, wait, railY;

	private interface Step {
		boolean run(Minecraft mc, MinecraftServer server) throws Exception;
	}

	public static void register() {
		Showcase showcase = new Showcase();
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> showcase.tick());
	}

	private void tick() {
		if (done) return;
		Minecraft mc = Minecraft.getInstance();
		try {
			if (++ticks > 20 * 60 * 20) throw new IllegalStateException("Showcase timed out");
			if (!started) {
				if (!(mc.screen instanceof TitleScreen)) return;
				started = true;
				mc.createWorldOpenFlows().createFreshLevel("showcase",
					new LevelSettings("Showcase", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
					new WorldOptions(20261116L, false, false),
					registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
					new TitleScreen());
				return;
			}
			MinecraftServer server = mc.getSingleplayerServer();
			if (mc.level == null || mc.player == null || server == null) return;
			if (!planned) {
				planned = true;
				plan();
			}
			if (trainRunning) reportTrain(mc);
			if (wait > 0) {
				wait--;
				return;
			}
			while (!steps.isEmpty() && steps.peek().run(mc, server)) {
				steps.poll();
				if (wait > 0) return;
			}
			if (steps.isEmpty()) finish(mc, "ok\n" + String.join("\n", report));
		} catch (Throwable error) {
			error.printStackTrace();
			finish(mc, error + "\n" + String.join("\n", report));
		}
	}

	private void finish(Minecraft mc, String message) {
		done = true;
		try {
			Files.createDirectories(out);
			Files.writeString(out.resolve("result.txt"), message);
		} catch (Exception ignored) {
		}
		mc.stop();
	}

	private void plan() {
		steps.add(onServer(this::build));
		// Let the built chunks unload so Surveyor summarises them as built, then go there.
		pause(400);
		steps.add(onServer(this::arrive));
		pause(40);
		steps.add(onServer(this::nameStation));
		// Hoofprint charts one region a second once Surveyor has summarised the chunks.
		pause(500);
		steps.add((mc, server) -> {
			trainRunning = true;
			mc.setScreen((Screen) Class.forName("garden.hestia.hoofprint.HoofprintScreen").getConstructor().newInstance());
			return true;
		});
		pause(60);
		steps.add((mc, server) -> {
			RailLayers layers = RailLayers.get(Level.OVERWORLD);
			report.add("paths=" + layers.network().paths().size());
			report.add("stations=" + layers.network().stations().stream().map(Object::toString).toList());
			report.add("surfaceSheets=" + layers.sheets(false).size() + " caveSheets=" + layers.sheets(true).size());
			report.add("trains=" + CreateClient.RAILWAYS.trains.size() + " reports=" + TrainMapSyncClient.currentData.size());
			if (layers.network().paths().isEmpty()) throw new IllegalStateException("No railway reached the client");
			if (layers.sheets(false).isEmpty()) throw new IllegalStateException("Nothing was drawn on charted ground");
			return true;
		});
		shot("map-ink");
		key(GLFW.GLFW_KEY_TAB);
		shot("map-ink-cave");
		key(GLFW.GLFW_KEY_TAB);
		steps.add((mc, server) -> {
			mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, 1);
			mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, 1);
			mc.screen.keyPressed(GLFW.GLFW_KEY_LEFT_ALT, 0, 0);
			return true;
		});
		shot("map-ink-close");
		steps.add((mc, server) -> {
			mc.screen.keyReleased(GLFW.GLFW_KEY_LEFT_ALT, 0, 0);
			Config.LINE_STYLE.set(LineStyle.RAILS);
			return true;
		});
		pause(40);
		shot("map-rails-close");
		steps.add((mc, server) -> {
			mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -1);
			mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -1);
			return true;
		});
		shot("map-rails");
		steps.add((mc, server) -> {
			mc.setScreen(null);
			return true;
		});
		shot("minimap-rails");
		steps.add((mc, server) -> {
			Config.LINE_STYLE.set(LineStyle.INK);
			return true;
		});
		pause(40);
		shot("minimap-ink");
	}

	private void pause(int ticks) {
		steps.add((mc, server) -> {
			wait = ticks;
			return true;
		});
	}

	private void key(int key) {
		steps.add((mc, server) -> {
			mc.screen.keyPressed(key, 0, 0);
			return true;
		});
	}

	private void shot(String name) {
		pause(20);
		steps.add((mc, server) -> {
			Files.createDirectories(out);
			Screenshot.grab(out.toFile(), name + ".png", mc.getMainRenderTarget(), message -> {});
			System.out.println("[showcase] shot " + name);
			wait = 5;
			return true;
		});
	}

	private Step onServer(java.util.function.Consumer<MinecraftServer> action) {
		return (mc, server) -> {
			CompletableFuture.runAsync(() -> action.accept(server), server).join();
			return true;
		};
	}

	// ---- scene --------------------------------------------------------------------------------------------

	private void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.setDayTime(6000);
		level.setWeatherParameters(100000, 0, false, false);
		Block track = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track"));
		railY = level.getChunk(SITE_X >> 4, SITE_Z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SITE_X & 15, SITE_Z & 15) + 1;
		int west = SITE_X - LENGTH / 2;
		// East-west main line and a north-south line crossing it.
		for (int i = 0; i <= LENGTH; i++) bed(level, west + i, SITE_Z);
		for (int i = -70; i <= 70; i++) bed(level, SITE_X - 40, SITE_Z + i);
		// A ridge over the main line (tunnel) and a canal under it (bridge).
		for (int x = west + 130; x < west + 165; x++) {
			for (int z = SITE_Z - 14; z <= SITE_Z + 14; z++) {
				int crest = railY + 6 + Math.min(Math.min(x - (west + 130), west + 164 - x), 14 - Math.abs(z - SITE_Z)) / 2;
				for (int y = railY + 5; y <= crest; y++) level.setBlock(new BlockPos(x, y, z), y == crest ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
			}
		}
		for (int x = west + 25; x < west + 45; x++) {
			for (int z = SITE_Z - 30; z <= SITE_Z + 30; z++) {
				for (int y = railY - 8; y <= railY + 12; y++) {
					if (z == SITE_Z && y == railY - 1) continue;
					level.setBlock(new BlockPos(x, y, z), y <= railY - 3 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(new BlockPos(x, railY - 9, z), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		for (int i = 0; i <= LENGTH; i++) {
			level.setBlock(new BlockPos(west + i, railY, SITE_Z), track.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO), 3);
		}
		for (int i = -70; i <= 70; i++) {
			TrackShape shape = i == 0 ? TrackShape.CR_O : TrackShape.ZO;
			level.setBlock(new BlockPos(SITE_X - 40, railY, SITE_Z + i), track.defaultBlockState().setValue(TrackBlock.SHAPE, shape), 3);
		}

		BlockPos stationPos = new BlockPos(SITE_X + 5, railY, SITE_Z + 2);
		level.setBlock(stationPos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
		level.setBlock(stationPos, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track_station")).defaultBlockState(), 3);
		if (level.getBlockEntity(stationPos) instanceof StationBlockEntity station) {
			CompoundTag tag = station.saveWithoutMetadata(level.registryAccess());
			tag.put("TargetTrack", NbtUtils.writeBlockPos(new BlockPos(0, 0, -2)));
			tag.putBoolean("TargetDirection", true);
			station.loadWithComponents(tag, level.registryAccess());
			station.setChanged();
		}

	}

	private void arrive(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.getInventory().add(new ItemStack(Items.MAP));
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(server.overworld(), SITE_X + 0.5, railY + 40, SITE_Z + 30.5, 180, 55);
	}

	/** Clears a loading gauge and puts ballast under one track position. */
	private void bed(ServerLevel level, int x, int z) {
		for (int y = railY; y <= railY + 4; y++) {
			for (int side = -1; side <= 1; side++) level.setBlock(new BlockPos(x, y, z + side), Blocks.AIR.defaultBlockState(), 2);
		}
		BlockState ballast = Blocks.STONE_BRICKS.defaultBlockState();
		level.setBlock(new BlockPos(x, railY - 1, z), ballast, 2);
	}

	private void nameStation(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getBlockEntity(new BlockPos(SITE_X + 5, railY, SITE_Z + 2)) instanceof StationBlockEntity station) {
			report.add("stationNamed=" + station.updateName("Riverside"));
		} else {
			report.add("stationNamed=missing");
		}
	}

	/** Stands in for the server's train reports: a two-carriage train stopped at the station. */
	private void reportTrain(Minecraft mc) {
		try {
			Train train = CreateClient.RAILWAYS.trains.computeIfAbsent(fakeTrain, id -> {
				Train fake = new Train(id, mc.player.getUUID(), null, new ArrayList<>(), new ArrayList<>(), false, 1);
				fake.name = net.minecraft.network.chat.Component.literal("The Showcase Flyer");
				return fake;
			});
			TrainMapSyncEntry entry = new TrainMapSyncEntry();
			float y = railY;
			entry.positions = new Float[]{SITE_X + 12f, y, SITE_Z + 0.5f, SITE_X + 5f, y, SITE_Z + 0.5f, SITE_X + 3f, y, SITE_Z + 0.5f, SITE_X - 4f, y, SITE_Z + 0.5f};
			entry.dimensions = List.of(Level.OVERWORLD, Level.OVERWORLD);
			entry.targetStationName = "Riverside";
			entry.targetStationDistance = 0;
			entry.prevPositions = entry.positions;
			TrainMapSyncClient.currentData.put(train.id, entry);
		} catch (Throwable error) {
			trainRunning = false;
			report.add("fakeTrain=" + error);
		}
	}
}
