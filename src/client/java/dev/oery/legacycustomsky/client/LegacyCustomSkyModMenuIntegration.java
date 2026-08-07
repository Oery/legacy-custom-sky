package dev.oery.legacycustomsky.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.oery.legacycustomsky.client.gui.LegacyCustomSkyConfigScreen;

/**
 * Registered under the "modmenu" entrypoint in fabric.mod.json - only ever
 * loaded by Mod Menu itself scanning for that entrypoint, so this class (and
 * its compile-only dependency on Mod Menu's API) is never touched if Mod Menu
 * isn't installed.
 */
public final class LegacyCustomSkyModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LegacyCustomSkyConfigScreen::new;
	}
}
