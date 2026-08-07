package dev.oery.legacycustomsky.client.mixin;

import dev.oery.legacycustomsky.client.customsky.CustomSkyManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Blends fog toward the current dimension's active Custom Sky layers instead of
 * the vanilla horizon color, for everything that isn't terrain (entities,
 * particles, water surface fog, etc. - terrain itself gets a finer per-pixel
 * treatment via {@code CustomSkyTerrainPipelines}/{@code CustomSkyEnvironmentMap}).
 * Vanilla computes this in {@code AtmosphericFogEnvironment.getBaseColor()}, which
 * mixes the environment's flat fog color with {@code EnvironmentAttributes.SKY_COLOR}
 * as distance increases; this mixin substitutes
 * {@link CustomSkyManager#blendFogSkyColor} for that vanilla sky color and leaves
 * the rest of the method (weather darkening, distance falloff, sunrise/sunset
 * tint) untouched.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogEnvironmentMixin {
	// @At("STORE") only matches int-typed *local variable assignments* in
	// getBaseColor's body - method params (including renderDistance) never get an
	// explicit STORE bytecode (they're already in their LVT slots on entry), so they
	// don't count. In store order: fogColor (ordinal 0), the inner if-block's `color`
	// local (ordinal 1), skyColor (ordinal 2, the one we want) - see
	// AtmosphericFogEnvironment.getBaseColor in the decompiled 26.2 sources.
	@ModifyVariable(method = "getBaseColor", at = @At("STORE"), ordinal = 2)
	private int legacyCustomSky$blendFogWithCustomSky(
		final int skyColor, final ClientLevel level, final Camera camera, final int renderDistance, final float partialTicks
	) {
		return CustomSkyManager.blendFogSkyColor(level.dimension().identifier(), skyColor, camera, level, partialTicks);
	}
}
