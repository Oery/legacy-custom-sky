package dev.oery.legacycustomsky.client.customsky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.oery.legacycustomsky.LegacyCustomSky;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * One {@link RenderPipeline} per {@link BlendMode}. Modern Minecraft bakes blend
 * state into the pipeline object at registration time instead of exposing a
 * runtime {@code glBlendFunc} call, so - unlike MCPatcher, which picked a blend
 * mode per draw call via {@code BlendMethod.applyBlending()} - we need one
 * pre-built pipeline per mode and select which one to bind for each layer/draw.
 */
public final class CustomSkyPipelines {
	private static final Map<BlendMode, RenderPipeline> PIPELINES = new EnumMap<>(BlendMode.class);

	private CustomSkyPipelines() {
	}

	public static void bootstrap() {
		for (BlendMode mode : BlendMode.values()) {
			PIPELINES.put(mode, build(mode));
		}
	}

	public static RenderPipeline get(final BlendMode mode) {
		return PIPELINES.get(mode);
	}

	private static RenderPipeline build(final BlendMode mode) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation(LegacyCustomSky.id("pipeline/custom_sky_" + mode.name().toLowerCase()))
			.withVertexShader("core/position_tex")
			.withFragmentShader("core/position_tex")
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
			.withPrimitiveTopology(PrimitiveTopology.QUADS);

		if (mode.blendFunction != null) {
			builder.withColorTargetState(new ColorTargetState(mode.blendFunction));
		}

		return RenderPipelines.register(builder.build());
	}
}
