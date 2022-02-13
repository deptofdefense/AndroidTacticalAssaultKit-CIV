
#include <time.h>
#include "helpers.h"

int MonthDays[13] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};

#define DEG_TO_RAD(degrees)       (((double)(degrees)) * 1.7453292519943295e-2)
#define RAD_TO_DEG(radians)       (((double)(radians)) * 57.295779513082322)

#define MIN_DATE   (-657434L)  // about year 100
#define MAX_DATE   2958465L    // about year 9999

// Half a second, expressed in days
#define HALF_SECOND  (1.0/172800.0)

#define JulianCentury       36525.0       /* Days in Julian century */
#define J2000          2451545.0       /* Julian day of J2000 epoch */
#define fixangle(a) ((a) - 360.0 * (floor((a) / 360.0)))  /* Fix angle */


/**
 * This function will compute the Julian date at the beginning
 * of a Julian day (12h UT) for a given Gregorian calendar
 * date (year, month, day).
 *
 * @param year Four-digit year.
 * @param month Two-digit month.
 * @param day Two-digit day-of-month.
 * @return Julian date at 12h UT on the input calendar date.
 */
long int juldat (short int year, short int month, short int day)
{
   long int jd12h;

   /*
      Compute the Julian date at the beginning of the Julian day.
   */

   jd12h = (long) day - 32075L + 1461L * ((long) year + 4800L
         + ((long) month - 14L) / 12L) / 4L
         + 367L * ((long) month - 2L - ((long) month - 14L) / 12L * 12L)
         / 12L - 3L * (((long) year + 4900L + ((long) month - 14L)
         / 12L) / 100L) / 4L;

   return (jd12h);
}

/**
 * This function will compute the calendar date (year, month, day,
 * hours and minutes) for a given Julian date.
 *
 * @param jd Julian date.
 * @param year Four-digit year.
 * @param month Two-digit month.
 * @param day Two-digit day.
 * @param hour Two-digit hour.
 * @param minute Two-digit minute (rounded)
 *
 */
void jdcd (double jd, short int *year, short int *month, short int *day,
           short int *hour, short int *minute)
{
   long int intjd, jd12h;
   short int test;
   double fracjd, dummy, secnds;

   /*
      Round 'jd' to the nearest integer (i.e. nearest 12h).  Then, break up
      'jd' into integer and fractional parts.
   */

   jd12h  = (long int) (jd + 0.5);
   intjd  = (long int) jd;
   fracjd = jd - (double) (intjd);

   /*
      Compute time of day from fractional part; add one-half day since
      Julian day and calendar day start 12 hours apart.
   */

   dummy  = fmod (((fracjd * 24.0) + 12.0), 24.0);
   *hour   = (short int) dummy;

   dummy  = (dummy - (double) (*hour)) * 60.0;
   *minute = (short int) dummy;

   secnds = (dummy - (double) (*minute)) * 60.0;

   /*
      Rectify output values; adjust calendar date taking into account
      rounding the seconds.
   */

   test = (short int) (secnds + 0.5);

   if (test >= 30)
      *minute += 1;

   if (*minute >= 60)
   {
      *minute = 0;
      *hour  += 1;
   }

   if (*hour >= 24)
   {
      *hour  = 0;
      jd12h += 1;
   }

   /*
      Compute calendar date.
   */

   caldat (jd12h, year,month,day);

   return;
}

/**
 * Converts a Julian date to a year, including the fractional part,
 * on the Gregorian calendar.
 *
 * @param jd Julian date.
 * @param fyear Year, including fractional part, on the Gregorian Calendar
 * @return 0 = Everything OK, 1 = Invalid input Julian date (must be > 0.0).
 */
short int jd2fyr (double jd, double *fyear)
{
   short int error = 0;
   short int cumday[2][13]=
      {{0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365},
       {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366}};
   short int year, month, day, lpyr;

   long int ijd_p;

   double daysinyr[2] = {365.0, 366.0};
   double jd_p, fjd_p;

   /*
      Set output to zero, and set error flag, if the input Julian date
      in invalid.
   */

   if (jd <= 0.0)
   {
      *fyear = 0.0;
      return (error = 1);
   }

   /*
      Increase the input Julian date by 0.5 day to facilitate the
      computations.
   */

   jd_p = jd + 0.5;

   /*
      Decompose 'jd_p' into integer and fractional parts.
   */

   ijd_p = (long int) jd_p;
   fjd_p = jd_p - (double) ijd_p;

   /*
      Compute Gregorian calendar date corresponding to 'ijd_prime'.
   */

   caldat (ijd_p, &year,&month,&day);

   /*
      Determine if the input year is a leap year.
   */

   lpyr = is_leap_year (year);

   /*
      Compute the year corresponding to the input Julian date.
   */

   *fyear = (double) year + 
      ((double) (cumday[lpyr][month-1] + day - 1) + fjd_p) / 
      daysinyr[lpyr];

   return (error);
}

/**
 * This function will compute the year, month, and day on the
 * Gregorian calendar given the (long int) Julian date at the
 * beginning of the Julian day.
 *
 * @param jd Julian date at beginning of Julian day.
 * @param year Four-digit year.
 * @param month Two-digit month.
 * @param day Two-digit day-of month.
 */
void caldat (long int jd, short int *year, short int *month, short int *day)
{
   long int k ,m, n;

   /*
      Compute the calendar date.
   */

   k     = jd + 68569;
   n     = 4 * k / 146097;

   k     = k - (146097 * n + 3) / 4;
   m     = 4000 * (k + 1) / 1461001;
   k     = k - 1461 * m / 4 + 31;

   *month = static_cast<short>(80 * k / 2447);
   *day   = static_cast<short>(k - 2447 * *month / 80);
   k      = *month / 11;

   *month = static_cast<short>(*month + 2 - 12 * k);
   *year  = static_cast<short>(100 * (n - 49) + m + k);

   return;
}

/**
 * This function will compute the Julian date for a given calendar
 * date (year, month, day, hours, minutes, and seconds).
 *
 * @param year Four-digit year.
 * @param month Two-digit month.
 * @param day Two-digit day-of-month.
 * @param hour Two-digit hour on 24-hour clock.
 * @param minute Two-digit minutes.
 * @param secnds Two-digit seconds.
 * @return Julian date.
 */
double cd2jd (short int year, short int month, short int day,
              short int hour, short int minute, double secnds)
{
   long int jd12h;

   double fday, jd;

   /*
      Compute the fractional part of the calendar day.
   */

   fday = ((((secnds / 60.0) + (double) (minute)) / 60.0)
              + (double) (hour)) / 24.0;

   /*
      Compute the Julian date at the beginning of the Julian day.
   */

   jd12h = (long) day - 32075 + 1461 * ((long) year + 4800
         + ((long) month - 14) / 12) / 4
         + 367 * ((long) month - 2 - ((long) month - 14) / 12 * 12)
         / 12 - 3 * (((long) year + 4900 + ((long) month - 14) / 12)
         / 100) / 4;

   /*
      Add together; subtract one-half day since the Julian day and the
      calendar day start 12 hours apart.
   */

   jd = (fday - 0.5) + (double) (jd12h);

   return (jd);
}

/**
 * To determine if a Gregorian calendar year is a leap year.
 *
 * @param year Year on the Gregorian calendar.
 * @return 0 = Year is not a leap year, 1 = Year is a leap year.
 */
short int is_leap_year (short int year)
{
   short int lpyr;

   /*
      Determine if the input year is a leap year.
   */

   if ((year % 4) == 0)
      lpyr = 1;
    else
      lpyr = 0;

   if ((year % 100) == 0)
   {
      if ((year % 400) == 0)
         lpyr = 1;
       else
         lpyr = 0;
   }

   return (lpyr);
}

/**
 * Evaluate a polynomial of degree 'degree' using Horner's rule.
 * The polynomial is in the form:
 * y = a[0] + a[1] * x + ... a[degree] * pow (x, degree)
 *
 * @param a Array of polynomial coefficients.
 * @param degree Degree of the polynomial
 * @param x Independent variable.
 * @return The value of the polynomial evaluated at 'x'.
 */
double eval_poly (double a[], short int degree, double x)
{
   short int i;
   
   double y;

   y = a[degree]; 

   for (i = (degree - 1); i >= 0; i--)
   {
      y = y * x + a[i];
   }

   return (y); 
}



/* The following functions were added for previous versions of SLAC, and were
*  added here again for SLAC v1.2.
*  These functions are not part of SLAC, but are part of the SLAC wrapper. They
*  are not called by SLAC, but are called ONLY by the SLAC wrapper (in SlacWrapper.cpp).
*  In addition, all of these functions are STAND ALONE and do not depend on anything
*  in the SLAC source code. Therefore, all four of these functions SHOULD BE MOVED into the
*  CSlacWrapper class so that they will not have to be added again for future SLAC upgrades.
*/


/**
 * Solves the kepler equation.
 *
 * @param m Mean anomaly.
 * @param ecc Eccentricity.
 * @return Eccentric anomaly.
 */
static double kepler(double m, double ecc)
{
   double e, delta;
#define EPSILON 1E-6

   e = m = DEG_TO_RAD(m);
   do {
      delta = e - ecc * sin(e) - m;
      e -= delta / (1 - ecc * cos(e));
   } while (fabs(delta) > EPSILON);
   return e;
}
//
//

/**
 * Calculates the obliquity of the ecliptic for a given Julian date. This uses Laskar's tenth-degree
 * polynomial fit (J. Laskar, Astronomy and Astrophysics, Vol. 157, page 68 [1986]) which is accurate
 * to within 0.01 arc second between AD 1000 and AD 3000, and within a few seconds of arc for +/-10000
 * years around AD 2000. If we're outside the range in which this fit is valid (deep time) we simply
 * return the J2000 value of the obliquity, which happens to be almost precisely the mean.
 *
 * @param jd Julian date.
 * @return Obliquity of the ecliptic.
 */
static double obliqeq(double jd)
{
#define Asec(x) ((x) / 3600.0)

   static double oterms[10] = {
      Asec(-4680.93),
      Asec(   -1.55),
      Asec( 1999.25),
      Asec(  -51.38),
      Asec( -249.67),
      Asec(  -39.05),
      Asec(    7.12),
      Asec(   27.87),
      Asec(    5.79),
      Asec(    2.45)
   };

   double eps = 23 + (26 / 60.0) + (21.448 / 3600.0), u, v;
   int i;

   v = u = (jd - J2000) / (JulianCentury * 100);

   if (fabs(u) < 1.0) {
      for (i = 0; i < 10; i++) {
         eps += oterms[i] * v;
         v *= u;
      }
   }
   return eps;
}

/**
 * Calculates Greenwich Mean Siderial Time for a given instant expressed as a Julian date and fraction.
 *
 * @param jd Julian date.
 * @return Greenwich Mean Siderial Time (GMST)
 */
double gmst(double jd)
{
   double t, theta0;

   // Time, in Julian centuries of 36525 ephemeris days, measured from the epoch 1900 January 0.5 ET
   t = ((floor(jd + 0.5) - 0.5) - 2415020.0) / JulianCentury;

   theta0 = 6.6460656 + 2400.051262 * t + 0.00002581 * t * t;

   t = (jd + 0.5) - (floor(jd + 0.5));

   theta0 += (t * 24.0) * 1.002737908;

   theta0 = (theta0 - 24.0 * (floor(theta0 / 24.0)));

   return theta0;
}
//
//


/**
 * Calculates position of the Sun. JD is the Julian date of the instant for which the position is desired
 * and APPARENT should be nonzero if the apparent position (corrected for nutation and aberration) is desired.
 * The Sun's coordinates are returned in RA and DEC, both specified in degrees (divide RA by 15 to obtain
 * hours). The radius vector to the Sun in astronomical units is returned in RV and the Sun's longitude (true
 * or apparent, as desired) is returned as degrees in SLONG.
 *
 * @param jd Julian date.
 * @param apparent Apparent position of the son.
 * @param ra Radian coordinates.
 * @param dec Decimal coordinates.
 * @param rv Radius vector to the sun.
 * @param slong Sun's longitude.
 */
void sunpos(double jd, int apparent, double *ra, double *dec, double *rv, double *slong)
{
   double t, t2, t3, l, m, e, ea, v, theta, omega, eps;

   // Time, in Julian centuries of 36525 ephemeris days, measured from the epoch 1900 January 0.5 ET
   t = (jd - 2415020.0) / JulianCentury;
   t2 = t * t;
   t3 = t2 * t;

   // Geometric mean longitude of the Sun, referred to the mean equinox of the date
   l = fixangle(279.69668 + 36000.76892 * t + 0.0003025 * t2);

   // Sun's mean anomaly
   m = fixangle(358.47583 + 35999.04975 * t - 0.000150 * t2 - 0.0000033 * t3);

   // Eccentricity of the Earth's orbit
   e = 0.01675104 - 0.0000418 * t - 0.000000126 * t2;

   // Eccentric anomaly
   ea = kepler(m, e);

   // True anomaly
   v = fixangle(2 * RAD_TO_DEG(atan(sqrt((1 + e) / (1 - e))  * tan(ea / 2))));

   // Sun's true longitude
   theta = l + v - m;

   // Obliquity of the ecliptic
   eps = obliqeq(jd);

   // Corrections for Sun's apparent longitude, if desired
   if (apparent)
   {
      omega = fixangle(259.18 - 1934.142 * t);
      theta = theta - 0.00569 - 0.00479 * sin(DEG_TO_RAD(omega));
      eps += 0.00256 * cos(DEG_TO_RAD(omega));
   }

   // Return Sun's longitude and radius vector
   *slong = theta;
   *rv = (1.0000002 * (1 - e * e)) / (1 + e * cos(DEG_TO_RAD(v)));

   // Determine solar co-ordinates
   *ra = fixangle(RAD_TO_DEG(atan2(cos(DEG_TO_RAD(eps)) * sin(DEG_TO_RAD(theta)), cos(DEG_TO_RAD(theta)))));
   *dec = RAD_TO_DEG(asin(sin(DEG_TO_RAD(eps)) * sin(DEG_TO_RAD(theta))));
}

/**
 * Calculates various time values for a given OLE date.
 *
 * @param dtSrc  Source date.
 * @param tmDest Destination time.
 * @return Boolean value indicating success.
 */
bool TmFromOleDate(double dtSrc, struct tm& tmDest)
{
   // The legal range does not actually span year 0 to 9999.
   if (dtSrc > MAX_DATE || dtSrc < MIN_DATE) // about year 100 to about 9999
      return false;

   long nDays;             // Number of days since Dec. 30, 1899
   long nDaysAbsolute;     // Number of days since 1/1/0
   long nSecsInDay;        // Time in seconds since midnight
   long nMinutesInDay;     // Minutes in day

   long n400Years;         // Number of 400 year increments since 1/1/0
   long n400Century;       // Century within 400 year block (0,1,2 or 3)
   long n4Years;           // Number of 4 year increments since 1/1/0
   long n4Day;             // Day within 4 year block (0 is 1/1/yr1, 1460 is 12/31/yr4)
   long n4Yr;              // Year within 4 year block (0,1,2 or 3)
   bool bLeap4 = true;     // TRUE if 4 year block includes leap year

   double dblDate = dtSrc; // temporary serial date

   // If a valid date, then this conversion should not overflow
   nDays = (long)dblDate;

   // Round to the second
   dblDate += ((dtSrc > 0.0) ? HALF_SECOND : -HALF_SECOND);

   nDaysAbsolute = (long)dblDate + 693959L; // Add days from 1/1/0 to 12/30/1899

   dblDate = fabs(dblDate);
   nSecsInDay = (long)((dblDate - floor(dblDate)) * 86400.);

   // Calculate the day of week (sun=1, mon=2...)
   //   -1 because 1/1/0 is Sat.  +1 because we want 1-based
   tmDest.tm_wday = (int)((nDaysAbsolute - 1) % 7L) + 1;

   // Leap years every 4 yrs except centuries not multiples of 400
   n400Years = (long)(nDaysAbsolute / 146097L);

   // Set nDaysAbsolute to day within 400-year block
   nDaysAbsolute %= 146097L;

   // -1 because first century has extra day
   n400Century = (long)((nDaysAbsolute - 1) / 36524L);

   // Non-leap century
   if (n400Century != 0)
   {
      // Set nDaysAbsolute to day within century
      nDaysAbsolute = (nDaysAbsolute - 1) % 36524L;

      // +1 because 1st 4 year increment has 1460 days
      n4Years = (long)((nDaysAbsolute + 1) / 1461L);

      if (n4Years != 0)
         n4Day = (long)((nDaysAbsolute + 1) % 1461L);
      else
      {
         bLeap4 = false;
         n4Day = (long)nDaysAbsolute;
      }
   }
   else
   {
      // Leap century - not special case!
      n4Years = (long)(nDaysAbsolute / 1461L);
      n4Day = (long)(nDaysAbsolute % 1461L);
   }

   if (bLeap4)
   {
      // -1 because first year has 366 days
      n4Yr = (n4Day - 1) / 365;

      if (n4Yr != 0)
         n4Day = (n4Day - 1) % 365;
   }
   else
   {
      n4Yr = n4Day / 365;
      n4Day %= 365;
   }

   // n4Day is now 0-based day of year. Save 1-based day of year, year number
   tmDest.tm_yday = (int)n4Day + 1;
   tmDest.tm_year = n400Years * 400 + n400Century * 100 + n4Years * 4 + n4Yr;

   // Handle leap year: before, on, and after Feb. 29
   if (n4Yr == 0 && bLeap4)
   {
      // Leap Year
      if (n4Day == 59)
      {
         /* Feb. 29 */
         tmDest.tm_mon = 2;
         tmDest.tm_mday = 29;
         goto DoTime;
      }

      // Pretend it's not a leap year for month/day comp
      if (n4Day >= 60)
         --n4Day;
   }

   // Make n4DaY a 1-based day of non-leap year and compute month/day for everything but Feb. 29
   ++n4Day;

   // Month number always >= n/32, so save some loop time
   for (tmDest.tm_mon = (n4Day >> 5) + 1;
      n4Day > MonthDays[tmDest.tm_mon]; tmDest.tm_mon++){};

      tmDest.tm_mday = (int)(n4Day - MonthDays[tmDest.tm_mon-1]);

DoTime:
   if (nSecsInDay == 0)
      tmDest.tm_hour = tmDest.tm_min = tmDest.tm_sec = 0;
   else
   {
      tmDest.tm_sec = (int)nSecsInDay % 60L;
      nMinutesInDay = nSecsInDay / 60L;
      tmDest.tm_min = (int)nMinutesInDay % 60;
      tmDest.tm_hour = (int)nMinutesInDay / 60;
   }

   return true;
}

/**
 * Caches the date (in days) and time (in fractional days).
 *
 * @param wYear Four-digit year.
 * @param wMonth Two-digit month.
 * @param wDay Two-digit day-of-month.
 * @param wHour Two-digit hour.
 * @param wMinute Two-digit minutes.
 * @param wSecond Two-digit seconds.
 * @return Date.
 */
double create_DATE(unsigned short wYear, unsigned short wMonth, unsigned short wDay, unsigned short wHour, unsigned short wMinute, unsigned short wSecond)
{
   long nDate;
   double dblTime;

   //  Check for leap year and set the number of days in the month
   bool bLeapYear = ((wYear & 3) == 0) &&
      ((wYear % 100) != 0 || (wYear % 400) == 0);

   //int nDaysInMonth =
   //   MonthDays[wMonth] - MonthDays[wMonth-1] +
   //   ((bLeapYear && wDay == 29 && wMonth == 2) ? 1 : 0);

   //It is a valid date; make Jan 1, 1AD be 1
   nDate = wYear*365L + wYear/4 - wYear/100 + wYear/400 +
      MonthDays[wMonth-1] + wDay;

   //  If leap year and it's before March, subtract 1:
   if (wMonth <= 2 && bLeapYear)
      --nDate;

   //  Offset so that 12/30/1899 is 0
   nDate -= 693959L;

   dblTime = (((long)wHour * 3600L) +  // hrs in seconds
      ((long)wMinute * 60L) +  // mins in seconds
      ((long)wSecond)) / 86400.;

   double d;
   d = (double) nDate + ((nDate >= 0) ? dblTime : -dblTime);

   return d;
}
