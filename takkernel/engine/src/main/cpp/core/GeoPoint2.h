#ifndef TAK_ENGINE_CORE_GEOPOINT2_H_INCLUDED
#define TAK_ENGINE_CORE_GEOPOINT2_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Core
        {

            enum AltitudeReference
            {
                HAE, AGL, INDICATED, UNKNOWN
            };

            class ENGINE_API GeoPoint2
            {
            public:
                GeoPoint2() NOTHROWS;
                GeoPoint2(const double lat, const double lng) NOTHROWS;
                GeoPoint2(const double lat, const double lng, const double alt, const AltitudeReference altRef) NOTHROWS;
                GeoPoint2(const double lat, const double lng, const double alt, const AltitudeReference altRef, const double ce, const double le) NOTHROWS;
                ~GeoPoint2() NOTHROWS;
            public :
                bool operator==(const GeoPoint2& rhs) const;
                bool operator<(const GeoPoint2& rhs) const;
            public:
                double latitude;
                double longitude;
                double altitude;
                AltitudeReference altitudeRef;
                double ce90;
                double le90;
            };

            typedef std::unique_ptr<GeoPoint2, void(*)(const GeoPoint2 *)> GeoPoint2Ptr;
            typedef std::unique_ptr<const GeoPoint2, void(*)(const GeoPoint2 *)> GeoPoint2Ptr_const;

            ENGINE_API double GeoPoint2_slantAngle(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS;
            ENGINE_API double GeoPoint2_slantDistance(const GeoPoint2 &a, const GeoPoint2 &b) NOTHROWS;
            ENGINE_API GeoPoint2 GeoPoint2_slantMidpoint(const GeoPoint2 &a, const GeoPoint2 &b) NOTHROWS;

            ENGINE_API double GeoPoint2_distance(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS;
            ENGINE_API double GeoPoint2_bearing(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS;
            ENGINE_API GeoPoint2 GeoPoint2_midpoint(const GeoPoint2 &a, const GeoPoint2 &b, const bool quick) NOTHROWS;
            ENGINE_API GeoPoint2 GeoPoint2_pointAtDistance(const GeoPoint2 &a, const double az, const double distance, const bool quick) NOTHROWS;
            ENGINE_API GeoPoint2 GeoPoint2_pointAtDistance(const GeoPoint2 &a, const double az, const double distance, const double inclination, const bool quick) NOTHROWS;
            ENGINE_API GeoPoint2 GeoPoint2_pointAtDistance(const GeoPoint2 &a, const GeoPoint2 &b, const double weight, const bool quick) NOTHROWS;

            ENGINE_API double GeoPoint2_approximateMetersPerDegreeLatitude(const double latitude) NOTHROWS;
            ENGINE_API double GeoPoint2_approximateMetersPerDegreeLongitude(const double latitude) NOTHROWS;

            ENGINE_API TAK::Engine::Util::TAKErr GeoPoint2_lobIntersection(GeoPoint2 &intersection, const GeoPoint2 &p1, const double &brng1, const GeoPoint2 &p2, const double &brng2) NOTHROWS;
            ENGINE_API double GeoPoint2_alongTrackDistance(const GeoPoint2 &start, const GeoPoint2 &end, const GeoPoint2 &p, const bool quick) NOTHROWS;

            ENGINE_API double GeoPoint2_distanceToHorizon(const double altitudeMsl) NOTHROWS;
        }
    }
}

#endif // TAK_ENGINE_CORE_GEOPOINT2_H_INCLUDED
