R"(
precision mediump float;
uniform sampler2D uTexture;
uniform vec4 uColor;
varying vec2 vSpriteBottomLeft;
varying vec2 vSpriteDimensions;
varying float vPointSize;
varying vec4 vColor;
varying vec2 vRotation;

void main(void) {
  // Calculate the center of the sprite in the texture atlas
  vec2 topRight = vSpriteBottomLeft + vSpriteDimensions;
  vec2 origin = (vSpriteBottomLeft + (topRight)) / 2.0;

  // Calculate the dimensions of the sprite in a 0 - 1 range
  // Used to make sure that non-square sprites aren't drawn as squares
  float maxDimension = max(vSpriteDimensions.x, vSpriteDimensions.y);
  vec2 normalizedSpriteDimensions = vSpriteDimensions / maxDimension;

  // Create a transform to move the sprite to the origin, rotate it, then move it to its original position
  mat3 rotation = mat3(vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(origin, 1.0)) 
                * mat3(vec3(vRotation.x, vRotation.y, 0.0), vec3(-vRotation.y, vRotation.x, 0.0), vec3(0.0, 0.0, 1.0))
                * mat3(vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(-origin, 1.0));

  vec2 atlasTexPos = (rotation * vec3(vSpriteBottomLeft + (vSpriteDimensions * ((gl_PointCoord / normalizedSpriteDimensions))), 1.0)).xy;

  // Make sure the rotated atlasTexPos stays within the bounds of the sprite so we don't touch any 
  // neighboring sprites or run off the edge of the texture atlas.
  bvec3 isDiscard = bvec3(any(greaterThanEqual(atlasTexPos, topRight)), any(lessThanEqual(atlasTexPos, vSpriteBottomLeft)), any(greaterThanEqual(gl_PointCoord, normalizedSpriteDimensions)));
  gl_FragColor = uColor * vColor * texture2D(uTexture, atlasTexPos);
  // turn fragment transparent if it should be discarded
  gl_FragColor.a = gl_FragColor.a * (1.0-float(any(isDiscard)));
  // XXX - candidate for separate shader for depth sorted sprites
  if(gl_FragColor.a < 0.1)
      discard;
}
)"
