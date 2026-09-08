// Liquid — sine-distorted color wash (adapted from custom(2).frag)
vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec4 result = vec4(0.0);
    vec2 uv = (2.0 * fragCoord - u_Resolution) / min(u_Resolution.x, u_Resolution.y);
    uv += (u_Mouse - vec2(0.5)) * 1.5;

    for (float i = 1.0; i < 10.0; i++) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + u_Time);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + u_Time);
    }

    float s = max(abs(sin(u_Time - uv.y - uv.x)), 0.001);
    // 1/s runs to ~1000, so the raw divide clips to flat white nearly everywhere.
    // Reinhard-style rolloff keeps the same shape inside a displayable range.
    float glow = 1.0 / s;
    result = vec4(u_Color.rgb * (glow / (glow + 4.0)), u_Color.a);
    return result;
}
