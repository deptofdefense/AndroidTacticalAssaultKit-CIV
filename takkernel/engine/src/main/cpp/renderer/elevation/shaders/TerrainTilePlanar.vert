R"(

uniform mat4 uMVP;
uniform mat4 uNormalMatrix;
uniform mat4 uModelViewOffscreen;
uniform float uTexWidth;
uniform float uTexHeight;
uniform float uElevationScale;
in vec3 aVertexCoords;
in vec3 aNormals;
out vec3 vNormal;
out vec2 vTexPos;
out float vLumAdj;
in float aNoDataFlag;
out float vNoDataFlag;
void main() {
  vec4 offscreenPos = uModelViewOffscreen * vec4(aVertexCoords.xy, 0.0, 1.0);
  offscreenPos.x = offscreenPos.x / offscreenPos.w;
  offscreenPos.y = offscreenPos.y / offscreenPos.w;
  offscreenPos.z = offscreenPos.z / offscreenPos.w;
  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);
  vTexPos = texPos.xy;
  vNormal = aNormals;
  vLumAdj = 0.0;
  vNoDataFlag = aNoDataFlag;
  gl_Position = uMVP * vec4(aVertexCoords.xy, aVertexCoords.z*uElevationScale, 1.0);
}

)"