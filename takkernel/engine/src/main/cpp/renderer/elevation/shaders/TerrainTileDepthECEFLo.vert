R"(
precision highp float;
uniform mat4 uMVP;
uniform float uElevationScale;
in vec3 aVertexCoords;
in vec3 aEcefVertCoords;
out float vDepth;
void main() {
    gl_Position = uMVP * vec4(aEcefVertCoords.xyz, 1.0);
    vDepth = (gl_Position.z + 1.0) * 0.5; 
}
)"