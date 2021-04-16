#include "raster/DatasetProjection2.h"

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "util/Memory.h"

using namespace TAK::Engine::Raster;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    class CartesianProjectiveTransformProjection2 : public DatasetProjection2
    {
    public :
        CartesianProjectiveTransformProjection2(const Matrix2 &img2proj, const Matrix2 &proj2img) NOTHROWS;
    public :
        TAKErr imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS override;
		TAKErr groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS override;
    private :
        Matrix2 img2proj_;
        Matrix2 proj2img_;
    };

    class GeoProjectiveTransformProjection2 : public DatasetProjection2
    {
    public :
        GeoProjectiveTransformProjection2(Projection2Ptr &&proj, const Matrix2 &img2proj, const Matrix2 &proj2img) NOTHROWS;
    public :
        TAKErr imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS override;
		TAKErr groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS override;
    private :
        Projection2Ptr proj_;
        CartesianProjectiveTransformProjection2 impl_;
    };
}

DatasetProjection2::DatasetProjection2() NOTHROWS
{ }


DatasetProjection2::~DatasetProjection2() NOTHROWS
{ }

TAKErr TAK::Engine::Raster::DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const Math::Matrix2 &img2proj) NOTHROWS
{
    TAKErr code(TE_Ok);
    Matrix2 proj2img;
    code = img2proj.createInverse(&proj2img);
    TE_CHECKRETURN_CODE(code);
    return DatasetProjection2_create(value, srid, img2proj, proj2img);
}
TAKErr TAK::Engine::Raster::DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const Math::Matrix2 &img2proj, const Math::Matrix2 &proj2img) NOTHROWS
{
    if (srid == 4326 || srid == -1) {
        value = DatasetProjection2Ptr(new CartesianProjectiveTransformProjection2(img2proj, proj2img), Memory_deleter_const<DatasetProjection2, CartesianProjectiveTransformProjection2>);
        return TE_Ok;
    } else {
        TAKErr code(TE_Ok);
        Projection2Ptr proj(nullptr, nullptr);
        code = ProjectionFactory3_create(proj, srid);
        TE_CHECKRETURN_CODE(code);
        value = DatasetProjection2Ptr(new GeoProjectiveTransformProjection2(std::move(proj), img2proj, proj2img), Memory_deleter_const<DatasetProjection2, GeoProjectiveTransformProjection2>);
        return TE_Ok;
    }
}
TAKErr TAK::Engine::Raster::DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const std::size_t width, const std::size_t height, const Core::GeoPoint2 &ul, const Core::GeoPoint2 &ur, const Core::GeoPoint2 &lr, const Core::GeoPoint2 &ll) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!width || !height)
        return TE_InvalidArg;
    if (isnan(ul.latitude) || isnan(ul.longitude))
        return TE_InvalidArg;
    if (isnan(ur.latitude) || isnan(ur.longitude))
        return TE_InvalidArg;
    if (isnan(lr.latitude) || isnan(lr.longitude))
        return TE_InvalidArg;
    if (isnan(ll.latitude) || isnan(ll.longitude))
        return TE_InvalidArg;

    Point2<double> imgUL(0, 0);
    Point2<double> imgUR(static_cast<double>(width-1u), 0);
    Point2<double> imgLR(static_cast<double>(width-1), static_cast<double>(height-1));
    Point2<double> imgLL(0, static_cast<double>(height-1));
    Point2<double> projUL, projUR, projLR, projLL;

    Projection2Ptr proj(nullptr, nullptr);
    if (srid != 4326 && srid != -1) {
        if (ProjectionFactory3_create(proj, srid) != TE_Ok)
            Logger_log(TELL_Warning, "DatasetProjection2_create: Failed to find EPSG:%d, defaulting to EPSG:4326; projection errors may result.", srid);
    }

    if (proj.get()) {
        code = proj->forward(&projUL, ul);
        TE_CHECKRETURN_CODE(code);
        code = proj->forward(&projUR, ur);
        TE_CHECKRETURN_CODE(code);
        code = proj->forward(&projLR, lr);
        TE_CHECKRETURN_CODE(code);
        code = proj->forward(&projLL, ll);
        TE_CHECKRETURN_CODE(code);
    } else {
        projUL.x = ul.longitude;
        projUL.y = ul.latitude;
        projUR.x = ur.longitude;
        projUR.y = ur.latitude;
        projLR.x = lr.longitude;
        projLR.y = lr.latitude;
        projLL.x = ll.longitude;
        projLL.y = ll.latitude;
    }

    Matrix2 img2proj;
    code = Matrix2_mapQuads(&img2proj, imgUL, imgUR, imgLR, imgLL, projUL, projUR, projLR, projLL);
    TE_CHECKRETURN_CODE(code);

    Matrix2 proj2img;
    if (img2proj.createInverse(&proj2img) != TE_Ok) {
        // if we can't inverse it, attempt reverse mapping as last ditch
        code = Matrix2_mapQuads(&proj2img, projUL, projUR, projLR, projLL, imgUL, imgUR, imgLR, imgLL);
        TE_CHECKRETURN_CODE(code);
    }

    return DatasetProjection2_create(value, srid, img2proj, proj2img);
}

namespace
{
    CartesianProjectiveTransformProjection2::CartesianProjectiveTransformProjection2(const Matrix2 &img2proj_, const Matrix2 &proj2img_) NOTHROWS :
        img2proj_(img2proj_),
        proj2img_(proj2img_)
    {}
    TAKErr CartesianProjectiveTransformProjection2::imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!ground)
            return TE_InvalidArg;
        Point2<double> proj;
        code = img2proj_.transform(&proj, image);
        TE_CHECKRETURN_CODE(code);
        ground->latitude = proj.y;
        ground->longitude = proj.x;
        ground->altitude = proj.z;
        ground->altitudeRef = AltitudeReference::HAE;
        return TE_Ok;
    }
    TAKErr CartesianProjectiveTransformProjection2::groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS
    {
        if (!image)
            return TE_InvalidArg;
        Point2<double> gp(ground.longitude, ground.latitude);
        if (isnan(ground.altitude))
            gp.z = 0;
        else
            gp.z = ground.altitude;
        return proj2img_.transform(image, gp);
    }

    GeoProjectiveTransformProjection2::GeoProjectiveTransformProjection2(Projection2Ptr &&proj_, const Matrix2 &img2proj_, const Matrix2 &proj2img_) NOTHROWS :
        proj_(std::move(proj_)),
        impl_(img2proj_, proj2img_)
    {}
    TAKErr GeoProjectiveTransformProjection2::imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS
    {
        if (!ground)
            return TE_InvalidArg;
        TAKErr code(TE_Ok);
        // convert to map projection CS
        code = impl_.imageToGround(ground, image);
        TE_CHECKRETURN_CODE(code);
        // convert to WGS84
        return proj_->inverse(ground, Point2<double>(ground->longitude, ground->latitude, ground->altitude));
    }
    TAKErr GeoProjectiveTransformProjection2::groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS
    {
        if (!image)
            return TE_InvalidArg;
        TAKErr code(TE_Ok);
        // convert to map projection CS
        Point2<double> proj;
        code = this->proj_->forward(&proj, ground);
        TE_CHECKRETURN_CODE(code);
        return impl_.groundToImage(image, GeoPoint2(proj.y, proj.x, proj.z, AltitudeReference::HAE));
    }
}
