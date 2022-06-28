R"(
#version 100
precision mediump float;
uniform float uMinEl;
uniform float uMaxEl;
uniform float uSaturation;
uniform float uValue;
uniform float uAlpha;
varying float vEl;
varying float vNoDataFlag;
vec3 hsv2rgb(vec3 hsv) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(hsv.xxx + K.xyz) * 6.0 - K.www);
    return hsv.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), hsv.y); 
}
void main(void) {
  float hue = (1.0 - (clamp(vEl, uMinEl, uMaxEl)-uMinEl)/(uMaxEl-uMinEl)) * 2.0 / 3.0;
  vec3 hsv = vec3(hue, uSaturation, uValue);
  gl_FragColor = vec4(hsv2rgb(hsv).rgb, uAlpha*vNoDataFlag);
}

)"