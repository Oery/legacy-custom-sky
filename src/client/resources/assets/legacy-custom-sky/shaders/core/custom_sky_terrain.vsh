#version 330

// Copy of vanilla's core/terrain.vsh (net.minecraft.client.renderer.RenderPipelines
// TERRAIN_SNIPPET), plus one extra varying (worldDirection) so
// custom_sky_terrain.fsh can sample the per-direction custom sky fog environment
// map instead of mixing to a single flat FogColor. See CustomSkyTerrainPipelines.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 worldDirection;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    // pos is already camera-relative (camera at origin), so it doubles as the
    // fragment's view direction once normalized in the fragment shader.
    worldDirection = pos;
}
