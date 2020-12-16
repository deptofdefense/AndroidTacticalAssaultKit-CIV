#include "core/GeoPoint2.h"

#include <algorithm>
#include <cmath>
#include <limits>

#include "core/ProjectionFactory3.h"
#include "core/Datum2.h"
#include "core/GeoPoint.h"
#include "elevation/ElevationManager.h"
#include "util/Distance.h"
#include "math/Vector4.h"
#include "math/Plane2.h"
#include "math/Ray2.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    double greatCircleBearing(const double lat1, const double lng1, const double lat2, const double lng2) NOTHROWS
    {
        //https://www.movable-type.co.uk/scripts/latlong.html
        const double rlat1 = lat1 / 180.0*M_PI;
        const double rlng1 = lng1 / 180.0*M_PI;
        const double rlat2 = lat2 / 180.0*M_PI;
        const double rlng2 = lng2 / 180.0*M_PI;

        const double rbrng = atan2(sin(rlng2 - rlng1) * cos(rlat2), (cos(rlat1)*sin(rlat2)) - (sin(rlat1)*cos(rlat2)*cos(rlng2-rlng1)));
        return rbrng / M_PI*180.0;
    }

    double greatCircleDistance(const double lat1, const double lng1, const double lat2, const double lng2) NOTHROWS
    {
        //https://www.movable-type.co.uk/scripts/latlong.html
        const double R = Datum2::WGS84.reference.semiMajorAxis;
        const double rlat1 = lat1 / 180.0*M_PI;
        const double rlng1 = lng1 / 180.0*M_PI;
        const double rlat2 = lat2 / 180.0*M_PI;
        const double rlng2 = lng2 / 180.0*M_PI;

        const double sin_dlat_div_2 = sin((rlat2 - rlat1) / 2.0);
        const double sin_dlng_div_2 = sin((rlng2 - rlng1) / 2.0);
        const double a = (sin_dlat_div_2*sin_dlat_div_2) + (cos(rlat1)*cos(rlat2)*(sin_dlng_div_2*sin_dlng_div_2));

        const double c = 2 * atan2(sqrt(a), sqrt(1.0-a));

        const double d = R * c;
        return d;
    }

    GeoPoint2 greatCirclePointAtDistance(const double lat1, const double lng1, const double brng, const double d) NOTHROWS
    {
        //https://www.movable-type.co.uk/scripts/latlong.html
        const double R = Datum2::WGS84.reference.semiMajorAxis;
        const double rlat1 = lat1 / 180.0*M_PI;
        const double rlng1 = lng1 / 180.0*M_PI;

        const double rlat2 = asin( sin(rlat1)*cos(d/R) + cos(rlat1)*sin(d/R)*cos(brng/180.0*M_PI) );
        const double rlng2 = rlng1 + atan2(sin(brng/180.0*M_PI)*sin(d/R)*cos(rlat1), cos(d/R)-sin(rlat1)*sin(rlat2));
        return GeoPoint2(rlat2 / M_PI * 180.0, rlng2 / M_PI * 180.0);
    }

    GeoPoint2 hae(const GeoPoint2 &p) NOTHROWS
    {
        GeoPoint2 retval(p);
        retval.altitudeRef = AltitudeReference::HAE;
        if (isnan(p.altitude) || p.altitudeRef == AltitudeReference::HAE) {
            // no-op
        } else {
            // AGL
            retval.altitude = NAN;
        }
        return retval;
    }
}

GeoPoint2::GeoPoint2() NOTHROWS :
    latitude(NAN),
    longitude(NAN),
    altitude(NAN),
    altitudeRef(AltitudeReference::HAE),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint2::GeoPoint2(const double lat, const double lng) NOTHROWS :
    latitude(lat),
    longitude(lng),
    altitude(NAN),
    altitudeRef(AltitudeReference::HAE),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint2::GeoPoint2(const double lat, const double lng, const double alt, const AltitudeReference altRef) NOTHROWS :
    latitude(lat),
    longitude(lng),
    altitude(alt),
    altitudeRef(altRef),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint2::GeoPoint2(const double lat, const double lng, const double alt, const AltitudeReference altRef, const double ce, const double le) NOTHROWS :
    latitude(lat),
    longitude(lng),
    altitude(alt),
    altitudeRef(altRef),
    ce90(ce),
    le90(le)
{}

GeoPoint2::~GeoPoint2() NOTHROWS
{}

bool GeoPoint2::operator==(const GeoPoint2& rhs) const
{
    constexpr double epsilon = std::numeric_limits<double>::epsilon();
    bool bEquals = (altitudeRef == rhs.altitudeRef);
    bEquals = bEquals && (fabs(latitude - rhs.latitude) < epsilon);
    bEquals = bEquals && (fabs(longitude - rhs.longitude) < epsilon);
    bEquals = bEquals && (fabs(altitude - rhs.altitude) < epsilon);
    return bEquals;
}

bool GeoPoint2::operator<(const GeoPoint2& rhs) const
{
    // Order by latitude and longitude with longitude being dominant.
    // This is a fairly simplistic algorithm for doing 2D sorting like this,
    // but it is really only implemented to allow this class to be used
    // with std containers such as map. If strict ordering is very important,
    // this algorithm may need to be revisited.
    double leftMetric = (latitude + 90) + ((longitude + 180) * 1000);
    double rightMetric = (rhs.latitude + 90) + ((rhs.longitude + 180) * 1000);

    return leftMetric < rightMetric;
}

double TAK::Engine::Core::GeoPoint2_slantAngle(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS
{
    double angRads;
    if (quick)
    {
        if (isnan(a.altitude) || isnan(b.altitude))
            return NAN;
        double dist = GeoPoint2_distance(a, b, true);
        double altDiff = b.altitude - a.altitude;
        double hyp = std::sqrt(std::pow(dist, 2) + std::pow(altDiff, 2));
        angRads = std::acos(dist / hyp);
        if (altDiff < 0) angRads *= -1;
    }
    else
    {
        TAKErr code(TE_Ok);
        Projection2Ptr ecef(nullptr, nullptr);
        code = ProjectionFactory3_create(ecef, 4978);
        if (code != TE_Ok)
            return NAN;

        Point2<double> origin(0, 0, 0);
        Point2<double> ptA, ptB;
        ecef->forward(&ptA, a);
        ecef->forward(&ptB, b);

        TAK::Engine::Math::Vector4<double> vecOd(ptB.x - origin.x, ptB.y - origin.y, ptB.z - origin.z);
        TAK::Engine::Math::Vector4<double> vecOs(ptA.x - origin.x, ptA.y - origin.y, ptA.z - origin.z);
        TAK::Engine::Math::Vector4<double> vecSd(ptB.x - ptA.x, ptB.y - ptA.y, ptB.z - ptA.z);
        TAK::Engine::Math::Vector4<double> vecOsNormal = vecOs;
        vecOsNormal.normalize(&vecOsNormal);
        TAK::Engine::Math::Plane2 planeSt(vecOsNormal, TAK::Engine::Math::Point2<double>(ptA.x, ptA.y));
        vecOd.normalize(&vecOd);
        TAK::Engine::Math::Ray2<double> ray(origin, vecOd);
        TAK::Engine::Math::Point2<double> ptIntersect(0, 0, 0);
        planeSt.intersect(&ptIntersect, ray);
        TAK::Engine::Math::Vector4<double> vecSt(ptIntersect.x - ptA.x, ptIntersect.y - ptA.y, ptIntersect.z - ptA.z);
        vecSd.normalize(&vecSd);
        vecSt.normalize(&vecSt);

        angRads = -vecSt.dot(&vecSd);
    }
#define TO_DEGREES(x) ((x)*(180.0/M_PI))
    return TO_DEGREES(angRads);
#undef TO_DEGREES
}

double TAK::Engine::Core::GeoPoint2_slantDistance(const GeoPoint2 &a, const GeoPoint2 &b) NOTHROWS
{
    TAKErr code(TE_Ok);
    Projection2Ptr ecef(nullptr, nullptr);
    code = ProjectionFactory3_create(ecef, 4978);
    if(code != TE_Ok)
      return NAN;

    Point2<double> ptA, ptB;
    ecef->forward(&ptA, a);
    ecef->forward(&ptB, b);

    return std::sqrt(std::pow(ptB.x - ptA.x, 2) + std::pow(ptB.y - ptA.y, 2) + std::pow(ptB.z - ptA.z, 2));
}
GeoPoint2 TAK::Engine::Core::GeoPoint2_slantMidpoint(const GeoPoint2 &a, const GeoPoint2 &b) NOTHROWS
{
    TAKErr code(TE_Ok);
    do {
        Projection2Ptr ecef(nullptr, nullptr);
        code = ProjectionFactory3_create(ecef, 4978);
        TE_CHECKBREAK_CODE(code);

        Point2<double> ptA, ptB;
        code = ecef->forward(&ptA, a);
        TE_CHECKBREAK_CODE(code);
        code = ecef->forward(&ptB, b);
        TE_CHECKBREAK_CODE(code);

        GeoPoint2 retval;
        code = ecef->inverse(&retval, Point2<double>((ptA.x+ptB.x)/2.0, (ptA.y+ptB.y)/2.0, (ptA.z+ptB.z)/2.0));
        TE_CHECKBREAK_CODE(code);

        return retval;
    } while(false);

    Logger_log(TELL_Warning, "Failed to compute accurate midpoint a={%lf, %lf, %lf} b={%lf, %lf, %lf}, approximating", a.latitude, a.longitude, a.altitude, b.latitude, b.longitude, b.altitude);
    GeoPoint2 surfaceMidpoint = GeoPoint2_midpoint(a, b, false);
    surfaceMidpoint.altitude = (hae(a).altitude+hae(b).altitude) / 2.0;
    surfaceMidpoint.altitudeRef = AltitudeReference::HAE;
    return surfaceMidpoint;
}

double TAK::Engine::Core::GeoPoint2_distance(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS
{
    if (quick)
        return greatCircleDistance(a.latitude, a.longitude, b.latitude, b.longitude);
    else
        return atakmap::util::distance::calculateRange(atakmap::core::GeoPoint(a), atakmap::core::GeoPoint(b));
}
double TAK::Engine::Core::GeoPoint2_bearing(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS
{
    double result[2];
    if (!quick && atakmap::util::distance::computeDirection(atakmap::core::GeoPoint(a), atakmap::core::GeoPoint(b), result))
        return result[1];
    return greatCircleBearing(a.latitude, a.longitude, b.latitude, b.longitude);
}
GeoPoint2 TAK::Engine::Core::GeoPoint2_midpoint(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS
{
    return GeoPoint2_pointAtDistance(a, GeoPoint2_bearing(a, b, quick), GeoPoint2_distance(a, b, quick) / 2.0, quick);
}
GeoPoint2 TAK::Engine::Core::GeoPoint2_pointAtDistance(const GeoPoint2 &a, const double az, const double distance, const bool quick) NOTHROWS
{
    return GeoPoint2_pointAtDistance(a, az, distance, 0.0, quick);
}
GeoPoint2 TAK::Engine::Core::GeoPoint2_pointAtDistance(const GeoPoint2& a, const double az, const double distance, const double inclination, const bool quick) NOTHROWS
{
    GeoPoint2 retval;
    if (quick) {
        retval = greatCirclePointAtDistance(a.latitude, a.longitude, az, distance);
        retval.altitude = a.altitude;
        retval.altitudeRef = a.altitudeRef;
    } else {
        atakmap::core::GeoPoint result;
        atakmap::util::distance::pointAtRange(atakmap::core::GeoPoint(a), distance, az, result);
        atakmap::core::GeoPoint_adapt(&retval, result);
    }

    // adjust destination altitude based on inclination angle
    if (!isnan(inclination) && inclination && !isnan(retval.altitude))
    {
        // XXX - this is not correct math as it assumes no curvature, better
        //       API would be to express the rate of altitude change over
        //       distance
        double elevation = tan(inclination * M_PI / 180.0) * distance;

        // adjust
        retval.altitude += elevation;
    }

    return retval;
}

double TAK::Engine::Core::GeoPoint2_approximateMetersPerDegreeLongitude(const double latitude) NOTHROWS
{
    const double rlat = latitude / 180.0 * M_PI;
    return 111412.84 * cos(rlat) - 93.5 * cos(3 * rlat);
}

double TAK::Engine::Core::GeoPoint2_approximateMetersPerDegreeLatitude(const double latitude) NOTHROWS
{
    const double rlat = latitude /180.0 * M_PI;
    return 111132.92 - 559.82 * cos(2 * rlat) + 1.175 * cos(4 * rlat);
}


TAKErr TAK::Engine::Core::GeoPoint2_lobIntersection(GeoPoint2 &intersection, const GeoPoint2 &p1, const double &brng1, const GeoPoint2 &p2, const double &brng2) NOTHROWS
{
    // informed by https://www.movable-type.co.uk/scripts/latlong-vectors.html#intersection
    // ported from https://github.com/chrisveness/geodesy/blob/master/latlon-spherical.js
    // see www.edwilliams.org/avform.htm#Intersection

    static const double epsilon = std::numeric_limits<double>::denorm_min();

    const double phi1 = p1.latitude / 180.0 * M_PI, lambda1 = p1.longitude / 180.0 * M_PI;
    const double phi2 = p2.latitude / 180.0 * M_PI, lambda2 = p2.longitude / 180.0 * M_PI;
    const double theta13 = brng1 / 180.0 * M_PI, theta23 = brng2 / 180.0 * M_PI;
    const double delPhi = phi2 - phi1, delLambda = lambda2 - lambda1;

    // angular distance p1-p2
    const double sigma12 = 2.0 * asin(sqrt(sin(delPhi/2) * sin(delPhi/2)
                                           + cos(phi1) * cos(phi2) * sin(delLambda/2) * sin(delLambda/2)));
    if (abs(sigma12) < epsilon) {
        // coincident points
        intersection.latitude = p1.latitude;
        intersection.longitude = p1.longitude;
        return TE_Ok;
    }

    // initial/final bearings between points
    const double cosThetaA = (sin(phi2) - sin(phi1) * cos(sigma12)) / (sin(sigma12) * cos(phi1));
    const double cosThetaB = (sin(phi1) - sin(phi2) * cos(sigma12)) / (sin(sigma12) * cos(phi2));
    const double thetaA = acos(std::min(std::max(cosThetaA, -1.0), 1.0)); // protect against rounding errors
    const double thetaB = acos(std::min(std::max(cosThetaB, -1.0), 1.0)); // protect against rounding errors

    const double sinDelLambda = sin(delLambda);
    const double theta12 = sinDelLambda > 0 ? thetaA : 2 * M_PI - thetaA;
    const double theta21 = sinDelLambda > 0 ? 2 * M_PI - thetaB : thetaB;

    const double alpha1 = theta13 - theta12; // angle 2-1-3
    const double alpha2 = theta21 - theta23; // angle 1-2-3

    if (sin(alpha1) == 0 && sin(alpha2) == 0) return TE_IllegalState; // infinite intersections
    if (sin(alpha1) * sin(alpha2) < 0) return TE_IllegalState;        // ambiguous intersection (antipodal?)

    const double cosAlpha3 = -cos(alpha1) * cos(alpha2) + sin(alpha1) * sin(alpha2) * cos(sigma12);

    const double sigma13 = atan2(sin(sigma12) * sin(alpha1) * sin(alpha2), cos(alpha2) + cos(alpha1) * cosAlpha3);

    const double phi3 = asin(sin(phi1) * cos(sigma13) + cos(phi1) * sin(sigma13) * cos(theta13));

    const double delLambda13 = atan2(sin(theta13) * sin(sigma13) * cos(phi1), cos(sigma13) - sin(phi1) * sin(phi3));
    const double lambda3 = lambda1 + delLambda13;

    intersection.latitude = phi3 / M_PI * 180.0;
    intersection.longitude = lambda3 / M_PI * 180.0;

    return TE_Ok;
}
