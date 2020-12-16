#include "formats/osr/OSRProjectionSpi.h"

#include <memory>

#include <ogr_srs_api.h>

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "util/Memory.h"

using namespace TAK::Engine::Formats::OSR;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> OGRCoordinateTransformationPtr;

    class GdalProjection : public Projection2
    {
    public:
        GdalProjection(OGRSpatialReferenceH srs, const int srid) NOTHROWS;
        ~GdalProjection() NOTHROWS override;
    public:
        int getSpatialReferenceID() const NOTHROWS override;

        TAKErr forward(Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS override;
        TAKErr inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS override;
        double getMinLatitude() const NOTHROWS override;
        double getMaxLatitude() const NOTHROWS override;
        double getMinLongitude() const NOTHROWS override;
        double getMaxLongitude() const NOTHROWS override;

        bool is3D() const NOTHROWS override;
    private :
        std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> forwardImpl;
        std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> inverseImpl;
        int srid;
    }; // end class Projection

    class SpiImpl : public ProjectionSpi3
    {
    public :
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS override;
    };

    void OGRSpatialReference_deleter(OGRSpatialReferenceH handle);
    void OGRCoordinateTransformation_deleter(OGRCoordinateTransformationH handle);

}

ProjectionSpi3 &TAK::Engine::Formats::OSR::OSRProjectionSpi_get() NOTHROWS
{
    static SpiImpl spi;
    return spi;
}

namespace
{
    GdalProjection::GdalProjection(OGRSpatialReferenceH srs, const int srid_) NOTHROWS :
        forwardImpl(nullptr, nullptr),
        inverseImpl(nullptr, nullptr),
        srid(srid_)
    {
        std::unique_ptr<void, void(*)(OGRSpatialReferenceH)> wgs84(OSRNewSpatialReference(nullptr), OGRSpatialReference_deleter);
        if(!wgs84.get())
            return;
        if(OSRImportFromEPSG(wgs84.get(), 4326) != OGRERR_NONE)
            return;

        forwardImpl = OGRCoordinateTransformationPtr(OCTNewCoordinateTransformation(wgs84.get(), srs), OGRCoordinateTransformation_deleter);
        inverseImpl = OGRCoordinateTransformationPtr(OCTNewCoordinateTransformation(srs, wgs84.get()), OGRCoordinateTransformation_deleter);
    }
    GdalProjection::~GdalProjection() NOTHROWS
    {}
    int GdalProjection::getSpatialReferenceID() const NOTHROWS
    {
        return srid;
    }
    TAKErr GdalProjection::forward(Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS
    {
        if(!forwardImpl.get())
            return TE_IllegalState;
        double x = geo.longitude;
        double y = geo.latitude;
        double z = ::isnan(geo.altitude) ? 0.0 : geo.altitude;
        if(!OCTTransform(forwardImpl.get(), 1, &x, &y, &z))
            return TE_Err;
        proj->x = x;
        proj->y = y;
        proj->z = z;
        return TE_Ok;
    }
    TAKErr GdalProjection::inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS
    {
        if(!inverseImpl.get())
            return TE_IllegalState;
        double x = proj.x;
        double y = proj.y;
        double z = proj.z;
        if(!OCTTransform(inverseImpl.get(), 1, &x, &y, &z))
            return TE_Err;
        geo->longitude = x;
        geo->latitude = y;
        geo->altitude = z;
        geo->altitudeRef = AltitudeReference::HAE;
        return TE_Ok;
    }
    double GdalProjection::getMinLatitude() const NOTHROWS
    {
        return -90.0;
    }
    double GdalProjection::getMaxLatitude() const NOTHROWS
    {
        return 90.0;
    }
    double GdalProjection::getMinLongitude() const NOTHROWS
    {
        return -180.0;
    }
    double GdalProjection::getMaxLongitude() const NOTHROWS
    {
        return 180.0;
    }
    bool GdalProjection::is3D() const NOTHROWS
    {
        return false;
    }

    TAKErr SpiImpl::create(Projection2Ptr &value, const int srid) NOTHROWS
    {
        std::unique_ptr<void, void(*)(OGRSpatialReferenceH)> srs(OSRNewSpatialReference(nullptr), OGRSpatialReference_deleter);
        if(!srs.get())
            return TE_Err;
        if(OSRImportFromEPSG(srs.get(), srid) != OGRERR_NONE)
            return TE_InvalidArg;
        value = Projection2Ptr(new GdalProjection(srs.get(), srid), Memory_deleter_const<Projection2, GdalProjection>);
        return TE_Ok;
    }

    void OGRSpatialReference_deleter(OGRSpatialReferenceH handle)
    {
        OSRDestroySpatialReference(handle);
    }
    void OGRCoordinateTransformation_deleter(OGRCoordinateTransformationH handle)
    {
        OCTDestroyCoordinateTransformation(handle);
    }
}
