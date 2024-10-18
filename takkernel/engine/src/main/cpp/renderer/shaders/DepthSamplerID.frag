R"(
precision highp float;
uniform int uColor;
vec3 UnpackColor(float f) {
    vec3 color;
    color.b = floor(f / 256.0 / 256.0);
    color.g = floor((f - color.b * 256.0 * 256.0) / 256.0);
    color.r = floor(f - color.b * 256.0 * 256.0 - color.g * 256.0);
    return color / 255.0;
}
void main()
{
    gl_FragColor = vec4(UnpackColor(float(uColor)), 0.0);
}
)"