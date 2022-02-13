R"(

#version 100
precision highp float;
varying highp vec3 eyeRay;
uniform highp vec3 campos;
uniform vec3 uSunPos;
uniform float alpha;
#define PI 3.141592
#define iSteps 16
#define jSteps 8
#define planetRadius 6356752.3142
#define planetEllipsoid 6378137.0
#define atmosphereHeight 100e3
uniform sampler2D colorTexture;

highp vec2 rsi(highp vec3 r0, highp vec3 ray_dir, highp float sr) {
   // ray-sphere intersection that assumes
   // the sphere is centered at the origin.
   // No intersection when result.x > result.y
   ray_dir = normalize(ray_dir);
   highp float a = dot(ray_dir, ray_dir);
   highp float b = 2.0 * dot(ray_dir, r0);
   highp float c = dot(r0, r0) - (sr * sr);
   highp float d = (b*b) - 4.0*a*c;

   highp float s = step(0.0, d);
   d = max(.0001, d);
   highp float sqr = sqrt(d);

   highp vec2 no_intersect = vec2(1e15, -1e15);
   highp vec2 intersect = vec2((-b - sqr)/(2.0*a),
                             (-b + sqr)/(2.0*a)
                             );

   return no_intersect * (1.0 - s) +  intersect * s;
}

highp vec2 atmosphere_intersect(highp vec3 ray_dir, highp vec3 r0, highp float rPlanet, highp float rAtmos)
{
	highp vec2 no_intersect = vec2(1e15, -1e15);
	highp float foutside = max(sign(length(r0) - (rAtmos)), 0.0); //0 inside atmosphere, 1 outside
    // Calculate the step size of the primary ray.
    highp vec2 p = rsi(r0, ray_dir, rAtmos);
	highp vec2 planet_p = rsi(r0, ray_dir, rPlanet);
    planet_p.x = mix(planet_p.x, 1e15, step(planet_p.x, 0.0));

    float b_no_intersect = float( p.x > p.y || p.y < 0.0 || planet_p.y > planet_p.x);

    p.y = min(p.y, planet_p.x);
    p.x = p.x * foutside;

    p = mix(p, no_intersect, b_no_intersect);

    return p;
}

highp vec4 atmosphere(highp vec3 ray_dir, highp vec3 r0, highp vec3 pSun, highp float iSun, highp vec2 atmosphere_intersection, highp float rPlanet, highp float rAtmos) {
 // Normalize the sun and view directions.
    pSun = normalize(pSun);
    ray_dir = normalize(ray_dir);

    highp float height = length(campos);

    highp float angle = dot(ray_dir, normalize(campos));

    highp float y = (atmosphere_intersection.y - atmosphere_intersection.x) / (atmosphereHeight * 25.0);

    float outside = step(rAtmos, length(campos));

    y = mix(abs(acos(angle) / PI), y, outside);

    highp float x = (height - rPlanet - 20000.0) / atmosphereHeight;

    vec4 tex = texture2D(colorTexture, vec2(x, y));

    return vec4(tex.x, tex.y,tex.z, alpha);
}
void main(void) {
	vec4 col = vec4(0,0,0,alpha);

	highp float r = planetRadius;
	
	highp vec2 intersection = atmosphere_intersect(eyeRay, campos, planetRadius, planetRadius + atmosphereHeight);
	if(intersection.x < intersection.y)
	{
		col = atmosphere(
				eyeRay,               // normalized ray direction
				campos ,        // ray origin
				campos,                                // position of the sun
				33.0,                                   // intensity of the sun
				intersection,
				r,                           // radius of the planet in meters
				r + atmosphereHeight                   // radius of the atmosphere in meters
		);
	}
    gl_FragColor = col;
}
)"