package dev.jensh.immersivetrainlines.mixin;

import dev.jensh.immersivetrainlines.map.MapPainter;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/** Draws railways onto Hoofprint's world map, between the terrain and its landmarks and players. */
@Pseudo
@Mixin(targets = "garden.hestia.hoofprint.HoofprintScreen", remap = false)
public abstract class HoofprintScreenMixin extends Screen {
	@Shadow private double centreX;
	@Shadow private double centreZ;
	@Shadow private boolean caveMode;
	@Shadow private boolean hideDecorations;
	@Shadow private boolean inspectMode;
	@Shadow private ResourceKey<Level> dim;
	@Shadow private Landmark hoveredLandmark;
	@Shadow private Landmark editingLandmark;
	@Shadow private PlayerSummary hoveredPlayer;

	@Shadow
	abstract float getScaleFactor();

	@Unique
	private List<Component> immersiveTrainLines$tooltip;

	protected HoofprintScreenMixin(Component title) {
		super(title);
	}

	// The first write to hoveredLandmark sits right after the terrain textures are drawn.
	@Inject(method = "render", at = @At(value = "FIELD", target = "Lgarden/hestia/hoofprint/HoofprintScreen;hoveredLandmark:Lfolk/sisby/surveyor/landmark/Landmark;", opcode = Opcodes.PUTFIELD, ordinal = 0))
	private void immersiveTrainLines$drawRailways(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MapPainter.View view = new MapPainter.View(dim, centreX, centreZ, getScaleFactor(), 0, 0, width, height, caveMode);
		immersiveTrainLines$tooltip = MapPainter.draw(graphics, view, !hideDecorations, inspectMode, mouseX, mouseY);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void immersiveTrainLines$drawTooltip(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (immersiveTrainLines$tooltip == null || inspectMode || hoveredLandmark != null || hoveredPlayer != null || editingLandmark != null) return;
		graphics.renderTooltip(font, immersiveTrainLines$tooltip, Optional.empty(), mouseX, mouseY);
	}
}
