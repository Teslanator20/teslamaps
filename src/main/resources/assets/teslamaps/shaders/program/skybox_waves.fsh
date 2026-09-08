#version 330

uniform sampler2D InSampler;

layout(std140) uniform SkyboxConfig {
    vec4 u_Color;
    vec2 u_Resolution;
    float u_Time;
    float u_Scale;
    vec2 u_Mouse;
};

#define u_Speed u_Scale

in vec2 texCoord;

out vec4 fragColor;

#moj_import <teslamaps:bg_waves.glsl>

void main() {
    fragColor = teslamaps_bg_color(gl_FragCoord.xy);
    fragColor += texture(InSampler, texCoord) * 0.0000001;
}
