R"(#version 300 es
uniform mat4 u_mvp;
uniform vec2 uViewport;

in vec3 aPosition;
in vec4 a_color;
in float aExteriorVertex;
in float aOutlineWidth;

out vec4 v_color;
out float vExteriorVertex;
out float vOutlineWidth;

void main() {
  gl_Position = u_mvp * vec4(aPosition, 1.0);
  v_color = a_color;
  vExteriorVertex = aExteriorVertex;
  vOutlineWidth = aOutlineWidth;
}
)"
