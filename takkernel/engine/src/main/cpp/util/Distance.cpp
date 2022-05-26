////============================================================================
////
////    FILE:           Distance.cpp
////
////    DESCRIPTION:    Implementation of distance-related functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 15, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "util/Distance.h"
#include "core/Ellipsoid.h"

#include <cmath>

#include "core/GeoPoint.h"

#ifdef MSVC
#include "vscompat.h"
#endif


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


enum { MAX_ITERS = 20 };

const double RADIANS (M_PI / 180.0);
const double DEGREES (180.0 / M_PI);

const double WGS84_A (6378137.0);       // WGS84 major axis
const double WGS84_B (6356752.3142);    // WGS84 semi-major axis
const double WGS84_F (1.0 - WGS84_B / WGS84_A); // WGS84 flattening
const double WGS84_A2_B2_1 ((WGS84_A * WGS84_A) / (WGS84_B * WGS84_B) - 1.0);

const double TOLERANCE_0 = 5.0e-15, // tol0
    TOLERANCE_1 = 5.0e-14, // tol1
    TOLERANCE_2 = 5.0e-13, // tt
    TOLERANCE_3 = 7.0e-3; // tol2
    
const int MAX_CONVERGENCE_ITERATIONS = 12;
    
struct DistanceConstants {

    explicit DistanceConstants(const atakmap::core::Ellipsoid &e);
    
    double semiMajorAxis;
    double semiMinorAxis;
    double eccentricitySquared;
    double maxOrthodromicDistance;
    double A, B, C, D, E, F;
    double fo, f, f2, f3, f4;
    double T1, T2, T4, T6;
    double a01, a02, a03, a21, a22, a23, a42, a43, a63;
};
    
    
inline double castToAngleRange(double alpha) {
    return alpha - (2 * M_PI) * floor(alpha / (2 * M_PI) + 0.5);
}
    
inline double convertAngleToHeading(double alpha) {
    if (alpha < 0) {
        return alpha + 2 * M_PI;
    } else {
        return alpha;
    }
}
    
double getMeridianArcLengthRadians(double P1, double P2, const DistanceConstants &dconst);
    
}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace util                          // Open util namespace.
{
namespace distance                      // Open distance namespace.
{


double
calculateRange (const core::GeoPoint& from,
                const core::GeoPoint& to)
  {
    //
    // Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
    // using the "Inverse Formula" (section 4).
    //

    //
    // Convert lat/lon to radians.
    //
    double lat1 (from.latitude * RADIANS);
    double lat2 (to.latitude * RADIANS);
    double lon1 (from.longitude * RADIANS);
    double lon2 (to.longitude * RADIANS);

    double L (lon2 - lon1);
    double A (0.0);
    double U1 (atan ((1.0 - WGS84_F) * tan (lat1)));
    double U2 (atan ((1.0 - WGS84_F) * tan (lat2)));

    double cosU1 (cos (U1));
    double cosU2 (cos (U2));
    double sinU1 (sin (U1));
    double sinU2 (sin (U2));
    double cosU1cosU2 (cosU1 * cosU2);
    double sinU1sinU2 (sinU1 * sinU2);

    double sigma (0.0);
    double deltaSigma (0.0);
    double cosSqAlpha (0.0);
    double cos2SM (0.0);
    double cosSigma (0.0);
    double sinSigma (0.0);
    double cosLambda (0.0);
    double sinLambda (0.0);

    double lambda (L);                  // initial guess

    for (std::size_t iter (0); iter < MAX_ITERS; iter++)
      {
        cosLambda = cos (lambda);
        sinLambda = sin (lambda);

        double t1 (cosU2 * sinLambda);
        double t2 (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
        double sinSqSigma (t1 * t1 + t2 * t2);                  // (14)

        sinSigma = sqrt (sinSqSigma);
        cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;         // (15)
        sigma = atan2 (sinSigma, cosSigma);                     // (16)

        double sinAlpha (sinSigma == 0                          // (17)
                         ? 0.0
                         : cosU1cosU2 * sinLambda / sinSigma);

        cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
        cos2SM = cosSqAlpha == 0                                // (18)
            ? 0.0
            : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;

        double uSquared (cosSqAlpha * WGS84_A2_B2_1);           // defn

        A = 1 + (uSquared / 16384.0)                            // (3)
              * (4096.0 + uSquared
                        * (-768.0 + uSquared * (320.0 - 175.0 * uSquared)));

        double B ((uSquared / 1024.0)                           // (4)
                  * (256.0 + uSquared
                           * (-128.0 + uSquared * (74.0 - 47.0 * uSquared))));
        double C ((WGS84_F / 16.0)                              // (10)
                  * cosSqAlpha
                  * (4.0 + WGS84_F * (4.0 - 3.0 * cosSqAlpha)));
        double cos2SMSq (cos2SM * cos2SM);

        deltaSigma = B * sinSigma                               // (6)
                       * (cos2SM + (B / 4.0)
                                 * (cosSigma * (-1.0 + 2.0 * cos2SMSq)
                                    - (B / 6.0) * cos2SM
                                    * (-3.0 + 4.0 * sinSigma * sinSigma)
                                    * (-3.0 + 4.0 * cos2SMSq)));

        double lambdaOrig (lambda);

        lambda = L + (1.0 - C) * WGS84_F * sinAlpha             // (11)
                   * (sigma + C
                            * sinSigma
                            * (cos2SM + C
                                      * cosSigma
                                      * (-1.0 + 2.0 * cos2SM * cos2SM)));

        double delta ((lambda - lambdaOrig) / lambda);

        if (fabs (delta) < 1.0e-12)
          {
            break;
          }
      }

    return WGS84_B * A * (sigma - deltaSigma);
//        results[0] = distance;
//        if (results.length > 1) {
//            float initialBearing = (float) atan2(cosU2 * sinLambda,
//                cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
//            initialBearing /= RADIANS;
//            results[1] = initialBearing;
//            if (results.length > 2) {
//                float finalBearing = (float) atan2(cosU1 * sinLambda,
//                    -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
//                finalBearing /= RADIANS;
//                results[2] = finalBearing;
//            }
//        }
  }

void
pointAtRange (const core::GeoPoint& p,
              const double range,
              const double azimuth,
              core::GeoPoint& to)
  {
    // http://en.wikipedia.org/wiki/Vincenty's_formulae
    const double phi1 (RADIANS * p.latitude);         // latitude of the pt
    const double tanU1 ((1.0 - WGS84_F) * tan (phi1));
    const double U1 (atan (tanU1));                   // reduced latitude
    const double lambda1 (RADIANS * p.longitude);     // longitude of the pt
    const double alpha1 (RADIANS * azimuth);
    const double s (range);

    const double cosU1 (cos (U1));
    const double sinU1 (sin (U1));
    const double cosAlpha1 (cos (alpha1));
    const double sinAlpha1 (sin (alpha1));

    const double sigma1 (atan2 (tanU1, cosAlpha1));
    const double sinAlpha (cosU1 * sinAlpha1);
    const double sin2Alpha (sinAlpha * sinAlpha);
    const double cos2Alpha (1.0 - sin2Alpha);
    const double u2 (cos2Alpha * WGS84_A2_B2_1);
    const double A (1.0
                    + u2 / 16384.0
                         * (4096.0 + u2 * (-768.0 + u2 * (320.0 - 175.0 * u2))));
    const double B (u2 / 1024.0
                       * (256.0 + u2 * (-128.0 + u2 * (74.0 - 47.0 * u2))));

    // vincenty's modification
    /*
    const double k1 = (Math.sqrt(1+u2)-1)/(Math.sqrt(1+u2)+1);
    const double A = 1+((1.0d/4.0d)*(k1*k1));
    const double B = k1*(1-((3.0d/8.0d)*(k1*k1)));
    */

    double sigma (s / (WGS84_B * A));

    double _2sigmaM;
    double deltaSigma (0.0);
    double lastDeltaSigma (deltaSigma);

    double sinSigma;
    double cosSigma;
    double cos2sigmaM;
    double cos2sigmaMSquared;

    do
      {
        lastDeltaSigma = deltaSigma;

        sinSigma = sin (sigma);
        cosSigma = cos (sigma);

        _2sigmaM = 2 * sigma1 + sigma;

        cos2sigmaM = cos (_2sigmaM);
        cos2sigmaMSquared = cos2sigmaM * cos2sigmaM;

        deltaSigma = B * sinSigma
                       * (cos2sigmaM
                          + B / 4.0
                              * (cosSigma * (-1.0 + 2.0 * cos2sigmaMSquared)
                                 - (B / 6.0
                                      * cos2sigmaM
                                      * (-3.0 + 4.0 * sinSigma * sinSigma)
                                      * (-3.0 + 4.0 * cos2sigmaMSquared))));
        sigma = s / (WGS84_B * A) + deltaSigma;
      } while (std::abs (deltaSigma - lastDeltaSigma) > 1e-12);

    const double phi2
        (atan2 (sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1,
                (1.0 - WGS84_F)
                * sqrt (sin2Alpha
                        + pow (sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1,
                               2.0))));
    const double lambda (atan2 (sinSigma * sinAlpha1,
                                cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1));
    const double C (WGS84_F / 16.0
                            * cos2Alpha
                            * (4.0 + WGS84_F * (4.0 - 3.0 * cos2Alpha)));
    const double L (lambda
                    - (1.0 - C) * WGS84_F
                                * sinAlpha
                                * (sigma
                                   + C * sinSigma
                                       * (cos2sigmaM
                                          + C * cosSigma
                                              * (-1.0 + 2.0 * cos2sigmaMSquared))));
//    const double alpha2 = atan2(sinSigma, (-1 * sinU1*sinSigma) + (cosU1*cosSigma*cosAlpha1));

    to.latitude = phi2 / RADIANS;
    to.longitude = (L + lambda1) / RADIANS;
    to.altitude = p.altitude;
    to.altitudeRef = p.altitudeRef;
    to.ce90 = NAN;
    to.le90 = NAN;
}

bool
computeDirection(const core::GeoPoint &point1,
                 const core::GeoPoint &point2,
                 double distAzimuthOut[]){

    // XXX-- make static and thread safe
    const ::DistanceConstants dconst(atakmap::core::Ellipsoid::createWGS84());
    
    // Protect internal variables from change.
    const double long1 = RADIANS * point1.longitude;
    const double lat1 = RADIANS * point1.latitude;
    const double long2 = RADIANS * point2.longitude;
    const double lat2 = RADIANS * point2.latitude;
    /*
     * Solution of the geodetic inverse problem after T.Vincenty. Modified Rainsford's method
     * with Helmert's elliptical terms. Effective in any azimuth and at any distance short of
     * antipodal. Latitudes and longitudes in radians positive North and East. Forward azimuths
     * at both points returned in radians from North. Programmed for CDC-6600 by LCDR L.Pfeifer
     * NGS ROCKVILLE MD 18FEB75 Modified for IBM SYSTEM 360 by John G.Gergen NGS ROCKVILLE MD
     * 7507 Ported from Fortran to Java by Daniele Franzoni. Source:
     * org.geotools.referencing.GeodeticCalcultor see http://geotools.org
     */
    const double dlon = castToAngleRange(long2 - long1);
    const double ss = fabs(dlon);
    if (ss < TOLERANCE_1) {
        double distance = getMeridianArcLengthRadians(lat1, lat2, dconst);
        double azimuth = (lat2 > lat1) ? 0.0 : M_PI;
        distAzimuthOut[0] = distance;
        distAzimuthOut[1] = DEGREES * convertAngleToHeading(azimuth);
        return true;
    }
    /*
     * Computes the limit in longitude (alimit), it is equal to twice the distance from the
     * equator to the pole, as measured along the equator.
     */
    const double ESQP = dconst.eccentricitySquared / (1.0 - dconst.eccentricitySquared);
    const double alimit = M_PI * dconst.fo;
    if (ss >= alimit && lat1 < TOLERANCE_3 && lat1 > -TOLERANCE_3
        && lat2 < TOLERANCE_3 && lat2 > -TOLERANCE_3) {
        // Computes an approximate AZ
        const double CONS = (M_PI - ss) / (M_PI * dconst.f);
        double AZ = asin(CONS);
        double AZ_TEMP, S, AO;
        int iter = 0;
        do {
            if (++iter > 8) {
                /*error*/
                return false;
            }
            S = cos(AZ);
            const double C2 = S * S;
            // Compute new AO
            AO = dconst.T1 + dconst.T2 * C2 + dconst.T4 * C2 * C2 + dconst.T6 * C2 * C2 * C2;
            const double CS = CONS / AO;
            S = asin(CS);
            AZ_TEMP = AZ;
            AZ = S;
        } while (fabs(S - AZ_TEMP) >= TOLERANCE_2);
        
        const double AZ1 = (dlon < 0.0) ? 2.0 * M_PI - S : S;
        double azimuth = castToAngleRange(AZ1);
        S = cos(AZ1);
        
        // Equatorial - geodesic(S-s) SMS
        const double U2 = ESQP * S * S;
        const double U4 = U2 * U2;
        const double U6 = U4 * U2;
        const double U8 = U6 * U2;
        const double BO = 1.0 + 0.25 * U2 + 0.046875 * U4 + 0.01953125 * U6
        + -0.01068115234375 * U8;
        S = sin(AZ1);
        const double SMS = dconst.semiMajorAxis * M_PI
        * (1.0 - dconst.f * fabs(S) * AO - BO * dconst.fo);
        double distance = dconst.semiMajorAxis * ss - SMS;
        distAzimuthOut[0] = distance;
        distAzimuthOut[1] = DEGREES * convertAngleToHeading(azimuth);
        return true;
    }
    
    // the reduced latitudes
    const double u1 = atan(dconst.fo * sin(lat1) / cos(lat1));
    const double u2 = atan(dconst.fo * sin(lat2) / cos(lat2));
    const double su1 = sin(u1);
    const double cu1 = cos(u1);
    const double su2 = sin(u2);
    const double cu2 = cos(u2);
    double xy, w, q2, q4, q6, r2, r3, sig, ssig, slon, clon, sinalf, ab = dlon;
    int kcount = 1;
    do {
        clon = cos(ab);
        slon = sin(ab);
        const double csig = su1 * su2 + cu1 * cu2 * clon;
        ssig = hypot(slon * cu2, su2 * cu1 - su1 * cu2 * clon);
        sig = atan2(ssig, csig);
        sinalf = cu1 * cu2 * slon / ssig;
        w = (1.0 - sinalf * sinalf);
        const double t4 = w * w;
        const double t6 = w * t4;
        
        // the coefficents of type a
        const double ao = dconst.f + dconst.a01 * w + dconst.a02 * t4 + dconst.a03 * t6;
        const double a2 = dconst.a21 * w + dconst.a22 * t4 + dconst.a23 * t6;
        const double a4 = dconst.a42 * t4 + dconst.a43 * t6;
        const double a6 = dconst.a63 * t6;
        
        // the multiple angle functions
        double qo = 0.0;
        if (w > TOLERANCE_0) {
            qo = -2.0 * su1 * su2 / w;
        }
        q2 = csig + qo;
        q4 = 2.0 * q2 * q2 - 1.0;
        q6 = q2 * (4.0 * q2 * q2 - 3.0);
        r2 = 2.0 * ssig * csig;
        r3 = ssig * (3.0 - 4.0 * ssig * ssig);
        
        // the longitude difference
        const double s = sinalf
        * (ao * sig + a2 * ssig * q2 + a4 * r2 * q4 + a6 * r3 * q6);
        double xz = dlon + s;
        xy = fabs(xz - ab);
        ab = dlon + s;
        if (++kcount > MAX_CONVERGENCE_ITERATIONS) {
            /*Log.w(TAG, "Distance calculation failed to converge within "
                  + MAX_CONVERGENCE_ITERATIONS
                  + " iterations. Returning imprecise result.");*/
            break;
        }
    } while (xy >= TOLERANCE_1);
    
    const double z = ESQP * w;
    const double bo = 1.0
    + z
    * (1.0 / 4.0 + z
       * (-3.0 / 64.0 + z
          * (5.0 / 256.0 - z * (175.0 / 16384.0))));
    const double b2 = z
    * (-1.0 / 4.0 + z
       * (1.0 / 16.0 + z
          * (-15.0 / 512.0 + z * (35.0 / 2048.0))));
    const double b4 = z * z
    * (-1.0 / 128.0 + z * (3.0 / 512.0 - z * (35.0 / 8192.0)));
    const double b6 = z * z * z * (-1.0 / 1536.0 + z * (5.0 / 6144.0));
    
    // The distance in ellispoid axis units.
    double distance = dconst.semiMinorAxis
    * (bo * sig + b2 * ssig * q2 + b4 * r2 * q4 + b6 * r3 * q6);
    double az1 = (dlon < 0) ? M_PI * (3.0 / 2.0) : M_PI / 2;
    
    // now compute the az1 & az2 for latitudes not on the equator
    if ((fabs(su1) >= TOLERANCE_0) || (fabs(su2) >= TOLERANCE_0)) {
        const double tana1 = slon * cu2 / (su2 * cu1 - clon * su1 * cu2);
        const double sina1 = sinalf / cu1;
        
        // azimuths from north,longitudes positive east
        az1 = atan2(sina1, sina1 / tana1);
    }
    double azimuth = castToAngleRange(az1);
    distAzimuthOut[0] = distance;
    distAzimuthOut[1] = DEGREES * convertAngleToHeading(azimuth);
    return true;
}

}                                       // Close distance namespace.
}                                       // Close util namespace.
}                                       // Close atakmap namespace.

namespace {
    DistanceConstants::DistanceConstants(const atakmap::core::Ellipsoid &e)
    : semiMajorAxis(e.semiMajorAxis),
    semiMinorAxis(e.semiMinorAxis) {
    
        /* calculation of GPNHRI parameters */
        f = (semiMajorAxis - semiMinorAxis) / semiMajorAxis;
        fo = 1.0 - f;
        f2 = f * f;
        f3 = f * f2;
        f4 = f * f3;
        eccentricitySquared = f * (2.0 - f);
        
        /* Calculation of GNPARC parameters */
        const double E2 = eccentricitySquared;
        const double E4 = E2 * E2;
        const double E6 = E4 * E2;
        const double E8 = E6 * E2;
        const double EX = E8 * E2;
        
        A = 1.0 + 0.75 * E2 + 0.703125 * E4 + 0.68359375 * E6
        + 0.67291259765625 * E8 + 0.6661834716796875 * EX;
        B = 0.75 * E2 + 0.9375 * E4 + 1.025390625 * E6 + 1.07666015625 * E8
        + 1.1103057861328125 * EX;
        C = 0.234375 * E4 + 0.41015625 * E6 + 0.538330078125 * E8
        + 0.63446044921875 * EX;
        D = 0.068359375 * E6 + 0.15380859375 * E8 + 0.23792266845703125 * EX;
        E = 0.01922607421875 * E8 + 0.0528717041015625 * EX;
        F = 0.00528717041015625 * EX;
        
        maxOrthodromicDistance = semiMajorAxis * (1.0 - E2) * M_PI * A - 1.0;
        
        T1 = 1.0;
        T2 = -0.25 * f * (1.0 + f + f2);
        T4 = 0.1875 * f2 * (1.0 + 2.25 * f);
        T6 = 0.1953125 * f3;
        
        const double a = f3 * (1.0 + 2.25 * f);
        a01 = -f2 * (1.0 + f + f2) / 4.0;
        a02 = 0.1875 * a;
        a03 = -0.1953125 * f4;
        a21 = -a01;
        a22 = -0.25 * a;
        a23 = 0.29296875 * f4;
        a42 = 0.03125 * a;
        a43 = 0.05859375 * f4;
        a63 = 5.0 * f4 / 768.0;
    }
    
    double getMeridianArcLengthRadians(double P1, double P2, const DistanceConstants &dconst) {
        /*
         * Latitudes P1 and P2 in radians positive North and East. Forward azimuths at both points
         * returned in radians from North. Source: org.geotools.referencing.GeodeticCalcultor see
         * http://geotools.org
         */
        double S1 = fabs(P1);
        double S2 = fabs(P2);
        double DA = (P2 - P1);
        // Check for a 90 degree lookup
        if (S1 > TOLERANCE_0 || S2 <= (M_PI / 2 - TOLERANCE_0)
            || S2 >= (M_PI / 2 + TOLERANCE_0)) {
            const double DB = sin(P2 * 2.0) - sin(P1 * 2.0);
            const double DC = sin(P2 * 4.0) - sin(P1 * 4.0);
            const double DD = sin(P2 * 6.0) - sin(P1 * 6.0);
            const double DE = sin(P2 * 8.0) - sin(P1 * 8.0);
            const double DF = sin(P2 * 10.0) - sin(P1 * 10.0);
            // Compute the S2 part of the series expansion
            S2 = -DB * dconst.B / 2.0 + DC * dconst.C / 4.0 - DD * dconst.D / 6.0 + DE * dconst.E / 8.0
            - DF * dconst.F / 10.0;
        } else {
            S2 = 0;
        }
        // Compute the S1 part of the series expansion
        S1 = DA * dconst.A;
        // Compute the arc length
        return fabs(dconst.semiMajorAxis * (1.0 - dconst.eccentricitySquared) * (S1 + S2));
    }

}

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
