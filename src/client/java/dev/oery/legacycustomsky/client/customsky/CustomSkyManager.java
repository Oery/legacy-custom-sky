package dev.oery.legacycustomsky.client.customsky;

import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
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
}
