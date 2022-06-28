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
    vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);
    lla = lla / lla.w;
    vec3 ecef = lla2ecef(vec3(lla.xy, lla.z*uElevationScale));
    vec4 offscreenPos = uModelViewOffscreen * vec4(lla.xy, 0.0, 1.0);
    offscreenPos.x = offscreenPos.x / offscreenPos.w;
    offscreenPos.y = offscreenPos.y / offscreenPos.w;
    offscreenPos.z = offscreenPos.z / offscreenPos.w;
    vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);
    vNormal = aNormals;
    vTexPos = texPos.xy;
    vLumAdj = 0.0;
    vNoDataFlag = aNoDataFlag;
    gl_Position = uMVP * vec4(ecef.xyz, 1.0);
}
)"