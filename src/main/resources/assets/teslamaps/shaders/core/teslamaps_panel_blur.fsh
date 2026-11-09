#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    float dist = roundedBoxSDF(localPos, shapeParams.xy, shapeParams.z);
    float aa = max(fwidth(dist), 0.0001);
    float alpha = 1.0 - smoothstep(-aa, aa, dist);
    if (alpha <= 0.0) discard;

    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec2 texel = 1.0 / size;
    float radius = min(abs(shapeParams.w), 60.0);
    bool box = shapeParams.w < 0.0;
    float sigma = max(radius * 0.5, 0.0001);
    float stepPx = max(1.0, radius / 6.0);

    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (float dx = -radius; dx <= radius + 0.001; dx += stepPx) {
        for (float dy = -radius; dy <= radius + 0.001; dy += stepPx) {
            float w = box ? 1.0 : exp(-(dx * dx + dy * dy) / (2.0 * sigma * sigma));
            acc += texture(Sampler0, uv + vec2(dx, dy) * texel).rgb * w;
            wsum += w;
        }
    }
    vec3 col = wsum > 0.0 ? acc / wsum : texture(Sampler0, uv).rgb;
    fragColor = vec4(col, vertexColor.a * alpha);
}
