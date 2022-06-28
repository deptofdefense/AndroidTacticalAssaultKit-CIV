R"(
attribute vec3 aVertexCoords;
uniform mat4 uMVP;
void main()
{
    gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);
}
)"