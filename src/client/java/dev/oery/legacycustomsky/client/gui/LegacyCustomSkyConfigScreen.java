package dev.oery.legacycustomsky.client.gui;

import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings screen: a normal {@link OptionsSubScreen} (same base
 * vanilla's own Video Settings/Accessibility screens use, so it gets the
 * standard layout/"Done" button for free) built from vanilla widgets only -
 * no Mod Menu classes referenced here, so it works standalone regardless of
 * how it's opened. {@link dev.oery.legacycustomsky.client.LegacyCustomSkyModMenuIntegration}
 * is the only thing that knows about Mod Menu; it just hands this screen to it.
 */
public final class LegacyCustomSkyConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("legacy-custom-sky.config.title");

	public LegacyCustomSkyConfigScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		LegacyCustomSkyConfig config = LegacyCustomSkyConfig.get();
		this.list.addSmall(
			OptionInstance.createBoolean("legacy-custom-sky.options.enabled", config.enabled, value -> {
				config.enabled = value;
				config.save();
			}),
			OptionInstance.createBoolean("legacy-custom-sky.options.autoDisableClouds", config.autoDisableClouds, value -> {
				config.autoDisableClouds = value;
				config.save();
			})
		);
	}
}
