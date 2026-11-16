package dev.jensh.immersivetrainlines.test;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = "immersive_train_lines_test", dist = Dist.CLIENT)
public final class ImmersiveTrainLinesTest {
	public ImmersiveTrainLinesTest() {
		if (System.getProperty("immersivetrainlines.showcase") != null) Showcase.register();
	}
}
