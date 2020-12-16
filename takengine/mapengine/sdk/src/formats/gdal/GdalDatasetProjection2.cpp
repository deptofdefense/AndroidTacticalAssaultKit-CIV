#include "formats/gdal/GdalDatasetProjection2.h"

#include <memory>

#include <gdal.h>

#include "raster/gdal/GdalDatasetProjection.h"
#include "util/Memory.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Util;

namespace
{
    typedef std::unique_ptr<void, void(CPL_STDCALL*)(GDALDatasetH)> GDALDatasetHPtr;

    class GdalDatasetProjection2 : public DatasetProjection2
    {
    public :
        GdalDatasetProjection2(GDALDatasetHPtr &&dataset, std::unique_ptr<atakmap::raster::gdal::GdalDatasetProjection> &&impl) NOTHROWS;
    public :
        TAKErr imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS override;
		TAKErr groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS override;
    private :
        GDALDatasetHPtr dataset;
        std::unique_ptr<atakmap::raster::gdal::GdalDatasetProjection> impl;
    };
}

TAKErr TAK::Engine::Formats::GDAL::GdalDatasetProjection2_create(DatasetProjection2Ptr &value, const char *path) NOTHROWS
{
    TAKErr code(TE_Ok);
    GDALDatasetHPtr dataset(GDALOpen(path, GA_ReadOnly), GDALClose);
    if (!dataset.get())
        return TE_InvalidArg;
    try {
        std::unique_ptr<atakmap::raster::gdal::GdalDatasetProjection> proj;
        proj.reset(atakmap::raster::gdal::GdalDatasetProjection::getInstance((GDALDataset *)dataset.get()));
        if (!proj.get())
            return TE_Err;
        value = DatasetProjection2Ptr(new GdalDatasetProjection2(std::move(dataset), std::move(proj)), Memory_deleter_const<DatasetProjection2, GdalDatasetProjection2>);
        return code;
    } catch (...) {
        return TE_Err;
    }
}

namespace
{
    GdalDatasetProjection2::GdalDatasetProjection2(GDALDatasetHPtr &&dataset_, std::unique_ptr<atakmap::raster::gdal::GdalDatasetProjection> &&impl_) NOTHROWS :
        dataset(std::move(dataset_)),
        impl(std::move(impl_))
    {}
    TAKErr GdalDatasetProjection2::imageToGround(GeoPoint2 *ground, const Point2<double> &image) const NOTHROWS
    {
        if (!ground)
            return TE_InvalidArg;
        try {
            atakmap::math::Point<double> image_l(image.x, image.y, image.z);
            atakmap::core::GeoPoint ground_l;
            impl->inverse(&image_l, &ground_l);
            atakmap::core::GeoPoint_adapt(ground, ground_l);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }
    TAKErr GdalDatasetProjection2::groundToImage(Point2<double> *image, const GeoPoint2 &ground) const NOTHROWS
    {
        if (!image)
            return TE_InvalidArg;
        try {
            atakmap::core::GeoPoint ground_l(ground);
            atakmap::math::Point<double> image_l;
            impl->forward(&ground_l, &image_l);
            image->x = image_l.x;
            image->y = image_l.y;
            image->z = image_l.z;
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }
}
