R"(
#version 100
uniform mat4 uProjection;
uniform mat4 uModelView;
attribute vec3 aVertexCoords;
attribute vec4 aColor;
varying vec4 vColor;
void main() {
  vColor = aColor;
  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);
}
)"