#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 shapeParams;   // x=resX, y=resY, z=time, w=scale
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

#define u_Color      vertexColor
#define u_Resolution shapeParams.xy
#define u_Time       shapeParams.z
#define u_Scale      shapeParams.w
#define u_Speed      shapeParams.w
#define u_Mouse      localPos

#include <teslamaps:bg_waves.glsl>

void main() {
    fragColor = teslamaps_bg_color(gl_FragCoord.xy);
}
