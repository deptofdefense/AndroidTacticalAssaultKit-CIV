R"(#version 300 es
precision mediump float;
uniform mediump vec2 u_viewportSize;
uniform bool u_hitTest;
in vec4 v_color;
in float v_mix;
flat in int f_pattern;
flat in int f_factor;
flat in float f_dist;
in vec2 v_normal;
flat in float f_halfStrokeWidth;
out vec4 v_FragColor;
void main(void) {
  float d = (f_dist*v_mix);
  int idist = int(d);
  float b0 = float((f_pattern>>((idist/f_factor)%16))&0x1);
  float b1 = float((f_pattern>>(((idist+1)/f_factor)%16))&0x1);
  float alpha = mix(b0, b1, fract(d));
  float antiAlias = smoothstep(-1.0, 0.25, f_halfStrokeWidth-length(v_normal));
  v_FragColor = v_color;
  // applies pattern/anti-alias only if not hit-testing
  v_FragColor.a = mix(v_color.a*antiAlias*alpha, v_color.a, float(u_hitTest));
}
)"