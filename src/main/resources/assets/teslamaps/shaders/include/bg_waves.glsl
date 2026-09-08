// Waves — stacked colour bands with soft wave-shaped edges.
vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec2 uv = fragCoord / u_Resolution;
    uv += (u_Mouse - vec2(0.5)) * 0.15;
    float t = u_Time * 0.22;

    vec3 col = vec3(0.05, 0.06, 0.12);

    vec3 palette[4];
    palette[0] = vec3(0.13, 0.16, 0.42);
    palette[1] = vec3(0.36, 0.16, 0.55);
    palette[2] = vec3(0.72, 0.24, 0.50);
    palette[3] = vec3(0.96, 0.52, 0.36);

    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float base = 0.78 - fi * 0.17;
        float w = base
                + 0.075 * sin(uv.x * 3.0 + t * (0.8 + fi * 0.15) + fi * 1.9)
                + 0.035 * sin(uv.x * 7.3 - t * (0.5 + fi * 0.1) + fi * 3.1);

        float edge = smoothstep(w + 0.012, w - 0.012, uv.y);
        col = mix(col, palette[i], edge);
    }

    // a thin sheen just above each crest keeps the bands from looking flat
    col += vec3(0.10, 0.12, 0.16) * (1.0 - uv.y) * 0.35;
    return vec4(clamp(col, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
