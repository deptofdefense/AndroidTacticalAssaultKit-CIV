R"(
#version 100
precision mediump float;
varying vec4 vColor;
void main(void) {
  gl_FragColor = vColor;
}
)"