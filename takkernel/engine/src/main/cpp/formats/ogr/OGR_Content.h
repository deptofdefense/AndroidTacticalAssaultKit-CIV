#pragma once

#include "OGR_Content2.h"
#include "feature/FeatureDataSource.h"
#include "feature/OGRDriverDefinition2.h"

#include <cstddef>
#include <memory>
#include <stack>

#include "feature/Style.h"

#include <ogr_geometry.h>
#ifdef __ANDROID__
#include <ogr_core.h>
#endif
#include "feature/LegacyAdapters.h"
#include "ogr_feature.h"
#include "port/String.h"
#include "OGRUtils.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace OGR {


    class OGR_Content : public atakmap::feature::FeatureDataSource::Content
    {
    private :
        typedef std::unique_ptr<GDALDataset, void(*) (GDALDataset*)> GDAL_DatasetPtr;
        typedef std::unique_ptr<OGRCoordinateTransformation, decltype (&OGRCoordinateTransformation::DestroyCT)> OGR_CoordinateTransformationPtr;
        typedef std::unique_ptr<OGRFeature, decltype (&OGRFeature::DestroyFeature)> OGR_FeaturePtr;
        typedef std::unique_ptr<OGRSpatialReference, decltype (&OGRSpatialReference::DestroySpatialReference)> OGR_SpatialReferencePtr;
        typedef std::unique_ptr<OGRGeometry, void(*)(OGRGeometry *)> OGRGeometryPtr;
    public:
        OGR_Content (const char* filePath, std::size_t sizeThreshold);
        OGR_Content (const char* filePath, char **openOptions, std::size_t sizeThreshold);
    public ://  FeatureDataSource::Content INTERFACE
        atakmap::feature::FeatureDataSource::FeatureDefinition *get() const override;

        const char* getFeatureSetName () const override
        {
            return impl.currentFeatureSetName;
        }

        double getMaxResolution () const override
        {
            double r = NAN;
            impl.getMaxResolution(&r);
            return r;
        }

        double getMinResolution() const override
        {
            double r = NAN;
            impl.getMinResolution(&r);
            return r;
        }

        virtual const char* getProvider () const 
        {
            return impl.getProvider();
        }

        const char* getType () const override
        {
            return impl.getType();
        }

        bool moveToNextFeature () override
        {
            return impl.moveToNextFeatureImpl();
        }
        bool moveToNextFeatureSet() override
        {
            return impl.moveToNextFeatureSet() == TAK::Engine::Util::TE_Ok;
        }
    private:
        OGR_Content2 impl;
    };
}
}
}
}
