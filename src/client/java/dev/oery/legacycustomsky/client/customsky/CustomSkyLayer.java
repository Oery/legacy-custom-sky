package dev.oery.legacycustomsky.client.customsky;

import dev.oery.legacycustomsky.LegacyCustomSky;
import java.util.Properties;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A single skybox layer parsed from one {@code sky<i>.properties} file, plus the
 * runtime state (bound texture) needed to render it. Config semantics, the
 * fade-brightness curve, and the rotation formula are a port of MCPatcher's Better
 * Skies mod ({@code com.prupe.mcpatcher.sky.SkyRenderer.Layer}) - see
 * {@code README.md}.
 */
public final class CustomSkyLayer {
	private static final int TICKS_PER_DAY = 24000;
	private static final double TOD_OFFSET = -0.25;

	public final Identifier textureLocation;
	private final double a;
	private final double b;
	private final double c;
	private final BlendMode blend;
	private final boolean rotate;
	private final float speed;
	private final float axisX;
	private final float axisY;
	private final float axisZ;

	private @Nullable AbstractTexture texture;

	// Average color of a narrow band around the vertical midpoint of each side face's
	// texture cell (the true horizon, not the zenith-ish top edge - see
	// CustomSkyReloadListener.computeHorizonColors()), used to approximate what this
	// layer contributes to distant fog. Default to opaque white so a decode failure
	// (see CustomSkyReloadListener) degrades gracefully instead of crashing.
	private int southColor = 0xFFFFFFFF;
	private int westColor = 0xFFFFFFFF;
	private int northColor = 0xFFFFFFFF;
	private int eastColor = 0xFFFFFFFF;

	private CustomSkyLayer(
		final Identifier textureLocation,
		final double a,
		final double b,
		final double c,
		final BlendMode blend,
		final boolean rotate,
		final float speed,
		final float axisX,
		final float axisY,
		final float axisZ
	) {
		this.textureLocation = textureLocation;
		this.a = a;
		this.b = b;
		this.c = c;
		this.blend = blend;
		this.rotate = rotate;
		this.speed = speed;
		this.axisX = axisX;
		this.axisY = axisY;
		this.axisZ = axisZ;
	}

	/**
	 * Parses and validates one layer. Returns {@code null} (logging why) if the
	 * properties are missing required fields or don't describe a valid fade curve.
	 *
	 * @param propertiesId the resource location of the {@code .properties} file itself,
	 *                      used to resolve relative {@code source} paths
	 * @param defaultSource the texture to use if {@code source} isn't specified:
	 *                      {@code sky<i>.png} next to the properties file
	 */
	public static @Nullable CustomSkyLayer parse(final Properties props, final Identifier propertiesId, final Identifier defaultSource) {
		Identifier source = resolveSource(props.getProperty("source"), propertiesId, defaultSource);
		if (source == null) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] Invalid source texture in {}", propertiesId);
			return null;
		}

		int startFadeIn = parseTime(props.getProperty("startFadeIn"));
		int endFadeIn = parseTime(props.getProperty("endFadeIn"));
		int endFadeOut = parseTime(props.getProperty("endFadeOut"));
		if (startFadeIn < 0 || endFadeIn < 0 || endFadeOut < 0) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] {} is missing one of startFadeIn/endFadeIn/endFadeOut", propertiesId);
			return null;
		}

		// Unwrap into a strictly increasing tick sequence and derive startFadeOut,
		// mirroring MCPatcher's SkyRenderer.Layer.readFadeTimers() - there is no
		// startFadeOut= property; its value is uniquely determined by the other three.
		while (endFadeIn <= startFadeIn) {
			endFadeIn += TICKS_PER_DAY;
		}
		while (endFadeOut <= endFadeIn) {
			endFadeOut += TICKS_PER_DAY;
		}
		if (endFadeOut - startFadeIn >= TICKS_PER_DAY) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] {} fade times must fall within a 24 hour period", propertiesId);
			return null;
		}

		// f(x) = a cos x + b sin x + c, solved so f(startFadeIn)=0, f(endFadeIn)=1,
		// f(endFadeOut)=0 (Cramer's rule), giving one continuous brightness curve for
		// the whole day instead of four separate linear phases.
		double s0 = normalize(startFadeIn, TICKS_PER_DAY, TOD_OFFSET);
		double s1 = normalize(endFadeIn, TICKS_PER_DAY, TOD_OFFSET);
		double e1 = normalize(endFadeOut, TICKS_PER_DAY, TOD_OFFSET);
		double det = Math.cos(s0) * Math.sin(s1) + Math.cos(e1) * Math.sin(s0) + Math.cos(s1) * Math.sin(e1)
			- Math.cos(s0) * Math.sin(e1) - Math.cos(s1) * Math.sin(s0) - Math.cos(e1) * Math.sin(s1);
		if (det == 0.0) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] {} fade curve is degenerate", propertiesId);
			return null;
		}
		double a = (Math.sin(e1) - Math.sin(s0)) / det;
		double b = (Math.cos(s0) - Math.cos(e1)) / det;
		double c = (Math.cos(e1) * Math.sin(s0) - Math.cos(s0) * Math.sin(e1)) / det;

		float speed = parseFloat(props.getProperty("speed"), 1.0F);
		if (speed < 0.0F) {
			LegacyCustomSky.LOGGER.warn("[CustomSky] {} has a negative speed", propertiesId);
			return null;
		}

		BlendMode blend = BlendMode.parse(props.getProperty("blend"));
		boolean rotate = parseBoolean(props.getProperty("rotate"), true);
		float[] axis = parseAxis(props.getProperty("axis"));

		return new CustomSkyLayer(source, a, b, c, blend, rotate, speed, axis[0], axis[1], axis[2]);
	}

	/**
	 * Mirrors MCPatcher's documented {@code sky.properties} path syntax
	 * ({@code research/mcpatcher/doc/sky.properties}): {@code namespace:path} is
	 * absolute; {@code ~/path} is relative to the pack's {@code mcpatcher/} root;
	 * {@code ./path} is relative to the properties file's own directory; anything
	 * else (no prefix) is relative to the namespace's asset root - only an
	 * *omitted* {@code source} defaults to the sibling {@code sky<i>.png}.
	 */
	private static @Nullable Identifier resolveSource(final @Nullable String raw, final Identifier propertiesId, final Identifier defaultSource) {
		if (raw == null) {
			return defaultSource;
		}

		if (raw.indexOf(':') >= 0) {
			return Identifier.parse(raw);
		}

		String namespace = propertiesId.getNamespace();
		if (raw.startsWith("~/")) {
			return Identifier.fromNamespaceAndPath(namespace, "mcpatcher/" + raw.substring(2));
		}

		if (raw.startsWith("./")) {
			int lastSlash = propertiesId.getPath().lastIndexOf('/');
			String dir = lastSlash < 0 ? "" : propertiesId.getPath().substring(0, lastSlash + 1);
			return Identifier.fromNamespaceAndPath(namespace, dir + raw.substring(2));
		}

		return Identifier.fromNamespaceAndPath(namespace, raw);
	}

	/** Parses {@code hh:mm} or {@code hh:mm:ss} wall-clock time into a 0-24000 tick-of-day value. */
	private static int parseTime(final @Nullable String str) {
		if (str == null) {
			return -1;
		}

		String[] parts = str.trim().split(":");
		if (parts.length < 2 || parts.length > 3) {
			return -1;
		}

		try {
			int hour = Integer.parseInt(parts[0].trim());
			int min = Integer.parseInt(parts[1].trim());
			int sec = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 0;
			if (hour < 0 || hour > 23 || min < 0 || min > 59 || sec < 0 || sec > 59) {
				return -1;
			}

			double fractionOfDay = (hour * 3600.0 + min * 60.0 + sec) / 86400.0;
			return (int) Math.round(fractionOfDay * TICKS_PER_DAY) % TICKS_PER_DAY;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static boolean parseBoolean(final @Nullable String str, final boolean defVal) {
		if (str == null) {
			return defVal;
		}

		if (str.equalsIgnoreCase("true")) {
			return true;
		}

		if (str.equalsIgnoreCase("false")) {
			return false;
		}

		return defVal;
	}

	private static float parseFloat(final @Nullable String str, final float defVal) {
		if (str == null) {
			return defVal;
		}

		try {
			return Float.parseFloat(str.trim());
		} catch (NumberFormatException e) {
			return defVal;
		}
	}

	/**
	 * Parses the user-facing compass axis and remaps it to the world-space
	 * rotation axis, matching MCPatcher's {@code (x,y,z) -> (z,y,-x)} convention
	 * (default input {@code 0 0 1} = "south" maps to rotation axis {@code 1 0 0} =
	 * the same east-west axis vanilla rotates the sun around).
	 */
	private static float[] parseAxis(final @Nullable String str) {
		float[] defVal = {1.0F, 0.0F, 0.0F};
		if (str == null) {
			return defVal;
		}

		String[] parts = str.trim().split("\\s+");
		if (parts.length != 3) {
			return defVal;
		}

		float[] xyz = new float[3];
		for (int i = 0; i < 3; i++) {
			try {
				xyz[i] = Float.parseFloat(parts[i]);
			} catch (NumberFormatException e) {
				return defVal;
			}
		}

		float magnitudeSq = xyz[0] * xyz[0] + xyz[1] * xyz[1] + xyz[2] * xyz[2];
		if (magnitudeSq < 1.0E-5F) {
			return defVal;
		}

		// Real packs commonly specify a non-unit axis (e.g. "0.0 -0.2 0.0") because
		// MCPatcher's original glRotatef(angle, x, y, z) normalizes its axis per the
		// OpenGL spec. JOML's Matrix4f#rotate(ang, x, y, z) does NOT - it assumes a
		// unit vector, so an unnormalized axis bakes a scale/shear into the
		// "rotation" instead of just rotating, silently warping or hiding the
		// layer. Normalize here to match the behavior these packs were authored
		// against.
		float invMagnitude = (float) (1.0 / Math.sqrt(magnitudeSq));
		return new float[] {xyz[2] * invMagnitude, xyz[1] * invMagnitude, -xyz[0] * invMagnitude};
	}

	private static double normalize(final double time, final int period, final double offset) {
		return 2.0 * Math.PI * (time / period + offset);
	}

	public void bindTexture(final TextureManager textureManager) {
		this.texture = textureManager.getTexture(this.textureLocation);
	}

	public @Nullable AbstractTexture texture() {
		return this.texture;
	}

	public void setHorizonColors(final int south, final int west, final int north, final int east) {
		this.southColor = south;
		this.westColor = west;
		this.northColor = north;
		this.eastColor = east;
	}

	public BlendMode blend() {
		return this.blend;
	}

	public boolean rotate() {
		return this.rotate;
	}

	public float axisX() {
		return this.axisX;
	}

	public float axisY() {
		return this.axisY;
	}

	public float axisZ() {
		return this.axisZ;
	}

	/**
	 * Brightness for the current moment: the fade curve solved in {@link #parse}
	 * (a single continuous function of time of day - no separate on/off-window
	 * gating needed, the curve itself falls to zero and below outside the active
	 * window) times the rain-darkening factor. Mirrors MCPatcher's
	 * {@code SkyRenderer.Layer.prepare()}.
	 *
	 * @param worldTime the world's tick clock, including the partial tick fraction
	 * @param rainStrength 0 (clear) to 1 (full rain), same as vanilla's rain strength
	 */
	public float brightness(final double worldTime, final float rainStrength) {
		double x = normalize(worldTime, TICKS_PER_DAY, 0.0);
		double fade = this.a * Math.cos(x) + this.b * Math.sin(x) + this.c;
		float brightness = (1.0F - rainStrength) * (float) fade;
		return Mth.clamp(brightness, 0.0F, 1.0F);
	}

	/**
	 * Rotation angle in degrees for this frame: the sun's angle (0-360, matching
	 * vanilla's {@code EnvironmentAttributes.SUN_ANGLE}) scaled by {@code speed}.
	 * Mirrors MCPatcher's {@code render()}: {@code glRotatef(celestialAngle * 360F
	 * * speed, ...)}.
	 */
	public float rotationDegrees(final float sunAngleDegrees) {
		return this.rotate ? sunAngleDegrees * this.speed : 0.0F;
	}

	/**
	 * Approximates the color of this layer's skybox in an arbitrary world direction,
	 * by picking/interpolating between the two nearest of the four cached
	 * compass-face colors (see {@link #setHorizonColors}). Used both for a single
	 * direction (the camera's own forward vector, for non-terrain fog consumers) and
	 * for many directions at once (baking the per-pixel terrain fog environment map -
	 * see {@code CustomSkyEnvironmentMap}).
	 *
	 * <p>{@code ensureGeometry()}'s raw cube geometry places south/west/north/east at
	 * world +Z/-X/-Z/+X (matching vanilla's own convention), but
	 * {@code CustomSkyRenderer.drawLayer()} rotates that geometry on screen by a fixed
	 * {@link CustomSkyRenderer#FIXED_Y_ROTATION_DEGREES} offset and then this layer's
	 * own day-rotation before drawing it - so picking the right face requires undoing
	 * both of those on the given direction first, in reverse order, to map it back
	 * into the raw geometry's compass space.
	 *
	 * @param direction any world-space direction (only the horizontal component is used)
	 * @param sunAngleDegrees vanilla's current sun angle, same input {@link #rotationDegrees} uses
	 */
	public int colorTowardDirection(final Vector3fc direction, final float sunAngleDegrees) {
		Vector3f local = new Vector3f(direction.x(), 0.0F, direction.z());
		if (local.lengthSquared() < 1.0E-8F) {
			// Straight up/down: no single horizontal direction is more "toward" this
			// layer than another, so just average all four faces evenly.
			return ARGB.srgbLerp(0.5F, ARGB.srgbLerp(0.5F, this.southColor, this.eastColor), ARGB.srgbLerp(0.5F, this.northColor, this.westColor));
		}

		local.normalize();
		local.rotateY((float) Math.toRadians(-CustomSkyRenderer.FIXED_Y_ROTATION_DEGREES));
		if (this.rotate) {
			float angleDegrees = this.rotationDegrees(sunAngleDegrees);
			local.rotateAxis((float) Math.toRadians(-angleDegrees), this.axisX, this.axisY, this.axisZ);
		}

		// south=+Z(0deg), east=+X(90deg), north=-Z(180deg), west=-X(270deg).
		float angleDegrees = (float) Math.toDegrees(Math.atan2(local.x, local.z));
		if (angleDegrees < 0.0F) {
			angleDegrees += 360.0F;
		}

		int segment = (int) (angleDegrees / 90.0F) & 3;
		float t = (angleDegrees % 90.0F) / 90.0F;
		int colorA = this.faceColor(segment);
		int colorB = this.faceColor((segment + 1) & 3);
		return ARGB.srgbLerp(t, colorA, colorB);
	}

	private int faceColor(final int segment) {
		return switch (segment) {
			case 0 -> this.southColor;
			case 1 -> this.eastColor;
			case 2 -> this.northColor;
			default -> this.westColor;
		};
	}
}
