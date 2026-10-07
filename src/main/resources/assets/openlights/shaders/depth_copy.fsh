#version 330 core

in vec2 uv;
uniform sampler2D SourceDepth;
uniform sampler2D DistantDepth;
uniform int MergeDistant;
uniform int DistantReversedDepth;
uniform int DistantZeroToOneDepth;
uniform mat4 InverseProjection;
uniform mat4 DistantInverseProjection;
uniform mat4 DistantToVanillaView;
uniform mat4 Projection;
layout(location = 0) out vec4 fragDepth;

void main() {
    float depth = texture(SourceDepth, uv).r;
    vec4 view = InverseProjection * vec4(uv * 2.0 - 1.0, min(depth, 0.999999) * 2.0 - 1.0, 1.0);
    float distance = length(view.xyz / view.w);
    fragDepth = vec4(depth, 0.0, distance, 1.0);
    // DH copies background color before normal opaque geometry writes color/depth.
    // A native receiver therefore owns the final scene pixel, including coplanar LOD overlaps.
    if (MergeDistant == 0 || depth < 0.999999) return;
    float distant = texture(DistantDepth, uv).r;
    if (distant < 0.0 || distant > 1.0 || (DistantReversedDepth != 0 ? distant <= 0.0 : distant >= 1.0)) return;
    float distantNdc = DistantZeroToOneDepth != 0 ? distant : distant * 2.0 - 1.0;
    vec4 distantView = DistantInverseProjection * vec4(uv * 2.0 - 1.0, distantNdc, 1.0);
    distantView = DistantToVanillaView * vec4(distantView.xyz / distantView.w, 1.0);
    float distantDistance = length(distantView.xyz);
    if (any(isnan(distantView)) || any(isinf(distantView)) || distantView.z >= 0.0) return;
    vec4 clip = Projection * distantView;
    // Terrain beyond Minecraft's far plane still clips fog at its true distance.
    float receiverDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 0.999998);
    fragDepth = vec4(receiverDepth, 1.0, distantDistance, 1.0);
}
