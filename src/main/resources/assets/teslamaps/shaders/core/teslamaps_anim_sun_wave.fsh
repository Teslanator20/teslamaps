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

float rgb2b(vec3 c) { return max(max(c.r, c.g), c.b); }

void main() {
    vec4 o = texture(Sampler0, texCoord0) * vertexColor;
    if (o.a < 0.1) discard;
    float span = 60.0 * pxScale;
    float t = fract(gl_FragCoord.x / span - rawTicks * 0.03);
    float w = 0.5 - 0.5 * cos(t * 6.28318530718);
    vec3 col = w < 0.5 ? mix(vec3(1.0000, 0.6667, 0.0000), vec3(1.0000, 1.0000, 0.3333), w * 2.0)
                       : mix(vec3(1.0000, 1.0000, 0.3333), vec3(1.0000, 1.0000, 1.0000), (w - 0.5) * 2.0);
    fragColor = vec4(col * rgb2b(o.rgb), o.a);
}
