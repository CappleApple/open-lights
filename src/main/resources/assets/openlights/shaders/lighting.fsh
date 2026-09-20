#version 330 core

in vec2 uv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D OpaqueDepth;
uniform sampler2D SceneColor;
uniform sampler2DArray ShadowDepth;
uniform mat4 InverseProjection;
uniform mat4 InverseViewRotation;
uniform vec3 CameraLocal;
uniform int LightCount;
uniform vec4 LightPositionRange[8];
uniform vec4 LightColorIntensity[8];
uniform vec4 LightDirectionOuter[8];
uniform vec4 LightUpInner[8];
uniform vec4 LightShape[8];
uniform vec4 LightVolumeShadow[8];
uniform vec4 InnerBeamColor[8];
uniform vec4 OuterBeamColor[8];
uniform vec4 InnerBeamShape[8];
uniform vec4 OuterBeamShape[8];
uniform mat4 ShadowMatrix[24];
uniform vec2 ShadowTexel;
uniform int MediumCount;
layout(std140) uniform MediumBlock {
    vec4 MediumMinimum[32];
    vec4 MediumMaximum[32];
    // RGB stores extinction per block; alpha stores additional scattering density.
    vec4 MediumTint[32];
};
uniform int VolumetricSteps;

const float EPSILON = 0.000001;
const float SHADOW_DEPTH_EPSILON = 0.0000001;

vec3 safeUnit(vec3 value, vec3 fallback) {
    float magnitudeSquared = dot(value, value);
    return magnitudeSquared > EPSILON ? value * inversesqrt(magnitudeSquared) : fallback;
}

vec3 finiteColor(vec3 value) {
    if (any(isnan(value)) || any(isinf(value))) return vec3(0.0);
    return clamp(value, vec3(0.0), vec3(16.0));
}

// Segment clipping also handles rays starting inside a medium.
bool clipBox(vec3 start, vec3 direction, float segmentLength, vec3 minimum, vec3 maximum,
             out float entry, out float exitDistance) {
    entry = 0.0;
    exitDistance = segmentLength;
    for (int axis = 0; axis < 3; ++axis) {
        if (abs(direction[axis]) < EPSILON) {
            if (start[axis] < minimum[axis] || start[axis] > maximum[axis]) return false;
        } else {
            float a = (minimum[axis] - start[axis]) / direction[axis];
            float b = (maximum[axis] - start[axis]) / direction[axis];
            entry = max(entry, min(a, b));
            exitDistance = min(exitDistance, max(a, b));
            if (exitDistance <= entry) return false;
        }
    }
    return exitDistance > entry;
}


vec3 segmentTransmission(vec3 source, vec3 destination) {
    int count = clamp(MediumCount, 0, 32);
    if (count == 0) return vec3(1.0);
    vec3 delta = destination - source;
    float distance = length(delta);
    if (distance < EPSILON) return vec3(1.0);
    vec3 direction = delta / distance;
    vec3 segmentMinimum = min(source, destination);
    vec3 segmentMaximum = max(source, destination);
    vec3 opticalDepth = vec3(0.0);
    for (int medium = 0; medium < count; ++medium) {
        vec3 minimum = MediumMinimum[medium].xyz;
        vec3 maximum = MediumMaximum[medium].xyz;
        if (any(lessThan(segmentMaximum, minimum)) || any(greaterThan(segmentMinimum, maximum))) continue;
        float entry;
        float exitDistance;
        if (clipBox(source, direction, distance, minimum, maximum, entry, exitDistance)) {
            opticalDepth += MediumTint[medium].rgb * (exitDistance - entry);
        }
    }
    return exp(-min(opticalDepth, vec3(40.0)));
}

int pointFace(vec3 delta) {
    vec3 magnitude = abs(delta);
    if (magnitude.x >= magnitude.y && magnitude.x >= magnitude.z) return delta.x >= 0.0 ? 0 : 1;
    if (magnitude.y >= magnitude.z) return delta.y >= 0.0 ? 2 : 3;
    return delta.z >= 0.0 ? 4 : 5;
}

float compareShadow(int layer, vec2 coordinate, float depth) {
    float occluderDepth = textureLod(ShadowDepth, vec3(coordinate, float(layer)), 0.0).r;
    return step(depth - SHADOW_DEPTH_EPSILON, occluderDepth);
}

// A perspective projection still maps a plane to a plane in normalized device coordinates.
// Analytic tangents avoid derivatives inside divergent light/shadow branches.
vec2 receiverPlaneGradient(mat4 matrix, vec4 clip, vec3 normal) {
    vec3 axis = abs(normal.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = cross(normal, axis);
    vec3 bitangent = cross(normal, tangent);
    vec4 a = matrix * vec4(tangent, 0.0);
    vec4 b = matrix * vec4(bitangent, 0.0);
    // The common perspective denominator cancels when computing the plane gradient.
    vec3 projectedA = a.xyz * clip.w - clip.xyz * a.w;
    vec3 projectedB = b.xyz * clip.w - clip.xyz * b.w;
    vec3 plane = cross(projectedA, projectedB);
    if (abs(plane.z) <= max(length(plane.xy), 1.0) * 0.0000001) return vec2(0.0);
    return -plane.xy / plane.z;
}

float compareReceiverPlane(int layer, vec2 coordinate, vec3 receiver, vec2 gradient) {
    // Nearest filtering fetches texel centers, not the requested fractional coordinate.
    vec2 texelCenter = (floor(clamp(coordinate, ShadowTexel * 0.5,
            vec2(1.0) - ShadowTexel * 0.5) / ShadowTexel) + 0.5) * ShadowTexel;
    float depth = receiver.z + dot(gradient, texelCenter - receiver.xy);
    return compareShadow(layer, texelCenter, depth);
}

vec3 emitterPosition(int light, int emitter);

float shadowVisibility(int light, vec3 position, vec3 normal, int emitter, bool filtered) {
    int baseLayer = int(floor(LightVolumeShadow[light].y + 0.5));
    int layerCount = int(floor(LightVolumeShadow[light].z + 0.5));
    if (baseLayer < 0 || layerCount <= 0) return 1.0;
    int type = int(floor(LightShape[light].x + 0.5));
    int relativeLayer = type == 0 ? pointFace(position - LightPositionRange[light].xyz)
            : (type == 2 ? emitter : 0);
    if (relativeLayer >= layerCount) return 1.0;
    int layer = baseLayer + relativeLayer;
    if (layer < 0 || layer >= 24) return 1.0;
    vec3 towardEmitter = emitterPosition(light, emitter) - position;
    float emitterDistance = length(towardEmitter);
    vec3 lightDirection = safeUnit(towardEmitter, vec3(0.0));
    // Small world-space offsets cover depth quantization without distance-dependent leaks.
    // Grazing receivers are corrected per texel below instead of inflating this bias.
    float receiverBias = min(0.004, emitterDistance * 0.25);
    vec3 receiver = position + lightDirection * receiverBias;
    if (filtered) {
        vec3 facingNormal = dot(normal, lightDirection) >= 0.0 ? normal : -normal;
        receiver += facingNormal * min(0.002, emitterDistance * 0.125);
    }
    vec4 clip = ShadowMatrix[layer] * vec4(receiver, 1.0);
    if (clip.w <= EPSILON) return 1.0;
    vec3 projected = clip.xyz / clip.w * 0.5 + 0.5;
    if (any(lessThan(projected, vec3(0.0))) || any(greaterThan(projected, vec3(1.0)))) return 1.0;
    if (!filtered) return compareShadow(layer, projected.xy, projected.z);
    vec2 gradient = receiverPlaneGradient(ShadowMatrix[layer], clip, normal);
    vec2 offset = ShadowTexel * 0.5;
    return 0.25 * (compareReceiverPlane(layer, projected.xy + vec2(-offset.x, -offset.y), projected, gradient)
            + compareReceiverPlane(layer, projected.xy + vec2(offset.x, -offset.y), projected, gradient)
            + compareReceiverPlane(layer, projected.xy + vec2(-offset.x, offset.y), projected, gradient)
            + compareReceiverPlane(layer, projected.xy + offset, projected, gradient));
}

vec3 emitterPosition(int light, int emitter) {
    vec3 position = LightPositionRange[light].xyz;
    if (LightShape[light].x < 1.5) return position;
    vec3 forward = safeUnit(LightDirectionOuter[light].xyz, vec3(0.0, 0.0, -1.0));
    vec3 up = safeUnit(LightUpInner[light].xyz, vec3(0.0, 1.0, 0.0));
    vec3 right = safeUnit(cross(forward, up), vec3(1.0, 0.0, 0.0));
    up = safeUnit(cross(right, forward), up);
    float horizontal = (emitter & 1) == 0 ? -1.0 : 1.0;
    float vertical = (emitter & 2) == 0 ? -1.0 : 1.0;
    return position + right * (horizontal * LightShape[light].y * 0.25)
            + up * (vertical * LightShape[light].z * 0.25);
}

float geometricAttenuation(int light, vec3 source, vec3 destination, out vec3 sourceToPoint) {
    vec3 delta = destination - source;
    float squaredDistance = dot(delta, delta);
    float range = max(LightPositionRange[light].w, 0.001);
    if (squaredDistance >= range * range) return 0.0;
    float distance = sqrt(max(squaredDistance, EPSILON));
    sourceToPoint = delta / distance;
    float edge = max(1.0 - distance / range, 0.0);
    float attenuation = edge * edge / (1.0 + squaredDistance * 0.06);
    int type = int(floor(LightShape[light].x + 0.5));
    if (type != 0) {
        vec3 forward = safeUnit(LightDirectionOuter[light].xyz, vec3(0.0, 0.0, -1.0));
        float outer = type == 2 ? LightShape[light].w : LightDirectionOuter[light].w;
        outer = clamp(outer, -1.0, 0.9999);
        float inner = type == 2 ? min(outer + 0.08, 1.0) : clamp(LightUpInner[light].w, outer + 0.0001, 1.0);
        attenuation *= smoothstep(outer, inner, dot(forward, sourceToPoint));
    }
    return attenuation;
}

vec3 beamLayer(vec4 color, vec4 shape, float distance, float cosine) {
    if (distance >= shape.z || color.a <= 0.0 || cosine < shape.x) return vec3(0.0);
    float cone = shape.y - shape.x < 0.000001 ? step(shape.x, cosine)
            : smoothstep(shape.x, shape.y, cosine);
    float radial = pow(max(0.0, 1.0-distance/max(shape.z,0.001)), shape.w)
            / (1.0 + distance*distance*0.06);
    return color.rgb * color.a * cone * radial;
}

vec3 radianceAt(int light, vec3 source, vec3 destination, out vec3 sourceToPoint) {
    vec3 radiance;
    if (LightShape[light].x > 0.5 && LightShape[light].x < 1.5 && LightShape[light].y > 0.5) {
        vec3 delta=destination-source;
        float distance=length(delta);
        sourceToPoint=safeUnit(delta, LightDirectionOuter[light].xyz);
        if(distance>=LightPositionRange[light].w) return vec3(0.0);
        float cosine=dot(LightDirectionOuter[light].xyz,sourceToPoint);
        radiance=beamLayer(InnerBeamColor[light],InnerBeamShape[light],distance,cosine)
                +beamLayer(OuterBeamColor[light],OuterBeamShape[light],distance,cosine);
    } else {
        radiance=vec3(geometricAttenuation(light,source,destination,sourceToPoint));
    }
    return radiance*LightColorIntensity[light].rgb*clamp(LightColorIntensity[light].w,0.0,64.0);
}

vec3 surfaceLight(int light, vec3 position, vec3 normal) {
    int samples = LightShape[light].x > 1.5 ? 4 : 1;
    vec3 result = vec3(0.0);
    for (int emitter = 0; emitter < samples; ++emitter) {
        vec3 source = emitterPosition(light, emitter);
        vec3 sourceToPoint = vec3(0.0);
        vec3 radiance = radianceAt(light, source, position, sourceToPoint);
        if (max(radiance.r,max(radiance.g,radiance.b)) <= 0.00001) continue;
        float visibility = shadowVisibility(light, position, normal, emitter, true);
        if (visibility <= 0.0) continue;
        float diffuse = max(0.15, 0.5 + 0.5 * dot(normal, -sourceToPoint));
        result += segmentTransmission(source, position) * radiance * diffuse * visibility;
    }
    return result / float(samples);
}

vec3 relativePositionAt(vec2 coordinate, float depth) {
    vec4 view = InverseProjection * vec4(coordinate * 2.0 - 1.0, min(depth, 0.999999) * 2.0 - 1.0, 1.0);
    float inverseW = abs(view.w) > EPSILON ? 1.0 / view.w : 0.0;
    return (InverseViewRotation * vec4(view.xyz * inverseW, 0.0)).xyz;
}

vec3 receiverNormal(ivec2 pixel, ivec2 size, float depth, vec3 relativePosition, vec3 fallback) {
    ivec2 left = max(pixel - ivec2(1, 0), ivec2(0));
    ivec2 right = min(pixel + ivec2(1, 0), size - ivec2(1));
    ivec2 down = max(pixel - ivec2(0, 1), ivec2(0));
    ivec2 up = min(pixel + ivec2(0, 1), size - ivec2(1));
    float leftDepth = texelFetch(OpaqueDepth, left, 0).r;
    float rightDepth = texelFetch(OpaqueDepth, right, 0).r;
    float downDepth = texelFetch(OpaqueDepth, down, 0).r;
    float upDepth = texelFetch(OpaqueDepth, up, 0).r;
    // Prefer the neighbor on the receiver's plane, rather than differentiating across a block edge.
    bool useLeft = pixel.x > 0 && (pixel.x == size.x - 1 || abs(leftDepth - depth) < abs(rightDepth - depth));
    bool useDown = pixel.y > 0 && (pixel.y == size.y - 1 || abs(downDepth - depth) < abs(upDepth - depth));
    vec2 xUv = (vec2(useLeft ? left : right) + 0.5) / vec2(size);
    vec2 yUv = (vec2(useDown ? down : up) + 0.5) / vec2(size);
    vec3 dx = relativePositionAt(xUv, useLeft ? leftDepth : rightDepth) - relativePosition;
    vec3 dy = relativePositionAt(yUv, useDown ? downDepth : upDepth) - relativePosition;
    if (useLeft) dx = -dx;
    if (useDown) dy = -dy;
    // Normalize each tangent before the cross product; close surfaces have tiny pixel footprints.
    dx *= inversesqrt(max(dot(dx, dx), 0.00000000000000000001));
    dy *= inversesqrt(max(dot(dy, dy), 0.00000000000000000001));
    vec3 normal = safeUnit(cross(dx, dy), fallback);
    return dot(normal, fallback) < 0.0 ? -normal : normal;
}

void main() {
    int lightCount = clamp(LightCount, 0, 8);
    if (lightCount == 0) {
        fragColor = vec4(0.0);
        return;
    }

    ivec2 depthSize = textureSize(OpaqueDepth, 0);
    ivec2 depthPixel = clamp(ivec2(uv * vec2(depthSize)), ivec2(0), depthSize - ivec2(1));
    vec2 depthUv = (vec2(depthPixel) + 0.5) / vec2(depthSize);
    float depth = clamp(texelFetch(OpaqueDepth, depthPixel, 0).r, 0.0, 1.0);
    // Lighting may run at half resolution: reconstruct the fetched full-resolution depth texel.
    vec3 relativePosition = relativePositionAt(depthUv, depth);
    vec3 position = CameraLocal + relativePosition;
    vec3 rayDirection = safeUnit(relativePosition, vec3(0.0, 0.0, -1.0));
    vec3 normal = depth < 0.999999 ? receiverNormal(depthPixel, depthSize, depth, relativePosition, -rayDirection)
            : -rayDirection;
    vec3 direct = vec3(0.0);
    bool hasSurface = depth < 0.999999;
    float cameraDistance = length(relativePosition);
    float surfaceCoverage = 1.0 - smoothstep(28.0, 32.0, cameraDistance);
    bool hasVolume = false;
    for (int light = 0; light < lightCount; ++light) {
        if (LightColorIntensity[light].w <= 0.0 || LightPositionRange[light].w <= 0.0) continue;
        if (hasSurface && surfaceCoverage > 0.0) direct += surfaceLight(light, position, normal);
        hasVolume = hasVolume || LightVolumeShadow[light].x > 0.0;
    }

    // Final scene color is a bounded reflectance proxy, not a shaderpack material buffer.
    vec3 reflectance = clamp(texture(SceneColor, uv).rgb * 0.65 + vec3(0.18), vec3(0.18), vec3(1.0));
    direct *= reflectance * surfaceCoverage;
    if (hasSurface && max(direct.r, max(direct.g, direct.b)) > 0.00001) {
        direct *= segmentTransmission(CameraLocal, position);
    }
    vec3 volume = vec3(0.0);
    int steps = clamp(VolumetricSteps, 0, 24);
    // Sources and receivers stay within the scene cache's guarded 32-block coverage.
    float rayLength = hasSurface ? min(cameraDistance, 32.0) : 32.0;
    if (hasVolume && steps > 0 && rayLength > EPSILON) {
        // March only where a light can contribute. A panel's four emitters fit inside this bound.
        float marchStart = rayLength;
        float marchEnd = 0.0;
        for (int light = 0; light < lightCount; ++light) {
            if (LightVolumeShadow[light].x <= 0.0 || LightColorIntensity[light].w <= 0.0
                    || LightPositionRange[light].w <= 0.0) continue;
            float radius = LightPositionRange[light].w;
            if (LightShape[light].x > 1.5) radius += length(LightShape[light].yz) * 0.25;
            vec3 toCamera = CameraLocal - LightPositionRange[light].xyz;
            float alongRay = dot(toCamera, rayDirection);
            float discriminant = radius * radius - dot(toCamera, toCamera) + alongRay * alongRay;
            if (discriminant <= 0.0) continue;
            float extent = sqrt(discriminant);
            float entry = max(0.0, -alongRay - extent);
            float exitDistance = min(rayLength, -alongRay + extent);
            if (exitDistance <= entry) continue;
            marchStart = min(marchStart, entry);
            marchEnd = max(marchEnd, exitDistance);
        }
        if (marchEnd <= marchStart) {
            fragColor = vec4(finiteColor(direct), 1.0);
            return;
        }
        // Clip camera/media intervals once, outside the light and marching loops.
        float mediumEntry[32];
        float mediumExit[32];
        int cameraMedium[32];
        int cameraMediumCount = 0;
        int mediumCount = clamp(MediumCount, 0, 32);
        for (int medium = 0; medium < mediumCount; ++medium) {
            float entry;
            float exitDistance;
            bool intersects = clipBox(CameraLocal, rayDirection, rayLength,
                    MediumMinimum[medium].xyz, MediumMaximum[medium].xyz, entry, exitDistance);
            if (intersects) {
                cameraMedium[cameraMediumCount] = medium;
                mediumEntry[cameraMediumCount] = entry;
                mediumExit[cameraMediumCount] = exitDistance;
                ++cameraMediumCount;
            }
        }
        float stepLength = (marchEnd - marchStart) / float(steps);
        float jitter = fract(dot(gl_FragCoord.xy, vec2(0.754877666, 0.569840296)));
        for (int march = 0; march < steps; ++march) {
            float distance = marchStart + (float(march) + 0.25 + jitter * 0.5) * stepLength;
            vec3 samplePosition = CameraLocal + rayDirection * distance;
            vec3 opticalDepth = vec3(distance * 0.006);
            vec3 scatteringTint = vec3(1.0);
            float density = 1.0;
            for (int interval = 0; interval < cameraMediumCount; ++interval) {
                int medium = cameraMedium[interval];
                float thickness = clamp(distance - mediumEntry[interval], 0.0,
                        max(mediumExit[interval] - mediumEntry[interval], 0.0));
                opticalDepth += MediumTint[medium].rgb * thickness;
                if (distance >= mediumEntry[interval] && distance <= mediumExit[interval]) {
                    float boost = clamp(MediumTint[medium].w, 0.0, 8.0);
                    density += boost;
                    scatteringTint += exp(-MediumTint[medium].rgb) * boost;
                }
            }
            scatteringTint /= density;
            vec3 viewTransmission = exp(-min(opticalDepth, vec3(40.0)));
            if (max(viewTransmission.r, max(viewTransmission.g, viewTransmission.b)) < 0.0001) break;
            vec3 scattered = vec3(0.0);
            for (int light = 0; light < lightCount; ++light) {
                float strength = clamp(LightVolumeShadow[light].x, 0.0, 8.0);
                if (strength <= 0.0 || LightColorIntensity[light].w <= 0.0 || LightPositionRange[light].w <= 0.0) continue;
                // Interleave panel quadrants along the ray instead of four shadow/media queries per step.
                int emitter = LightShape[light].x > 1.5 ? (march & 3) : 0;
                vec3 source = emitterPosition(light, emitter);
                vec3 sourceToPoint = vec3(0.0);
                vec3 radiance = radianceAt(light, source, samplePosition, sourceToPoint);
                if (max(radiance.r,max(radiance.g,radiance.b)) <= 0.00001) continue;
                float visibility = shadowVisibility(light, samplePosition, vec3(0.0), emitter, false);
                if (visibility <= 0.0) continue;
                float phase = 0.35 + 0.65 * pow(max(dot(sourceToPoint, -rayDirection), 0.0), 4.0);
                scattered += radiance * segmentTransmission(source, samplePosition) * visibility * phase * strength;
            }
            float coverage = 1.0 - smoothstep(28.0, 32.0, distance);
            volume += scattered * viewTransmission * scatteringTint * min(density, 8.0) * stepLength * coverage * 0.025;
        }
    }
    fragColor = vec4(finiteColor(direct + volume), 1.0);
}
