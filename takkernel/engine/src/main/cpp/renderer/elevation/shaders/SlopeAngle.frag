R"(
#version 100
precision mediump float;
uniform sampler2D uTexture;
uniform float uAlpha;
varying float vNoDataFlag;
varying float vAngle;
void main(void) {
  vec4 color = texture2D(uTexture, vec2(vAngle, 0.5));
  gl_FragColor = vec4(color.rgb, color.a*uAlpha*step(1.0, vNoDataFlag));
}

)"