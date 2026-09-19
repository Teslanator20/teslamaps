#version 150

in vec4 vertexColor;
in vec2 texCoord0;

layout(std140) uniform TeslaChromaUniforms {
    float chromaSize;
    float timeOffset;
    float saturation;
    int forwardDirection;
    float fadeStartX;
    float fadeWidth;
    vec4 fadeColorA;
    vec4 fadeColorB;
    float rawTicks;
    float pxScale;
};

uniform sampler2D Sampler0;

out vec4 fragColor;

float rgb2b(vec3 c) { return max(max(c.r, c.g), c.b); }

vec3 hsb(float h, float s, float v) {
    vec3 rgb = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    rgb = rgb * rgb * (3.0 - 2.0 * rgb);
    return v * mix(vec3(1.0), rgb, s);
}

void main() {
    vec4 o = texture(Sampler0, texCoord0) * vertexColor;
    if (o.a < 0.1) discard;
    float span = 90.0 * pxScale;
    float hue = fract(gl_FragCoord.x / span - rawTicks * 0.015);
    fragColor = vec4(hsb(hue, 1.0, 1.0) * rgb2b(o.rgb), o.a);
}
