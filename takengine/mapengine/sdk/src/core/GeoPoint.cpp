#include "core/GeoPoint.h"

using namespace atakmap::core;

GeoPoint::GeoPoint() :
    latitude(NAN),
    longitude(NAN),
    altitude(NAN),
    altitudeRef(AltitudeReference::HAE),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint::GeoPoint(const double lat, const double lng) :
    latitude(lat),
    longitude(lng),
    altitude(NAN),
    altitudeRef(AltitudeReference::HAE),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint::GeoPoint(const double lat, const double lng, const double alt, const AltitudeReference altRef) :
    latitude(lat),
    longitude(lng),
    altitude(alt),
    altitudeRef(altRef),
    ce90(NAN),
    le90(NAN)
{}

GeoPoint::GeoPoint(const double lat, const double lng, const double alt, const AltitudeReference altRef, const double ce, const double le) :
    latitude(lat),
    longitude(lng),
    altitude(alt),
    altitudeRef(altRef),
    ce90(ce),
    le90(le)
{}

GeoPoint::GeoPoint(const TAK::Engine::Core::GeoPoint2 &other) :
    latitude(other.latitude),
    longitude(other.longitude),
    altitude(other.altitude),
    altitudeRef(AltitudeReference::UNKNOWN),
    ce90(other.ce90),
    le90(other.le90)
{
    switch (other.altitudeRef)
    {
    case TAK::Engine::Core::AltitudeReference::AGL:
        this->altitudeRef = AltitudeReference::AGL;
        break;
    case TAK::Engine::Core::AltitudeReference::HAE:
        this->altitudeRef = AltitudeReference::HAE;
        break;
    case TAK::Engine::Core::AltitudeReference::INDICATED:
        this->altitudeRef = AltitudeReference::INDICATED;
        break;
    case TAK::Engine::Core::AltitudeReference::UNKNOWN:
    default:
        this->altitudeRef = AltitudeReference::UNKNOWN;
        break;
    }
}

GeoPoint::~GeoPoint()
{}

bool GeoPoint::operator==(const GeoPoint& rhs) const
{
    constexpr double epsilon = std::numeric_limits<double>::epsilon();
    bool bEquals = (altitudeRef == rhs.altitudeRef);
    bEquals = bEquals && (fabs(latitude - rhs.latitude) < epsilon);
    bEquals = bEquals && (fabs(longitude - rhs.longitude) < epsilon);
    bEquals = bEquals && (fabs(altitude - rhs.altitude) < epsilon);
    return bEquals;
}

bool GeoPoint::operator<(const GeoPoint& rhs) const
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

bool GeoPoint::isValid() const
{
    return !isnan(latitude)
        && !isnan(longitude)
#if 0
        && fabs(latitude) <= 90
        && fabs(longitude) <= 180
#endif
        ;
}

void GeoPoint::set(double lat, double lon, double alt, AltitudeReference alt_ref, double ce_90, double le_90) {
    this->latitude = lat;
    this->longitude = lon;
    this->altitude = alt;
    this->altitudeRef = alt_ref;
    this->ce90 = ce_90;
    this->le90 = le_90;
}

void GeoPoint::set(double lat, double lon)
{
    this->latitude = lat;
    this->longitude = lon;
}

//Converts the given GeoPoint to a Geopoint2 object, made to eliminate the code block from being utilized multiple times
void atakmap::core::GeoPoint_adapt(TAK::Engine::Core::GeoPoint2* gp2, const GeoPoint& gp)
{
    gp2->latitude = gp.latitude;
    gp2->longitude = gp.longitude;
    gp2->altitude = gp.altitude;
    gp2->ce90 = gp.ce90;
    gp2->le90 = gp.le90;
    if (gp.altitudeRef == atakmap::core::AltitudeReference::AGL)
        gp2->altitudeRef = TAK::Engine::Core::AltitudeReference::AGL;
    else if (gp.altitudeRef == atakmap::core::AltitudeReference::HAE)
        gp2->altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;
    else if (gp.altitudeRef == atakmap::core::AltitudeReference::INDICATED)
        gp2->altitudeRef = TAK::Engine::Core::AltitudeReference::INDICATED;
    else
        gp2->altitudeRef = TAK::Engine::Core::AltitudeReference::UNKNOWN;
}
