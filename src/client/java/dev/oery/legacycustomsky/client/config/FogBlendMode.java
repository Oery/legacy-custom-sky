package dev.oery.legacycustomsky.client.config;

/**
 * How distant fog should relate to the active custom sky, from cheapest/least
 * accurate to most expensive/most accurate. Gates
 * {@code dev.oery.legacycustomsky.client.mixin.AtmosphericFogEnvironmentMixin}
 * (VANILLA vs. everything else) and
 * {@code dev.oery.legacycustomsky.client.mixin.ChunkSectionLayerMixin} (PER_PIXEL
 * only).
 */
public enum FogBlendMode {
	/** No change from vanilla: fog fades to the dimension's normal color, ignoring the custom sky entirely. */
	VANILLA("vanilla"),
	/** Fog fades toward one color per frame, sampled from the custom sky toward wherever the camera is facing. */
	DIRECTIONAL("directional"),
	/** Terrain fog samples the custom sky per-pixel/per-direction; everything else still gets the DIRECTIONAL treatment. */
	PER_PIXEL("perPixel");

	private final String key;

	FogBlendMode(final String key) {
		this.key = key;
	}

	public String translationKey() {
		return "legacy-custom-sky.options.fogBlendMode." + this.key;
	}

	public String tooltipKey() {
		return this.translationKey() + ".tooltip";
	}
}
