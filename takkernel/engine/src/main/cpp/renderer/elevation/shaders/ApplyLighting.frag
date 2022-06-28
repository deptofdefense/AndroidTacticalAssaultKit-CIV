R"(
vec4 ApplyLighting(vec4 color)
{
  vec3 light_color = vec3(1.0, 1.0, 1.0);
  float lum = 0.0;
  for(int i = 0; i < 2; i++) {
    lum = lum + max(dot(vNormal, uLightSourceNormal[i]), 0.0)*uLightSourceContribution[i];
  }
  lum =  min((uMinAmbientLight + (1.0-uMinAmbientLight)*(lum + vLumAdj)), 1.0);
  color =  color *  vec4(lum*light_color, 1.0);
  return color;
}
)"