R"(
uniform mat4 uMVP;
uniform mat4 uLocalTransform[3];
uniform mat4 uModelViewOffscreen;
uniform mat4 uNormalMatrix;
uniform float uTexWidth;
uniform float uTexHeight;
uniform float uElevationScale;
in vec3 aVertexCoords;
out vec2 vTexPos;
in vec3 aNormals;
out vec3 vNormal;
out float vLumAdj;
in float aNoDataFlag;
out float vNoDataFlag;
vec3 lla2ecef(in vec3 llh);
void main() {
  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);
  lla /= lla.w;
  vec4 llaLocal = uLocalTransform[1] * lla;
  vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);
  lla2ecef_in /= lla2ecef_in.w;
  vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;
  ecefSurface /= ecefSurface.w;
  vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));
  vec4 offscreenPos = uModelViewOffscreen * vec4(lla.xy, 0.0, 1.0);
  offscreenPos.x = offscreenPos.x / offscreenPos.w;
  offscreenPos.y = offscreenPos.y / offscreenPos.w;
  offscreenPos.z = offscreenPos.z / offscreenPos.w;
  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);
  vTexPos = texPos.xy;
  vNormal = aNormals;
  vLumAdj = 0.0;
  vNoDataFlag = aNoDataFlag;
  gl_Position = uMVP * vec4(ecef.xyz, 1.0);
}
)"