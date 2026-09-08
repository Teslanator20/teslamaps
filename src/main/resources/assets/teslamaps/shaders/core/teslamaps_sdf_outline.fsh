#version 330

in vec2 localPos;
in vec4 shapeParams;
in vec4 vertexColor;

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    vec2 halfSize = shapeParams.xy;
    float radius = shapeParams.z;
    float thickness = max(shapeParams.w, 0.0001);

    float outer = roundedBoxSDF(localPos, halfSize, radius);
    float inner = roundedBoxSDF(localPos, halfSize - thickness, max(radius - thickness, 0.0));

    float aa = max(fwidth(outer), 0.0001);
    float alpha = (1.0 - smoothstep(-aa, aa, outer)) - (1.0 - smoothstep(-aa, aa, inner));
    if (alpha <= 0.0) discard;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
