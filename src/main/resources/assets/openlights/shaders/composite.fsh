#version 330 core

in vec2 uv;
layout(location = 0) out vec4 fragColor;
uniform sampler2D SceneColor;
uniform sampler2D LightTexture;
uniform sampler2D OpaqueDepth;
uniform vec2 LightTexel;
uniform float BloomStrength;

float depthWeight(float center, float sampleDepth) {
    bool centerSky = center >= 0.999999;
    bool sampleSky = sampleDepth >= 0.999999;
    if (centerSky != sampleSky) return 0.0;
    float relativeDifference = abs(center - sampleDepth) / max(min(1.0 - center, 1.0 - sampleDepth), 0.00001);
    return exp(-relativeDifference * 24.0);
}

vec3 upsampleLight(vec2 coordinate, float centerDepth) {
    ivec2 size = textureSize(LightTexture, 0);
    vec2 location = coordinate * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(location));
    vec2 fraction = fract(location);
    vec3 total = vec3(0.0);
    float totalWeight = 0.0;
    for (int sampleIndex = 0; sampleIndex < 4; ++sampleIndex) {
        ivec2 corner = ivec2(sampleIndex & 1, (sampleIndex >> 1) & 1);
        ivec2 pixel = clamp(base + corner, ivec2(0), size - ivec2(1));
        vec2 sampleUv = (vec2(pixel) + 0.5) / vec2(size);
        vec2 bilinear = mix(vec2(1.0) - fraction, fraction, vec2(corner));
        float weight = bilinear.x * bilinear.y * depthWeight(centerDepth, texture(OpaqueDepth, sampleUv).r);
        total += texelFetch(LightTexture, pixel, 0).rgb * weight;
        totalWeight += weight;
    }
    // Avoid borrowing light from the opposite side of a depth discontinuity.
    return totalWeight > 0.00001 ? total / totalWeight : vec3(0.0);
}

void main() {
    vec4 scene = texture(SceneColor, uv);
    float depth = texture(OpaqueDepth, uv).r;
    vec3 light = upsampleLight(uv, depth);
    vec3 halo = vec3(0.0);
    float haloWeight = 0.0;
    ivec2 lightSize = textureSize(LightTexture, 0);
    for (int neighbor = 0; neighbor < (BloomStrength > 0.0 ? 4 : 0); ++neighbor) {
        vec2 axis = neighbor < 2 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
        float sign = (neighbor & 1) == 0 ? -1.0 : 1.0;
        vec2 sampleUv = clamp(uv + axis * LightTexel * sign * 1.5, vec2(0.0), vec2(1.0));
        ivec2 pixel = clamp(ivec2(sampleUv * vec2(lightSize)), ivec2(0), lightSize - ivec2(1));
        sampleUv = (vec2(pixel) + 0.5) / vec2(lightSize);
        float sampleDepth = texture(OpaqueDepth, sampleUv).r;
        float weight = depthWeight(depth, sampleDepth);
        halo += texelFetch(LightTexture, pixel, 0).rgb * weight;
        haloWeight += weight;
    }
    if (haloWeight > 0.00001) light += halo / haloWeight * clamp(BloomStrength, 0.0, 2.0) * 0.15;
    if (any(isnan(light)) || any(isinf(light))) light = vec3(0.0);
    light = clamp(light, vec3(0.0), vec3(16.0));
    // A soft knee affects only added light; the unlit scene keeps its original colors.
    float peak = max(light.r, max(light.g, light.b));
    vec3 addedLight = light / (1.0 + peak * 0.65);
    fragColor = vec4(clamp(max(scene.rgb, vec3(0.0)) + addedLight, vec3(0.0), vec3(16.0)), scene.a);
}
