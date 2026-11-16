package dev.jensh.immersivetrainlines.raster;

/** A stretch of track centreline, sampled about every half block. */
public record TrackPath(double[] x, double[] y, double[] z) {
	public int size() {
		return x.length;
	}
}
