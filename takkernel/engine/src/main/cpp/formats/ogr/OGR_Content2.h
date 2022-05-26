#pragma once

#include "feature/FeatureDataSource2.h"
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

    class OGR_Content;

    Util::TAKErr OGR_Content2_create(Feature::FeatureDataSource2::ContentPtr &content, const char *filePath, char **openOptions, std::size_t sizeThreshold) NOTHROWS;

    class OGR_Content2 : public Feature::FeatureDataSource2::Content
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
    private:
        // Constructors throw invalid_argument
        // Use static create function for non-throwing creation
        OGR_Content2(const char* filePath, char **openOptions, std::size_t sizeThreshold);

    public:
        virtual ~OGR_Content2() NOTHROWS;

    public ://  FeatureDataSource2::Content INTERFACE
        const char *getType() const NOTHROWS override;
        const char *getProvider() const NOTHROWS override;

        Util::TAKErr moveToNextFeature() NOTHROWS override;
        Util::TAKErr moveToNextFeatureSet() NOTHROWS override;

        Util::TAKErr get(Feature::FeatureDefinition2 **feature) const NOTHROWS override;

        Util::TAKErr getFeatureSetName(Port::String &name) const NOTHROWS override;
        Util::TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS override;
        Util::TAKErr getMinResolution(double *value) const NOTHROWS override;
        Util::TAKErr getMaxResolution(double *value) const NOTHROWS override;
        Util::TAKErr getVisible(bool *visible) const NOTHROWS override;


    private:
        const atakmap::util::AttributeSet& getCurrentFeatureAttributes () const;
        std::size_t preprocessDataset ();
        void setCurrentFeatureName ();

        // Moves to the next feature, loading the data for that feature
        // but does not populate feature definition results.
        // Returns true if new feature data loaded, false if out of results
        bool moveToNextFeatureImpl() NOTHROWS;

        // Create a legacy FeatureDefinition from the currently available
        // feature data.
        // If vis is non-NULL, it will be set to indicate the intended default
        // visibility of the result.
        Util::TAKErr createLegacyResult(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition> &legacyResult, bool *vis = NULL) const;

    private :
        std::unique_ptr<Feature::FeatureDefinition2, void(*)(const Feature::FeatureDefinition2 *)> curResult;
        std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition> curLegacyResult;
        bool curVisible;
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

        friend class OGR_Content;
        friend Util::TAKErr OGR_Content2_create(Feature::FeatureDataSource2::ContentPtr &content, const char *filePath, char **openOptions, std::size_t sizeThreshold) NOTHROWS;
    };
}
}
}
}
