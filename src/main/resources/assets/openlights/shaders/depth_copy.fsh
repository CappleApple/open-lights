#version 330 core

in vec2 uv;
uniform sampler2D SourceDepth;
layout(location = 0) out float fragDepth;

void main() {
    fragDepth = texture(SourceDepth, uv).r;
}
