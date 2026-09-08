// Shimmer — directional wave normal lighting (adapted from custom(1).frag)

vec2 directionalWaveNormal(vec2 p, float amp, vec2 dir, float freq, float speed, float k) {
    float a = dot(p, dir) * freq + u_Time * speed;
    float b = 0.5 * k * freq * amp * pow((sin(a) + 1.0) * 0.5, k - 1.0) * cos(a);
    return vec2(dir.x * b, dir.y * b);
}

vec3 summedWaveNormal(vec2 p) {
    vec2 sum = vec2(0.0);
    sum += directionalWaveNormal(p, 0.5,    normalize(vec2(1.0,  1.0)),   5.0, 1.5, 1.0);
    sum += directionalWaveNormal(p, 0.25,   normalize(vec2(1.4,  1.0)),  11.0, 2.4, 1.5);
    sum += directionalWaveNormal(p, 0.125,  normalize(vec2(-0.8,-1.0)),  10.0, 2.0, 2.0);
    sum += directionalWaveNormal(p, 0.0625, normalize(vec2(1.3,  1.0)),  15.0, 4.0, 2.2);
    sum += directionalWaveNormal(p, 0.03125,normalize(vec2(-1.7,-1.0)),   5.0, 1.8, 3.0);
    return normalize(vec3(-sum.x, -sum.y, 1.0));
}

vec4 teslamaps_bg_color(vec2 fragCoord) {
    vec4 result = vec4(0.0);
    vec2 p = (2.0 * fragCoord - u_Resolution) / min(u_Resolution.x, u_Resolution.y);
    p += (u_Mouse - vec2(0.5)) * 1.5;
    vec3 normal = summedWaveNormal(p);

    vec3 c = mix(vec3(0.31, 0.15, 0.1), vec3(0.2, 0.25, 0.4),
                 dot(normal, normalize(vec3(0.1, 0.2, 0.5))) * 0.5 + 0.5);
    c = mix(c, vec3(0.7, 0.9, 1.0),
            pow(dot(normal, normalize(vec3(-0.4, 0.1, 1.0))) * 0.5 + 0.5, 2.0));
    c = mix(c, vec3(0.9, 0.98, 1.0),
            pow(dot(normal, normalize(vec3(-0.1, -0.3, 0.5))) * 0.5 + 0.5, 10.0));

    result = vec4(c * u_Color.rgb, u_Color.a);
    return result;
}
