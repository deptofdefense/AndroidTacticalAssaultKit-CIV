R"(
#version 100
uniform mat4 uMVP;
attribute vec3 aVertexCoords;
attribute vec3 aEcefVertCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vNoDataFlag;
varying float vAngle;
void main() {
  vNoDataFlag = aNoDataFlag;
  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));
  gl_Position = uMVP * vec4(aEcefVertCoords, 1.0);
}

)"