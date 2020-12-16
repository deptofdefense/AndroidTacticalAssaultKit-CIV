#ifndef TAK_ENGINE_FORMATS_OGR_OGRFEATUREDATASTORE_H_INCLUDED
#define TAK_ENGINE_FORMATS_OGR_OGRFEATUREDATASTORE_H_INCLUDED

#include <cstdint>
#include <memory>

#include <map>

#include <gdal.h>

#include "feature/AbstractDataSourceFeatureDataStore2.h"
#include "port/Platform.h"
#include "thread/Thread.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace OGR {
                class ENGINE_API OGRFeatureDataStore : public Feature::AbstractFeatureDataStore2
                {
                public :
                    class ENGINE_API SchemaHandler;

                    typedef std::unique_ptr<SchemaHandler, void(*)(const SchemaHandler *)> SchemaHandlerPtr;
                private :
                    class OgrLayerFeatureCursor;
                    class FeatureSetCursorImpl;
                    class DriverDefSchemaHandler;

                    struct FeatureSetDefn
                    {
                    public :
                        FeatureSetDefn();
                    public :
                        int64_t fsid;
                        Port::String layerName;
                        Port::String displayName;
                        bool visible;
                        
                        std::shared_ptr<void> lla2layer;
                        double minResolution;
                        double maxResolution;

                        Port::String type;
                        Port::String provider;

                        std::shared_ptr<SchemaHandler> schema;
                    };
                private :
                    struct FeatureSetDefn_LT
                    {
                        bool operator()(const FeatureSetDefn &a, const FeatureSetDefn &b) const
                        {
                            if (!a.displayName && !b.displayName)
                                return a.fsid < b.fsid;
                            else if (!b.displayName)
                                return true;
                            else if (!a.displayName)
                                return false;
#ifdef MSVC
                            int cmp = _stricmp(a.displayName, b.displayName);
#else
                            int cmp = strcasecmp(a.displayName, b.displayName);
#endif
                            if (!cmp)
                                return a.fsid < b.fsid;
                            else
                                return cmp < 0;
                        }
                    };
                public :
                    OGRFeatureDataStore(const char *uri, const char *workingDir, const bool asyncRefresh) NOTHROWS;
                    OGRFeatureDataStore(const char *uri, const char *workingDir, const bool asyncRefresh, SchemaHandlerPtr &&schema) NOTHROWS;
                    virtual ~OGRFeatureDataStore() NOTHROWS;
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
                protected:
                    virtual Util::TAKErr createSchemaHandler(SchemaHandlerPtr &value, GDALDatasetH dataset) const NOTHROWS;
                private:
                    Util::TAKErr PrepareQuery(std::list<std::pair<FeatureSetDefn, FeatureDataStore2::FeatureQueryParameters>> &value, const Feature::FeatureDataStore2::FeatureQueryParameters &qparams) NOTHROWS;
                    Util::TAKErr ConfigureForQuery(OGRLayerH layer, const FeatureSetDefn &defn, const Feature::FeatureDataStore2::FeatureQueryParameters &qparams) NOTHROWS;
                    Util::TAKErr refreshImpl() NOTHROWS;
                    Util::TAKErr OpenConnection(std::unique_ptr<void, void(*)(GDALDatasetH)> &value) NOTHROWS;
                    Util::TAKErr CloseConnection(std::unique_ptr<void, void(*)(GDALDatasetH)> &&value) NOTHROWS;
                private:
                    static void *asyncRefresh(void *opaque);
                    static bool matches(const FeatureSetDefn &defn, const Feature::FeatureDataStore2::FeatureQueryParameters &qparams) NOTHROWS;
                    static Util::TAKErr Filter(Feature::FeatureDataStore2::FeatureQueryParameters *value, const FeatureSetDefn &db, const FeatureDataStore2::FeatureQueryParameters &qparams) NOTHROWS;
                private:
                    Port::String uri;
                    Port::String workingDir;

                    Thread::ThreadPtr refreshThread;

                    std::map<int64_t, std::shared_ptr<FeatureSetDefn>> fsidToFeatureDb;
                    std::map<Port::String, std::shared_ptr<FeatureSetDefn>> fsNameToFeatureDb;

                    int refreshRequest;

                    bool disposed;
                    bool disposing;

                    std::list<GDALDatasetH> _connectionPool;

                    bool backgroundRefresh;

                    Port::String provider;
                    Port::String type;

                    std::shared_ptr<SchemaHandler> userSchema;
                    std::shared_ptr<SchemaHandler> schema;
                };

                class ENGINE_API OGRFeatureDataStore::SchemaHandler
                {
                protected :
                    virtual ~SchemaHandler() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr ignoreLayer(bool *value, OGRLayerH layer) const NOTHROWS = 0;
                    virtual bool styleRequiresAttributes() const NOTHROWS = 0;
                    virtual Util::TAKErr getFeatureStyle(Feature::StylePtr_const &value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet &attribs) NOTHROWS = 0;
                    virtual Util::TAKErr getFeatureName(Port::String &value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet &attribs) NOTHROWS = 0;
                    virtual Util::TAKErr getFeatureSetName(Port::String &value, OGRLayerH layer) NOTHROWS = 0;
                };
            }
        }
    }
}

#endif
