// Deep Space — volumetric fractal fold, marched in a tiled space.
vec4 teslamaps_bg_color(vec2 fragCoord) {
    const int ITERATIONS = 14;
    const int VOLSTEPS = 13;
    const float FORMUPARAM = 0.53;
    const float STEPSIZE = 0.12;
    const float ZOOM = 0.80;
    const float TILE = 0.85;
    const float BRIGHTNESS = 0.0016;
    const float DARKMATTER = 0.30;
    const float DISTFADING = 0.73;
    const float SATURATION = 0.85;

    vec2 uv = fragCoord / u_Resolution - 0.5;
    uv.y *= u_Resolution.y / u_Resolution.x;
    uv += (u_Mouse - vec2(0.5)) * 0.25;

    vec3 dir = vec3(uv * ZOOM, 1.0);
    float t = u_Time * 0.012 + 0.25;

    vec3 from = vec3(1.0, 0.5, 0.5) + vec3(t * 2.0, t, -2.0);

    float s = 0.1;
    float fade = 1.0;
    vec3 v = vec3(0.0);

    for (int r = 0; r < VOLSTEPS; r++) {
        vec3 p = from + s * dir * 0.5;
        p = abs(vec3(TILE) - mod(p, vec3(TILE * 2.0)));   // fold space into tiles

        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < ITERATIONS; i++) {
            p = abs(p) / dot(p, p) - FORMUPARAM;
            a += abs(length(p) - pa);
            pa = length(p);
        }

        float dm = max(0.0, DARKMATTER - a * a * 0.001);
        a *= a * a;
        if (r > 5) fade *= 1.0 - dm;

        v += fade;
        v += vec3(s, s * s, s * s * s * s) * a * BRIGHTNESS * fade;
        fade *= DISTFADING;
        s += STEPSIZE;
    }

    v = mix(vec3(length(v)), v, SATURATION);
    return vec4(clamp(v * 0.01, 0.0, 1.0) * u_Color.rgb, u_Color.a);
}
