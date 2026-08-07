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
import java.util.List;
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
 * {@link CustomSkyManager#activeLayers}/{@link CustomSkyManager#compositeColorTowardDirection}
 * exactly as-is (the same brightness-weighted layer compositing already used for
 * non-terrain fog), and avoids needing new per-blend-mode GPU pipelines or a real
 * hardware cubemap. Layer brightness/rotation is resolved once per frame via
 * {@code activeLayers} (not once per texel - each layer's rotation involves real
 * trig, and redoing it {@link #WIDTH}x{@link #HEIGHT} times a frame was the
 * original, much slower version of this class), so per-texel work is just a cheap
 * linear combination (see {@link CustomSkyLayer.HorizonBasis}) - both that and the
 * deliberately coarse grid size keep this affordable to fully recompute every
 * frame, which matters since this is only ever sampled through terrain fog
 * (inherently blurry/distant, so it doesn't need fine angular resolution).
 */
public final class CustomSkyEnvironmentMap {
	// Deliberately coarse: terrain fog is already blurred by distance/depth, and
	// GPU bilinear filtering (see the sampler in ensureResources()) smooths between
	// texels, so this doesn't need to resolve fine sky detail - it just needs to
	// point terrain fog toward roughly the right part of the sky. Was 128x64
	// (8192 texels) originally; that plus redoing each layer's rotation per texel
	// (see the class doc) was measured to roughly halve framerate.
	private static final int WIDTH = 32;
	private static final int HEIGHT = 16;

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
	 * sky is active for {@code dimensionId} - {@link CustomSkyManager#activeLayers}
	 * returns an empty list in that case, and {@link CustomSkyManager#compositeColorTowardDirection}
	 * falls back to returning {@code vanillaSkyColor} unchanged for every direction,
	 * so the texture just becomes a flat copy of vanilla's own sky color, which is
	 * exactly what the terrain shader should sample either way.
	 */
	public static void update(final Identifier dimensionId, final ClientLevel level, final Camera camera, final float partialTicks) {
		ensureResources();

		double worldTime = level.getDefaultClockTime() + partialTicks;
		float rainStrength = level.getRainLevel(partialTicks);
		float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks);
		int vanillaSkyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);

		// Brightness/rotation resolved once per layer here, not per texel below -
		// see the class doc for why that mattered.
		List<CustomSkyManager.PreparedLayer> activeLayers = CustomSkyManager.activeLayers(dimensionId, worldTime, rainStrength, sunAngleDegrees);

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

				int color = CustomSkyManager.compositeColorTowardDirection(activeLayers, direction, vanillaSkyColor);
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
