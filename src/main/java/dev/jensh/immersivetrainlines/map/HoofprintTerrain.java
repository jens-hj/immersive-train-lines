package dev.jensh.immersivetrainlines.map;

import dev.jensh.immersivetrainlines.ImmersiveTrainLines;
import dev.jensh.immersivetrainlines.raster.Terrain;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import garden.hestia.hoofprint.HoofprintMapStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.BitSet;
import java.util.Map;

/** The ground as Hoofprint has drawn it: which chunks are on the map, and how high Surveyor says they are. */
public class HoofprintTerrain implements Terrain {
	// Same reference height Hoofprint flattens Surveyor layers against.
	private static final int WORLD_HEIGHT = 999;
	private static final int[] NO_DATA = new int[0];
	private static boolean warned;

	private final ResourceKey<Level> dimension;
	private final boolean heights;
	private final Long2ObjectMap<int[]> columns = new Long2ObjectOpenHashMap<>();

	public HoofprintTerrain(ResourceKey<Level> dimension) {
		this.dimension = dimension;
		Level level = Minecraft.getInstance().level;
		// Under a bedrock roof everything would read as a tunnel.
		this.heights = level == null || level.dimension() != dimension || !level.dimensionType().hasCeiling();
	}

	/** Number of chunks on the map; changes whenever Hoofprint charts more ground. */
	public int chartedCount() {
		int count = 0;
		for (BitSet chunks : HoofprintMapStorage.get(dimension).terrainFilled.values()) count += chunks.cardinality();
		return count;
	}

	@Override
	public boolean charted(int chunkX, int chunkZ) {
		Map<RegionPos, BitSet> filled = HoofprintMapStorage.get(dimension).terrainFilled;
		BitSet chunks = filled.get(new RegionPos(chunkX >> 5, chunkZ >> 5));
		return chunks != null && chunks.get(((chunkX & 31) << 5) + (chunkZ & 31));
	}

	@Override
	public int surfaceY(int x, int z) {
		int[] column = column(x >> 4, z >> 4);
		return column == NO_DATA ? UNKNOWN : column[(x & 15) * 16 + (z & 15)];
	}

	@Override
	public boolean water(int x, int z) {
		int[] column = column(x >> 4, z >> 4);
		return column != NO_DATA && column[256 + (x & 15) * 16 + (z & 15)] > 0;
	}

	private int[] column(int chunkX, int chunkZ) {
		if (!heights) return NO_DATA;
		long key = ChunkPos.asLong(chunkX, chunkZ);
		int[] column = columns.get(key);
		if (column == null) {
			column = read(chunkX, chunkZ);
			columns.put(key, column);
		}
		return column;
	}

	private int[] read(int chunkX, int chunkZ) {
		try {
			WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
			WorldTerrain terrain = summary == null ? null : summary.terrain();
			ChunkSummary chunk = terrain == null ? null : terrain.get(new ChunkPos(chunkX, chunkZ));
			LayerSummary.Raw layer = chunk == null ? null : chunk.toSingleLayer(null, null, WORLD_HEIGHT);
			if (layer == null) return NO_DATA;
			RegistryPalette<Block>.ValueView palette = terrain.getBlockPalette(new ChunkPos(chunkX, chunkZ));
			int[] column = new int[512];
			for (int i = 0; i < 256; i++) {
				// A tree canopy is not a tunnel roof: columns topped by leaves tell us nothing about the ground.
				Block top = palette == null ? null : palette.byId(layer.blocks()[i]);
				boolean canopy = top != null && top.defaultBlockState().is(BlockTags.LEAVES);
				column[i] = layer.exists().get(i) && !canopy ? WORLD_HEIGHT - layer.depths()[i] : UNKNOWN;
				column[256 + i] = layer.waterDepths()[i];
			}
			return column;
		} catch (Throwable error) {
			if (!warned) {
				warned = true;
				ImmersiveTrainLines.LOGGER.warn("Could not read Surveyor terrain; tunnels and bridges will be drawn as plain track", error);
			}
			return NO_DATA;
		}
	}
}
