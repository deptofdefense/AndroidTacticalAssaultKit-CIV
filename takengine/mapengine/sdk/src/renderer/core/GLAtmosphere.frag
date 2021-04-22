R"(

precision highp float;
varying highp vec3 eyeRay;
uniform highp vec3 campos;
uniform vec3 uSunPos;
#define PI 3.141592
#define iSteps 16
#define jSteps 8
#define planetRadius 6356752.3142
#define planetEllipsoid 6378137.0
#define atmosphereHeight 100e3
#define kRlh vec3(5.5e-6, 13.0e-6, 22.4e-6)	// Rayleigh scattering coefficient
#define kMie 21e-6		// Mie scattering coefficient
#define shRlh 8e3		// Rayleigh scale height
#define shMie 1.2e3		// Mie scale height
#define g 0.758			// Mie preferred scattering direction


highp vec2 rsi(highp vec3 r0, highp vec3 ray_dir, highp float sr) {
   // ray-sphere intersection that assumes
   // the sphere is centered at the origin.
   // No intersection when result.x > result.y
   ray_dir = normalize(ray_dir);
   highp float a = dot(ray_dir, ray_dir);
   highp float b = 2.0 * dot(ray_dir, r0);
   highp float c = dot(r0, r0) - (sr * sr);
   highp float d = (b*b) - 4.0*a*c;
   if (d < 0.0) return vec2(1e15,-1e15);
   return vec2(
   (-b - sqrt(d))/(2.0*a),
   (-b + sqrt(d))/(2.0*a)
   );
}

highp vec2 atmosphere_intersect(highp vec3 ray_dir, highp vec3 r0, highp float rPlanet, highp float rAtmos)
{
	highp vec2 no_intersect = vec2(1e15, -1e15);
	highp float foutside = max(sign(length(r0) - (rAtmos)), 0.0); //0 inside atmosphere, 1 outside
    // Calculate the step size of the primary ray.
    highp vec2 p = rsi(r0, ray_dir, rAtmos);
    if (p.x > p.y) return no_intersect;
	if(p.y < 0.0) return no_intersect;
	highp vec2 planet_p = rsi(r0, ray_dir, rPlanet);
	if(planet_p.x < 0.0)
	   planet_p.x = 1e15;
	   
	if(planet_p.y > planet_p.x)
		return no_intersect;
	
    p.y = min(p.y, planet_p.x);
	p.x = p.x * foutside;
	
	return p;
}

highp vec3 atmosphere(highp vec3 ray_dir, highp vec3 r0, highp vec3 pSun, highp float iSun, highp vec2 atmosphere_intersection, highp float rPlanet, highp float rAtmos) {
 // Normalize the sun and view directions.
    pSun = normalize(pSun);
    ray_dir = normalize(ray_dir);

	highp vec2 p = atmosphere_intersection;

    highp float iStepSize = (p.y - p.x) / float(iSteps);
	
    highp float iTime = 0.0;

    // Initialize accumulators for Rayleigh and Mie scattering.
    highp vec3 totalRlh = vec3(0,0,0);
    highp vec3 totalMie = vec3(0,0,0);

    // Initialize optical depth accumulators for the primary ray.
    highp float iOdRlh = 0.0;
    highp float iOdMie = 0.0;

    // Calculate the Rayleigh and Mie phases.
    highp float mu = dot(ray_dir, pSun);
    highp float mumu = mu * mu;
    highp float gg = g * g;
    highp float pRlh = 3.0 / (16.0 * PI) * (1.0 + mumu);
    highp float pMie = 3.0 / (8.0 * PI) * ((1.0 - gg) * (mumu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * g, 1.5) * (2.0 + gg));

    // Sample the primary ray.
    for (int i = 0; i < iSteps; i++) {

        // Calculate the primary ray sample position.
        highp vec3 iPos = r0 + ray_dir * ( p.x + iTime + iStepSize * 0.5);

        // Calculate the height of the sample.
        highp float iHeight = length(iPos) - rPlanet;
		iHeight = max(5000.0, iHeight);
		
        // Calculate the optical depth of the Rayleigh and Mie scattering for this step.
        highp float odStepRlh = exp(-iHeight / shRlh) * iStepSize;
        highp float odStepMie = exp(-iHeight / shMie) * iStepSize;

        // Accumulate optical depth.
        iOdRlh += odStepRlh;
        iOdMie += odStepMie;

        // Calculate attenuation.
        highp  vec3 attn = exp(-(kMie * (iOdMie) + kRlh * (iOdRlh)));

        // Accumulate scattering.
        totalRlh += odStepRlh * attn;
        totalMie += odStepMie * attn;

        // Increment the primary ray time.
        iTime += iStepSize;

    }

    // Calculate and return the final color.
    return iSun * (pRlh * kRlh * totalRlh + pMie * kMie * totalMie);
}
void main(void) {
	vec3 col = vec3(0,0,0);

	highp float r = planetEllipsoid;
	
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
		col = 1.0 - exp(-1.0 * col);
	}
    gl_FragColor = vec4(col.x, col.y, col.z, 1.0);
}
)"