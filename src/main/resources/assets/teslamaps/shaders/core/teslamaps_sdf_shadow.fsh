#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    float blur = max(shapeParams.w, 0.0001);
    float dist = roundedBoxSDF(localPos, shapeParams.xy, shapeParams.z);
    float alpha = vertexColor.a * (1.0 - smoothstep(0.0, blur, dist));
    if (alpha <= 0.0) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
