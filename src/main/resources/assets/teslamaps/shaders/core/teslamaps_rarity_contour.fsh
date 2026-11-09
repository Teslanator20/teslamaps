#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

float itemAlpha(vec2 uv) {
    vec2 lo = min(shapeParams.xy, shapeParams.zw);
    vec2 hi = max(shapeParams.xy, shapeParams.zw);
    if (any(lessThanEqual(uv, lo)) || any(greaterThanEqual(uv, hi))) return 0.0;
    return texture(Sampler0, uv).a;
}

void main() {
    vec2 st = abs(shapeParams.zw - shapeParams.xy) / 16.0;
    vec2 uv = localPos;
    float n = max(max(itemAlpha(uv + vec2(st.x, 0.0)), itemAlpha(uv - vec2(st.x, 0.0))),
                  max(itemAlpha(uv + vec2(0.0, st.y)), itemAlpha(uv - vec2(0.0, st.y))));
    n = max(n, max(max(itemAlpha(uv + st), itemAlpha(uv - st)),
                   max(itemAlpha(uv + vec2(st.x, -st.y)), itemAlpha(uv + vec2(-st.x, st.y)))));
    float a = n * (1.0 - itemAlpha(uv));
    if (a <= 0.0) discard;
    vec4 tint = vertexColor * ColorModulator;
    fragColor = vec4(tint.rgb, tint.a * a);
}
