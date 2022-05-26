#ifndef TAK_ENGINE_FORMATS_RS_H_INCLUDED
#define TAK_ENGINE_FORMATS_RS_H_INCLUDED


#include <math.h>

#include "cons.h"
#include "rslib.h"
#include "deltat.h"
#include "smeph.h"

   /*
       observer  : structure providing the location of the observer.
          latitude  = latitude of the observer (degrees)
          longitude = longitude of the observer; east positive (degrees)
   */
   typedef struct
   {
      double latitude;
      double longitude;
   } observer;

   /*
       phenomenon: structure containing information for a specific
                   astronomical phenomenon (e.g., rise, set, transit,
                   beginning of twilight, etc.)

          type      = phenomenon type where...
                      B = beginning of twilight
                      R = rise
                      T = transit
                      S = set
                      E = end of twilight;
          jd        = UT Julian date of the phenomenon;
          loc_jd    = "Julian date" of the phenomenon in local time;
          angle     = azimuth at time of rise/set or altitude at time
                      of transit, degrees;
          tran_dir  = direction along the horizon to the body at time
                      of transit (N or S; will be blank for phenomena
                      other than transits);
          interval  = search interval used for calculation, in minutes.
          warning   = warning code for the phenomenon calculation.
   */
   typedef struct
   {
      char type;
      double jd;
      double loc_jd;
      double angle;
      char tran_dir;
      short int interval;
      short int warning;
   } phenomenon;

   short int rsttwi (long int jd, observer *location, double delta_t,
                     double time_zone, char object, char twilight,
                     char *message, short int *num_phen, 
                     phenomenon phen[6]);

   short int phenom_search (double jd_ut, observer *location,
                            double time_zone, double radius, 
                            double d_t[3], double d_ra[3], 
                            double d_dec[3], double d_dis[3], 
                            double zd_crit[2], short int num_zd_c,
                            short int interval,
                            short int *num_phen, char *message,
                            phenomenon phen_data[6]);

#endif
