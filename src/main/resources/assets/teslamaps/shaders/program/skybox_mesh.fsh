#version 330
#extension GL_ARB_separate_shader_objects : require

// In-world sky: same procedural body, driven by a uniform block because this
// one runs as a post pass inside the level frame graph.
uniform sampler2D InSampler;

layout(std140) uniform SkyboxConfig {
    vec4 u_Color;
    vec2 u_Resolution;
    float u_Time;
    float u_Scale;
    vec2 u_Mouse;
};

#define u_Speed u_Scale

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

#include <teslamaps:bg_mesh.glsl>

void main() {
    fragColor = teslamaps_bg_color(gl_FragCoord.xy);
    // Keeps InSampler live; the chain declares it as an input.
    fragColor += texture(InSampler, texCoord) * 0.0000001;
}
