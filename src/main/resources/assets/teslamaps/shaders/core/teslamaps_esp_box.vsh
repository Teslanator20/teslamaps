#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// per box: min.xyz, max.xyz, rgba
layout(std140) uniform BoxData {
    vec4 Boxes[1023];
};

layout(location = 0) in vec3 Position;

layout(location = 0) out vec4 vertexColor;

void main() {
    int base = gl_InstanceIndex * 3;
    vec3 lo = Boxes[base].xyz;
    vec3 hi = Boxes[base + 1].xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(mix(lo, hi, Position), 1.0);
    vertexColor = Boxes[base + 2];
}
