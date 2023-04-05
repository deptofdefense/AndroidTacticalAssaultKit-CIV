R"(#version 300 es
precision mediump float;
uniform vec4 u_color;

in vec4 v_color;
in float vExteriorVertex;
in float vOutlineWidth;

out vec4 fragColor;

// Polygon outline implementation taken from here:
// https://community.khronos.org/t/how-do-i-draw-a-polygon-with-a-1-2-or-n-pixel-inset-outline-in-opengl-4-1/104201

void main(void) {
  vec2 gradient = vec2(dFdx(vExteriorVertex), dFdy(vExteriorVertex));
  float distance = vExteriorVertex / length(gradient);
  if (distance < vOutlineWidth) {
    fragColor = u_color;
  } else {
    fragColor = v_color * u_color;
  }
}
)"