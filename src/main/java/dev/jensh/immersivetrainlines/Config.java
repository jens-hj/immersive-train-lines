package dev.jensh.immersivetrainlines;

import dev.jensh.immersivetrainlines.raster.LineStyle;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
	public enum TrainVisibility {
		/** Trains near you, and trains a charted station can vouch for because they are standing at its platform. */
		REPORTED,
		/** Every train on charted ground, live. */
		ALL,
		NONE
	}

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.EnumValue<LineStyle> LINE_STYLE;
	public static final ModConfigSpec.BooleanValue CHARTED_ONLY;
	public static final ModConfigSpec.EnumValue<TrainVisibility> TRAINS;
	public static final ModConfigSpec.IntValue NEARBY_RANGE;
	public static final ModConfigSpec.BooleanValue STATION_LABELS;
	public static final ModConfigSpec.BooleanValue MINIMAP;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		LINE_STYLE = builder
			.comment("How railways are drawn.",
				"INK: paper-map symbol, an ink line with cross-ticks; tunnels dashed, bridges cased.",
				"RAILS: the track seen from above, iron rail over wooden sleepers; tunnels only show in cave mode.")
			.defineEnum("lineStyle", LineStyle.INK);
		CHARTED_ONLY = builder
			.comment("Only draw railways and stations on ground your map has already charted.",
				"Disable to reveal every railway the server knows about.")
			.define("chartedOnly", true);
		TRAINS = builder
			.comment("Which trains the map shows.",
				"REPORTED: trains near you (or carrying you), and trains standing at a charted station.",
				"ALL: every train on charted ground, live.",
				"NONE: no trains, a pure paper map.")
			.defineEnum("trains", TrainVisibility.REPORTED);
		NEARBY_RANGE = builder
			.comment("How close (in blocks) a train must be for you to mark it on the map yourself.")
			.defineInRange("nearbyRange", 96, 0, 512);
		STATION_LABELS = builder
			.comment("Always write station names on the world map. When disabled they show on hover and while inspecting (Alt).")
			.define("stationLabels", false);
		MINIMAP = builder
			.comment("Draw railways on the Immersive Minimaps minimap as well.")
			.define("minimap", true);
		SPEC = builder.build();
	}

	private Config() {
	}
}
