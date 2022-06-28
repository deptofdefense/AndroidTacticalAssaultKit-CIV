R"(
#version 100
uniform mat4 uMVP;
attribute vec4 aVertexCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vNoDataFlag;
varying float vAngle;
void main() {
  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);
  vNoDataFlag = aNoDataFlag;
  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));
}
)"