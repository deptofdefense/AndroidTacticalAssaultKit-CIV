R"(
precision highp float;
uniform sampler2D uTexture;
varying vec2 vTexCoords;
void main()
{
    gl_FragColor = texture2D(uTexture, vTexCoords);
}
)"