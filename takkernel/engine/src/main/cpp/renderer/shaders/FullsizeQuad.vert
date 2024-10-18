R"(
precision lowp float;
attribute vec2 aVertexCoords;
varying vec2 vTexCoords;
const vec2 scale = vec2(0.5, 0.5);
void main()
{
    vTexCoords = aVertexCoords * scale + scale;
    gl_Position = vec4(aVertexCoords, 0.0, 1.0);
}
)"