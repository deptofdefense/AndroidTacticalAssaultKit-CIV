R"(#version 300 es
precision mediump float;
uniform float uEdgeSoftness;
uniform float uRadius;

flat in vec2 vRectSize;
flat in vec4 vColor;
in vec3 vVertexCoord;

out vec4 fragmentColor;

float roundedBoxSDF(vec2 pos, vec2 halfSize, float radius) {
    return length(max(abs(pos)-halfSize+radius, 0.0))-radius;
}

void main(void) {
    vec2 size = vRectSize;
    vec2 pos = vVertexCoord.xy;
    // Calculate distance to edge
    float distance = roundedBoxSDF(pos - (size / 2.0), (size / 2.0) - uEdgeSoftness, uRadius);
    // Smooth the result
    float smoothedAlpha = 1.0-smoothstep(0.0, uEdgeSoftness, distance);

    fragmentColor = vec4(vColor.rgb, clamp(smoothedAlpha, 0.0, vColor.a));
}
)"