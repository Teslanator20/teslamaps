// Mesh — a handful of soft colour blobs drifting into each other.
vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec2 uv = fragCoord / u_Resolution;
    uv.x *= u_Resolution.x / u_Resolution.y;
    uv += (u_Mouse - vec2(0.5)) * 0.25;
    float t = u_Time * 0.2;

    vec3 col = vec3(0.03, 0.035, 0.06);

    vec2 p0 = vec2(0.35 + 0.22 * sin(t * 0.9), 0.35 + 0.18 * cos(t * 1.1));
    vec2 p1 = vec2(0.85 + 0.20 * cos(t * 0.7), 0.30 + 0.22 * sin(t * 0.8));
    vec2 p2 = vec2(0.55 + 0.25 * sin(t * 0.6 + 2.0), 0.80 + 0.15 * cos(t * 1.3));
    vec2 p3 = vec2(1.10 + 0.18 * sin(t * 1.2 + 1.0), 0.65 + 0.20 * cos(t * 0.5));

    col += vec3(0.30, 0.20, 0.95) * smoothstep(0.60, 0.0, length(uv - p0)) * 0.55;
    col += vec3(0.95, 0.25, 0.55) * smoothstep(0.55, 0.0, length(uv - p1)) * 0.55;
    col += vec3(0.15, 0.75, 0.85) * smoothstep(0.60, 0.0, length(uv - p2)) * 0.55;
    col += vec3(0.95, 0.60, 0.20) * smoothstep(0.50, 0.0, length(uv - p3)) * 0.55;

    // gentle vignette so the middle of the menu stays readable
    vec2 c = uv - vec2(0.5 * (u_Resolution.x / u_Resolution.y), 0.5);
    col *= 1.0 - 0.45 * smoothstep(0.2, 1.0, length(c));

    return vec4(clamp(col, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
