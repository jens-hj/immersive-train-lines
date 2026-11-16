package dev.jensh.immersivetrainlines.mixin;

import dev.jensh.immersivetrainlines.Config;
import dev.jensh.immersivetrainlines.map.MapPainter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws railways onto the Immersive Minimaps minimap, under its landmarks, players and frame. */
@Pseudo
@Mixin(targets = "cc.cassian.immersiveminimaps.overlay.MinimapOverlay", remap = false)
public abstract class MinimapOverlayMixin {
	@Shadow private double centreX;
	@Shadow private double centreZ;
	@Shadow private boolean caveMode;
	@Shadow private ResourceKey<Level> dim;

	@Shadow
	abstract float getScaleFactor();

	@Shadow
	private int getXOffset() {
		throw new AssertionError();
	}

	@Shadow
	private int getYOffset() {
		throw new AssertionError();
	}

	@Shadow
	private int width() {
		throw new AssertionError();
	}

	@Shadow
	private int height() {
		throw new AssertionError();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At(value = "INVOKE", target = "Lcc/cassian/immersiveminimaps/overlay/MinimapOverlay;renderLandmarks(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	private void immersiveTrainLines$drawRailways(GuiGraphics graphics, float delta, CallbackInfo ci) {
		if (!Config.MINIMAP.get()) return;
		int left = getXOffset();
		int top = getYOffset();
		MapPainter.View view = new MapPainter.View(dim, centreX, centreZ, getScaleFactor(), left, top, width(), height(), caveMode);
		graphics.enableScissor(left, top, left + width(), top + height());
		MapPainter.draw(graphics, view, true, false, Double.NaN, Double.NaN);
		graphics.disableScissor();
	}
}
