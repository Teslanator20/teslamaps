#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DilateConfig {
    vec2 DilateDir;
    float Radius;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 step = DilateDir / InSize;
    vec4 best = texture(InSampler, texCoord);
    for (float i = 1.0; i <= Radius; i += 1.0) {
        vec4 a = texture(InSampler, texCoord + step * i);
        vec4 b = texture(InSampler, texCoord - step * i);
        if (a.a > best.a) best = a;
        if (b.a > best.a) best = b;
    }
    fragColor = best;
}
