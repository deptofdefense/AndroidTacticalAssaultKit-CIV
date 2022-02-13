
#include "rs.h"

/**
 * To compute phenomena times (rise, set, transit, and twilight for
 * Sun; rise, set, transit for Moon) and associated quantities for
 * the designated object on a given day.
 *
 * @param jd The Julian date at 12h on the day of interest.
 * @param location A structure providing the location of the observer.
 * @param delta_t The difference between the TT and the UT1 time scales on 'jd' TT-UT1 (seconds).
 * @param time_zone The time zone of the observer, west positive (hours).
 * @param object The object code; 0 = Sun, 1 = Moon.
 * @param twilight The twilight code; 0 = civil, 1 = nautical, 2 = astronomical.
 * @param message  Indicates special condition for the date of interest.
                   = ' ' ... no special message;
                   = 'a' ... body continuously above horizon;
                   = 'b' ... body continuously below horizon.
 * @param num_phen The number of phenomena found on the date of interest.
 * @param phen Structure containing all phenomena data for the date of interest.
 * @return Success status of operation
 *         =  0 ... everything OK
 *         =  1 ... invalid input value of 'object'
 *         =  2 ... invalid input value of 'twilight'
 *         > 10 ... error from function 'set_interval' + 10
 *         > 20 ... error from function 'phenom_search' + 20
 */
short int rsttwi (long int jd, observer *location, double delta_t,
                  double time_zone, char object, char twilight,
                  char *message, short int *num_phen, 
                  phenomenon phen[6])

{
   static short int last_obj = -1;
   short int error = 0;
   short int body, start_index, n_zd_c, interval, i;

   static long int last_jd = 0;

   static double t[3], ra[3], dec[3], dis[3];
   double ra_int[3] = {0.0, 0.0, 0.0};
   double zd_crit[2] = {0.0, 0.0};
   double obs_horizon = 90.56666666666667;
   double radius, loc_jd, jd_ut, jd_tt, tjd;

   /*
      Check object designation and set 'body' code (body = 10 is Sun;
      body = 11 is Moon).
   */

   switch (object)
   {
      case 0:
         body = 10;
         radius = SUNR;
         break;

      case 1:
         body = 11;
         radius = MOONR;
         break;

      default:
         return (error = 1);
   }

   /*
      Check for valid twilight code.
   */

   if ((twilight < 0) || (twilight > 2))
      return (error = 2);

   /*
      Compute a "local Julian date" corresponding to 0h local time.
   */

   loc_jd = (double) jd - 0.5;

   /*
      Compute the real Julian date on the UT time scale corresponding
      to 'loc_jd'.
   */

   jd_ut = loc_jd + (time_zone / 24.0);

   /*
      Compute the real Julian date on the TT time scale.
   */

   jd_tt = jd_ut + (delta_t / 86400.0);

   /*
      Compute equatorial spherical coordinates at 0h, 12h, and 24h local
      time on the day of interest.
   */

   if (((jd - 1L) != last_jd) || (object != last_obj))
   {
      for (i = 0; i < 3; i++)
         t[i] = (double) i * 12.0;

      start_index = 0;
   }
    else
   {
      ra[0] = ra[2];
      dec[0] = dec[2];
      dis[0] = dis[2];

      start_index = 1;
   }

   for (i = start_index; i < 3; i++)
   {
      tjd = ((double) (i) * 0.5) + jd_tt; 
      applan (tjd,body, &ra[i],&dec[i],&dis[i]);
   }

   /*
      Create an array of right ascension suitable for interpolation - make
      sure that the r.a. is continuous (i.e. no discontinuity over
      23h - 0h).
   */

   for (i = 0; i < 3; i++)
   {
      ra_int[i] = ra[i];
      if (i > 0)
        if (ra_int[i] < ra_int[i-1])
           ra_int[i] += 24.0;
   }

   /*
      Set the basic critical zenith distance that defines rise or set.
      This includes the zenith distance of the geometric horizon and
      standard refraction.  The corrections for parallax and
      semidiameter will be taken into account in function 'phenom_search'.
   */

   zd_crit[0] = obs_horizon;

   /*
      Set the critical zenith distance (in degrees) that defines
      twilight.  Also set the number of critical zenith distances
      to be checked: Sun, 2 (rise/set and twilight); Moon, 1 (rise/set).
   */

   if (body == 10)
   {
      zd_crit[1] = 90.0 + ((double) (twilight + 1) * 6.0);
      n_zd_c = 2;
   }
    else
   {
      zd_crit[1] = zd_crit[0];
      n_zd_c = 1;
   }

   /*
      Set the interval size in minutes.
   */

   if ((error = set_interval (0,body,(twilight+1),location->latitude,
      zd_crit[0],dec[1], &interval)) != 0)
   {
      return (error + 10);
   }

   /*
      Check each interval of the day for the occurrence of phenomena.
   */

   if ((error = phenom_search (jd_ut,location,time_zone,radius,t,
      ra_int,dec,dis,zd_crit,n_zd_c,interval, num_phen,message,
      phen)) != 0)
   {
      return (error + 20);
   }

   /*
     Set the values of 'last_jd' and 'last_obj'.
   */

   last_jd = jd;
   last_obj = object;

   return (error);
}

/**
 * Performs a search for all rise/set/transit/twilight phenomena
 * occuring on a specified day.  The time of each phenomena is
 * computed.
 *
 * @param jd_ut The Julian date (UT) corresponding to 0h local time on the day of interest.
 * @param location A structure providing the location of the observer.
 * @param time_zone The time zone of the observer, west positive (hours).
 * @param radius The radius of the body of interest (meters).
 * @param d_t Array of times: 0h, 12h, and 24h local time on the date of interest.
 * @param d_ra Array of right ascensions at 0h, 12h, and 24h local time on the date of interest (hours).
 * @param d_dec Array of declinations at 0h, 12h, and 24h local time on the date of interest (degrees).
 * @param d_dis Array of distances from center of Earth to center of object at 0h, 12h, and 24h local time on the date of interest (AU).
 * @param zd_crit Array of (maximum) 2 critical zenith distances taht define phenomena (degrees). The first element is used to define rise/set; the second is used to define twilight.
 * @param num_zd_c The number of critical zenith distances in 'zd_crit[]' that will be used (Sun = 2, Moon = 1).
 * @param interval_min The time step to be used in the phenomena search (minutes). Must be a factor of 1440 (total minutes in a day).
 * @param num_phen The number of phenomena found on the date of interest.
 * @param message Indicates special condition for the date of interest.
 *        = ' ' ... no special message;
 *        = 'a' ... body continuously above horizon;
 *        = 'b' ... body continuously below horizon.
 * @param phen_data Structure containing all phenomena for the date of interest.
 * @return Status
 *         =     0 ... everything OK
 *         =     1 ... input 'interval_min' is not a factor of 1440.
 *         = 10-29 ... error from function 'interp2' + 10 or 20;
 *                     10 indicates error interpolating right ascension,
 *                     20 indicates error interpolating declination.
 *         = 30-39 ... error from function 'inv_int' + 30.
 */
short int phenom_search (double jd_ut, observer *location,
                        double time_zone, double radius, double d_t[3], 
                        double d_ra[3], double d_dec[3], 
                        double d_dis[3], double zd_crit[2],
                        short int num_zd_c, short int interval_min,
                        short int *num_phen, char *message,
                        phenomenon phen_data[6])
{
   short int r_count = 0;
   short int s_count = 0;
   short int j = -1;
   short int error = 0;
   short int minute, i, sign_1, sign_2, sign_3;

   double h_t[3] = {0.0, 0.0, 0.0};
   double h_ra[3] = {0.0, 0.0, 0.0};
   double h_dec[3] = {0.0, 0.0, 0.0};
   double h_dis[3] = {0.0, 0.0, 0.0};
   double jd[3] = {0.0, 0.0, 0.0};
   double last[3] = {0.0, 0.0, 0.0};
   double loc_ha[3] = {0.0, 0.0, 0.0};
   double l_ha[3] = {0.0, 0.0, 0.0};
   double zd[3] = {0.0, 0.0, 0.0};
   double zd_c_test[3] = {0.0, 0.0, 0.0};
   double tol = 1.0e-5;
   double interval_hrs, zd_c[2], jd_loc, sd, ehp, zd_c_rs, test_1, 
      test_2, t_phen, ra_phen, dec_phen, dis_phen, last_phen, 
      loc_ha_phen, z_tran, az_tran;

   /*
      Check to make sure that the interval in minutes is a factor of 1440.
   */

   if ((1440 % interval_min) != 0)
   {
      return (error = 1);
   }
   
   /*
      Compute the hourly interval corresponding to the input search
      interval in minutes.
   */

   interval_hrs = (float) interval_min / 60.0;

   /*
      Compute a "local Julian date" corresponding to 0h local time.
   */

   jd_loc = jd_ut - (time_zone / 24.0);

   /*
      Loop through the day at the specified interval in minutes.
   */

   for (minute = 0; minute < 1440; minute += interval_min)
   {

      /*
         Compute equatorial coordinates, the local hour angle of the body,
         and the zenith distance of the body, all at the time of interest.
      */

      if (minute == 0)
      {
         h_t[0] = 0.0;
         h_ra[0] = d_ra[0];
         h_dec[0] = d_dec[0];
         h_dis[0] = d_dis[0];
         jd[0] = jd_ut;
         last[0] = st (jd_ut,location->longitude);
         loc_ha[0] = lha (d_ra[0],last[0]);
         zd[0] = z_dist (h_dec[0],loc_ha[0],location->latitude);
      }
       else
      {
         h_t[0] = h_t[2];
         h_ra[0] = h_ra[2];
         h_dec[0] = h_dec[2];
         h_dis[0] = h_dis[2];
         jd[0] = jd[2];
         last[0] = last[2];
         loc_ha[0] = loc_ha[2];
         zd[0] = zd[2];
      }

      /*
         Compute equatorial coordinates, the local hour angle of the body,
         and the zenith distance of the body, all one time-step (interval)
         later.
      */

      h_t[2] = h_t[0] + interval_hrs;

      if (minute == (1440 - interval_min))
      {
         h_ra[2] = d_ra[2];
         h_dec[2] = d_dec[2];
         h_dis[2] = d_dis[2];
      }
       else
      {
         if ((error = interp2 (h_t[2],d_t,d_ra, &h_ra[2])) != 0)
            return (10 + error);
         if ((error = interp2 (h_t[2],d_t,d_dec, &h_dec[2])) != 0)
            return (20 + error);
         if ((error = interp2 (h_t[2],d_t,d_dis, &h_dis[2])) != 0)
            return (20 + error);
      }

      jd[2] = jd_ut + h_t[2] / 24.0;
      last[2] = st (jd[2],location->longitude);
      loc_ha[2] = lha (h_ra[2], last[2]);
      zd[2] = z_dist (h_dec[2],loc_ha[2],location->latitude);

      /*
         Check for occurrence of a rise, set, beginning of twilight, or end
         of twilight within the interval.
      */

      for (i = 0; i < num_zd_c; i++)
      {

      /*
         For rise/set phenomena, compute the critical zenith distances each
         time-step.  For twilight, the critical zenith distances at each
         time-step is defined.
      */

      if (i == 0)
      {
         sd = semidi (h_dis[0],radius);
         ehp = eh_par (h_dis[0]);
         zd_c_test[0] = zd_crit[i] + sd - ehp;

         sd = semidi (h_dis[2],radius);
         ehp = eh_par (h_dis[2]);
         zd_c_test[2] = zd_crit[i] + sd - ehp;

         zd_c_rs = zd_c_test[0];
      }
       else
      {
         zd_c_test[0] = zd_crit[i];
         zd_c_test[2] = zd_crit[i];
      }

         /*
           Compare computed zenith distances of the body at the beginning of
           the current interval and the beginning of the next interval with the
           critical zenith distance that defines the phenomenon.
         */

         test_1 = zd[0] - zd_c_test[0];
         test_2 = zd[2] - zd_c_test[2];

         sign_1 = sign (test_1);
         sign_2 = sign (test_2);

         /*
            If the signs of the test differences are not the same, a phenomenon
            has occurred within the interval.
         */

         if (sign_1 != sign_2)
         {
            j++;
            phen_data[j].interval = interval_min;
            /*
               Determine the type of phenomenon.
               R = rise, S = set, B = beginning of twilight, E = end of twilight.
            */

            if ((sign_1 > 0) && (sign_2 < 0))
            {
               if (i == 0)
               {
                  phen_data[j].type = 'R';
                  r_count++;
               }
                else
                  phen_data[j].type = 'B';
            }
             else
            {
               if (i == 0)
               {
                  phen_data[j].type = 'S';
                  s_count++;
               }
                else
                  phen_data[j].type = 'E';
            }

            /*
               Refine the critical zenith distance that will be used to interpolate
               the time of the phenomenon.  For rise/set, do linear interpolation to
               find the zenith distance corresponding to the zero in the 'test'
               values.  For twilight, the critical zenith distance is defined.
            */

            if (i == 0)
            {
               zd_c[i] = (test_2 / (test_2 - test_1)) * 
                  (zd_c_test[0] - zd_c_test[2]) + zd_c_test[2];
            }
             else
               zd_c[i] = zd_crit[1];

            /*
               Compute ephemeris quantities, etc. at the middle of the interval.
               These are required for inverse interpolation.
            */

            h_t[1] = h_t[0] + (0.5 * interval_hrs);
            if ((error = interp2 (h_t[1],d_t,d_ra, &h_ra[1])) != 0)
               return (10 + error);
            if ((error = interp2 (h_t[1],d_t,d_dec, &h_dec[1])) != 0)
               return (20 + error);
            jd[1] = jd_ut + h_t[1] / 24.0;
            last[1] = st (jd[1],location->longitude);
            loc_ha[1] = lha (h_ra[1], last[1]);
            zd[1] = z_dist (h_dec[1],loc_ha[1],location->latitude);

            /*
               Inverse-interpolate to find the phenomenon time - the time
               at which the body is at the critical zenith distance.
            */

            error = inv_int (zd_c[i],h_t,zd,tol, &t_phen);
            phen_data[j].warning = error;
            if (error != 0)
            {
               if (error == 2)
                  error = 0;
                else
                  return (30 + error);
            }

            phen_data[j].jd = jd_ut + t_phen / 24.0;
            phen_data[j].loc_jd = jd_loc + t_phen / 24.0;

            /*
               Compute the azimuth of the body at the time of the phenomenon.
            */

            if ((error = interp2 (t_phen,d_t,d_ra, &ra_phen)) != 0)
               return (10 + error);
            if ((error = interp2 (t_phen,d_t,d_dec,&dec_phen)) != 0)
               return (20 + error);

            last_phen = st (phen_data[j].jd,location->longitude);
            loc_ha_phen = lha (ra_phen,last_phen);
            phen_data[j].angle = azimuth (dec_phen,loc_ha_phen,
               location->latitude);
            phen_data[j].tran_dir = ' ';
         }
      }

      /*
         Now check for occurrence of a transit within the interval.
      */

      if (loc_ha[2] < loc_ha[0])
      {
         j++;
         phen_data[j].interval = interval_min;
         phen_data[j].type = 'T';

         /*
            Compute ephemeris quantities, etc. at the middle of the interval.
            These are required for inverse interpolation.
         */

         h_t[1] = h_t[0] + (0.5 * interval_hrs);
         if ((error = interp2 (h_t[1],d_t,d_ra, &h_ra[1])) != 0)
            return (10 + error);
         if ((error = interp2 (h_t[1],d_t,d_dec, &h_dec[1])) != 0)
            return (20 + error);
         jd[1] = jd_ut + h_t[1] / 24.0;
         last[1] = st (jd[1],location->longitude);
         loc_ha[1] = lha (h_ra[1], last[1]);

         /*
            Make sure that the local hour angles are continuous (i.e. no dis-
            continuity over 360 deg - 0 deg).
         */

         for (i = 0; i < 3; i++)
         {
            l_ha[i] = loc_ha[i];
            if (i > 0)
               if (l_ha[i] < l_ha[i-1])
                  l_ha[i] += 360.0;
         }
         
         /*
            Inverse-interpolate to find the time of transit - the time
            at which the body is at a local hour angle of 0 (or 360) degrees.
         */

         error = inv_int (360.0,h_t,l_ha,tol, &t_phen);
         phen_data[j].warning = error;
         if (error != 0)
         {
            if (error == 2)
               error = 0;
             else
               return (30 + error);
         }

         phen_data[j].jd = jd_ut + t_phen / 24.0;
         phen_data[j].loc_jd = jd_loc + t_phen / 24.0;

         /*
            Compute the topocentric altitude of the body at the time of transit.
            First, correct for parallax.  Then, correct for refraction if the
            body is on or above the horizon.  Altitude of the horizon is
            based on standard value of 34 arcminutes.
         */

         if ((error = interp2 (t_phen,d_t,d_ra, &ra_phen)) != 0)
            return (10 + error);
         if ((error = interp2 (t_phen,d_t,d_dec,&dec_phen)) != 0)
            return (20 + error);
         if ((error = interp2 (t_phen,d_t,d_dis,&dis_phen)) != 0)
            return (20 + error);

         z_tran = z_dist (dec_phen,0.0,location->latitude);
         phen_data[j].angle = 90.0 - (z_tran + 
            par_zd (z_tran,dis_phen));
         if (phen_data[j].angle >= -0.5666666666666667)
            phen_data[j].angle = phen_data[j].angle +
               refract (phen_data[j].angle);

         /*
            Compute the azimuth of the body at the time of the transit. Use this
            to determine the direction along the horizon (N or S) to the body at
            time of transit.
         */

         if (phen_data[j].angle == 90.0)
            phen_data[j].tran_dir = ' ';
          else
         {
            last_phen = st (phen_data[j].jd,location->longitude);
            loc_ha_phen = lha (ra_phen,last_phen);
            az_tran = azimuth (dec_phen,loc_ha_phen,location->latitude);

            if ((az_tran < 0.5) || (az_tran > 359.5))
               phen_data[j].tran_dir = 'N';
             else if ((az_tran < 180.5) && (az_tran > 179.5))
               phen_data[j].tran_dir = 'S';
             else
               phen_data[j].tran_dir = ' ';
         }
      }
   }

   /*
      Set the number of phenomena found during the day.
   */

   *num_phen = j + 1;

   /*
      Determine if the body was continually above ('a') or below ('b') the
      horizon throughout the day.
   */

   if ((r_count == 0) && (s_count == 0))
   {
      sign_3 = sign (zd_c_rs - zd[0]);

      if (sign_3 >= 0)
         *message = 'a';
       else
         *message = 'b';
   }
    else
      *message = ' ';

   return (error);
}
