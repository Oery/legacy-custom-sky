package dev.oery.legacycustomsky.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.oery.legacycustomsky.client.config.FogBlendMode;
import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import dev.oery.legacycustomsky.client.customsky.CustomSkyManager;
import dev.oery.legacycustomsky.client.customsky.CustomSkyTerrainPipelines;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects chunk section rendering to {@link CustomSkyTerrainPipelines}'
 * replacement pipelines instead of vanilla's {@code SOLID_TERRAIN}/
 * {@code CUTOUT_TERRAIN}/{@code TRANSLUCENT_TERRAIN}, so terrain fog samples the
 * per-direction {@code CustomSkyEnvMap} instead of mixing to a flat color - but
 * only when {@link LegacyCustomSkyConfig#fogBlendMode} is
 * {@link FogBlendMode#PER_PIXEL}, the current dimension actually has an active
 * custom sky, and the camera is in plain atmospheric (distance) fog rather than
 * {@link FogType#WATER}/{@link FogType#LAVA}/{@link FogType#POWDER_SNOW} - those
 * fog types never mix with the sky in vanilla either (see
 * {@code FogRenderer.computeFogColor}'s per-{@code FogEnvironment} color sources),
 * so underwater/lava/powder-snow fog should keep fading to vanilla's own
 * {@code FogColor} uniform instead of the custom-sky-blended {@code CustomSkyEnvMap}.
 * Without the fogBlendMode/hasLayers part of this gate, every terrain fragment in every dimension
 * (including ones no pack targets, or with a cheaper blend mode selected) would
 * unconditionally pay for the extra texture sample + trig in
 * {@code custom_sky_terrain.fsh}, for no visual benefit - measured as part of a
 * real framerate regression.
 *
 * <p>{@code pipeline()} is the single call site
 * ({@code ChunkSectionsToRender.renderGroup}: {@code renderPass.setPipeline(...
 * layer.pipeline())}) that actually binds a pipeline for chunk section draws -
 * confirmed by reading the decompiled 26.2 source rather than assumed, since
 * {@code ChunkSectionLayer}'s enum constants also expose the vanilla pipeline via
 * a raw field read in {@code vertexFormat()}, which this mixin deliberately
 * doesn't touch (the replacement pipelines share the exact same vertex format as
 * their vanilla counterparts, so vanilla's {@code vertexFormat()} stays correct
 * either way).
 */
@Mixin(ChunkSectionLayer.class)
public abstract class ChunkSectionLayerMixin {
	@Inject(method = "pipeline", at = @At("RETURN"), cancellable = true)
	private void legacyCustomSky$useEnvMapPipeline(final CallbackInfoReturnable<RenderPipeline> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null
			|| LegacyCustomSkyConfig.get().fogBlendMode != FogBlendMode.PER_PIXEL
			|| !CustomSkyManager.hasLayers(mc.level.dimension().identifier())) {
			return;
		}

		// Mirrors FogRenderer.getFogType(camera): FogType.NONE means "no fluid in the
		// camera", i.e. plain atmospheric fog. WATER/LAVA/POWDER_SNOW fog colors never
		// mix with the sky in vanilla, so leave those to the untouched vanilla pipeline.
		//? if <26.2 {
		/*Camera camera = mc.gameRenderer.getMainCamera();
		*///?} else
		Camera camera = mc.gameRenderer.mainCamera();
		FogType fogType = camera.getFluidInCamera();
		if (fogType != FogType.NONE && fogType != FogType.ATMOSPHERIC) {
			return;
		}

		cir.setReturnValue(CustomSkyTerrainPipelines.replacementFor(cir.getReturnValue()));
	}
}
