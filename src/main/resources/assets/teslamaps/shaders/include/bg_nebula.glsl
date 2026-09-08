// Nebula — domain-warped cloud layers with a sprinkling of stars.
float nb_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float nb_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(nb_hash(i), nb_hash(i + vec2(1.0, 0.0)), f.x),
               mix(nb_hash(i + vec2(0.0, 1.0)), nb_hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float nb_fbm(vec2 p) {
    float v = 0.0;
    float w = 0.5;
    for (int i = 0; i < 5; i++) {
        v += w * nb_noise(p);
        p = p * 2.02 + vec2(1.7, 9.2);
        w *= 0.5;
    }
    return v;
}

vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec2 uv = fragCoord / u_Resolution;
    uv.x *= u_Resolution.x / u_Resolution.y;
    uv += (u_Mouse - vec2(0.5)) * 0.6;
    float t = u_Time * 0.06;

    vec2 q = vec2(nb_fbm(uv * 2.0 + t), nb_fbm(uv * 2.0 + vec2(5.2, 1.3) - t));
    vec2 r = vec2(nb_fbm(uv * 2.0 + 3.0 * q + vec2(1.7, 9.2) + t * 1.4),
                  nb_fbm(uv * 2.0 + 3.0 * q + vec2(8.3, 2.8) - t * 1.1));
    float f = nb_fbm(uv * 2.0 + 3.5 * r);

    vec3 col = vec3(0.02, 0.02, 0.06);
    col = mix(col, vec3(0.25, 0.10, 0.55), clamp(f * f * 1.6, 0.0, 1.0));
    col = mix(col, vec3(0.85, 0.25, 0.55), clamp(length(q) * 0.7, 0.0, 1.0));
    col = mix(col, vec3(0.15, 0.55, 0.95), clamp(r.x * 0.9, 0.0, 1.0));
    col *= 0.35 + 0.85 * f;

    return vec4(clamp(col, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
