package dev.oery.legacycustomsky.client.mixin;

import dev.oery.legacycustomsky.client.customsky.CustomSkyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Suppresses vanilla's stars whenever the current dimension has active Custom
 * Sky layers, the equivalent of MCPatcher's own star-suppression bytecode patch
 * (the "disable default stars" patch in {@code BetterSkies.RenderGlobalMod},
 * gated on {@code SkyRenderer.active} - see {@code research/mcpatcher}) so a
 * resource pack's custom skybox fully replaces the vanilla one instead of
 * showing through it.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	// float params of renderSunMoonAndStars, in order: sunAngle(0), moonAngle(1),
	// starAngle(2), rainBrightness(3), starBrightness(4).
	@ModifyVariable(method = "renderSunMoonAndStars", at = @At("HEAD"), ordinal = 4, argsOnly = true)
	private float legacyCustomSky$suppressVanillaStars(final float starBrightness) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && CustomSkyManager.hasLayers(mc.level.dimension().identifier())) {
			return 0.0F;
		}

		return starBrightness;
	}
}
