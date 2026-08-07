package dev.oery.legacycustomsky.client.customsky;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;

/**
 * A small equirectangular texture, re-baked from the CPU every frame, holding
 * "what should distant terrain fade into if it's looking this direction" for the
 * current dimension - {@link CustomSkyLayer#colorTowardDirection} sampled across a
 * grid of directions instead of just the camera's own forward vector, so
 * {@code custom_sky_terrain.fsh} can look up a real per-pixel/per-direction fog
 * color instead of one flat value for the whole screen.
 *
 * <p>Deliberately CPU-computed (not a GPU-rendered cubemap): it reuses
 * {@link CustomSkyManager#skyColorTowardDirection} exactly as-is (the same
 * brightness-weighted layer compositing already used for non-terrain fog), and
 * avoids needing new per-blend-mode GPU pipelines or a real hardware cubemap - a
 * small texture (see {@link #WIDTH}/{@link #HEIGHT}) is cheap enough to fully
 * recompute on the CPU every frame, and this is only ever sampled through terrain
 * fog, which is inherently blurry/distant, so coarse resolution is fine.
 */
public final class CustomSkyEnvironmentMap {
	private static final int WIDTH = 128;
	private static final int HEIGHT = 64;

	private static @org.jspecify.annotations.Nullable NativeImage stagingImage;
	private static @org.jspecify.annotations.Nullable GpuTexture texture;
	private static @org.jspecify.annotations.Nullable GpuTextureView textureView;
	private static @org.jspecify.annotations.Nullable GpuSampler sampler;

	private CustomSkyEnvironmentMap() {
	}

	public static GpuTextureView textureView() {
		ensureResources();
		return textureView;
	}

	public static GpuSampler sampler() {
		ensureResources();
		return sampler;
	}

	/**
	 * Re-bakes the whole texture for the current frame. Cheap even when no custom
	 * sky is active for {@code dimensionId} - {@link CustomSkyManager#skyColorTowardDirection}
	 * already falls back to returning {@code vanillaSkyColor} unchanged for every
	 * direction in that case, so the texture just becomes a flat copy of vanilla's
	 * own sky color, which is exactly what the terrain shader should sample either way.
	 */
	public static void update(final Identifier dimensionId, final ClientLevel level, final Camera camera, final float partialTicks) {
		ensureResources();

		double worldTime = level.getDefaultClockTime() + partialTicks;
		float rainStrength = level.getRainLevel(partialTicks);
		float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks);
		int vanillaSkyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);

		Vector3f direction = new Vector3f();
		for (int y = 0; y < HEIGHT; y++) {
			// v=0 (top row) -> straight up, v=1 (bottom row) -> straight down.
			float elevation = (float) (Math.PI * (0.5 - (y + 0.5) / HEIGHT));
			float cosElevation = (float) Math.cos(elevation);
			float sinElevation = (float) Math.sin(elevation);

			for (int x = 0; x < WIDTH; x++) {
				// azimuth=0 -> south (+Z), matching colorTowardDirection's atan2(x, z) convention.
				float azimuth = (float) (((x + 0.5) / WIDTH - 0.5) * 2.0 * Math.PI);
				direction.set(cosElevation * Math.sin(azimuth), sinElevation, cosElevation * Math.cos(azimuth));

				int color = CustomSkyManager.skyColorTowardDirection(dimensionId, direction, vanillaSkyColor, worldTime, rainStrength, sunAngleDegrees);
				stagingImage.setPixel(x, y, color);
			}
		}

		GpuDevice device = RenderSystem.getDevice();
		GpuBufferSlice staging = device.createCommandEncoder().transientMemory().uploadStaging(stagingImage.getPixelBytes(), 1L, 16);
		device.createCommandEncoder().copyBufferToTexture(staging, 0, 0, WIDTH, HEIGHT, texture, 0, 0, WIDTH, HEIGHT, 0, 0);
	}

	private static void ensureResources() {
		if (texture != null) {
			return;
		}

		stagingImage = new NativeImage(WIDTH, HEIGHT, false);
		GpuDevice device = RenderSystem.getDevice();
		texture = device.createTexture(
			() -> "Custom sky fog environment map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1
		);
		textureView = device.createTextureView(texture);
		sampler = device.createSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
	}
}
