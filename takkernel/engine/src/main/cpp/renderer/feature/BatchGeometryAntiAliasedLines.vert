R"(#version 300 es
precision highp float;
const float c_smoothBuffer = 2.0;
uniform mat4 u_mvp;
uniform mediump vec2 u_viewportSize;
in vec3 a_vertexCoord0;
in vec3 a_vertexCoord1;
in vec2 a_texCoord;
in vec4 a_color;
in float a_normal;
in float a_dir;
in int a_pattern;
in int a_factor;
in float a_halfStrokeWidth;
out vec4 v_color;
flat out float f_dist;
out float v_mix;
flat out int f_pattern;
out vec2 v_normal;
flat out float f_halfStrokeWidth;
flat out int f_factor;
void main(void) {
  gl_Position = u_mvp * vec4(a_vertexCoord0.xyz, 1.0);
  vec4 next_gl_Position = u_mvp * vec4(a_vertexCoord1.xyz, 1.0);
  vec4 p0 = (gl_Position / gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);
  vec4 p1 = (next_gl_Position / next_gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);
  v_mix = a_dir;
  float dist = distance(p0.xy, p1.xy);
  float dx = p1.x - p0.x;
  float dy = p1.y - p0.y;
  float normalDir = (2.0*a_normal) - 1.0;
  float adjX = normalDir*(dx/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.y);
  float adjY = normalDir*(dy/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.x);
  gl_Position.x = gl_Position.x - adjY*gl_Position.w;
  gl_Position.y = gl_Position.y + adjX*gl_Position.w;
  v_color = a_color;
  v_normal = vec2(-normalDir*(dy/dist)*(a_halfStrokeWidth+c_smoothBuffer), normalDir*(dx/dist)*(a_halfStrokeWidth+c_smoothBuffer));
  f_pattern = a_pattern;
  f_factor = a_factor;
  f_dist = dist;
  f_halfStrokeWidth = a_halfStrokeWidth;
}
)"