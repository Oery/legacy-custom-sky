package dev.oery.legacycustomsky.client.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oery.legacycustomsky.client.customsky.CustomSkyEnvironmentMap;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Binds {@code CustomSkyEnvMap} - the extra sampler
 * {@code CustomSkyTerrainPipelines}' replacement terrain pipelines declare - once
 * per render pass, alongside vanilla's own {@code Sampler0}/{@code Sampler2}
 * binds. {@code ChunkSectionsToRender.renderGroup} binds those two directly
 * (not through a generic "bind every known sampler" helper), so there's no
 * existing hook to add a third sampler to without redirecting one of those two
 * calls; this redirects the second ({@code Sampler2}, the lightmap) since its
 * signature - {@code (String, GpuTextureView, GpuSampler)} - gives this handler
 * the exact {@code RenderPass} instance as a plain parameter (the call's
 * receiver), with no local-variable/ordinal guessing needed.
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {
	@Redirect(
		method = "renderGroup",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
			ordinal = 1
		)
	)
	private void legacyCustomSky$bindEnvMap(
		final RenderPass renderPass, final String name, final GpuTextureView textureView, final GpuSampler sampler
	) {
		renderPass.bindTexture(name, textureView, sampler);
		renderPass.bindTexture("CustomSkyEnvMap", CustomSkyEnvironmentMap.textureView(), CustomSkyEnvironmentMap.sampler());
	}
}
