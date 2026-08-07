package dev.oery.legacycustomsky.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oery.legacycustomsky.client.config.FogBlendMode;
import dev.oery.legacycustomsky.client.config.LegacyCustomSkyConfig;
import dev.oery.legacycustomsky.client.customsky.CustomSkyEnvironmentMap;
import dev.oery.legacycustomsky.client.customsky.CustomSkyManager;
import dev.oery.legacycustomsky.client.customsky.CustomSkyRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a second sky FramePass right after vanilla's own (see the private
 * {@code addSkyPass} in {@code LevelRenderer.java} in the research notes).
 *
 * <p>This originally injected at {@code HEAD}, drawing custom sky layers
 * <i>before</i> vanilla's sky disc/sun/moon/stars, matching where MCPatcher's own
 * bytecode patch inserted its {@code SkyRenderer.renderAll()} call relative to
 * the rest of {@code RenderGlobal.renderSky} (see the {@code BetterSkies.RenderGlobalMod}
 * patch in {@code research/mcpatcher}). That doesn't work here: none of
 * vanilla's sky pipelines (SKY/CELESTIAL/STARS/...) declare a
 * {@code DepthStencilState}, i.e. depth testing is off entirely for the whole
 * sky pass and draw order alone decides what's visible - so vanilla's opaque
 * sky disc, drawn after us, unconditionally overwrote our top face and the
 * upper part of our walls wherever it covered them. Injecting at {@code TAIL}
 * instead draws custom layers after everything vanilla draws. For the default
 * {@code add} blend mode (and most others) this looks identical either way
 * since additive blending is order-independent; only non-commutative modes
 * like {@code replace} actually change layering (arguably more correctly,
 * since "replace" now really does replace the whole sky rather than getting
 * drawn over by the sun/moon).
 *
 * <p>Fabric API 0.156.0's {@code LevelRenderEvents} has no sky-pass hook (its
 * earliest event fires after the sky pass, in the main terrain pass), so this
 * has to be a Mixin rather than an event subscription - see the feasibility
 * research notes.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	@Final
	private LevelTargetBundle targets;

	@Inject(method = "addSkyPass", at = @At("TAIL"))
	private void legacyCustomSky$addCustomSkyPass(final FrameGraphBuilder frame, final CameraRenderState cameraState, final GpuBufferSlice skyFog, final CallbackInfo ci) {
		if (cameraState.fogType == FogType.POWDER_SNOW || cameraState.fogType == FogType.LAVA || cameraState.entityRenderState.doesMobEffectBlockSky) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !CustomSkyManager.hasLayers(mc.level.dimension().identifier())) {
			return;
		}

		FramePass pass = frame.addPass("legacy_custom_sky");
		this.targets.main = pass.readsAndWrites(this.targets.main);
		pass.executes(() -> {
			// Deferred into the frame graph's execution phase, like everything else
			// here, instead of issuing the texture upload synchronously during graph
			// construction (what an earlier version of this did) - GPU commands
			// issued outside the frame graph's own ordering aren't guaranteed to be
			// sequenced correctly relative to the terrain draw that samples this
			// texture later in the same frame, which could plausibly explain terrain
			// fog occasionally sampling an unwritten (black) texture.
			//
			// custom_sky_terrain.fsh (see ChunkSectionLayerMixin) only ever samples
			// CustomSkyEnvMap when FogBlendMode.PER_PIXEL is selected - matching that
			// gate here too, so this doesn't do pointless work baking a texture
			// nothing will sample.
			if (LegacyCustomSkyConfig.get().fogBlendMode == FogBlendMode.PER_PIXEL) {
				float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				CustomSkyEnvironmentMap.update(mc.level.dimension().identifier(), mc.level, mc.gameRenderer.mainCamera(), partialTick);
			}

			RenderSystem.setShaderFog(skyFog);
			RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
			CustomSkyRenderer.render(mainTarget.getColorTextureView(), mainTarget.getDepthTextureView());
		});
	}

	/**
	 * Optional feature (see IMPLEMENTATION.md): forces
	 * clouds off for the frame, without touching the persisted
	 * {@code Options.cloudStatus()} value, whenever the current dimension has an
	 * active custom sky and the user has opted into auto-disabling clouds. Many
	 * Custom Sky packs paint their own cloud layer onto the sky cube (e.g. this
	 * mod's own test pack's {@code cloud1.png}/{@code cloud2.png} layers), which
	 * would otherwise double up with vanilla's separate flat cloud plane.
	 * {@code cloudStatus} is a local variable (not a parameter) read directly
	 * from {@code this.optionsRenderState.cloudStatus} - targeted the same way
	 * {@code SkyRendererMixin} targets {@code starBrightness}, by ordinal rather
	 * than name, since there's only one local of this type in the method.
	 */
	@ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
	private CloudStatus legacyCustomSky$suppressCloudsForCustomSky(final CloudStatus cloudStatus) {
		LegacyCustomSkyConfig config = LegacyCustomSkyConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (config.autoDisableClouds && mc.level != null && CustomSkyManager.hasLayers(mc.level.dimension().identifier())) {
			return CloudStatus.OFF;
		}

		return cloudStatus;
	}
}
