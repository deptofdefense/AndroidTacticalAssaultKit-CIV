#ifndef ATAKMAP_CORE_GEO_POINT_H_INCLUDED
#define ATAKMAP_CORE_GEO_POINT_H_INCLUDED

#include <cmath>
#include <limits>
#include "core/GeoPoint2.h"

namespace atakmap
{
namespace core
{

enum class AltitudeReference
{
    HAE, MSL, AGL, INDICATED, UNKNOWN
};

class ENGINE_API GeoPoint
{
public :
    GeoPoint();
    GeoPoint(const double lat, const double lng);
    GeoPoint(const double lat, const double lng, const double alt, const AltitudeReference altRef);
    GeoPoint(const double lat, const double lng, const double alt, const AltitudeReference altRef, const double ce, const double le);
    GeoPoint(const TAK::Engine::Core::GeoPoint2 &other);
    ~GeoPoint();
    bool operator==(const GeoPoint& rhs) const;
    bool operator<(const GeoPoint& rhs) const;
    bool isValid () const;
    void set(double lat, double lon, double alt, AltitudeReference altRef, double ce90, double le90);
    void set(double lat, double lon);
public:
    double latitude;
    double longitude;
    double altitude;
    AltitudeReference altitudeRef;
    double ce90;
    double le90;
};

ENGINE_API void GeoPoint_adapt(TAK::Engine::Core::GeoPoint2* gp2, const GeoPoint& gp);

} // end namespace atakmap::core
} // end namespace atakmap


#endif // ATAKMAP_CORE_GEO_POINT_H_INCLUDED
