#ifndef TAK_ENGINE_FORMATS_RSLIB_H_INCLUDED
#define TAK_ENGINE_FORMATS_RSLIB_H_INCLUDED

#include <math.h>

#include "cons.h"

   double eh_par (double r);

   double par_zd (double z, double r);

   double semidi (double r, double rp);

   double refract (double alt_geom);

   double st (double tjd, double lon);

   double lha (double ra, double last);

   double azimuth (double dec, double h, double lat);

   double z_dist (double dec, double h, double lat);

   short int set_interval (short int step_opt, short int body, 
                           short int twi_opt, double lat, double zd_hor, 
                           double dec, short int *interval);
                           
   short int interp2 (double t0, double t[3], double f[3], double *f_at_t0);

   short int inv_int (double f_at_t0, double t[3], double f[3], double tolerance, double *t0);

   short int sign (double a);

#endif
