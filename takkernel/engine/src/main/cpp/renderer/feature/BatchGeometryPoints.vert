R"(
uniform mat4 uMVP;
uniform vec2 uMapRotation;

uniform vec3 uCameraRtc;
uniform vec3 uWcsScale;
uniform float uTanHalfFov;
uniform float uViewportHeight;
uniform float uDrawTilt;

attribute float aPointSize;
attribute vec3 aVertexCoords;
attribute vec2 aRotation; // x: cos(theta), y: sin(theta)
attribute float aAbsoluteRotationFlag;

// Coordinates needed to access the sprite from the texture atlas
attribute vec2 aSpriteBottomLeft;
attribute vec2 aSpriteDimensions;

attribute vec4 aColor;
varying vec2 vSpriteBottomLeft;
varying vec2 vSpriteDimensions;
varying float vPointSize;
varying vec4 vColor;
varying vec2 vRotation;

void main() {
  // compute the line of sight
  vec3 lineOfSight = (aVertexCoords-uCameraRtc)*uWcsScale;

  // compute the radius of the point, in nominal WCS units
  float range = length(lineOfSight);
  float gsd = range*uTanHalfFov/(uViewportHeight/2.0);
  float radiusMeters = gsd*aPointSize/2.0;

  // adjust range to position point at near edge of radius
  range -= radiusMeters;
  
  // recompute vertex position
  vec3 adjustedPos = (normalize(lineOfSight)*range)/uWcsScale + uCameraRtc;

  gl_PointSize = aPointSize;
  vPointSize = aPointSize;
  vSpriteBottomLeft = aSpriteBottomLeft;
  vSpriteDimensions = aSpriteDimensions;
  vRotation = aRotation;
  gl_Position = uMVP * vec4(adjustedPos.xyz, 1.0);
  vColor = aColor;

  gl_Position.y += (abs(sin(uDrawTilt))*(aPointSize/2.0)/uViewportHeight) * gl_Position.w;
  // Calculate the actual rotation.
  vec2 actualRotation = aRotation + aAbsoluteRotationFlag * uMapRotation;
  vRotation = vec2(cos(actualRotation.x), sin(actualRotation.y));
}
)"