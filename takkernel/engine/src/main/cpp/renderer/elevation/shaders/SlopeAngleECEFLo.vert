R"(
#version 100
uniform mat4 uMVP;
uniform mat4 uLocalTransform[3];
attribute vec3 aVertexCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vNoDataFlag;
varying float vAngle;
vec3 lla2ecef(in vec3 llh);
void main() {
  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);
  lla = lla / lla.w;
  vec3 ecef = lla2ecef(vec3(lla.xy, lla.z));
  vNoDataFlag = aNoDataFlag;
  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));
  gl_Position = uMVP * vec4(ecef.xyz, 1.0);
}

)"