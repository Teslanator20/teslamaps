#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, ColorModulator.a);
}
