// Liquid Metal — folded flow field with a hard specular ridge on top.
vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec2 uv = (fragCoord * 2.0 - u_Resolution) / min(u_Resolution.x, u_Resolution.y);
    uv += (u_Mouse - vec2(0.5)) * 0.5;

    float t = u_Time * 0.25;
    vec2 p = uv * 1.6;

    // domain folding: each pass bends the field by the previous one
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        p += 0.42 * cos(p.yx * (2.2 + fi * 0.35) + t * (0.6 + fi * 0.12) + fi * 1.7);
    }

    float v = 0.5 + 0.5 * sin(p.x * 2.4 + p.y * 1.8);
    float ridge = pow(clamp(v, 0.0, 1.0), 8.0);
    float sheen = pow(0.5 + 0.5 * sin(p.y * 5.0 - t), 24.0);

    vec3 dark = vec3(0.04, 0.05, 0.08);
    vec3 steel = vec3(0.52, 0.58, 0.70);
    vec3 col = mix(dark, steel, v * v);
    col += vec3(0.95, 0.97, 1.0) * ridge * 0.55;
    col += vec3(0.65, 0.80, 1.0) * sheen * 0.35;

    return vec4(clamp(col, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
