// Starfield — stars streaming outward from the centre, three depth layers.
float sf_hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec4 teslamaps_bg_color(vec2 fragCoord) {
    float aspect = u_Resolution.x / u_Resolution.y;
    vec2 uv = fragCoord / u_Resolution;
    uv.x *= aspect;
    uv += (u_Mouse - vec2(0.5)) * 0.4;

    vec2 c = uv - vec2(0.5 * aspect, 0.5);
    float r = max(length(c), 0.001);
    float a = atan(c.y, c.x);
    float t = u_Time * 0.3;

    vec3 col = vec3(0.012, 0.016, 0.04);

    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        // log-polar grid: constant drift here reads as accelerating outward
        vec2 lp = vec2(a * (7.0 + fl * 4.0), log(r + 0.02) * (7.0 + fl * 3.0) - t * (1.0 + fl * 0.5));
        vec2 id = floor(lp);
        vec2 f = fract(lp) - 0.5;

        float rnd = sf_hash(id + fl * 53.0);
        if (rnd < 0.55) continue;

        vec2 offset = (vec2(fract(rnd * 31.0), fract(rnd * 97.0)) - 0.5) * 0.5;
        vec2 d = (f - offset) * vec2(1.0, 2.2);      // squashed = motion trail
        float star = smoothstep(0.20, 0.0, length(d));

        float depth = 1.0 / (1.0 + fl * 0.7);
        col += star * depth * (0.45 + 0.55 * fract(rnd * 17.0)) * vec3(0.85, 0.92, 1.0);
    }

    col += vec3(0.05, 0.09, 0.20) * smoothstep(0.9, 0.0, r);
    return vec4(clamp(col, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
