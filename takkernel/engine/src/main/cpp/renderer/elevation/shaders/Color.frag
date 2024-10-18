R"(
precision mediump float;
uniform sampler2D uTexture;
uniform vec4 uColor;
uniform float uMinAmbientLight;
uniform float uLightSourceContribution[2];
uniform vec3 uLightSourceNormal[2];
out vec4 FragColor;
in vec3 vNormal;
in vec2 vTexPos;
in float vLumAdj;
vec4 ApplyLighting(vec4 color);
void main(void) {
  float min_clamp = step(0.0, vTexPos.x) * step(0.0, vTexPos.y);
  float max_clamp = step(0.0, 1.0-vTexPos.x) * step(0.0, 1.0-vTexPos.y);
  vec4 color = texture(uTexture, vTexPos)*uColor*vec4(1.0, 1.0, 1.0, min_clamp*max_clamp);
  color = ApplyLighting(color);
  FragColor = color;
}
)"