#include "CelestialIllumination.h"

using namespace TAK::Engine::Formats::SLAT;

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace SLAT {
                short int CelestialIllumination::illuminance(double tjd_ut, double delta_t,
                                      observer *location, short int sky_cond,

                                      double *illum_tot, aux_data *sun_data,
                                      aux_data *moon_data)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To compute the illuminance of the Sun and Moon, and the total
                      illuminance, at a given time and location on the surface of the
                      Earth.

                   REFERENCES:
                      Janiczek, P.M. and DeYoung, J.A. (1987). "Computer Programs for
                         Sun and Moon Illuminance With Contingent Tables and
                         Diagrams", USNO Circular 171 (Washington: U.S. Naval
                         Observatory)
                      Janiczek, P.M. (1996). unpublished notes.

                   INPUT
                   ARGUMENTS:
                      tjd_ut (double)
                         Universal Time (UT) of interest, expressed as a Julian date.
                      delta_t (double)
                         The difference between the the TDT and UT1 time scales at
                         'tjd_ut', TDT-UT1 (seconds).
                      *location (struct observer)
                         A structure providing the location of the observer; latitude
                         and longitude (defined in file rs.h).
                      sky_cond (short int)
                         Code describing the sky conditions.
                         =  1 ... Average clear sky, < 70% covered by (scattered)
                                  clouds; direct rays of the Sun and Moon are
                                  unobstructed relative to the location of interest;
                         =  2 ... Sun/Moon easily visible but direct rays obstructed by
                                  thin clouds;
                         =  3 ... Direct rays of Sun/Moon are obstructed by average
                                  clouds;
                         = 10 ... Dark stratus clouds cover the entire sky.

                   OUTPUT
                   ARGUMENTS:
                      *illum_tot (double)
                         The total illuminance in units of lux.
                      *sun_data (struct aux_data)
                         Structure containing auxiliary data for the Sun (defined in
                         illum.h).
                      *moon_data (struct aux_data)
                         Structure containing auxiliary data for the Moon (defined in
                         illum.h).

                   RETURNED
                   VALUE:
                      (short int)
                         = 0 ... everything OK
                         = 1 ... invalid value of 'sky_cond'.

                   GLOBALS
                   USED:
                      SUNR, MOONR

                   FUNCTIONS
                   CALLED:
                      st          rslib.c
                      applan      smeph.c
                      lha         rslib.c
                      z_dist      rslib.c
                      azimuth     rslib.c
                      par_zd      rslib.c
                      semidi      rslib.c
                      refract     rslib.c
                      illum_sun   illum.c
                      eh_par      rslib.c
                      elong       illum.c
                      phs_ang     illum.c
                      phase       illum.c
                      illum_moon  illum.c

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/11-96/JAB (USNO/AA)
                      V1.1/07-05/JAB (USNO/AA): Apply refraction to the altitude of the
                                                object taking into account the semi-
                                                diameter of the disk.

                   NOTES:
                      None.

                ------------------------------------------------------------------------
                */
                {
                   static short int first_call = 1;
                   short int body[2] = {10, 11};
                   short int error = 0;
                   short int i;

                   static double ref0;
                   double tjd_tt, last, ra[2], dec[2], dis[2], loc_ha, zd, par,
                           eqh_par, alt[2], az[2], rp, sd, e, pa;

                   /*
                      Check the value of 'sky_cond'.
                   */

                   if ((sky_cond < 1) || (sky_cond > 10))
                      return (error = 1);

                   /*
                      Compute the terrestrial time (TT) corresponding to the input
                      Universal Time (UT).
                   */

                   tjd_tt = tjd_ut + (delta_t / 86400.0);


                   /*
                      Compute the topocentric altitude of each object, affected by
                      refraction.  Do the Sun first, then the Moon.  The "body number"
                      of the Sun is 10; the Moon is 11.
                   */

                   for (i = 0; i < 2; i++) {

                      /*
                         Compute the local apparent sidereal time.
                      */

                      if (i == 0)
                         last = st(tjd_ut, location->longitude);

                      /*
                         Compute the geocentric zenith distance.
                      */

                      applan(tjd_tt, body[i], &ra[i], &dec[i], &dis[i]);
                      loc_ha = lha(ra[i], last);
                      zd = z_dist(dec[i], loc_ha, location->latitude);

                      /*
                         Compute the parallax correction and apply to the geocentric
                         zenith distance to get the topocentric altitude of the center
                         of the disk.
                      */

                      par = par_zd(zd, dis[i]);
                      alt[i] = 90.0 - (zd + par);

                      /*
                         Apply refraction correction if the upper limb of the object,
                         corrected for refraction, will be on or above the horizon.
                      */

                      switch (i) {
                         case 0:
                            rp = SUNR;
                              break;

                         case 1:
                            rp = MOONR;
                              break;
                      }

                      sd = semidi(dis[i], rp);

                      if (first_call) {
                         ref0 = refract(0.0);
                         first_call = 0;
                      }

                      if (alt[i] >= -(sd + ref0))
                         alt[i] += refract(alt[i]);

                      /*
                         Compute the azimuth.
                      */

                      az[i] = azimuth(dec[i], loc_ha, location->latitude);

                   }

                   /*
                      Load the horizon coordinates into the auxiliary data structures.
                   */

                   sun_data->jd_ut = moon_data->jd_ut = tjd_ut;
                   sun_data->alt = alt[0];
                   sun_data->az = az[0];
                   moon_data->alt = alt[1];
                   moon_data->az = az[1];

                   /*
                      Compute the illuminance of the Sun.
                   */

                   sun_data->ill = illum_sun(sun_data->alt, sky_cond);

                   /*
                      Compute the illuminance of the Moon.  This is more complicated
                      due to phase and varying-distance effects.
                   */

                   eqh_par = eh_par(dis[1]);
                   e = elong(ra[0], dec[0], ra[1], dec[1]);
                   moon_data->ill = illum_moon(moon_data->alt, e, eqh_par, sky_cond);

                   /*
                      Compute the total illuminance: the sum of the illuminance of
                      the Sun, the Moon, and the sky background.  The last effect
                      is modelled here as a constant (see Ref. 1).
                   */

                   *illum_tot = sun_data->ill + moon_data->ill +
                                (0.0005 / (double) sky_cond);

                   /*
                      Compute fraction of the Moon illuminated.  Set the fraction of the
                      Sun illuminated to 1.0.
                   */

                   pa = phs_ang(dis[0], dis[1], e);
                   moon_data->frac_ill = phase(pa);
                   sun_data->frac_ill = 1.0;

                   return (error);
                }

/********illum_sun */

                double CelestialIllumination::illum_sun(double alt, short int sky_cond)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To compute the illuminance of the Sun at a location on the
                      surface of the Earth.

                   REFERENCES:
                      Brown, Dayton R.E. (1952). Natural Illumination Charts. Bur. of
                         Ships, U.S. Navy Report No. 374-1; Sept. 1952.
                      Janiczek, P.M. (1996). unpublished notes.

                   INPUT
                   ARGUMENTS:
                      alt (double)
                         The topocentric altitude of the Sun or Moon, affected by
                         refraction (degrees).
                      sky_cond (short int)
                         Code describing the sky conditions.
                         =  1 ... Average clear sky, < 70% covered by (scattered)
                                  clouds; direct rays of the Sun and Moon are
                                  unobstructed relative to the location of interest;
                         =  2 ... Sun/Moon easily visible but direct rays obstructed by
                                  thin clouds;
                         =  3 ... Direct rays of Sun/Moon are obstructed by average
                                  clouds;
                         = 10 ... Dark stratus clouds cover the entire sky.

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         Solar illuminance (units of lux).

                   GLOBALS
                   USED:
                      None.

                   FUNCTIONS
                   CALLED:
                      eval_cheby        illum.c
                      pow               math.h

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/09-96/JAB (USNO/AA)

                   NOTES:
                      1. This function creates a data structure containing parameters
                      of a fit to the illumination data in the first reference. The
                      data were fitted to a Chebyshev polynomial of degree 24.

                ------------------------------------------------------------------------
                */
                {
                   static short int first_time = 1;
                   short int i;

                   static cheby_parms q;

                   /*
                      Define the transformation parameters, c[0] and c[1], and the
                      coefficients of the Chebyshev polynomial, c[2]-c[26].
                   */

                   const double c[27] =
                           {0.3500000000000000e+02,
                            0.5500000000000000e+02,
                            0.2936506136978837e+01,
                            0.3627326327566490e+01,
                            -0.2233214447344062e+01,
                            0.1015115465301055e+01,
                            -0.1898083125016442e+00,
                            -0.2620601965183225e+00,
                            0.3829581829851666e+00,
                            -0.2879683995474197e+00,
                            0.1134487130232542e+00,
                            0.3092492471913735e-01,
                            -0.9853820413978147e-01,
                            0.8976982948378892e-01,
                            -0.4151839367722814e-01,
                            -0.5982465090666525e-02,
                            0.2913731978265127e-01,
                            -0.2832367750489338e-01,
                            0.1326901081959582e-01,
                            0.3365892788214903e-02,
                            -0.1123578761200569e-01,
                            0.9862218049840636e-02,
                            -0.3690281457418281e-02,
                            -0.1338196406378107e-02,
                            0.5060420921563732e-02,
                            -0.3079727252974708e-02,
                            0.2969002584378676e-02};

                   double log_i_sun, i_sun;

                   /*
                      Perform check on the altitude.  Polynomial is valid for altitudes
                      in the range -20 to +90 degrees.
                   */

                   if ((alt < -20.0) || (alt > 90.0))
                      return (1.0e-16);

                   /*
                      Set up the coefficients structure the first time this function
                      is called.  The structure is defined in 'illum.h'.
                   */

                   if (first_time == 1) {
                      q.degree = 24;

                      for (i = 0; i < q.degree + 3; i++)
                         q.c[i] = c[i];

                      first_time = 0;
                   }

                   /*
                      Evaluate the Chebyshev polynomial to get the illuminance of the
                      Sun.
                   */

                   log_i_sun = eval_cheby(&q, alt);
                   i_sun = pow(10.0, log_i_sun) / (double) sky_cond;

                   return (i_sun);
                }

                /********illum_moon */

                double CelestialIllumination::illum_moon(double alt, double elongation, double eqh_par,
                                  short int sky_cond)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To compute the illuminance of the Moon at a location on the
                      surface of the Earth.

                   REFERENCES:
                      Yallop, B.D. and Hohenkerk, C.Y. (1992) in Explanatory Supplement
                         to the Astronomical Almanac, Chapter 9, pp. 490-493.
                      Brown, Dayton R.E. (1952). Natural Illumination Charts. Bur. of
                         Ships, U.S. Navy Report No. 374-1; Sept. 1952.
                      Janiczek, P.M. and DeYoung, J.A. (1987). "Computer Programs for
                         Sun and Moon Illuminance With Contingent Tables and
                         Diagrams", USNO Circular 171 (Washington: U.S. Naval
                         Observatory)

                   INPUT
                   ARGUMENTS:
                      alt (double)
                         The topocentric altitude of the Sun or Moon, affected by
                         refraction (degrees).
                      elongation (double)
                         The elongation of the Moon (the angle between the vectors
                         from Earth to Moon and from Earth to Sun) in degrees.
                      eqh_par (double)
                         Equatorial horizontal parallax of the Moon (degrees).
                      sky_cond (short int)
                         Code describing the sky conditions.
                         =  1 ... Average clear sky, < 70% covered by (scattered)
                                  clouds; direct rays of the Sun and Moon are
                                  unobstructed relative to the location of interest;
                         =  2 ... Sun/Moon easily visible but direct rays obstructed by
                                  thin clouds;
                         =  3 ... Direct rays of Sun/Moon are obstructed by average
                                  clouds;
                         = 10 ... Dark stratus clouds cover the entire sky.

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         Lunar illuminance (units of lux).

                   GLOBALS
                   USED:
                      DEGRAD            cons.c

                   FUNCTIONS
                   CALLED:
                      illum_sun         illum.c
                      log10             math.h
                      pow               math.h
                      exp               math.h
                      sin               math.h
                      cos               math.h
                      tan               math.h

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/10-96/JAB (USNO/AA)

                   NOTES:
                      1. The illuminance of the Moon is composed of three terms: the
                      illuminance of the full Moon at altitude 90 degrees as measured
                      on the ground, a phase effect, and a distance effect (see
                      first reference, p. 493).  The first term is derived by scaling
                      the illuminance of the Sun: the illuminance of the Sun is
                      computed assuming the Sun is at the same altitude as the Moon.
                      Then, this value is scaled so as to produce an illuminance of
                      0.406 lux at 'alt' = 90 degrees (the illuminance of the full
                      moon at this altitude).  Thus, the behavior of the solar
                      illuminance as a function of altitude is transferred to the Moon.
                      The phase effect is modelled as in the third reference and the
                      distance effect is modelled as in the first reference.

                ------------------------------------------------------------------------
                */
                {
                   double ill_moon_0, l1, e_r, p, l2, l3, ill_moon;

                   /*
                      Compute the illuminance of the Sun assuming the Sun is at the Moon's
                      altitude.  Scale it so as to produce an illuminance of 0.406 lux
                      at 'alt' = 90 degrees (the illuminance of the full moon at 90
                      degrees altitude; determined by P.M. Janiczek, private
                      communication).
                   */

                   ill_moon_0 = illum_sun(alt, 1) * 3.277e-06;
                   l1 = log10(ill_moon_0);

                   /*
                      Compute the phase effect using a formula from the third reference.
                   */

                   e_r = elongation * DEGRAD;
                   p = 0.892 * (exp(-3.343 / pow(tan(e_r / 2.0), 0.632))) +
                       0.0344 * (sin(e_r) - e_r * cos(e_r));
                   l2 = log10(p);

                   /*
                      Compute the distance effect using a formula from the first
                      reference.
                   */

                   l3 = 2.0 * log10(eqh_par / 0.951);

                   /*
                      Compute the illuminance of the Moon.
                   */

                   ill_moon = pow(10.0, (l1 + l2 + l3)) / (double) sky_cond;

                   return (ill_moon);
                }

                /********phs_ang */

                double CelestialIllumination::phs_ang(double dissun, double disobj, double elong_ang)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To compute the phase angle, the angle between the vectors
                      from object to Sun and from object to Earth, as measured at the
                      center of the object.

                   REFERENCES:
                      Explanatory Supplement to the AE and AENA (1961).  London: H.M.
                         Stationery Office; p. 312.

                   INPUT
                   ARGUMENTS:
                      dissun (double)
                         The geocentric distance of the Sun (any units).
                      disobj (double)
                         The geocentric distance of the object (any units as long as
                         they are the same as those of 'dissun'.)
                      elong (double)
                         Elongation angle (geocentric angle between Sun and object),
                         in degrees.

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         Phase angle (degrees)

                   GLOBALS
                   USED:
                      DEGRAD    cons.c
                      RADDEG    cons.c

                   FUNCTIONS
                   CALLED:
                      sin       math.h
                      cos       math.h
                      atan2     math.h

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/08-96/JAB (USNO/AA)

                   NOTES:
                      None.

                ------------------------------------------------------------------------
                */
                {
                   double elong_r, pa;

                   elong_r = elong_ang * DEGRAD;

                   pa = atan2((dissun * sin(elong_r)),
                              (disobj - dissun * cos(elong_r))) * RADDEG;

                   return (pa);
                }

                /********phase */

                double CelestialIllumination::phase(double i)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      Compute the phase (fraction illuminated) of a planet given the
                      phase angle (angle between the Sun and Earth at the planet).

                   REFERENCES:
                      Hilton, J. L. (1992). 'Physical Ephemerides of the Sun, Moon,
                         Planets and Satellites,' in The Explanatory Supplement to the
                         Astronomical Almanac, (Mill Valley, CA: University Science
                         Books).

                   INPUT
                   ARGUMENTS:
                      i (double)
                         The phase angle of the planet (degrees).

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         Phase of the planet (fraction illuminated).

                   GLOBALS
                   USED:
                      DEGRAD     consts.c

                   FUNCTIONS
                   CALLED:
                      cos        math.h

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/09-93/WTH (USNO/AA) Convert FORTRAN to C.
                      V1.1/12-93/JAB (USNO/AA) Eliminate use of temporary variable.
                      V1.2/06-94/SPP (USNO/AA) Edited prolog.

                   NOTES:
                      None.

                ------------------------------------------------------------------------
                */
                {
                   double ph;

                   ph = 0.5 + 0.5 * cos(i * DEGRAD);

                   return (ph);
                }

                /********elong */

                double CelestialIllumination::elong(double rasun, double decsun, double raobj,
                             double decobj)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To compute an object's elongation from the Sun.

                   REFERENCES:
                      Yallop, B.D. and Hohenkerk, C.Y. (1992) in Explanatory
                         Supplement to the Astronomical Almanac, Chapter 9, p. 493.

                   INPUT
                   ARGUMENTS:
                      rasun (double)
                         Right ascension of the Sun in hours.
                      decsun (double)
                         Declination of the Sun in degrees.
                      raobj (double)
                         Right ascension of the object in hours.
                      decobj (double)
                         Declination of the object in degrees.

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         Elongation angle, on interval 0 - 180 degrees.

                   GLOBALS
                   USED:
                      DEGRAD    cons.c
                      RADDEG    cons.c

                   FUNCTIONS
                   CALLED:
                      sin       math.h
                      cos       math.h
                      acos      math.h

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/09-96/JAB (USNO/AA)

                   NOTES:
                      1. None.

                ------------------------------------------------------------------------
                */
                {
                   double hrs_r, ras_r, rao_r, decs_r, deco_r, cose, e;

                   /*
                      Convert input angles to radians.
                   */

                   hrs_r = 15.0 * DEGRAD;
                   ras_r = rasun * hrs_r;
                   rao_r = raobj * hrs_r;
                   decs_r = decsun * DEGRAD;
                   deco_r = decobj * DEGRAD;

                   /*
                      Determine the elongation angle on the interval 0 - 180 degrees.
                   */

                   cose = sin(decs_r) * sin(deco_r) + cos(decs_r) * cos(deco_r) *
                                                      cos(rao_r - ras_r);
                   e = acos(cose) * RADDEG;

                   return (e);
                }

                /********eval_cheby */

                double CelestialIllumination::eval_cheby(cheby_parms *p, double x)
                /*
                ------------------------------------------------------------------------

                   PURPOSE:
                      To evaluate a Chebyshev polynomial at a given value of 'x'.

                   REFERENCES:
                      See Note 1 below.

                   INPUT
                   ARGUMENTS:
                      *p (struct cheby_parms)
                         Structure containing the degree, transformation parameters,
                         and coefficients of the Chebyshev polynomial (defined in
                         illum.h).
                      x (double)
                         The value of the independent variable at which the polynomial
                         is to be evaluated.

                   OUTPUT
                   ARGUMENTS:
                      None.

                   RETURNED
                   VALUE:
                      (double)
                         The value of the Chebyshev polynomial at 'x'.

                   GLOBALS
                   USED:
                      None.

                   FUNCTIONS
                   CALLED:
                      None.

                   VER./DATE/
                   PROGRAMMER:
                      V1.0/09-96/JAB (USNO/AA)

                   NOTES:
                      1. This function is based on Fortran subroutine 'dcpval' by C. L.
                      Lawson, Jet Propulsion Laboratory, 1969 Dec. 17, modified
                      1973 Jul. 24, modified 1974 Nov. 19.  Original Fortran code
                      developed under NASA contract NAS7-918.

                ------------------------------------------------------------------------
                */
                {
                   short int j;

                   double w[3], s, s2, y;

                   w[0] = w[1] = 0.0;

                   /*
                      Transform 'x' to 's'.
                   */

                   s = (x - p->c[0]) / p->c[1];
                   s2 = s + s;

                   /*
                      Evaluate polynomial using recursion.
                   */

                   j = p->degree + 2;

                   while (j > 2) {
                      w[2] = w[1];
                      w[1] = w[0];
                      w[0] = (s2 * w[1] - w[2]) + p->c[j];
                      j--;
                   }

                   y = (s * w[0] - w[1]) + p->c[2];

                   return (y);
                }
            }
        }
    }
}
