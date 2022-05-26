
#include "smeph.h"

/**
 * To obtain the apparent place of Sun or Moon to navigational accuracy.
 *
 * @param tjd Julian date for apparent place.
 * @param obj Body identifcation number for desired celestial object (10 = Sun, 11 = Moon).
 * @param ra Apparent right ascension (hours), referred to true equinox of date.
 * @param dec Apparent declination (degrees), referred to true equator of date.
 * @param dis True distance from Earth to celestial object (AU).
 * @return Status
 *         = 0 ... Everything OK
 *         = 1 ... invalid value of 'obj'
 *
 */
short int applan (double tjd, short int obj, double *ra, double *dec, double *dis)
{
   short int error = 0;

   /*
      Obtain the apparent places.
   */

   switch (obj)
   {
      case 10:
         sun (tjd, ra,dec,dis);
         break;

      case 11:
         moon (tjd, ra,dec,dis);
         *dis = *dis * 1000.0 / AU;
         break;

      default:
         *ra = 0.0;
         *dec = 0.0;
         *dis = 0.0;
         error = 1;
    }

   return (error);
}

/**
 * To compute apparent equatorial spherical coordinates of Sun.
 *
 * @param jd Julian date on TT time scale.
 * @param ra Apparent right ascension (hours).
 * @param dec Apparent declination (degrees).
 * @param dis Geocentric distance (AU).
 */
void sun (double jd,double *ra, double *dec, double *dis)
{
   short int i;

   double sum_lon = 0.0;
   double sum_r = 0.0;
   const double factor = 1.0e-07;
   double u, arg, lon, lat, dlon, dpsi, etrue, lon_app, sin_lon;

   struct sun_con
   {
   double l;
   double r;
   double alpha;
   double nu;
   };

   static const struct sun_con con[50] =
      {{403406.0,      0.0, 4.721964,     1.621043},
       {195207.0, -97597.0, 5.937458, 62830.348067}, 
       {119433.0, -59715.0, 1.115589, 62830.821524}, 
       {112392.0, -56188.0, 5.781616, 62829.634302}, 
       {  3891.0,  -1556.0, 5.5474  , 125660.5691 }, 
       {  2819.0,  -1126.0, 1.5120  , 125660.9845 }, 
       {  1721.0,   -861.0, 4.1897  ,  62832.4766 }, 
       {     0.0,    941.0, 1.163   ,      0.813  }, 
       {   660.0,   -264.0, 5.415   , 125659.310  }, 
       {   350.0,   -163.0, 4.315   ,  57533.850  }, 
       {   334.0,      0.0, 4.553   ,    -33.931  }, 
       {   314.0,    309.0, 5.198   , 777137.715  }, 
       {   268.0,   -158.0, 5.989   ,  78604.191  }, 
       {   242.0,      0.0, 2.911   ,      5.412  }, 
       {   234.0,    -54.0, 1.423   ,  39302.098  }, 
       {   158.0,      0.0, 0.061   ,    -34.861  }, 
       {   132.0,    -93.0, 2.317   , 115067.698  }, 
       {   129.0,    -20.0, 3.193   ,  15774.337  }, 
       {   114.0,      0.0, 2.828   ,   5296.670  }, 
       {    99.0,    -47.0, 0.52    ,  58849.27   }, 
       {    93.0,      0.0, 4.65    ,   5296.11   }, 
       {    86.0,      0.0, 4.35    ,  -3980.70   }, 
       {    78.0,    -33.0, 2.75    ,  52237.69   }, 
       {    72.0,    -32.0, 4.50    ,  55076.47   }, 
       {    68.0,      0.0, 3.23    ,    261.08   }, 
       {    64.0,    -10.0, 1.22    ,  15773.85   }, 
       {    46.0,    -16.0, 0.14    ,  188491.03  }, 
       {    38.0,      0.0, 3.44    ,   -7756.55  }, 
       {    37.0,      0.0, 4.37    ,     264.89  }, 
       {    32.0,    -24.0, 1.14    ,  117906.27  }, 
       {    29.0,    -13.0, 2.84    ,   55075.75  }, 
       {    28.0,      0.0, 5.96    ,   -7961.39  }, 
       {    27.0,     -9.0, 5.09    ,  188489.81  }, 
       {    27.0,      0.0, 1.72    ,    2132.19  }, 
       {    25.0,    -17.0, 2.56    ,  109771.03  }, 
       {    24.0,    -11.0, 1.92    ,   54868.56  }, 
       {    21.0,      0.0, 0.09    ,   25443.93  }, 
       {    21.0,     31.0, 5.98    ,  -55731.43  }, 
       {    20.0,    -10.0, 4.03    ,   60697.74  }, 
       {    18.0,      0.0, 4.27    ,    2132.79  }, 
       {    17.0,    -12.0, 0.79    ,  109771.63  }, 
       {    14.0,      0.0, 4.24    ,   -7752.82  }, 
       {    13.0,     -5.0, 2.01    ,  188491.91  }, 
       {    13.0,      0.0, 2.65    ,     207.81  }, 
       {    13.0,      0.0, 4.98    ,   29424.63  }, 
       {    12.0,      0.0, 0.93    ,      -7.99  }, 
       {    10.0,      0.0, 2.21    ,  46941.14   }, 
       {    10.0,      0.0, 3.59    ,    -68.29   }, 
       {    10.0,      0.0, 1.50    ,  21463.25   }, 
       {    10.0,     -9.0, 2.55    , 157208.40   }};

   /*
      Define the time unit 'u', measured in units of 10000 Julian years
      from J2000.0.
   */

   u = (jd - T0) / 3652500.0;

   /*
      Compute longitude and distance terms from the series.
   */

   for (i = 0; i < 50; i++)
   {
      arg = con[i].alpha + con[i].nu * u;
      sum_lon += con[i].l * sin (arg);
      sum_r += con[i].r * cos (arg);
   }

   /*
      Compute mean longitude, latitude, and distance of date.
   */

   lon = 4.9353929 + 62833.1961680 * u + factor * sum_lon;
   lon = fmod (lon, TWOPI);
   if (lon < 0.0)
      lon += TWOPI;

   lat = 0.0;

   *dis = 1.0001026 + factor * sum_r;

   /*
      Compute aberration correction.
   */

   dlon = factor * (-993.0 + 17.0 * cos (3.10 + 62830.14 * u));

   /*
      Compute nutation in longitude and true obliquity.
   */

   nutation (u, &dpsi,&etrue);

   /*
      Compute apparent ecliptic longitude.
   */

   lon_app = lon + dlon + dpsi;

   /*
      Compute apparent equatorial spherical coordinates.
   */

   sin_lon = sin (lon_app);
   *ra = atan2 ((cos (etrue) * sin_lon), cos (lon_app)) * RADDEG;
   *ra = fmod (*ra, 360.0);
   if (*ra < 0.0)
      *ra += 360.0;
   *ra = *ra / 15.0;

   *dec = asin (sin (etrue) * sin_lon) * RADDEG;
   
   return;
}

/**
 * To compute apparent equatorial spherical coordinates of Moon.
 *    NOTES:
 *     1. The periodic terms are from ELP2000-82 (see second reference).
 *        The fundamental arguments are from ELP2000-85 (see third
 *        reference).
 *     2. The (0, 2, 0, 0) coefficient in distance is missing from the
 *        first reference.  The coefficient from the fourth reference
 *        (1066) has been substituted.
 *     3. Aberration formulae are from the fourth reference, p. 16.
 *     4. The quoted accuracy is 10 arcsec in longitude; 4 arcsec in
 *        latitude.
 *
 * @param jd Julian date on TT time scale.
 * @param ra Apparent right ascension (hours).
 * @param dec Apparent declination (degrees).
 * @param dis Geocentric distance (km).
 */
void moon (double jd, double *ra, double *dec, double *dis)
{
   short int i, j;

   double sum_lon = 0.0;
   double sum_lat = 0.0;
   double sum_r = 0.0;
   double t, a1, a2, a3, e, dm, mm, mpm, fm, arg, lon_term, lat_term,
      r_term, a, fadeg, fa[5], lon, lat, u, arg1, arg2, del_lon,
      del_lat, dpsi, etrue, sin_lon, sin_e, cos_e;

   struct fa_con
   {
     double fac[6];
   };

   struct per_lr
   {
   short int d_mult;
   short int m_mult;
   short int mp_mult;
   short int f_mult;
   long int l_coef;
   long int r_coef;
   };

   struct per_b
   {
   short int d_mult;
   short int m_mult;
   short int mp_mult;
   short int f_mult;
   long int b_coef;
   };

   /*
      Constants of the fundamental arguments.
      facon[0] ... D: Moon's mean elongation.
      facon[1] ... M: Sun's mean anomaly.
      facon[2] ... M-prime: Moon's mean anomaly.
      facon[3] ... F: Moon's argument of latitude.
      facon[4] ... L_prime: Moon's mean longitude, referred to the mean
                   equinox of date.
   */

   static const struct fa_con facon[5] =
      {{297.85,  0.73512, 1602961601.4603 , -5.8681, 0.006595,
        -0.00003184},
       {357.5166666666667, 44.79306, 129596581.0474, -0.5529,
         0.000147, 0.0},
       {134.95, 48.28096, 1717915923.4728 , 32.3893, 0.051651,
        -0.00024470},
       {93.26666666666667, 19.55755, 1739527263.0983, -12.2505,
        -0.001021, 0.00000417},
       {218.3 , 59.95571, 1732564372.83264, -4.7763, 0.006681,
        -0.00005522}};

   /*
      Constants for the periodic terms in longitude and distance.
   */

   static const struct per_lr lrc[60] =
      {{0,  0,  1,  0,   6288774, -20905355},
       {2,  0, -1,  0,   1274027,  -3699111},
       {2,  0,  0,  0,    658314,  -2955968},
       {0,  0,  2,  0,    213618,   -569925},
       {0,  1,  0,  0,   -185116,     48888},
       {0,  0,  0,  2,   -114332,     -3149},
       {2,  0, -2,  0,     58793,    246158},
       {2, -1, -1,  0,     57066,   -152138},
       {2,  0,  1,  0,     53322,   -170733},
       {2, -1,  0,  0,     45758,   -204586},
       {0,  1, -1,  0,    -40923,   -129620},
       {1,  0,  0,  0,    -34720,    108743},
       {0,  1,  1,  0,    -30383,    104755},
       {2,  0,  0, -2,     15327,     10321},
       {0,  0,  1,  2,    -12528,         0},
       {0,  0,  1, -2,     10980,     79661},
       {4,  0, -1,  0,     10675,    -34782},
       {0,  0,  3,  0,     10034,    -23210},
       {4,  0, -2,  0,      8548,    -21636},
       {2,  1, -1,  0,     -7888,     24208},
       {2,  1,  0,  0,     -6766,     30824},
       {1,  0, -1,  0,     -5163,     -8379},
       {1,  1,  0,  0,      4987,    -16675},
       {2, -1,  1,  0,      4036,    -12831},
       {2,  0,  2,  0,      3994,    -10445},
       {4,  0,  0,  0,      3861,    -11650},
       {2,  0, -3,  0,      3665,     14403},
       {0,  1, -2,  0,     -2689,     -7003},
       {2,  0, -1,  2,     -2602,         0},
       {2, -1, -2,  0,      2390,     10056},
       {1,  0,  1,  0,     -2348,      6322},
       {2, -2,  0,  0,      2236,     -9884},
       {0,  1,  2,  0,     -2120,      5751},
       {0,  2,  0,  0,     -2069,      1066},
       {2, -2, -1,  0,      2048,     -4950},
       {2,  0,  1, -2,     -1773,      4130},
       {2,  0,  0,  2,     -1595,         0},
       {4, -1, -1,  0,      1215,     -3958},
       {0,  0,  2,  2,     -1110,         0},
       {3,  0, -1,  0,      -892,      3258},
       {2,  1,  1,  0,      -810,      2616},
       {4, -1, -2,  0,       759,     -1897},
       {0,  2, -1,  0,      -713,     -2117},
       {2,  2, -1,  0,      -700,      2354},
       {2,  1, -2,  0,       691,         0},
       {2, -1,  0, -2,       596,         0},
       {4,  0,  1,  0,       549,     -1423},
       {0,  0,  4,  0,       537,     -1117},
       {4, -1,  0,  0,       520,     -1571},
       {1,  0, -2,  0,      -487,     -1739},
       {2,  1,  0, -2,      -399,         0},
       {0,  0,  2, -2,      -381,     -4421},
       {1,  1,  1,  0,       351,         0},
       {3,  0, -2,  0,      -340,         0},
       {4,  0, -3,  0,       330,         0},
       {2, -1,  2,  0,       327,         0},
       {0,  2,  1,  0,      -323,      1165},
       {1,  1, -1,  0,       299,         0},
       {2,  0,  3,  0,       294,         0},
       {2,  0, -1, -2,         0,      8752}};

   /*
      Constants for the periodic terms in latitude.
   */

   static const struct per_b bc[60] =
      {{0,  0,  0,  1,   5128122},
       {0,  0,  1,  1,    280602},
       {0,  0,  1, -1,    277693},
       {2,  0,  0, -1,    173237},
       {2,  0, -1,  1,     55413},
       {2,  0, -1, -1,     46271},
       {2,  0,  0,  1,     32573},
       {0,  0,  2,  1,     17198},
       {2,  0,  1, -1,      9266},
       {0,  0,  2, -1,      8822},
       {2, -1,  0, -1,      8216},
       {2,  0, -2, -1,      4324},
       {2,  0,  1,  1,      4200},
       {2,  1,  0, -1,     -3359},
       {2, -1, -1,  1,      2463},
       {2, -1,  0,  1,      2211},
       {2, -1, -1, -1,      2065},
       {0,  1, -1, -1,     -1870},
       {4,  0, -1, -1,      1828},
       {0,  1,  0,  1,     -1794},
       {0,  0,  0,  3,     -1749},
       {0,  1, -1,  1,     -1565},
       {1,  0,  0,  1,     -1491},
       {0,  1,  1,  1,     -1475},
       {0,  1,  1, -1,     -1410},
       {0,  1,  0, -1,     -1344},
       {1,  0,  0, -1,     -1335},
       {0,  0,  3,  1,      1107},
       {4,  0,  0, -1,      1021},
       {4,  0, -1,  1,       833},
       {0,  0,  1, -3,       777},
       {4,  0, -2,  1,       671},
       {2,  0,  0, -3,       607},
       {2,  0,  2, -1,       596},
       {2, -1,  1, -1,       491},
       {2,  0, -2,  1,      -451},
       {0,  0,  3, -1,       439},
       {2,  0,  2,  1,       422},
       {2,  0, -3, -1,       421},
       {2,  1, -1,  1,      -366},
       {2,  1,  0,  1,      -351},
       {4,  0,  0,  1,       331},
       {2, -1,  1,  1,       315},
       {2, -2,  0, -1,       302},
       {0,  0,  1,  3,      -283},
       {2,  1,  1, -1,      -229},
       {1,  1,  0, -1,       223},
       {1,  1,  0,  1,       223},
       {0,  1, -2, -1,      -220},
       {2,  1, -1, -1,      -220},
       {1,  0,  1,  1,      -185},
       {2, -1, -2, -1,       181},
       {0,  1,  2,  1,      -177},
       {4,  0, -2, -1,       176},
       {4, -1, -1, -1,       166},
       {1,  0,  1, -1,      -164},
       {4,  0,  1, -1,       132},
       {1,  0, -1, -1,      -119},
       {4, -1,  0, -1,       115},
       {2, -2,  0,  1,       107}};

   /*
      Compute time argument in Julian centuries from J2000.0
   */

   t = (jd - T0) / 36525.0;

   /*
      Evaluate fundamental arguments.
      fa[0] ... D: Moon's mean elongation.
      fa[1] ... M: Sun's mean anomaly.
      fa[2] ... M-prime: Moon's mean anomaly.
      fa[3] ... F: Moon's argument of latitude.
      fa[4] ... L_prime: Moon's mean longitude, referred to the mean
                equinox of date, including the constant term of the
                effect of light_time.
   */

   for (i = 0; i < 5; i++)
   {
      a = facon[i].fac[5];
      for (j = 4; j > 0; j--)
         a = a * t + facon[i].fac[j];

      fadeg = fmod ((facon[i].fac[0] + (a / 3600.0)), 360.0);
      if (fadeg < 0.0)
         fadeg += 360.0;

      fa[i] = fadeg * DEGRAD;
   }

   /*
      Evaluate additional arguments.
   */

   a1 = fmod ((119.75 +    131.849 * t), 360.0) * DEGRAD;
   if (a1 < 0)
      a1 += TWOPI;

   a2 = fmod (( 53.09 + 479264.290 * t), 360.0) * DEGRAD;
   if (a2 < 0)
      a2 += TWOPI;

   a3 = fmod ((313.45 + 481266.484 * t), 360.0) * DEGRAD;
   if (a3 < 0)
      a3 += TWOPI;

   e = 1.0 - 0.002516 * t - 0.0000074 * t * t;

   /*
      Sum the periodic terms. The multiplicative factor 'e' is used to
      account for the secular decrease in the eccentricity of Earth's
      orbit.
   */

   for (i = 0; i < 60; i++)
   {

      /*
         Longitude.
      */

       dm =  (double) lrc[i].d_mult;
       mm =  (double) lrc[i].m_mult;
       mpm = (double) lrc[i].mp_mult;
       fm =  (double) lrc[i].f_mult;

       arg = dm * fa[0] + mm * fa[1] + mpm * fa[2] + fm * fa[3];

       lon_term = (double) lrc[i].l_coef * sin (arg);

       if (fabs ((double)lrc[i].m_mult) == 1)
          lon_term *= e;
        else if (fabs ((double)lrc[i].m_mult) == 2)    
          lon_term *= e * e;

       sum_lon += lon_term;

      /*
         Distance.
      */

       r_term = (double) lrc[i].r_coef * cos (arg);

       if (fabs ((double)lrc[i].m_mult) == 1)
          r_term *= e;
        else if (fabs ((double)lrc[i].m_mult) == 2)    
          r_term *= e * e;

       sum_r += r_term;

      /*
         Latitude.
      */

       dm =  (double) bc[i].d_mult;
       mm =  (double) bc[i].m_mult;
       mpm = (double) bc[i].mp_mult;
       fm =  (double) bc[i].f_mult;

       arg = dm  * fa[0] + mm  * fa[1] + mpm * fa[2] + fm  * fa[3];

       lat_term = (double) bc[i].b_coef * sin (arg);

       if (fabs ((double)bc[i].m_mult) == 1)
          lat_term *= e;
        else if (fabs ((double)bc[i].m_mult) == 2)    
          lat_term *= e * e;

       sum_lat += lat_term;
   }

   /*
      Compute additional periodic terms due to planetary perturbations
      and the oblateness of Earth.  Terms involving 'a1' are due to Venus,
      terms involving 'a2' are due to Jupiter, and terms involving
      L_prime (fa[4]) are due to Earth oblateness.
   */

   sum_lon +=  3958.0 * sin (a1) +
               1962.0 * sin (fa[4] - fa[3]) +
                318.0 * sin (a2);

   sum_lat += -2235.0 * sin (fa[4]) +
                382.0 * sin (a3) +
                175.0 * sin (a1 - fa[3]) +
                175.0 * sin (a1 + fa[3]) +
                127.0 * sin (fa[4] - fa[2]) -
                115.0 * sin (fa[4] + fa[2]);

   /*
      Finally, compute the mean ecliptic coordinates (in radians)
      and distance (in km).
   */

   lon = fa[4] + (sum_lon / 1.0e6) * DEGRAD;
   lat = sum_lat / 1.0e6 * DEGRAD;
   *dis = 385000.56 + sum_r / 1.0e3;

   /*
      Compute aberration.
   */

   arg1 = (225.0 + 477198.9 * t) * DEGRAD;
   arg2 = (183.3 + 483202.0 * t) * DEGRAD;
   del_lon = (-0.00019524 - 0.00001059 * sin (arg1)) * DEGRAD;
   del_lat = (-0.00001754 * sin (arg2)) * DEGRAD;

   /*
      Compute nutation in longitude and true obliquity.
   */

   u = t / 100.0;
   nutation (u, &dpsi,&etrue);

   /*
      Add aberration to longitude and latitude, and nutation to longitude.
   */

   lon += del_lon + dpsi;
   lat += del_lat;

   /*
      Compute apparent equatorial spherical coordinates.  R.A. will
      be in hours; declination in degrees.
   */

   sin_lon = sin (lon);
   cos_e = cos (etrue);
   sin_e = sin (etrue);
   *ra = atan2 ((sin_lon * cos_e - tan (lat) * sin_e), cos (lon)) *
      RADDEG;
   *ra = fmod (*ra, 360.0);
   if (*ra < 0.0)
      *ra += 360.0;
   *ra = *ra / 15.0;

   *dec = asin (sin (lat) * cos_e + cos (lat) * sin_e * sin_lon) *
      RADDEG;
   
   return;
}

/**
 * To compute approximate values of the nutation in longitude of the true obliquity.
 *
 * @param u Time measured in unites of 10000 Julian years from J2000.0.
 * @param dpsi Nutation in logitude (radians).
 * @param etrue True obliquity of the ecliptic (radians).
 */
void nutation (double u, double *dpsi, double *etrue)
{
   const double factor = 1.0e-07;
   double a1, a2;

   /*
      Compute constants.
   */

   a1 = 2.18 -   3375.70 * u + 0.36 * u * u;
   a2 = 3.51 + 125666.39 * u + 0.10 * u * u;

   /*
      Compute nutation in longitude.
   */
 
   *dpsi = factor * (-834.0 * sin (a1) - 64.0 * sin (a2));

   /*
      Compute true obliquity.
   */

   *etrue = 0.4090928 + factor * (u * (-226938.0 - u * (75.0 - u * 
      (96926.0 - u * (2491.0 + 12104.0 * u)))) +
      446.0 * cos (a1) + 28.0 * cos (a2));

   *etrue = fmod (*etrue, TWOPI);
   if (*etrue < 0.0)
      *etrue += TWOPI;

   return;
}
