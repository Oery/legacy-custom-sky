package dev.oery.legacycustomsky.client.customsky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws the active {@link CustomSkyLayer}s for the current dimension: a shared
 * unit skybox cube (built once, reused by every layer/frame - unlike MCPatcher's
 * {@code SkyRenderer.Layer.render()}, which rebuilt six quads with immediate-mode
 * GL calls every single draw) bound to each layer's own
 * texture/pipeline/rotation/brightness for the frame.
 *
 * <p>Face layout matches MCPatcher's {@code doc/images/skybox.png} /
 * {@code SkyRenderer.Layer.drawTile()} tile-index convention: a 3x2 grid, top row
 * Bottom|Top|South, bottom row West|North|East.
 */
public final class CustomSkyRenderer {
	/**
	 * A coordinate-convention offset baked into this renderer's static cube geometry
	 * (see {@link #ensureGeometry()}) - not part of any per-layer {@code rotate}/
	 * {@code axis}/{@code speed} config. Applied unconditionally before each layer's
	 * own rotation in {@link #drawLayer}; anything that needs to map a world-space
	 * direction onto this cube's raw face geometry (e.g. picking which face is
	 * currently visible toward a given direction, for fog color sampling) must undo
	 * this same offset first, or it'll be off by the same amount the sky faces are
	 * rotated on screen.
	 */
	public static final float FIXED_Y_ROTATION_DEGREES = -90.0F;

	private static @org.jspecify.annotations.Nullable GpuBuffer cubeBuffer;
	private static RenderSystem.@org.jspecify.annotations.Nullable AutoStorageIndexBuffer quadIndices;

	private CustomSkyRenderer() {
	}

	public static void render(final GpuTextureView colorTexture, final GpuTextureView depthTexture) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}

		Identifier dimensionId = level.dimension().identifier();
		List<CustomSkyLayer> layers = CustomSkyManager.layersFor(dimensionId);
		if (layers.isEmpty()) {
			return;
		}

		ensureGeometry();

		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double worldTime = (double) level.getDefaultClockTime() + partialTick;
		float rainStrength = level.getRainLevel(partialTick);
		Camera camera = mc.gameRenderer.mainCamera();
		float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);

		for (CustomSkyLayer layer : layers) {
			float brightness = layer.brightness(worldTime, rainStrength);
			if (brightness < 1.0E-4F) {
				continue;
			}

			AbstractTexture texture = layer.texture();
			if (texture == null) {
				continue;
			}

			drawLayer(layer, texture, brightness, sunAngleDegrees, colorTexture, depthTexture);
		}
	}

	private static void drawLayer(
		final CustomSkyLayer layer,
		final AbstractTexture texture,
		final float brightness,
		final float sunAngleDegrees,
		final GpuTextureView colorTexture,
		final GpuTextureView depthTexture
	) {
		Matrix4f pose = RenderSystem.getModelViewMatrixCopy();
		// A fixed -90 deg Y rotation, applied unconditionally for every layer before
		// its own per-face/day-rotation transform - a coordinate-convention offset
		// baked into this renderer's static cube geometry (ensureGeometry(), below),
		// not part of the per-layer `rotate`/`axis`/`speed` day-night spin. Skipping
		// it doesn't affect whether a face looks upside down (that's a rotation
		// around the vertical axis), but it does rotate which wall each texture
		// cell's edges line up with.
		pose.rotate((float) Math.toRadians(FIXED_Y_ROTATION_DEGREES), 0.0F, 1.0F, 0.0F);
		if (layer.rotate()) {
			float angleDegrees = layer.rotationDegrees(sunAngleDegrees);
			pose.rotate((float) Math.toRadians(angleDegrees), layer.axisX(), layer.axisY(), layer.axisZ());
		}

		Vector4f color = layer.blend().colorFor(brightness);
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(pose, color);
		// 6 faces * 4 vertices = 24 vertices, but the index buffer/drawIndexed count is
		// an INDEX count, not a vertex count: each quad becomes 2 triangles = 6 indices,
		// so 6 faces need 36 indices, not 24 (which only covered 4 of the 6 faces' worth
		// of triangles - the missing-face bug).
		int indexCount = 6 * 6;
		GpuBuffer indexBuffer = quadIndices.getBuffer(indexCount);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Custom sky layer", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
			renderPass.setPipeline(CustomSkyPipelines.get(layer.blend()));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
			renderPass.setVertexBuffer(0, cubeBuffer.slice());
			renderPass.setIndexBuffer(indexBuffer, quadIndices.type());
			renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
		}
	}

	private static void ensureGeometry() {
		if (cubeBuffer != null) {
			return;
		}

		quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);

			// column: 0=left third, 1=middle third, 2=right third. row: 0=top half, 1=bottom half.
			// Each face is A(top-left) B(bottom-left) C(bottom-right) D(top-right) as seen from
			// inside the cube looking at that face, so "up" (high Y) always lands on texture-top
			// (v0) for the four vertical faces - verified by cross product (B-A)x(C-A) giving an
			// inward-facing normal for every face, not just eyeballed.
			addFace(builder, -100, -100, -100, -100, -100, 100, 100, -100, 100, 100, -100, -100, 0, 0); // bottom
			addFace(builder, -100, 100, -100, -100, -100, -100, 100, -100, -100, 100, 100, -100, 1, 1); // north
			addFace(builder, 100, 100, 100, 100, -100, 100, -100, -100, 100, -100, 100, 100, 2, 0); // south
			addFace(builder, -100, 100, 100, -100, 100, -100, 100, 100, -100, 100, 100, 100, 1, 0); // top (rotated 180deg from the vertical-wall corner convention - see addFace javadoc)
			addFace(builder, 100, 100, -100, 100, -100, -100, 100, -100, 100, 100, 100, 100, 2, 1); // east
			addFace(builder, -100, 100, 100, -100, -100, 100, -100, -100, -100, -100, 100, -100, 0, 1); // west

			try (MeshData mesh = builder.buildOrThrow()) {
				cubeBuffer = RenderSystem.getDevice().createBuffer(() -> "Custom sky cube", 40, mesh.vertexBuffer());
			}
		}
	}

	private static void addFace(
		final BufferBuilder builder,
		final float ax,
		final float ay,
		final float az,
		final float bx,
		final float by,
		final float bz,
		final float cx,
		final float cy,
		final float cz,
		final float dx,
		final float dy,
		final float dz,
		final int column,
		final int row
	) {
		float u0 = column / 3.0F;
		float u1 = (column + 1) / 3.0F;
		float v0 = row * 0.5F;
		float v1 = (row + 1) * 0.5F;
		builder.addVertex(ax, ay, az).setUv(u0, v0);
		builder.addVertex(bx, by, bz).setUv(u0, v1);
		builder.addVertex(cx, cy, cz).setUv(u1, v1);
		builder.addVertex(dx, dy, dz).setUv(u1, v0);
	}
}
