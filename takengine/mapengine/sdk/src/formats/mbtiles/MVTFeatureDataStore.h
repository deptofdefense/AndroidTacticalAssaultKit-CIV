#ifndef TAK_ENGINE_FORMATS_MVTFEATUREDATASTORE_H_INCLUDED
#define TAK_ENGINE_FORMATS_MVTFEATUREDATASTORE_H_INCLUDED

#include <cstdint>
#include <list>
#include <memory>

#include <map>

#include <gdal.h>

#include "feature/AbstractDataSourceFeatureDataStore2.h"
#include "formats/ogr/OGRFeatureDataStore.h"
#include "port/Platform.h"
#include "thread/Thread.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace MBTiles {
                class ENGINE_API MVTFeatureDataStore : public Feature::AbstractFeatureDataStore2
                {
                private :
                    class QueryContext;
                public :
                    MVTFeatureDataStore(const char *uri, const char *workingDir = nullptr) NOTHROWS;
                    ~MVTFeatureDataStore() NOTHROWS;
                public :
                    virtual Util::TAKErr getFeature(Feature::FeaturePtr_const &feature, const int64_t fid) NOTHROWS override;
                    virtual Util::TAKErr queryFeatures(Feature::FeatureCursorPtr &cursor) NOTHROWS override;
                    virtual Util::TAKErr queryFeatures(Feature::FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS override;
                    virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS override;
                    virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS override;
                    virtual Util::TAKErr getFeatureSet(Feature::FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS override;
                    virtual Util::TAKErr queryFeatureSets(Feature::FeatureSetCursorPtr &cursor) NOTHROWS override;
                    virtual Util::TAKErr queryFeatureSets(Feature::FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS override;
                    virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS override;
                    virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS override;
                    virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;
                    virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS override;
                    virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                    virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS override;
                    virtual Util::TAKErr isAvailable(bool *value) NOTHROWS override;
                    virtual Util::TAKErr refresh() NOTHROWS override;
                    virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                    virtual Util::TAKErr close() NOTHROWS override;
                protected :
                    virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS override;
                    virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS override;
                    virtual Util::TAKErr insertFeatureSetImpl(Feature::FeatureSetPtr_const *featureSet, const char *provider_val, const char *type_val, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                    virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS override;
                    virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS override;
                    virtual Util::TAKErr insertFeatureImpl(Feature::FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                    virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                    virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS override;
                    virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS override;
                    virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS override;
                    virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                    virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t setId, const bool visible) NOTHROWS override;
                    virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                    virtual Util::TAKErr setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS override;
                    virtual Util::TAKErr setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS override;
                private:
                    Port::String uri;
                    Port::String workingDir;
                    std::shared_ptr<OGR::OGRFeatureDataStore::SchemaHandler> schema;
                    std::shared_ptr<QueryContext> ctx;
                };
            }
        }
    }
}


#endif
