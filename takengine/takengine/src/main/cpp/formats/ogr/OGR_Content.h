#pragma once

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
    private:
        class GeoNode
        {
        public:
            GeoNode (OGRGeometryCollection* parent) :
                parent (parent),
                childCount (parent->getNumGeometries ()),
                childIndex (0)
            { }

            public :
                OGRGeometry* getNextChild ()
            {
                return childIndex < childCount
                    ? parent->getGeometryRef(childIndex++)
                    : nullptr;
            }
        private:
            OGRGeometryCollection* parent;
            int childCount;
            int childIndex;
        };
    
    private:
        enum State
        {
            Feature,
            Geometry
        };

        typedef std::vector<Port::String>   StringVector;
    public:
        OGR_Content (const char* filePath, std::size_t sizeThreshold);
        OGR_Content (const char* filePath, char **openOptions, std::size_t sizeThreshold);
    public ://  FeatureDataSource::Content INTERFACE
        atakmap::feature::FeatureDataSource::FeatureDefinition* get () const override;

        const char* getFeatureSetName () const override
        {
            return currentFeatureSetName;
        }

        double getMaxResolution () const override
        { return 0.0; }

        double getMinResolution () const override;

        virtual const char* getProvider () const 
        {
    #ifdef __ANDROID__
            return "ogr";
    #else
            return "OGR";
    #endif
        }

        const char* getType () const override
        { return driver->getType (); }

        bool moveToNextFeature () override;
        bool moveToNextFeatureSet () override;
    private:
        const atakmap::util::AttributeSet& getCurrentFeatureAttributes () const;
        std::size_t preprocessDataset ();
        void setCurrentFeatureName ();
        private :
            TAK::Engine::Port::String filePath;
        std::size_t areaThreshold;
        GDAL_DatasetPtr dataSource;
        int layerCount;
        std::size_t levelOfDetail;
        TAK::Engine::Feature::OGRDriverDefinition2Ptr driver;
        int layerIndex;
        State state;
        OGRLayer* currentLayer;             // Reference only, not adopted.
        OGRFeatureDefn* currentLayerDef;    // Reference only, not adopted.
        const char *currentLayerName;
        TAK::Engine::Port::String currentFeatureSetName;
        StringVector layerNameFields;
        OGR_CoordinateTransformationPtr layerTransform;
        OGR_FeaturePtr currentFeature;
        TAK::Engine::Port::String currentFeatureName;
        mutable std::unique_ptr<atakmap::util::AttributeSet> currentFeatureAttributes;
        std::stack<GeoNode> geoStack;
        OGRGeometryPtr currentGeometry;       // Reference only, not adopted.
        std::size_t geometryCount;
    };
}
}
}
}
