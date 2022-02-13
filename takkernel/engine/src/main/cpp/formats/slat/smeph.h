#ifndef TAK_ENGINE_FORMATS_SMEPH_H_INCLUDED
#define TAK_ENGINE_FORMATS_SMEPH_H_INCLUDED

#include <math.h>

#include "cons.h"

   short int applan (double tjd, short int obj, double *ra, double *dec, double *dis);
                     
   void sun (double jd, double *ra, double *dec, double *dis);

   void moon (double jd, double *ra, double *dec, double *dis);

   void nutation (double u, double *dpsi, double *etrue);

#endif
