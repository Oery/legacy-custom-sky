package dev.oery.legacycustomsky.client.customsky;

import dev.oery.legacycustomsky.LegacyCustomSky;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * Discovers and (re)loads Custom Sky layers on resource-pack (re)apply, the
 * Fabric-resource-loader equivalent of MCPatcher's own texture-pack-change
 * handler ({@code SkyRenderer}'s static {@code TexturePackChangeHandler} in
 * {@code research/mcpatcher/newcode/src/com/prupe/mcpatcher/sky/SkyRenderer.java}).
 *
 * <p>Two layouts are recognized:
 * <ul>
 * <li>MCPatcher's own path convention, {@code assets/<any namespace>/
 * mcpatcher/sky/world<N>/sky<index>.properties} (that mod's {@code v2Path}, see
 * {@code SkyRenderer.WorldEntry.loadSkies()}), so existing Custom Sky resource
 * packs work unmodified. MCPatcher used the numeric dimension id directly as
 * {@code N}, which only really covers {@code world0} = Overworld and
 * {@code world1} = the End reliably in modern Minecraft - dimensions no longer
 * have stable small ids, so that's all this maps; anything else under
 * {@code mcpatcher/sky/} is skipped with a warning.</li>
 * <li>A modern one that addresses dimensions by their actual {@link Identifier}
 * instead of a legacy numeric id: {@code assets/<any namespace>/skies/
 * <dimension namespace>/<dimension path>/sky<index>.properties}, e.g.
 * {@code assets/my_pack/skies/minecraft/overworld/sky1.properties} - meant for
 * packs targeting dimensions that don't fit the legacy scheme.</li>
 * </ul>
 * Both use {@link ResourceManager#listResources}, so - unlike MCPatcher's
 * index-probing loop that stopped at the first gap - indices don't need to be
 * contiguous.
 */
public final class CustomSkyReloadListener extends SimpleReloadListener<Map<Identifier, List<CustomSkyLayer>>> {
	private static final Pattern MODERN_SKY_PATH = Pattern.compile("skies/([a-z0-9_.\\-]+)/(.+)/sky(\\d+)\\.properties");
	private static final Pattern LEGACY_SKY_PATH = Pattern.compile("mcpatcher/sky/world(\\d+)/sky(\\d+)\\.properties");
	private static final Map<Integer, Identifier> LEGACY_WORLD_DIMENSIONS = Map.of(
		0, Identifier.withDefaultNamespace("overworld"),
		1, Identifier.withDefaultNamespace("the_end")
	);

	@Override
	protected Map<Identifier, List<CustomSkyLayer>> prepare(final PreparableReloadListener.SharedState state) {
		ResourceManager resourceManager = state.resourceManager();
		Map<Identifier, List<Identifier>> propertiesByDimension = new HashMap<>();

		for (Identifier resourceId : resourceManager.listResources("skies", id -> id.getPath().endsWith(".properties")).keySet()) {
			Matcher matcher = MODERN_SKY_PATH.matcher(resourceId.getPath());
			if (matcher.matches()) {
				Identifier dimensionId = Identifier.fromNamespaceAndPath(matcher.group(1), matcher.group(2));
				propertiesByDimension.computeIfAbsent(dimensionId, unused -> new ArrayList<>()).add(resourceId);
			}
		}

		for (Identifier resourceId : resourceManager.listResources("mcpatcher/sky", id -> id.getPath().endsWith(".properties")).keySet()) {
			Matcher matcher = LEGACY_SKY_PATH.matcher(resourceId.getPath());
			if (!matcher.matches()) {
				continue;
			}

			int world = Integer.parseInt(matcher.group(1));
			Identifier dimensionId = LEGACY_WORLD_DIMENSIONS.get(world);
			if (dimensionId == null) {
				LegacyCustomSky.LOGGER.warn(
					"[CustomSky] {} uses legacy world{} - only world0 (overworld) and world1 (the_end) map to a"
						+ " modern dimension; use the skies/<dimension namespace>/<dimension path>/ layout instead",
					resourceId,
					world
				);
				continue;
			}

			propertiesByDimension.computeIfAbsent(dimensionId, unused -> new ArrayList<>()).add(resourceId);
		}

		Map<Identifier, List<CustomSkyLayer>> result = new HashMap<>();
		for (Map.Entry<Identifier, List<Identifier>> dimensionEntry : propertiesByDimension.entrySet()) {
			List<Identifier> propertiesIds = dimensionEntry.getValue();
			propertiesIds.sort((a, b) -> Integer.compare(indexOf(a), indexOf(b)));

			List<CustomSkyLayer> layers = new ArrayList<>();
			for (Identifier propertiesId : propertiesIds) {
				CustomSkyLayer layer = loadLayer(resourceManager, propertiesId);
				if (layer != null) {
					layers.add(layer);
				}
			}

			if (!layers.isEmpty()) {
				result.put(dimensionEntry.getKey(), layers);
				LegacyCustomSky.LOGGER.info("[CustomSky] Loaded {} layer(s) for dimension {}", layers.size(), dimensionEntry.getKey());
			}
		}

		return result;
	}

	@Override
	protected void apply(final Map<Identifier, List<CustomSkyLayer>> prepared, final PreparableReloadListener.SharedState state) {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		for (List<CustomSkyLayer> layers : prepared.values()) {
			for (CustomSkyLayer layer : layers) {
				layer.bindTexture(textureManager);
			}
		}

		CustomSkyManager.set(prepared);
	}

	private static int indexOf(final Identifier propertiesId) {
		Matcher modern = MODERN_SKY_PATH.matcher(propertiesId.getPath());
		if (modern.matches()) {
			return Integer.parseInt(modern.group(3));
		}

		Matcher legacy = LEGACY_SKY_PATH.matcher(propertiesId.getPath());
		return legacy.matches() ? Integer.parseInt(legacy.group(2)) : Integer.MAX_VALUE;
	}

	private static @Nullable CustomSkyLayer loadLayer(final ResourceManager resourceManager, final Identifier propertiesId) {
		Properties props = new Properties();
		try (InputStream in = resourceManager.open(propertiesId)) {
			props.load(in);
		} catch (IOException e) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] Failed to read {}", propertiesId, e);
			return null;
		}

		Identifier defaultSource = propertiesId.withPath(path -> path.substring(0, path.length() - ".properties".length()) + ".png");
		CustomSkyLayer layer = CustomSkyLayer.parse(props, propertiesId, defaultSource);
		if (layer == null) {
			return null;
		}

		// Mirrors MCPatcher's own SkyRenderer.Layer.readTexture(), which skips a layer
		// entirely if its texture isn't found (TexturePackAPI.hasResource(texture) ==
		// false -> properties.error(...)). Without this, TextureManager.getTexture()
		// silently falls back to the built-in missing-texture placeholder and the
		// layer still draws - which is actively harmful for non-commutative blend
		// modes like burn/multiply/screen: burn-blending a magenta/black checkerboard
		// placeholder computes dst*(1-color), i.e. zeroes out whichever channels the
		// placeholder is bright in and passes the rest through untouched, visibly
		// tinting everything drawn underneath (and stacked with) that layer instead
		// of just failing silently.
		if (resourceManager.getResource(layer.textureLocation).isEmpty()) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] {} references missing texture {}", propertiesId, layer.textureLocation);
			return null;
		}

		return layer;
	}
}
