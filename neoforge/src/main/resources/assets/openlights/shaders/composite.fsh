#version 330 core

in vec2 uv;
layout(location = 0) out vec4 fragColor;
uniform sampler2D SceneColor;
uniform sampler2D LightTexture;
uniform sampler2D OpaqueDepth;
uniform vec2 LightTexel;
uniform float BloomStrength;
uniform int ReplaceLighting;
uniform sampler3D WorldTexture;
uniform sampler2D NativeLightmap;
uniform vec3 WorldMinimum;
uniform vec2 WorldGrid;
uniform sampler3D NearTexture;
uniform vec3 NearMinimum;
uniform vec2 NearGrid;
uniform sampler3D ColorTexture;
uniform sampler3D NearColorTexture;
uniform mat4 InverseProjection;
uniform mat4 InverseViewRotation;
uniform vec3 CameraLocal;
uniform sampler3D BlockSectionAtlas;
uniform sampler3D AggregateSectionAtlas;
uniform sampler3D BlockDirectionAtlas;
uniform int BlockLightStyle;
uniform float BlockLightIntensity;
uniform isampler3D BlockSectionTable;
uniform vec3 BlockSectionMinimum;
uniform vec2 BlockSectionSize;
uniform float BlockSectionSide;
uniform float BlockSectionLayers;

// Positive pages address full-resolution bricks; -1 is a known unlit section; 0 is pending.
bool cachedBlockLight(vec3 position, out vec4 value, out vec3 direction) {
    direction=vec3(0.0,1.0,0.0);
    vec3 sectionPosition = (position - BlockSectionMinimum) / 16.0;
    ivec3 section = ivec3(floor(sectionPosition));
    if (any(lessThan(section, ivec3(0))) || any(greaterThanEqual(vec3(section), vec3(BlockSectionSize.x, BlockSectionSize.y, BlockSectionSize.x)))) return false;
    int page = texelFetch(BlockSectionTable, section, 0).r;
    if (page == 0) return false;
    value = vec4(0.0);
    if (page < 0) return true;
    int slot = page - 1, side = int(BlockSectionSide);
    vec3 brick = vec3(slot % side, (slot / side) % side, slot / (side * side));
    vec3 local = fract(sectionPosition) * 16.0;
    vec3 coordinate = (brick * 18.0 + local + 1.0) / (vec3(BlockSectionSide, BlockSectionSide, BlockSectionLayers) * 18.0);
    value = texture(BlockSectionAtlas, coordinate);
    vec4 aggregate = texture(AggregateSectionAtlas, coordinate);
    if (BlockLightStyle != 0) {
        value=aggregate;
        vec4 encoded=texture(BlockDirectionAtlas,coordinate);
        if(value.a>0.001) {
            vec3 vector=encoded.rgb/value.a*2.0-1.0;
            // Retain the weighted directional moment, including opposing-light cancellation.
            direction=clamp(vector,vec3(-1.0),vec3(1.0));
        }
    } else if (aggregate.a > value.a) value = aggregate;
    return true;
}

float cachedEmission(vec3 position) {
    vec3 cell=(position-BlockSectionMinimum)/16.0;
    ivec3 section=ivec3(floor(cell));
    if(any(lessThan(section,ivec3(0)))||any(greaterThanEqual(vec3(section),vec3(BlockSectionSize.x,BlockSectionSize.y,BlockSectionSize.x))))return 0.0;
    int page=texelFetch(BlockSectionTable,section,0).r;if(page<=0)return 0.0;
    int slot=page-1,side=int(BlockSectionSide);
    ivec3 brick=ivec3(slot%side,(slot/side)%side,slot/(side*side));
    return texelFetch(BlockDirectionAtlas,brick*18+ivec3(floor(fract(cell)*16.0))+ivec3(1),0).a;
}

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
    if (ReplaceLighting != 0 && depth < 0.999999) {
        vec4 view = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec3 position = CameraLocal + (InverseViewRotation * vec4(view.xyz / view.w, 0.0)).xyz;
        vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
        if (dot(normal, CameraLocal - position) < 0.0) normal = -normal;
        float emission=BlockLightStyle!=0?cachedEmission(position-normal*0.05):0.0;
        // Query the exposed side of the surface, not an interpolated point inside the occluder.
        position += normal * 0.55;
        vec3 coordinate = ((position - WorldMinimum) / WorldGrid.x + 0.5) / WorldGrid.y;
        vec3 distantLevels = texture(WorldTexture, coordinate).rgb;
        vec2 levels = clamp(vec2(distantLevels.r, distantLevels.g / max(distantLevels.b, 0.00001)), 0.0, 1.0);
        vec4 distantTint = texture(ColorTexture, coordinate);
        vec3 tint = distantTint.a > 0.001 ? distantTint.rgb / distantTint.a : vec3(1.0);
        vec3 nearCell = (position - NearMinimum) / NearGrid.x;
        vec4 nearby = texture(NearTexture, (nearCell + 0.5) / NearGrid.y);
        vec3 nearBorder = min(nearCell, vec3(NearGrid.y - 1.0) - nearCell);
        float nearWeight = clamp(min(nearBorder.x, min(nearBorder.y, nearBorder.z)) / 2.0, 0.0, 1.0);
        // Alpha marks populated probes. Never interpret an unfilled cell as darkness.
        if (nearby.a > 0.001) {
            levels.x = mix(levels.x, nearby.r / nearby.a, nearWeight * nearby.a);
            if (nearby.b > 0.001) levels.y = mix(levels.y, nearby.g / nearby.b, nearWeight * nearby.a);
        }
        vec4 nearTint = texture(NearColorTexture, (nearCell + 0.5) / NearGrid.y);
        if (nearTint.a > 0.001) tint = mix(tint, nearTint.rgb / nearTint.a, nearWeight * nearTint.a);
        // Keep block-light detail identical on both sides of the old near-grid boundary.
        vec4 sectionLight;
        vec3 blockDirection;
        if (cachedBlockLight(position, sectionLight, blockDirection)) {
            levels.x = sectionLight.a;
            tint = sectionLight.a > 0.001 ? sectionLight.rgb / sectionLight.a : vec3(1.0);
        }
        // Retain time of day, dimension tint, gamma, darkness and night vision from the native palette.
        // World geometry was rendered with a neutral lightmap; this is its only base-light multiplication.
        vec3 fullLight = texture(NativeLightmap, (levels * 15.0 + 0.5) / 16.0).rgb;
        vec3 skyLight = texture(NativeLightmap, (vec2(0.0, levels.y) * 15.0 + 0.5) / 16.0).rgb;
        vec3 blockLight = max(vec3(0.0), fullLight - skyLight);
        float blockBrightness = max(blockLight.r, max(blockLight.g, blockLight.b));
        // The texture supplies the emitter spectrum; do not multiply it by vanilla's warm torch tint.
        if(BlockLightStyle != 0) {
            // Light modulates the neutral-lit surface texture. Apply the soft knee to
            // irradiance, before reflectance, so dark texels retain their contrast.
            float diffuse=max(0.15,0.5+0.5*dot(normal,blockDirection));
            vec3 irradiance=clamp(tint,0.0,1.0)*levels.x*levels.x*diffuse*BlockLightIntensity;
            float peak=max(irradiance.r,max(irradiance.g,irradiance.b));
            vec3 illumination=max(skyLight,vec3(emission))+irradiance/(1.0+peak*0.65);
            scene.rgb *= min(illumination,vec3(1.0));
        } else scene.rgb *= skyLight + blockBrightness * clamp(tint, 0.0, 1.0);
    }
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
