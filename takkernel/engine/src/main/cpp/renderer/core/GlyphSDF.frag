R"(
#version 100
precision mediump float;
uniform sampler2D uTexture;
uniform vec4 uColor;
uniform float uBuffer;
uniform float uGamma;
varying vec2 vTexPos;
void main() {
    float dist = texture2D(uTexture, vTexPos).r;
    float alpha = smoothstep(uBuffer - uGamma, uBuffer + uGamma, dist);
    gl_FragColor = vec4(uColor.rgb, alpha * uColor.a);
}
)"