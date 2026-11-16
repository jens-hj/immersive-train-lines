package dev.jensh.immersivetrainlines;

import dev.jensh.immersivetrainlines.map.RailLayers;
import dev.jensh.immersivetrainlines.map.TrainSightings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ImmersiveTrainLines.ID, dist = Dist.CLIENT)
public class ImmersiveTrainLines {
	public static final String ID = "immersive_train_lines";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	public ImmersiveTrainLines(ModContainer container) {
		container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> TrainSightings.tick());
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> RailLayers.clear());
	}
}
