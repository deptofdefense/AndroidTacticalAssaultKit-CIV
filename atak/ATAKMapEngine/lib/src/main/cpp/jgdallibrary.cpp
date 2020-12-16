#include "jgdallibrary.h"
#include "common.h"

#include <memory>

#include <ogr_srs_api.h>

#include <core/Projection2.h>
#include <core/ProjectionFactory3.h>
#include <util/Memory.h>
#include <cmath>

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::unique_ptr<void, void (*)(OGRCoordinateTransformationH)> OGRCoordinateTransformationPtr;

    class GdalProjection : public Projection2
    {
    public:
        GdalProjection(OGRSpatialReferenceH srs, const int srid) NOTHROWS;
        ~GdalProjection() NOTHROWS;
    public:
        int getSpatialReferenceID() const NOTHROWS;

        TAKErr forward(Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS;
        TAKErr inverse(GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS;
        double getMinLatitude() const NOTHROWS;
        double getMaxLatitude() const NOTHROWS;
        double getMinLongitude() const NOTHROWS;
        double getMaxLongitude() const NOTHROWS;

        bool is3D() const NOTHROWS;
    private :
        std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> forwardImpl;
        std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> inverseImpl;
        int srid;
    }; // end class Projection

    void OGRCoordinateTransformationH_deleter(const OGRCoordinateTransformationH value)
    {
        OCTDestroyCoordinateTransformation(value);
    }
    void OGRSpatialReferenceH_deleter(const OGRSpatialReferenceH value)
    {
        OSRDestroySpatialReference(value);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_gdal_GdalLibrary_registerProjectionSpi
  (JNIEnv *env, jclass clazz)
{
    class SpiImpl : public ProjectionSpi3
    {
    public :
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS
        {
            std::unique_ptr<void, void(*)(OGRSpatialReferenceH)> srs(OSRNewSpatialReference(NULL), OGRSpatialReferenceH_deleter);
            if(!srs.get())
                return TE_Err;
            if(OSRImportFromEPSG(srs.get(), srid) != OGRERR_NONE)
                return TE_InvalidArg;
            value = Projection2Ptr(new GdalProjection(srs.get(), srid), Memory_deleter_const<Projection2, GdalProjection>);
            return TE_Ok;
        }
    };

    ProjectionSpi3Ptr spi(new SpiImpl(), Memory_deleter_const<ProjectionSpi3, SpiImpl>);
    ProjectionFactory3_registerSpi(std::move(spi), 1);
}

namespace
{
    GdalProjection::GdalProjection(OGRSpatialReferenceH srs, const int srid_) NOTHROWS :
        forwardImpl(NULL, NULL),
        inverseImpl(NULL, NULL),
        srid(srid_)
    {
        std::unique_ptr<void, void(*)(OGRSpatialReferenceH)> wgs84(OSRNewSpatialReference(NULL), OGRSpatialReferenceH_deleter);
        if(!wgs84.get())
            return;
        if(OSRImportFromEPSG(wgs84.get(), 4326) != OGRERR_NONE)
            return;

        forwardImpl = OGRCoordinateTransformationPtr(OCTNewCoordinateTransformation(wgs84.get(), srs), OGRCoordinateTransformationH_deleter);
        inverseImpl = OGRCoordinateTransformationPtr(OCTNewCoordinateTransformation(srs, wgs84.get()), OGRCoordinateTransformationH_deleter);
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
}
