R"(
precision mediump float;
in float vDepth;
out vec4 vFragColor;
vec4 PackDepth(float v) {
  vec4 r = vec4(1.,255.,65025.,16581375.) * v;
  r = fract(r);
  r -= r.yzww * vec4(1.0/255.0,1.0/255.0,1.0/255.0,0.0);
  return r;
}
void main(void) {
  vFragColor = PackDepth(vDepth);
}

)"