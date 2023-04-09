R"(
#version 100
precision mediump float;
uniform sampler2D uTexture;
varying vec2 vTexPos;
uniform vec4 uColor;
void main(void) {
    vec4 color = texture2D(uTexture, vTexPos);
    gl_FragColor = uColor * color;
}
)"