R"(
#version 100
uniform mat4 uMVP;
uniform mat4 uLocalFrame;
attribute vec4 aVertexCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vEl;
varying float vNoDataFlag;
void main() {
  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);
  vEl = (uLocalFrame * vec4(aVertexCoords.xyz, 1.0)).z;
  vNoDataFlag = aNoDataFlag;
}
)"