#version 330
#extension GL_ARB_separate_shader_objects : require

// Ported from h2team's gui_rounded_rect.fsh (local coords centred, half size + radius in shapeParams)

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
    vec2 delta = abs(point) - halfSize + radius;
    return length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0) - radius;
}

void main() {
    float radius = min(shapeParams.z, min(shapeParams.x, shapeParams.y));
    float distanceToEdge = roundedBoxSdf(localPos, shapeParams.xy, radius);
    float aa = max(fwidth(distanceToEdge) * 1.5, 0.75);
    float alpha = 1.0 - smoothstep(0.0, aa, distanceToEdge);
    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
