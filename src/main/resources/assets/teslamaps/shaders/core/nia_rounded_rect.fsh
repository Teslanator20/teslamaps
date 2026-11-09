#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 delta = abs(point) - halfSize + radius;
    return length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;
}

void main() {
    vec2 halfSize = shapeParams.xy;
    float radius = min(shapeParams.z, min(halfSize.x, halfSize.y));
    float distanceToEdge = roundedBoxSdf(localPos, halfSize, radius);
    float aa = max(fwidth(distanceToEdge) * 1.5, 0.75);
    float alpha = 1.0 - smoothstep(0.0, aa, distanceToEdge);
    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
