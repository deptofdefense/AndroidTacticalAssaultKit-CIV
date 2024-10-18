R"(
precision highp float;
uniform mat4 uMVP;
uniform mat4 uLocalTransform[3];
uniform float uElevationScale;
in vec3 aVertexCoords;
out float vDepth;
vec3 lla2ecef(in vec3 llh);
void main() {
    vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);
    lla = lla / lla.w;
    vec3 ecef = lla2ecef(vec3(lla.xy, lla.z*uElevationScale));
    gl_Position = uMVP * vec4(ecef.xyz, 1.0);
    vDepth = (gl_Position.z + 1.0) * 0.5; 
}
)"