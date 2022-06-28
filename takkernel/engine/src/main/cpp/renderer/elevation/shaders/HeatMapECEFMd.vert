R"(
#version 100
uniform mat4 uMVP;
uniform mat4 uLocalTransform[3];
attribute vec3 aVertexCoords;
attribute vec3 aNormals;
attribute float aNoDataFlag;
varying float vEl;
varying float vNoDataFlag;
void main() {
    vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);
    lla /= lla.w;
    vec4 llaLocal = uLocalTransform[1] * lla;
    vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);
    lla2ecef_in /= lla2ecef_in.w;
    vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;
    ecefSurface /= ecefSurface.w;
    vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));
    vEl = lla.z;
    vNoDataFlag = aNoDataFlag;
    gl_Position = uMVP * vec4(ecef.xyz, 1.0);
}

)"