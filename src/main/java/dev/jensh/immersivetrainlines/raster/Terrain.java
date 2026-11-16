package dev.jensh.immersivetrainlines.raster;

/** What the map knows about the ground a railway runs over. */
public interface Terrain {
	int UNKNOWN = Integer.MIN_VALUE;

	/** Whether this chunk has been drawn onto the player's map. */
	boolean charted(int chunkX, int chunkZ);

	/** Y of the topmost mapped block in this column, or {@link #UNKNOWN}. */
	int surfaceY(int x, int z);

	boolean water(int x, int z);
}
