package dev.oery.legacycustomsky.client;

import dev.oery.legacycustomsky.LegacyCustomSky;
import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import dev.oery.legacycustomsky.client.customsky.CustomSkyPipelines;
import dev.oery.legacycustomsky.client.customsky.CustomSkyReloadListener;
import dev.oery.legacycustomsky.client.customsky.CustomSkyTerrainPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.PackType;

public class LegacyCustomSkyClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LegacyCustomSky.id("general"));

	private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.legacy-custom-sky.toggle", -1, CATEGORY)
	);

	@Override
	public void onInitializeClient() {
		CustomSkyPipelines.bootstrap();
		CustomSkyTerrainPipelines.bootstrap();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(LegacyCustomSky.id("custom_sky"), new CustomSkyReloadListener());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_KEY.consumeClick()) {
				LegacyCustomSkyConfig config = LegacyCustomSkyConfig.get();
				config.enabled = !config.enabled;
				config.save();
				LegacyCustomSky.LOGGER.info("[LegacyCustomSkyClient] Custom sky {}", config.enabled ? "enabled" : "disabled");
			}
		});
	}
}
