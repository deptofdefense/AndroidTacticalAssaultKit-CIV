R"(

#version 100
uniform mat4 uMVP;
attribute vec3 aVertexCoords;
attribute vec3 aEcefVertCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vEl;
varying float vNoDataFlag;
void main() {
  vec4 lla = vec4(aVertexCoords, 1.0);
  vEl = lla.z;
  vNoDataFlag = aNoDataFlag;
  gl_Position = uMVP * vec4(aEcefVertCoords, 1.0);
}

)"