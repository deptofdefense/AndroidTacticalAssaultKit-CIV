
#include "deltat.h"

/**
 * To compute an approximate value of "Delta T" (TT - UT1).  Delta T
 * is based on a model, which is a polynomial fit to determinations
 * (past) and predictions (future).  A default model is coded in
 * this function, but if an external file ('dt_file_name') is
 * found, the model contained in that file is used.
 *
 * NOTES:
 *     1. The Delta-T model is expressed as a polynomial of form:
 *           Delta-T (sec) = a[0] + a[1]* t + ... + a[5]*t^5
 *     2. In this function, the default model was obtained by fitting a
 *     a 3rd degree polynomial to USNO/EO Delta T determinations
 *     1969.915-2009.085 and USNO/EO Delta T predictions from the latter
 *     date until 2050.0.  The polynomial fits the data and predictions
 *     with a maximum error of 2.3 s (absolute value). The RMS error of
 *     the fit is 0.6 s.  Of course, the errors in the Delta T predicted
 *     values themselves also grow with time.  The range of validity has
 *     been set to 1970.0 to 2050.0.
 *     3. The default model can be "overridden" by a model contained in
 *     an external file ('dt_file').  The external file has the following
 *     format:
 *     Record 1: 4-digit year, 2-digit month, 2-digit day, 2-digit hour,
 *               2-digit minute, seconds (all integers except seconds,
 *               which is a double) specifying the starting date at which
 *               the model is valid
 *     Record 2: Same as Record 1, but specifying the ending date at
 *               which the model is valid
 *     Record 3: Coefficient a[0] (double)
 *     Record 4: Coefficient a[1] (double)
 *       ...              ...
 *     Record 8: Coefficient a[5] (double)
 *
 * @param dt_file_name Name of the external file containing the Delta T model.  See
 *                     specifications in 'Notes' below.  If this file does not exist
 *                     or cannot be properly read, the default model is used.  Thus,
 *                     to use the default model, simply enter a blank string.
 * @param tjd Julian date.
 * @param delta_t Difference Delta T = TT-UT1 in seconds.
 * @return Integer value conveying the type of model used
 *         =  0 ... default model used
 *         =  1 ... model contained in external file used.
 *         = 90 ... default model used, but input 'tjd' is outside the
 *                  range of the polynomial.
 *         = 91 ... model contained in the external file used, but input
 *                  'tjd' is outside the range of the polynomial.
 *         In the last two cases, the returned value of 'delta_t' is an
 *         extrapolation; i.e., the polynomial is evaluated outside its
 *         range of validity.
 */
short int deltat (char dt_file_name[], double tjd, double *delta_t)
{   
   static short int first_call = 1;
   static short int use_file;
   const short int degree = 5;
   short int year, month, day, hour, minute, i, status;

   static double tjd_min;
   static double tjd_max;
   static double a[6];
   double second, file_jd, x[6], jd_low, jd_high, fy, t;
   
   FILE *dt_file;

   /*
      On the first call to this function, the Delta-T model parameters are
      obtained and stored.  Model parameters are obtained from file
      'dt_file_name' if the file exists.  If the file doesn't exist a
      default model described in the prolog is used.
   */

   if (first_call)
   {
   
      /*
         Set up the default Delta_t model.
      */
      tjd_min = 2440586.5;
      tjd_max = 2469807.5;

      a[0] =   62.93054199666036;
      a[1] =   46.82214574872042;
      a[2] = - 50.91899736748310;
      a[3] =  157.1562405767032;
      a[4] =    0.0;
      a[5] =    0.0;

      /*
         Attempt to open the external file 'dt_file_name' containing the
         Delta-T model.
      */

      if ((dt_file = fopen (dt_file_name, "r")) == NULL)
      {
      
         /*
            The file does not exist.
         */

         use_file = 0;
      } else
      {
      
         /*
            The file exists; attempt to read its values.  First, read the dates
            that give the valid range of the polynomial, then read the polynomial
            coefficients.
         */

         use_file = 1;
         
         for (i = 1; i <= (degree + 3); i++)
         {
            switch (i)
            {
               case 1:
               case 2:
                  if ((fscanf (dt_file, " %hd %hd %hd %hd %hd %lf ", 
                        &year, &month, &day, &hour, &minute, 
                        &second)) != 6)
                  {
                     use_file = 0;
                  }
                   else
                  {
                     file_jd = cd2jd (year,month,day,hour,minute,second);
                   
                     if (i == 1)
                        jd_low = file_jd;
                      else if (i == 2)
                        jd_high = file_jd;
                  }
                  break;
                                          
               default:
                  if ((fscanf (dt_file, " %lf ", &x[i-3])) != 1)
                     use_file = 0;
                  break;
            }
            
            if (use_file == 0)
               break;
         }
         
         fclose (dt_file);
      }

      /*
         If the external file exists and was read, set up and store the
         Delta-T model parameters.
      */
         
      if (use_file)
      {
         tjd_min = jd_low;
         tjd_max = jd_high;
         
         for (i = 0; i <= degree; i++)
         {
            a[i] = x[i];
         }
      }
                  
      first_call = 0;
   }

   /*
      Compute the value of Delta T based on the model parameters set
      on the first call to this function.

      If the input Julian date is out of range, set the status flag to
      indicate this is the case, but evaluate the polynomial anyway.
   */

   status = use_file;
   
   if ((tjd < tjd_min) || (tjd > tjd_max))
      status += 90;
      
   jd2fyr (tjd, &fy);
   t = (fy - 2000.0) / 100.0;
   
   *delta_t = eval_poly (a,degree,t);
   
   return (status);
}
