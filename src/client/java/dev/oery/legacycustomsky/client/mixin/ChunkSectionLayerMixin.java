package dev.oery.legacycustomsky.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.oery.legacycustomsky.client.customsky.CustomSkyTerrainPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects chunk section rendering to {@link CustomSkyTerrainPipelines}'
 * replacement pipelines instead of vanilla's {@code SOLID_TERRAIN}/
 * {@code CUTOUT_TERRAIN}/{@code TRANSLUCENT_TERRAIN}, so terrain fog samples the
 * per-direction {@code CustomSkyEnvMap} instead of mixing to a flat color.
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
		cir.setReturnValue(CustomSkyTerrainPipelines.replacementFor(cir.getReturnValue()));
	}
}
