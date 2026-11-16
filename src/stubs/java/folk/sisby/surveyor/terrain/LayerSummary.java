package folk.sisby.surveyor.terrain;

import java.util.BitSet;

/** Compile-time signature stub. */
public class LayerSummary {
	public record Raw(BitSet exists, int[] depths, int[] biomes, int[] blocks, int[] lightLevels, int[] waterDepths, int[] waterLights) {
	}
}
