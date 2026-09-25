#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// per box: min.xyz, max.xyz, rgba
layout(std140) uniform BoxData {
    vec4 Boxes[1023];
};

in vec3 Position;

out vec4 vertexColor;

void main() {
    int base = gl_InstanceID * 3;
    vec3 lo = Boxes[base].xyz;
    vec3 hi = Boxes[base + 1].xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(mix(lo, hi, Position), 1.0);
    vertexColor = Boxes[base + 2];
}
