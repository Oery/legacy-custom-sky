package dev.oery.legacycustomsky.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.oery.legacycustomsky.LegacyCustomSky;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Persistent mod settings, stored as their own JSON file in the config
 * directory rather than vanilla's options.txt since these aren't vanilla
 * {@link net.minecraft.client.OptionInstance}-backed Options.
 */
public final class LegacyCustomSkyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("legacy-custom-sky.json");

	private static @Nullable LegacyCustomSkyConfig instance;

	public boolean enabled = true;
	public boolean autoDisableClouds = true;
	public FogBlendMode fogBlendMode = FogBlendMode.PER_PIXEL;

	public static LegacyCustomSkyConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static LegacyCustomSkyConfig load() {
		if (Files.exists(PATH)) {
			try (BufferedReader reader = Files.newBufferedReader(PATH)) {
				LegacyCustomSkyConfig loaded = GSON.fromJson(reader, LegacyCustomSkyConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				LegacyCustomSky.LOGGER.warn("[Config] Failed to read {}, using defaults", PATH, e);
			}
		}

		return new LegacyCustomSkyConfig();
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			LegacyCustomSky.LOGGER.warn("[Config] Failed to write {}", PATH, e);
		}
	}
}
