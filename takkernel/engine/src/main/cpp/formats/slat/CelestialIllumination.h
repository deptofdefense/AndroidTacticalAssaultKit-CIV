#ifndef TAK_ENGINE_FORMATS_CELESTIALILLUMINATION_H_INCLUDED
#define TAK_ENGINE_FORMATS_CELESTIALILLUMINATION_H_INCLUDED

#include "port/Platform.h"
#include "cons.h"
#include "rs.h"
#include "rslib.h"
#include "smeph.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace SLAT {
/*
   Structures

   aux_data: structure containing auxiliary information related to the
             illuminance calculation.

      jd_ut     = Julian date, Universal Time.
      alt       = topocentric altitude of the object, affected by
                  refraction (degrees).
      az        = azimuth of the object, measured E from N (degrees).
      ill       = illuminance due to the object (lux).
      frac_ill  = fraction of the object illuminated.
                  NOTE: This applies only to the Moon!
*/

                typedef struct
                {
                    double jd_ut;
                    double alt;
                    double az;
                    double ill;
                    double frac_ill;
                } aux_data;

/*
   cheby_parms: structure containing the parameters of a Chebyshev
      polynomial fit to discrete data.

      degree           = degree of the polynomial.
      c[0]-c[1]        = transformation parameters; used to transform
                         the independent variable into the range
                         (-1,+1).
      c[2]-c[degree+2] = coefficients of the Chebyshev polynomial.
*/

                typedef struct
                {
                    short int degree;
                    double c[27];
                } cheby_parms;

                class ENGINE_API CelestialIllumination
                {
                public:
                    static short int illuminance (double tjd_ut, double delta_t, observer *location, short int sky_cond, double *illum_tot, aux_data *sun_data, aux_data *moon_data);
                private:
                    static double illum_sun (double alt, short int sky_cond);
                    static double illum_moon (double alt, double elongation, double eqh_par, short int sky_cond);
                    static double phs_ang (double dissun, double disobj, double elong_ang);
                    static double phase (double i);
                    static double elong (double rasun, double decsun, double raobj, double decobj);
                    static double eval_cheby (cheby_parms *p, double x);
                };
            }
        }
    }
}  

#endif // TAK_ENGINE_FORMATS_CELESTIALILLUMINATION_H_INCLUDED
