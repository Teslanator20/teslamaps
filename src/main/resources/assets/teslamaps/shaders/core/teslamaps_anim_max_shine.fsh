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

void main() {
    vec4 o = texture(Sampler0, texCoord0) * vertexColor;
    if (o.a < 0.1) discard;
    float span = 60.0 * pxScale;
    float t = fract(gl_FragCoord.x / span - rawTicks * 0.03);
    float g = exp(-pow((t - 0.5) * 7.0, 2.0));
    vec3 col = mix(vec3(1.0000, 0.8431, 0.0000), vec3(1.0000, 1.0000, 1.0000), g);
    fragColor = vec4(col * rgb2b(o.rgb), o.a);
}
