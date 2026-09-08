#version 330

// Menu background: parameters arrive per-vertex so this can be drawn as a
// normal gui element. The procedural work lives in the shared include.
in vec2 localPos;
in vec4 shapeParams;   // x=resX, y=resY, z=time, w=scale
in vec4 vertexColor;

out vec4 fragColor;

#define u_Color      vertexColor
#define u_Resolution shapeParams.xy
#define u_Time       shapeParams.z
#define u_Scale      shapeParams.w
#define u_Speed      shapeParams.w
#define u_Mouse      localPos

#moj_import <teslamaps:bg_snow.glsl>

void main() {
    fragColor = teslamaps_bg_color(gl_FragCoord.xy);
}
