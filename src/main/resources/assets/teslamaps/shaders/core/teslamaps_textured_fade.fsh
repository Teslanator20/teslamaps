#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;

layout(std140) uniform TeslaChromaUniforms {
    float chromaSize;
    float timeOffset;
    float saturation;
    int forwardDirection;
    float fadeStartX;
    float fadeWidth;
    vec4 fadeColorA;
    vec4 fadeColorB;
    float rawTicks;
    float pxScale;
};

uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

float rgb2b(vec3 rgb) {
    return max(max(rgb.r, rgb.g), rgb.b);
}

void main() {
    vec4 originalColor = texture(Sampler0, texCoord0) * vertexColor;

    if (originalColor.a < 0.1) {
        discard;
    }

    // Static gradient across the tag width, anchored at the chat's left edge
    float t = clamp((gl_FragCoord.x - fadeStartX) / fadeWidth, 0.0, 1.0);

    vec3 col = mix(fadeColorA.rgb, fadeColorB.rgb, t);

    // Keep the glyph's own brightness so shadows stay dark
    fragColor = vec4(col * rgb2b(originalColor.rgb), originalColor.a);
}
