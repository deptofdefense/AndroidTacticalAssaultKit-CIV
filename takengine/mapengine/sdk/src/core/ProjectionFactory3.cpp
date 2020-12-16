
#include "core/ProjectionFactory3.h"

#include <cmath>
#include <set>

#include "core/Datum2.h"
#include "core/GeoPoint2.h"
#include "elevation/ElevationManager.h"
#include "math/Point2.h"
#include "thread/Lock.h"
#include "util/Error.h"
#include "util/Memory.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace // unnamed namespace
{

#define PROJ2_CLASS_DECL(clazz) \
    class clazz : public Projection2 \
    { \
    public: \
        virtual int getSpatialReferenceID() const NOTHROWS; \
        virtual TAKErr forward(Point2<double> *value, const GeoPoint2 &geo) const NOTHROWS; \
        virtual TAKErr inverse(GeoPoint2 *value, const Point2<double> &proj) const NOTHROWS; \
        virtual double getMinLatitude() const NOTHROWS; \
        virtual double getMaxLatitude() const NOTHROWS; \
        virtual double getMinLongitude() const NOTHROWS; \
        virtual double getMaxLongitude() const NOTHROWS; \
        virtual bool is3D() const NOTHROWS; \
    };

    PROJ2_CLASS_DECL(EquirectangularProjection);
    PROJ2_CLASS_DECL(WebMercatorProjection);
    PROJ2_CLASS_DECL(EcefWGS84);

    class InternalProjectionSpi : public ProjectionSpi3
    {
    public:
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS override;
    private:
        EquirectangularProjection proj4326;
        WebMercatorProjection proj3857;
        EcefWGS84 proj4978;
    };

    InternalProjectionSpi sdkSpi;

}; // end unnamed namespace


/******************************************************************************/
// Projection Factory definition
namespace
{
    struct SpiRegistryEntry
    {
        std::shared_ptr<ProjectionSpi3> spi;
        int priority{0};
    };

    struct SpiRegistryComparator
    {
        bool operator()(const SpiRegistryEntry &a, const SpiRegistryEntry &b) const
        {
            if (a.priority == b.priority)
                return a.spi.get() < b.spi.get();
            else
                return a.priority < b.priority;
        }
    };

    std::set<SpiRegistryEntry, SpiRegistryComparator> &spis() NOTHROWS
    {
        static std::set<SpiRegistryEntry, SpiRegistryComparator> s;
        return s;
    }
    Mutex &factoryMutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }

    bool sdkPreferred = true;
}

TAKErr TAK::Engine::Core::ProjectionFactory3_create(Projection2Ptr &value, const int srid) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(factoryMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    // if the SDK implementations are preferred, see if the projection can be
    // created before deferring to the client-registered SPIs
    if (sdkPreferred && (sdkSpi.create(value, srid) == TE_Ok))
        return TE_Ok;

    std::set<SpiRegistryEntry, SpiRegistryComparator> &registry = spis();
    std::set<SpiRegistryEntry, SpiRegistryComparator>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++) {
        if ((*it).spi->create(value, srid) == TE_Ok)
            return TE_Ok;
    }
    // if the SDK implementations are not preferred, try to obtain the
    // projection if none of the other SPIs could provide
    if (!sdkPreferred && (sdkSpi.create(value, srid) == TE_Ok))
        return TE_Ok;

    return TE_InvalidArg;
}
TAKErr TAK::Engine::Core::ProjectionFactory3_registerSpi(ProjectionSpi3Ptr &&spi, const int priority) NOTHROWS
{
    std::shared_ptr<ProjectionSpi3> spi_shared(std::move(spi));
    return ProjectionFactory3_registerSpi(spi_shared, priority);
}
TAKErr TAK::Engine::Core::ProjectionFactory3_registerSpi(const std::shared_ptr<ProjectionSpi3> &spi, const int priority) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(factoryMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    SpiRegistryEntry entry;
    entry.spi = spi;
    entry.priority = priority;

    // XXX - need to check for pointer collision on different priority???
    spis().insert(entry);

    return TE_Ok;
}

TAKErr TAK::Engine::Core::ProjectionFactory3_unregisterSpi(const ProjectionSpi3 &spi) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(factoryMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<SpiRegistryEntry, SpiRegistryComparator> &registry = spis();
    std::set<SpiRegistryEntry, SpiRegistryComparator>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++) {
        if ((*it).spi.get() == &spi) {
            registry.erase(it);
            return TE_Ok;
        }
    }

    return TE_InvalidArg;
}

TAKErr TAK::Engine::Core::ProjectionFactory3_setPreferSdkProjections(const bool sdk) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(factoryMutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    sdkPreferred = sdk;

    return TE_Ok;
}

/*****************************************************************************/

namespace
{

    int EquirectangularProjection::getSpatialReferenceID() const NOTHROWS
    {
        return 4326;
    }
    TAKErr EquirectangularProjection::forward(Point2<double> *value, const GeoPoint2 &geo) const NOTHROWS
    {
        value->x = geo.longitude;
        value->y = geo.latitude;
        if (isnan(geo.altitude)) {
            value->z = 0;
        } else if(geo.altitudeRef == AltitudeReference::HAE) {
            value->z = geo.altitude;
        } /*else if(geo.altitudeRef == AltitudeReference::MSL) {
            double offset;
            if(ElevationManager_getGeoidHeight(&offset, geo.latitude, geo.longitude) == TE_Ok) {
                value->z = geo.altitude + offset;
            } else {
                value->z = 0.0;
            }
        }*/ else {
            // XXX - AGL
            value->z = 0;
        }
        return TE_Ok;
    }
    TAKErr EquirectangularProjection::inverse(GeoPoint2 *value, const Point2<double> &proj) const NOTHROWS
    {
        value->latitude = proj.y;
        value->longitude = proj.x;
        value->altitude = proj.z;
        value->altitudeRef = AltitudeReference::HAE;
        value->ce90 = NAN;
        value->le90 = NAN;
        return TE_Ok;
    }
    double EquirectangularProjection::getMinLatitude() const NOTHROWS
    {
        return -90;
    }
    double EquirectangularProjection::getMaxLatitude() const NOTHROWS
    {
        return 90;
    }
    double EquirectangularProjection::getMinLongitude() const NOTHROWS
    {
        return -180;
    }
    double EquirectangularProjection::getMaxLongitude() const NOTHROWS
    {
        return 180;
    }
    bool EquirectangularProjection::is3D() const NOTHROWS
    {
        return false;
    }

    int WebMercatorProjection::getSpatialReferenceID() const NOTHROWS
    {
        return 3857;
    }
    TAKErr WebMercatorProjection::forward(Point2<double> *value, const GeoPoint2 &geo) const NOTHROWS
    {
#define TO_RADIANS(x) ((x)*(M_PI/180.0))
        value->x = Datum2::WGS84.reference.semiMajorAxis * TO_RADIANS(geo.longitude);
        value->y = Datum2::WGS84.reference.semiMajorAxis * log(tan(M_PI / 4.0 + TO_RADIANS(geo.latitude) / 2.0));
#undef TO_RADIANS
        if (isnan(geo.altitude)) {
            value->z = 0;
        } else if(geo.altitudeRef == AltitudeReference::HAE) {
            value->z = geo.altitude;
        } /*else if(geo.altitudeRef == AltitudeReference::MSL) {
            double offset;
            if(ElevationManager_getGeoidHeight(&offset, geo.latitude, geo.longitude) == TE_Ok) {
                value->z = geo.altitude + offset;
            } else {
                value->z = 0.0;
            }
        }*/ else {
            // XXX - AGL
            value->z = 0;
        }

        return TE_Ok;
    }
    TAKErr WebMercatorProjection::inverse(GeoPoint2 *value, const Point2<double> &proj) const NOTHROWS
    {
#define TO_DEGREES(x) ((x)*(180.0/M_PI))
        value->latitude = TO_DEGREES((M_PI / 2.0) - (2.0*atan(exp(-proj.y / Datum2::WGS84.reference.semiMajorAxis))));
        value->longitude = TO_DEGREES(proj.x / Datum2::WGS84.reference.semiMajorAxis);
#undef TO_DEGREES
        value->altitude = proj.z;
        value->altitudeRef = AltitudeReference::HAE;
        value->ce90 = NAN;
        value->le90 = NAN;
        return TE_Ok;
    }
    double WebMercatorProjection::getMinLatitude() const NOTHROWS
    {
        return -85.0511;
    }
    double WebMercatorProjection::getMaxLatitude() const NOTHROWS
    {
        return 85.0511;
    }
    double WebMercatorProjection::getMinLongitude() const NOTHROWS
    {
        return -180;
    }
    double WebMercatorProjection::getMaxLongitude() const NOTHROWS
    {
        return 180;
    }
    bool WebMercatorProjection::is3D() const NOTHROWS
    {
        return false;
    }


    int EcefWGS84::getSpatialReferenceID() const NOTHROWS
    {
        return 4978;
    }
    TAKErr EcefWGS84::forward(Point2<double> *value, const GeoPoint2 &geo) const NOTHROWS
    {
        const double a = Datum2::WGS84.reference.semiMajorAxis;
        const double b = Datum2::WGS84.reference.semiMinorAxis;
#define TO_RADIANS(x) ((x)*(M_PI / 180.0))
        const double latRad = TO_RADIANS(geo.latitude);
        const double cosLat = cos(latRad);
        const double sinLat = sin(latRad);
        const double lonRad = TO_RADIANS(geo.longitude);
#undef TO_RADIANS
        const double cosLon = cos(lonRad);
        const double sinLon = sin(lonRad);

        const double a2_b2 = (a*a) / (b*b);
        const double b2_a2 = (b*b) / (a*a);

        const double cden = sqrt((cosLat*cosLat) + (b2_a2 * (sinLat*sinLat)));
        const double lden = sqrt((a2_b2 * (cosLat*cosLat)) + (sinLat*sinLat));

        double altitude;
        if (isnan(geo.altitude)) {
            altitude = 0.0;
        } else if(geo.altitudeRef == AltitudeReference::HAE) {
            altitude = geo.altitude;
        //} else if(geo.altitudeRef == AltitudeReference::MSL) {
        //    double offset;
        //    if(ElevationManager_getGeoidHeight(&offset, geo.latitude, geo.longitude) == TE_Ok) {
        //        altitude = geo.altitude + offset;
        //    } else {
        //        // XXX - ? what's the failover here???
        //        altitude = 0.0;
        //    }
        //} else {
            // XXX - AGL, do we bother handling
        } else
            altitude = 0.0;

        const double X = ((a / cden) + altitude) * (cosLat*cosLon);
        const double Y = ((a / cden) + altitude) * (cosLat*sinLon);
        const double Z = ((b / lden) + altitude) * sinLat;

        value->x = X;
        value->y = Y;
        value->z = Z;

        return TE_Ok;
    }
    TAKErr EcefWGS84::inverse(GeoPoint2 *value, const Point2<double> &proj) const NOTHROWS
    {
        const double a = Datum2::WGS84.reference.semiMajorAxis;
        const double b = Datum2::WGS84.reference.semiMinorAxis;

#define TO_DEGREES(x) (((x)*180.0)/M_PI)
        const double e = sqrt(1 - ((b*b) / (a*a)));
        const double ep = sqrt((a*a - b*b) / (b*b));

        const double e_sq = e*e;
        const double ep_sq = ep*ep;
        const double p = sqrt(proj.x*proj.x + proj.y*proj.y);
        const double th = atan2(a*proj.z, b*p);
        const double th_sin = sin(th);
        const double th_cos = cos(th);
        const double rlon = atan2(proj.y, proj.x);
        const double rlat = atan2(
            (proj.z + ep_sq*b*th_sin*th_sin*th_sin),
            (p - e_sq*a*th_cos*th_cos*th_cos));
        const double N = a / sqrt(1 - e_sq*sin(rlat)*sin(rlat));
        const double h = p / cos(rlat) - N;

        double lat = TO_DEGREES(rlat);
        double lon = TO_DEGREES(rlon);

        if (lat < -90) lat = -180 - lat;
        if (lat > 90) lat = 180 - lat;

        // XXX - why are we getting NaN for 'h' on startup???

        value->latitude = lat;
        value->longitude = lon;
        value->altitude = h;
        value->altitudeRef = AltitudeReference::HAE;
        value->ce90 = NAN;
        value->le90 = NAN;

        return TE_Ok;
    }
    double EcefWGS84::getMinLatitude() const NOTHROWS
    {
        return -90;
    }
    double EcefWGS84::getMaxLatitude() const NOTHROWS
    {
        return 90;
    }
    double EcefWGS84::getMinLongitude() const NOTHROWS
    {
        return -180;
    }
    double EcefWGS84::getMaxLongitude() const NOTHROWS
    {
        return 180;
    }
    bool EcefWGS84::is3D() const NOTHROWS
    {
        return true;
    }

    TAKErr InternalProjectionSpi::create(Projection2Ptr &value, int srid) NOTHROWS
    {
        // relying on projection factory's mutex

        switch (srid) {
        case 4326: // equirectangular
            value = Projection2Ptr(&proj4326, Memory_leaker_const<Projection2>);
            break;
        case 3857:
        case 900913:
            value = Projection2Ptr(&proj3857, Memory_leaker_const<Projection2>);
            break;
        case 4978:
            value = Projection2Ptr(&proj4978, Memory_leaker_const<Projection2>);
            break;
        default:
            return TE_InvalidArg;
        }
        return TE_Ok;
    }

} // end unnamed namespace
