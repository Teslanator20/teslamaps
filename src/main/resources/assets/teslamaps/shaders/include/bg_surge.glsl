#define TAU 6.28318530718
#define MAX_ITER 5
#define INTENSITY 0.005

// Surge — turbulent plasma flow (adapted from sky.frag)
vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec4 result = vec4(0.0);
    vec2 uv = (fragCoord / u_Resolution.xy) * u_Scale;
    uv += (u_Mouse - vec2(0.5)) * 1.5;
    vec2 p = mod(uv * TAU, TAU) - 250.0;

    vec2 iterPos = p;
    float value = 1.0;
    float timeOffset = u_Time * 0.5 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float iterTime = timeOffset * (1.0 - (3.5 / float(n + 1)));
        iterPos = p + vec2(
            cos(iterTime - iterPos.x) + sin(iterTime + iterPos.y),
            sin(iterTime - iterPos.y) + cos(iterTime + iterPos.x)
        );
        float sinX = sin(iterPos.x + iterTime);
        float cosY = cos(iterPos.y + iterTime);
        float compX = p.x * INTENSITY / max(abs(sinX), 0.001);
        float compY = p.y * INTENSITY / max(abs(cosY), 0.001);
        float dist = sqrt(compX * compX + compY * compY);
        value += 1.0 / dist;
    }

    value = 1.17 - pow(value / float(MAX_ITER), 1.4);
    float pwr = value * value;
    pwr = pwr * pwr;
    pwr = pwr * pwr;

    float luminance = abs(pwr);
    // Alpha must not follow the luminance — that made the dark areas see-through,
    // so the world (and your hand) showed through the menu. Opaque, like the rest.
    result = vec4(luminance * u_Color.rgb * 1.4, u_Color.a);
    return result;
}
