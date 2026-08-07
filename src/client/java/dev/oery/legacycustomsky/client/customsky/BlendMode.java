package dev.oery.legacycustomsky.client.customsky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import org.jspecify.annotations.Nullable;
import org.joml.Vector4f;

/**
 * The nine skybox blend modes from MCPatcher's "Better Skies" mod. In MCPatcher
 * each mode was a runtime {@code glBlendFunc}/{@code glColor4f} pair (see
 * {@code com.prupe.mcpatcher.mal.resource.BlendMethod} - blend factors and
 * fadeRGB/fadeAlpha flags below were checked field-by-field against that class);
 * here each mode is instead a statically registered
 * {@link com.mojang.blaze3d.pipeline.RenderPipeline} (blend state can no longer
 * be changed per draw call), see {@link CustomSkyPipelines}.
 * {@code rgbFromBrightness}/{@code alphaFromBrightness} reproduce the exact
 * per-mode {@code glColor4f}/{@code applyFade} argument choice MCPatcher used.
 */
public enum BlendMode {
	ALPHA(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA), false, true),
	ADD(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE), false, true),
	SUBTRACT(new BlendFunction(BlendFactor.ONE_MINUS_DST_COLOR, BlendFactor.ZERO), true, false),
	MULTIPLY(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE_MINUS_SRC_ALPHA), true, true),
	DODGE(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE), true, false),
	BURN(new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_COLOR), true, false),
	SCREEN(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR), true, false),
	OVERLAY(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.SRC_COLOR), true, false),
	/** No blending: opaque draw, full pixel value written whenever brightness > 0. */
	REPLACE(null, false, true);

	public final @Nullable BlendFunction blendFunction;
	private final boolean rgbFromBrightness;
	private final boolean alphaFromBrightness;

	BlendMode(final @Nullable BlendFunction blendFunction, final boolean rgbFromBrightness, final boolean alphaFromBrightness) {
		this.blendFunction = blendFunction;
		this.rgbFromBrightness = rgbFromBrightness;
		this.alphaFromBrightness = alphaFromBrightness;
	}

	public Vector4f colorFor(final float brightness) {
		float rgb = this.rgbFromBrightness ? brightness : 1.0F;
		float alpha = this.alphaFromBrightness ? brightness : 1.0F;
		return new Vector4f(rgb, rgb, rgb, alpha);
	}

	public static BlendMode parse(final @Nullable String str) {
		if (str == null) {
			return ADD;
		}

		return switch (str.toLowerCase().trim()) {
			case "alpha" -> ALPHA;
			case "add" -> ADD;
			case "subtract" -> SUBTRACT;
			case "multiply" -> MULTIPLY;
			case "dodge" -> DODGE;
			case "burn" -> BURN;
			case "screen" -> SCREEN;
			case "overlay" -> OVERLAY;
			case "replace" -> REPLACE;
			default -> ADD;
		};
	}
}
