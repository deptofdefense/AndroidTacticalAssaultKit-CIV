R"(
#version 100
uniform mat4 uProjection;
uniform mat4 uModelView;
attribute vec2 aVertexCoords;
attribute vec4 aColor;
varying vec4 vColor;
void main() {
  vColor = aColor;
  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);
}
)"