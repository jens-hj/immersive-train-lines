package garden.hestia.hoofprint;

import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.BitSet;
import java.util.Map;

/** Compile-time signature stub of Hoofprint's map storage. */
public class HoofprintMapStorage {
	public final Map<RegionPos, BitSet> terrainFilled = null;

	public static HoofprintMapStorage get(ResourceKey<Level> dim) {
		throw new AssertionError("stub");
	}
}
