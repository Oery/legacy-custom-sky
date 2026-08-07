package dev.oery.legacycustomsky.client.customsky;

import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Holds the active custom sky layers per dimension, keyed by the dimension's own
 * {@link Identifier} (e.g. {@code minecraft:overworld}) rather than MCPatcher's
 * numeric world-type id, which no longer has a stable meaning in modern Minecraft.
 */
public final class CustomSkyManager {
	private static Map<Identifier, List<CustomSkyLayer>> layersByDimension = Map.of();

	private CustomSkyManager() {
	}

	public static void set(final Map<Identifier, List<CustomSkyLayer>> layers) {
		layersByDimension = layers;
	}

	public static void reset() {
		layersByDimension = Map.of();
	}

	/**
	 * The single choke point for the mod's master enable/disable toggle: when
	 * disabled, this (and by extension {@link #hasLayers}) reports no layers for
	 * any dimension, which alone turns off rendering, vanilla star suppression,
	 * and cloud auto-suppression without each of those needing their own check.
	 */
	public static List<CustomSkyLayer> layersFor(final Identifier dimensionId) {
		if (!LegacyCustomSkyConfig.get().enabled) {
			return List.of();
		}

		return layersByDimension.getOrDefault(dimensionId, List.of());
	}

	public static boolean hasLayers(final @Nullable Identifier dimensionId) {
		return dimensionId != null && !layersFor(dimensionId).isEmpty();
	}

	/** A layer bundled with its per-frame {@link CustomSkyLayer#brightness}/{@link CustomSkyLayer.HorizonBasis}, see {@link #activeLayers}. */
	public record PreparedLayer(CustomSkyLayer layer, float brightness, CustomSkyLayer.HorizonBasis basis) {
	}

	/**
	 * Resolves this dimension's active layers once per frame - brightness and
	 * rotation both only depend on frame-global state (time, rain, sun angle), not
	 * on which direction is being sampled - so {@link #compositeColorTowardDirection}
	 * can cheaply reuse this same list across many directions (e.g. every texel of
	 * {@code CustomSkyEnvironmentMap}) instead of redoing brightness/rotation work
	 * per direction. Layers below the brightness threshold are dropped here so
	 * {@link #compositeColorTowardDirection} doesn't need to re-check per direction either.
	 */
	public static List<PreparedLayer> activeLayers(final Identifier dimensionId, final double worldTime, final float rainStrength, final float sunAngleDegrees) {
		List<CustomSkyLayer> layers = layersFor(dimensionId);
		if (layers.isEmpty()) {
			return List.of();
		}

		List<PreparedLayer> prepared = new ArrayList<>(layers.size());
		for (CustomSkyLayer layer : layers) {
			float brightness = layer.brightness(worldTime, rainStrength);
			if (brightness < 1.0E-4F) {
				continue;
			}

			prepared.add(new PreparedLayer(layer, brightness, layer.horizonBasis(sunAngleDegrees)));
		}

		return prepared;
	}

	/**
	 * Blends {@code vanillaSkyColor} toward {@code activeLayers} (see
	 * {@link #activeLayers}) as seen from an arbitrary direction: each layer
	 * contributes {@link CustomSkyLayer#colorTowardDirection} weighted by its
	 * brightness, and the weighted average is lerped against {@code vanillaSkyColor}
	 * by total weight (clamped to {@code [0, 1]}) so the color fades in/out smoothly
	 * as layers fade (e.g. at dawn/dusk) instead of popping the instant a layer
	 * crosses the brightness threshold. Returns {@code vanillaSkyColor} unchanged if
	 * {@code activeLayers} is empty.
	 */
	public static int compositeColorTowardDirection(final List<PreparedLayer> activeLayers, final Vector3fc direction, final int vanillaSkyColor) {
		if (activeLayers.isEmpty()) {
			return vanillaSkyColor;
		}

		float totalWeight = 0.0F;
		float red = 0.0F;
		float green = 0.0F;
		float blue = 0.0F;
		for (PreparedLayer prepared : activeLayers) {
			int color = prepared.layer().colorTowardDirection(direction, prepared.basis());
			red += ARGB.redFloat(color) * prepared.brightness();
			green += ARGB.greenFloat(color) * prepared.brightness();
			blue += ARGB.blueFloat(color) * prepared.brightness();
			totalWeight += prepared.brightness();
		}

		if (totalWeight <= 0.0F) {
			return vanillaSkyColor;
		}

		red /= totalWeight;
		green /= totalWeight;
		blue /= totalWeight;
		float coverage = Math.min(totalWeight, 1.0F);
		int compositeColor = ARGB.colorFromFloat(1.0F, red, green, blue);
		return ARGB.srgbLerp(coverage, vanillaSkyColor, compositeColor);
	}

	/**
	 * Blends {@code vanillaSkyColor} toward this dimension's active custom sky
	 * layers as seen from the camera's own forward direction, for use in place of
	 * the vanilla horizon color non-terrain fog consumers (entities, particles,
	 * water surface fog, etc.) fade into ({@code AtmosphericFogEnvironment.getBaseColor}).
	 * Terrain itself gets a finer, per-pixel treatment via {@link #activeLayers}/
	 * {@link #compositeColorTowardDirection} baked into {@code CustomSkyEnvironmentMap} -
	 * this single-direction version is for everything else that only has one flat
	 * fog color to work with. Returns {@code vanillaSkyColor} unchanged if there's no
	 * active custom sky.
	 */
	public static int blendFogSkyColor(
		final Identifier dimensionId, final int vanillaSkyColor, final Camera camera, final ClientLevel level, final float partialTicks
	) {
		if (layersFor(dimensionId).isEmpty()) {
			return vanillaSkyColor;
		}

		double worldTime = level.getDefaultClockTime() + partialTicks;
		float rainStrength = level.getRainLevel(partialTicks);
		float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks);
		Vector3fc forward = camera.isPanoramicMode() ? camera.panoramicForwards() : camera.forwardVector();
		List<PreparedLayer> active = activeLayers(dimensionId, worldTime, rainStrength, sunAngleDegrees);
		return compositeColorTowardDirection(active, forward, vanillaSkyColor);
	}
}
