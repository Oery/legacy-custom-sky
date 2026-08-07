package dev.oery.legacycustomsky.client.customsky;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.oery.legacycustomsky.LegacyCustomSky;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drop-in replacements for vanilla's {@code SOLID_TERRAIN}/{@code CUTOUT_TERRAIN}/
 * {@code TRANSLUCENT_TERRAIN} pipelines ({@code net.minecraft.client.renderer.RenderPipelines}),
 * built from the same shared {@code TERRAIN_SNIPPET} with the exact same per-variant
 * extras (shader defines / blend state) vanilla applies, but pointed at
 * {@code custom_sky_terrain}'s shaders and given one extra sampler bind group
 * ({@code CustomSkyEnvMap}) so terrain fog can sample
 * {@link CustomSkyEnvironmentMap} per-pixel instead of mixing to vanilla's flat
 * {@code FogColor} uniform.
 *
 * <p>Swapped in for the real thing by {@code ChunkSectionLayerMixin}, which
 * redirects {@code ChunkSectionLayer.pipeline()} - the single call site
 * ({@code ChunkSectionsToRender.renderGroup}) that actually binds a pipeline for
 * chunk section draws - through {@link #replacementFor}.
 */
public final class CustomSkyTerrainPipelines {
	private static final Identifier SHADER_LOCATION = LegacyCustomSky.id("core/custom_sky_terrain");
	private static final BindGroupLayout CUSTOM_SKY_ENV_MAP = BindGroupLayout.builder().withSampler("CustomSkyEnvMap").build();

	public static final RenderPipeline SOLID_TERRAIN = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
			.withLocation(LegacyCustomSky.id("pipeline/custom_sky_solid_terrain"))
			.withVertexShader(SHADER_LOCATION)
			.withFragmentShader(SHADER_LOCATION)
			.withBindGroupLayout(CUSTOM_SKY_ENV_MAP)
			.build()
	);
	public static final RenderPipeline CUTOUT_TERRAIN = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
			.withLocation(LegacyCustomSky.id("pipeline/custom_sky_cutout_terrain"))
			.withVertexShader(SHADER_LOCATION)
			.withFragmentShader(SHADER_LOCATION)
			.withBindGroupLayout(CUSTOM_SKY_ENV_MAP)
			.withShaderDefine("ALPHA_CUTOUT", 0.5F)
			.build()
	);
	public static final RenderPipeline TRANSLUCENT_TERRAIN = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
			.withLocation(LegacyCustomSky.id("pipeline/custom_sky_translucent_terrain"))
			.withVertexShader(SHADER_LOCATION)
			.withFragmentShader(SHADER_LOCATION)
			.withBindGroupLayout(CUSTOM_SKY_ENV_MAP)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withShaderDefine("ALPHA_CUTOUT", 0.1F)
			.build()
	);

	private CustomSkyTerrainPipelines() {
	}

	/** Forces this class's static init (pipeline registration) to run eagerly. */
	public static void bootstrap() {
	}

	public static RenderPipeline replacementFor(final RenderPipeline vanilla) {
		if (vanilla == RenderPipelines.SOLID_TERRAIN) {
			return SOLID_TERRAIN;
		}

		if (vanilla == RenderPipelines.CUTOUT_TERRAIN) {
			return CUTOUT_TERRAIN;
		}

		if (vanilla == RenderPipelines.TRANSLUCENT_TERRAIN) {
			return TRANSLUCENT_TERRAIN;
		}

		return vanilla;
	}
}
