
#include "rslib.h"
#include "smeph.h"

/**
 * To compute the equitorial horizontal parallax of an object.
 *
 * @param r Distance from the center of Earth to the center of object (AU).
 * @return Equatorial horizontal parallax angle (degrees).
 */
double eh_par (double r)
{
   double ehp;

   ehp = asin (EARTHR / (r * AU)) * RADDEG;
   
   return ehp;
}

/**
 * To compute the approximate parallax in zenith distance of an object assuming a spherical Earth.
 *    NOTES:
 *     1. The neglect of the Earth oblateness can produce an error of
 *     up to 0.2 arcmin [see Hohenkerk, et al. (1992) in Explanatory
 *     Supplement to the Astronomical Almanac, p. 125].
 *
 * @param z Zenith distance of the object, referred to the center of the Earth (degrees).
 * @param r Distance from the center of the Earth to the center of the object (AU).
 * @return Parallax in zenith distance (degrees).
 */
double par_zd (double z, double r)
{
   double ehp, parzd;

   /*
      Compute the equatorial horizontal parallax.
   */

   ehp = eh_par (r) * DEGRAD;

   /*
      Compute the parallax in zenith distance.
   */

   parzd = asin (sin (ehp) * sin (z * DEGRAD)) * RADDEG;

   return (parzd);
}


/**
 * To compute the apparent semidiameter (angular radius) of a solar system object's disk.
 *
 * @param r Distance from observer to the center of the object (AU).
 * @param rp Radius of the object (meters).
 * @return Semidiameter of the object (degrees).
 */
double semidi (double r, double rp)
{
   double sins, sd;
   
   sins = rp / (r * AU);
   sd = asin (sins) * RADDEG;

   return sd;
}

/**
 * To compute the angle of refraction for standard conditions at sea level,
 * given a geometeric (computed) altitude.
 *    NOTES:
 *     1. This function is based on Fortran subroutine REFR given in the
 *     first reference.  The basic formula was determined by the authors
 *     of the first reference by modifying formula G of the second
 *     reference to allow input of a geometric altitude instead of
 *     an observed altitude.
 *     2. Standard conditions are T = 10 C; P = 1010 mb.
 *     3. Formula is valid for observed altitudes in the range 0-90
 *        degrees.
 *
 * @param alt_geom Geometeric (computed) altitude (degrees).
 * @return Refraction angle (degrees).
 */
double refract (double alt_geom)
{
   double arg,rm;

   arg = alt_geom + (8.6 / (alt_geom + 4.42));
   rm = 1.0 / tan (arg * DEGRAD);

   return (rm / 60.0);
}

/**
 * To compute the local apparent sidereal time.
 *
 * @param tjd Julian date (UT).
 * @param lon Longitude (degrees).
 * @return Local apparent sidereal time (radians).
 */
double st (double tjd, double lon)
{
   double t,gmst,mst,dpsi,etrue,ee,last;

   /*
      Compute the date in Julian centuries.
   */
   t = (tjd - T0) / 36525.0;

   /*
      Compute Greenwich mean sidereal time in seconds.
   */

   gmst = (-6.2e-06 * t * t * t) + (0.093104 * t * t)
      + 67310.54841 + (8640184.812866 * t)
      + (3155760000.0 * t);

   /*
      Compute local mean sidereal time in radians.
   */

   mst = (gmst / 240.0 + lon) * DEGRAD;

   /*
      Compute equation of the equinoxes.
   */

   nutation ((t / 100.0), &dpsi,&etrue);
   ee = dpsi * cos (etrue);

   /*
      Compute local apparent sidereal time in radians.
   */

   last = mst + ee;
   last = fmod (last,TWOPI);
   if (last < 0.0)
      last += TWOPI;

   return (last);
}

/**
 * To compute the local apparent hour angle of an object.
 *
 * @param ra Apparent right ascension of object (hours).
 * @param last Local apparent sidereal time (radians).
 * @return Local hour angle of the object, measured westward along the
 *         celestial equator from the observer's meridian to the hour
 *         circle containing the object (degrees).
 */
double lha (double ra, double last)
{
   double h;

   /*
      Compute local hour angle in degrees.
   */

   h = (last * RADDEG) - (ra * 15.0);
   h = fmod (h,360.0);
   if (h < 0.0)
      h += 360.0;

   return (h);
}

/**
 * To compute the azimuth (measured east from the north point) of an object.
 *
 * @param dec Declination (degrees).
 * @param h Local apparent hour angle (degrees).
 * @param lat Latitude of observer (degrees).
 * @return Azimuth of the object, measured eastward from the North point (degrees).
 */
double azimuth (double dec, double h, double lat)
{
   double lat_r, h_r, dec_r, num, den, az;

   /*
      Calculate the azimuth, except in circumstances where the function
      is ill-conditioned.
   */

   if (dec == 90.0)
      az = 0.0;

    else if (dec == -90.0)
      az = 180.0;

    else
    {
      lat_r = lat * DEGRAD;
      h_r = h * DEGRAD;
      dec_r = dec * DEGRAD;

      num = -sin (h_r);
      den = tan (dec_r) * cos (lat_r) - cos (h_r) * sin (lat_r);

      if (den == 0.0)
         az = 0.0;
       else
         az = atan2 (num,den) * RADDEG;

      az = fmod (az,360.0);
      if (az < 0.0)
         az += 360.0;
    }
 
   return (az);
}

/**
 * To compute the zenith distance of an object.
 *
 * @param dec Declination (degrees).
 * @param h Local apparent hour angle (degrees).
 * @param lat Latitude of observer (degrees).
 * @return Zenith distance of the object (degrees).
 */
double z_dist (double dec, double h, double lat)
{
   double lat_r, h_r, dec_r, zd;

   /*
      Preliminaries.
   */

   lat_r = lat * DEGRAD;
   h_r = h * DEGRAD;
   dec_r = dec * DEGRAD;

   /*
      Compute zenith distance.
   */

   zd = acos (sin (dec_r) * sin (lat_r) + cos (dec_r) * cos (h_r) *
      cos (lat_r)) * RADDEG;

   return (zd);
}

/**
 * Sets the time step (interval) used in the search for phenomena.
 *
 * @param step_opt Option for determining the time step.
 *                 = 0 ... set interval using algorithm
 *                 = 1 ... set interval to "small" value
 *                 = 2 ... set interval to "regular" value
 * @param body Body identification number for desired object.
 *             = 10 ... Sun
 *             = 11 ... Moon
 * @param twi_opt A code specifying the type of twilight desired.
 *                = 0 ... no twilight
 *                = 1 ... civil twilight
 *                = 2 ... nautical twilight
 *                = 3 ... astronomical twilight
 * @param lat Latitude (either astronomic or geodetic) of the observer in
 *            decimal degrees.
 * @param zd_hor Zenith distance of the horizon (degrees).
 * @param dec Declination of the object (degrees).
 * @param interval Time step to be used in the search for phenomena (minutes).
 * @return Status
 *         = 0 ... everything OK
 *         = 1 ... invalid value of 'step_opt'.
 */
short int set_interval (short int step_opt, short int body, 
                        short int twi_opt, double lat, double zd_hor, 
                        double dec, short int *interval)

{

   /*
      There are two possible time steps, 'small' and 'regular'.  They are
      defined in units of minutes.
   */

   static short int lastwi = 999;  
   static short int lastbod = 999;  
   short int small_time_step = 4;
   short int regular_time_step = 60;
   short int error = 0;

   /*
      'tol_slow' and 'tol_fast' are the basic test tolerances, in degrees,
      for a slow-moving object (Sun) and a fast-moving object (Moon).
      They establish an angular range above and below the horizon.
   */

   double tol_slow = 2.0;
   double tol_fast = 5.0;
   static double upper, lower;
   double twifac, zuc, zlc, ztest1, ztest2;

   /*
      Determine the time step based on the value of 'step_opt'.
   */

   switch (step_opt)
   {

      /*
         Set the time step by algorithm.  First, set the upper and
         lower test limits (number of degrees above and below the
         horizon) depending upon the object.  The basic test tolerances
         are adjusted for twilight, if necessary.
      */

      case 0:

         if ((twi_opt != lastwi) || (body != lastbod))
         {
            switch (body)
            {
               case 10:                              /* Sun */
                  twifac = 6.0 * (double) twi_opt;
                  upper = tol_slow;
                  lower = -(tol_slow + twifac);
                  break;

               case 11:                              /* Moon */
                  upper = tol_fast;
                  lower = -tol_fast;
                  break;

               default:                              /* Other */
                  upper = tol_slow;
                  lower = -tol_slow;
            }
          
            lastwi = twi_opt;
            lastbod = body;
         }

         /*
            Compute the zenith distances of the object at upper and lower
            culmination, respectively.
         */

         if (lat >= 0.0)
         {
            zuc = lat - dec;
            zlc = 180.0 - lat - dec;
         }
          else
         {
            zuc = dec - lat;
            zlc = 180.0 + lat + dec;
         }

         if (zuc < 0.0) 
            zuc = fabs (zuc);

         if (zlc > 180.0) 
            zlc = 360.0 - zlc;

         /*
            Compute the test conditions that determine whether or not the
            object can come close to grazing the horizon.
         */

         ztest1 = zd_hor - zuc;
         ztest2 = zd_hor - zlc;

         /*
            Compare the test conditions (distance above/below the horizon at
            upper and lower culminations) to the limits.
         */

         if (((ztest1 <= upper) && (ztest1 >= lower)) ||
             ((ztest2 <= upper) && (ztest2 >= lower))) 
               *interval = small_time_step;
             else
               *interval = regular_time_step;

         break;

      /*
         Set the 'small' interval.
      */

      case 1:
         *interval = small_time_step;
         break;

      /*
         Set the 'regular' interval.
      */

      case 2:
         *interval = regular_time_step;
         break;

      /*
         Invalid input value of 'step_opt'.
      */

      default:
         *interval = 0;
         error = 1;

   }
      
   return (error);
}

/**
 * To use second-degree Newton interpolation to compute the value of a
 * tabular function f(t) at a given time.
 *
 * @param t0 Time at which value of the function is desired.
 * @param t Array of values of the independent variable at which the
 *          value of the function is known.
 * @param f Array giving the values of the function at times 't'.
 * @param f_at_t0 Value of the function f(t) at 't0'.
 * @return Status
 *         = 0 ... Everything OK.
 *         = 1 ... Value of 't0' does not lie within range t[0] to t[2].
 */
short int interp2 (double t0, double t[3], double f[3], double *f_at_t0)
{
   double a, b, c, d;

   /*
      Make sure 't0' lies between t[0] and t[2].
   */

   if (((t0 < t[0]) && (t0 < t[2])) || 
       ((t0 > t[0]) && (t0 > t[2])))
      return (1);

   /*
      Compute divided differences.
   */

   a = (f[1] - f[0]) / (t[1] - t[0]);
   b = (f[2] - f[1]) / (t[2] - t[1]);
   c = (b - a) / (t[2] - t[0]);

   /*
      Compute the value of function 'f' at 't0'.
   */

   d = t0 - t[1];
   *f_at_t0 = f[1] + (d * b) + (d * (t0 - t[2]) * c);

   return (0);
}

/**
 *  To compute the time at which a tabular function f(t) has a given value.
 *
 * @param f_at_t0 Value of the function f(t) at 't0'.
 * @param t Array of values of the independent variable at which the
 *          value of the function is known.
 * @param f Array giving the values of the function at times 't'.
 * @param tolerance The precision to which the time 't0' is needed, in the same
 *                  units as the 't' array.
 * @param t0 Time at which the function f(t) has a value 'f_at_t0'.
 * @return Status
 *         = 0    ... Everything OK.
 *         = 1    ... Value of 'f_at_t0' does not lie between f[0] and
 *                    f[2].
 *         = 2    ... Process failed to converge after 'max_iter'
 *                    iterations.
 *         = 30 + ... Error from 'interp2' (30 + 'interp2' error code).
 */
short int inv_int (double f_at_t0, double t[3], double f[3], double tolerance, double *t0)
{
   short int max_iter = 50;
   short int error = 0;
   short int n = 0;

   double ta, tb, fa, fb, ti, fi, f_last;

   /*
      Make sure 'f_at_t0' lies between f[1] and f[2].
   */

   if (((f_at_t0 < f[0]) && (f_at_t0 < f[2])) || 
       ((f_at_t0 > f[0]) && (f_at_t0 > f[2])))
      return (1);

   /*
      Begin iterative procedure.  Use linear inverse interpolation to get
      estimate of 't0' ('ti') given 'f_at_t0'.  Then, do direct
      interpolation to find the value of 'f' ('fi') at 'ti'.  Use 'fi' in
      linear inverse interpolation to find a new value of 'ti'.  Repeat
      until process converges.
   */

   do
   {
      if (++n > max_iter)
         return (2);
       else
      {

         /*
            Determine values to be used for linear inverse interpolation.
         */

         if (n == 1)
         {
            ta = t[0];
            tb = t[2];
            fa = f[0];
            fb = f[2];
         }
          else
         {
            ta = ti;
            fa = fi;
            if (((f_at_t0 >= fi) && (f_at_t0 <= f[2])) ||
                ((f_at_t0 <= fi) && (f_at_t0 >= f[2])))
            {
               tb = t[2];
               fb = f[2];
            }
             else
            {
               tb = t[0];
               fb = f[0];
            }
         }

         /*
            Do linear inverse interpolation.
         */

         ti = (((f_at_t0 - fb) / (fa - fb)) * (ta - tb)) + tb;
   
         /*
            Do higher-order direct interpolation to find value of 'f' at 'ti'.
         */

         if (n == 1)
            f_last = f_at_t0;
          else
            f_last = fi;

         if ((error = interp2 (ti,t,f, &fi)) != 0)
            return (30 + error);
      }
   }
   while (fabs (fi - f_last) > tolerance);

   /*
      Once process converges, set the value of 't0'.
   */

   *t0 = ti;

   return (0);
}

/**
 * To determine the sign (positive, negative, or none) of a double precision number.
 *
 * @param a A double precision number
 * @return Value indicating the sign of the input value
 *         = +1 ... input number is positive.
 *         =  0 ... input number is exactly zero; no sign.
 *         = -1 ... input number is negative.
 */
short int sign (double a)
{
   short int int_sgn;

   if (a > 0.0)
      int_sgn = 1;
    else if (a < 0.0)
      int_sgn = -1;
    else if (a == 0.0)
      int_sgn = 0;

   return (int_sgn);
}
