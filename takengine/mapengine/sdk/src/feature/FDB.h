#ifndef TAK_ENGINE_FEATURE_FDB_H_INCLUDED
#define TAK_ENGINE_FEATURE_FDB_H_INCLUDED

#include "db/BindArgument.h"
#include "db/CursorWrapper2.h"
#include "db/Database2.h"
#include "db/WhereClauseBuilder2.h"
#include "feature/AbstractFeatureDataStore2.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureDefinition2.h"
#include "feature/FeatureSetCursor2.h"
#include "port/Platform.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/NonHeapAllocatable.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FDB : public AbstractFeatureDataStore2
            {
            private :
                typedef Util::TAKErr (*AttributeEncode)(Util::DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key);
                typedef Util::TAKErr (*AttributeDecode)(atakmap::util::AttributeSet &attr, Util::DataInput2 &dos, const char *key);
            private :
                struct FeatureSetDefn;
                struct AttributeCoder;
                class AttributeSpec;
                class Builder;
                class InsertContext;
                class FeatureCursorImpl;
                class FeatureSetCursorImpl;
            private :
                typedef std::map<int64_t, std::shared_ptr<AttributeSpec>> IdAttrSchemaMap;
                typedef std::map<Port::String, std::shared_ptr<AttributeSpec>, Port::StringLess> KeyAttrSchemaMap;
            private :
                FDB(int modificationFlags, int visibilityFlags) NOTHROWS;
                
            private :
                virtual ~FDB() NOTHROWS;
            public :
                Util::TAKErr open(const char *db) NOTHROWS;
            protected :
                Util::TAKErr open(const char *db, int* dbVersion, bool buildIndices) NOTHROWS;
            private :
                Util::TAKErr buildTables(bool indices) NOTHROWS;
                Util::TAKErr upgradeTables(int *dbVersion) NOTHROWS;
                Util::TAKErr createIndicesNoSync() NOTHROWS;
                Util::TAKErr dropIndicesNoSync() NOTHROWS;
                Util::TAKErr createTriggersNoSync() NOTHROWS;
            protected :
                virtual Util::TAKErr validateInfo() NOTHROWS;
            private :
                Util::TAKErr validateAttributeSchema() NOTHROWS;
            private :
                static Util::TAKErr isCompatible(bool *value, const FeatureSetDefn &defn, const FeatureSetQueryParameters *params) NOTHROWS;
                static Util::TAKErr isCompatible(bool *value, const FeatureSetDefn &defn, const FeatureQueryParameters *params) NOTHROWS;
            protected :
                virtual Util::TAKErr isCompatible(bool *value, const FeatureSetQueryParameters *params) NOTHROWS;
                virtual Util::TAKErr isCompatible(bool *value, const FeatureQueryParameters *params) NOTHROWS;
            public :
                virtual Util::TAKErr getFeature(FeaturePtr_const &value, const int64_t fid) NOTHROWS override;

                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result) NOTHROWS override;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result, const FeatureQueryParameters &params) NOTHROWS override;

                virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS override;
                
                virtual Util::TAKErr getFeatureSet(FeatureSetPtr_const &value, const int64_t fsid) NOTHROWS override;
            private :
                Util::TAKErr getFeatureSetImpl(FeatureSetPtr_const &value, const FeatureSetDefn &defn) NOTHROWS;
            protected :
                virtual Util::TAKErr filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &result, const FeatureSetQueryParameters *params, const bool softVisibilityCheck) NOTHROWS;
                virtual Util::TAKErr filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &result, const FeatureQueryParameters *params, const bool softVisibilityCheck) NOTHROWS;
            public :
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result, const FeatureSetQueryParameters &params) NOTHROWS override;

                virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS override;

                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;

                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS override;

                virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS override;

                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS override;

                virtual Util::TAKErr refresh() NOTHROWS override;
                
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;

                virtual Util::TAKErr close() NOTHROWS override;
            private :
                Util::TAKErr closeImpl() NOTHROWS;

            protected:
                virtual Util::TAKErr setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &paramsRef, const bool readOnly) NOTHROWS override;

                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS override;

                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS override;

                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;

                virtual Util::TAKErr setFeatureVersionImpl(const int64_t fid, const int64_t version) NOTHROWS;

                virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS override;

                virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS override;

                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *ref, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;

            private :
                virtual Util::TAKErr insertFeatureSetImpl(const int64_t fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
            protected :
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS override;

                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;

                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const char *type, const double minResolution, const double maxResolution) NOTHROWS;

                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS override;

                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS override;

                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;

                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
            private :
                Util::TAKErr insertFeatureImpl(int64_t *fid, InsertContext &ctx, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
            protected :
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS override;

                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;

                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;

                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;

                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;

                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;

                virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS override;

                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS override;


                /**************************************************************************/
            private :
                Util::TAKErr buildParamsWhereClauseNoCheck(bool *emptyResults, const FeatureQueryParameters &params, Port::Collection<std::shared_ptr<const FeatureSetDefn>> &fs, DB::WhereClauseBuilder2 &whereClause) NOTHROWS;
                Util::TAKErr buildParamsWhereClauseCheck(bool *emptyResults, const FeatureQueryParameters &params, const FeatureSetDefn &fs, DB::WhereClauseBuilder2 &whereClause) NOTHROWS;

                Util::TAKErr getMaxFeatureVersion(const int64_t fsid, int64_t *version) NOTHROWS;
                /**************************************************************************/
            protected :
                static Util::TAKErr encodeAttributes(FDB &impl, InsertContext &ctx, const atakmap::util::AttributeSet &metadata) NOTHROWS;

            private :
                static Util::TAKErr decodeAttributes(AttributeSetPtr_const &result, const uint8_t *blob, const std::size_t blobLen, IdAttrSchemaMap &schema) NOTHROWS;
                static Util::TAKErr decodeAttributesImpl(AttributeSetPtr_const &result, Util::DataInput2 &dis, IdAttrSchemaMap &schema) NOTHROWS;

                static Util::TAKErr insertAttrSchema(std::shared_ptr<AttributeSpec> &retval, InsertContext &ctx, DB::Database2 &database, const char *key, const atakmap::util::AttributeSet &metadata) NOTHROWS;

                /**************************************************************************/


            private :
                Port::String database_file_;

                bool spatial_index_enabled_;

                DB::DatabasePtr database_;

                std::map<int64_t, std::shared_ptr<FeatureSetDefn>> feature_sets_;
                
                bool info_dirty_;
                bool visible_;
                bool visible_check_;
                int min_lod_;
                int max_lod_;
                bool lod_check_;
                bool read_only_;

                IdAttrSchemaMap id_to_attr_schema_;
                KeyAttrSchemaMap key_to_attr_schema_;
                bool attr_schema_dirty_;

                friend class FeatureSetDatabase;
                friend class PersistentDataSourceFeatureDataStore2;
            };

            struct FDB::FeatureSetDefn
            {
                bool visible{false};
                int visibleVersion{-1};
                bool visibleCheck{false};
                int minLod{0};
                int maxLod{0};
                int lodVersion{-1};
                bool lodCheck{false};
                int nameVersion{-1};
                int64_t fsid{-1};
                Port::String name;
                Port::String type;
                Port::String provider;
                bool readOnly{false};
            };

            struct FDB::AttributeCoder
            {
            public:
                FDB::AttributeEncode encode;
                FDB::AttributeDecode decode;
            };

            class FDB::AttributeSpec
            {
            public :
                AttributeSpec(const char *key, const int64_t id, const int type);
                ~AttributeSpec();
            public :
                static Util::TAKErr initCoders() NOTHROWS;
            private:
                static std::map<atakmap::util::AttributeSet::Type, AttributeCoder> CLAZZ_TO_CODER;
                static std::map<int, AttributeCoder> TYPECODE_TO_CODER;
            public :
                const Port::String key;
                const int64_t id;
                const int type;
                const AttributeCoder coder;

                std::map<int, std::shared_ptr<AttributeSpec>> secondaryDefs;
            };

            /**************************************************************************/
            // InsertContext

            class FDB::InsertContext
            {
            public :
                InsertContext() NOTHROWS;
                ~InsertContext() NOTHROWS;
            public :
                std::map<Port::String, int64_t, Port::StringLess> styleIds;
                DB::StatementPtr insertFeatureBlobStatement;
                DB::StatementPtr insertFeatureWktStatement;
                DB::StatementPtr insertFeatureWkbStatement;
                DB::StatementPtr insertStyleStatement;
                DB::StatementPtr insertAttributesStatement;
                DB::StatementPtr insertAttributeSchemaStatement;
                DB::BindArgument insertGeomArg;
                Util::DynamicOutput codedAttribs;
            };


            /**************************************************************************/
            // Builder

            class FDB::Builder
            {
            public :
                Builder(FDB &db) NOTHROWS;
                ~Builder() NOTHROWS;
            public :
                Util::TAKErr createIndices() NOTHROWS;
                Util::TAKErr beginBulkInsertion() NOTHROWS;
                Util::TAKErr endBulkInsertion(const bool commit) NOTHROWS;
                Util::TAKErr insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
                Util::TAKErr insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name) NOTHROWS;
                Util::TAKErr insertFeature(int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
                Util::TAKErr insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS;
                Util::TAKErr setFeatureSetVisible(const int64_t fsid, const bool& visible) NOTHROWS;
                Util::TAKErr setFeatureVisible(const int64_t fid, const bool& visible) NOTHROWS;
                Util::TAKErr setFeatureVersion(const int64_t fid, const int64_t version) NOTHROWS;
                Util::TAKErr updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS;
                Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS;
            private :
                InsertContext ctx;
                FDB &db;
            };

            /**************************************************************************/
            // FeatureCursorImpl

            class FDB::FeatureCursorImpl : public DB::CursorWrapper2,
                                           public FeatureCursor2
            {
            public :
                FeatureCursorImpl(FDB &owner, DB::QueryPtr &&filter, const int idCol, const int fsidCol, const int versionCol, const int nameCol, const int geomCol, const int styleCol, const int attribsCol, const int altitudeModeCol, const int extrudeCol) NOTHROWS;
            public: // FeatureCursor2
                virtual TAK::Engine::Util::TAKErr getId(int64_t *value) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
            public: // FeatureDefinition2
                virtual TAK::Engine::Util::TAKErr getRawGeometry(RawData *value) NOTHROWS override;
                virtual GeometryEncoding getGeomCoding() NOTHROWS override;
                virtual AltitudeMode getAltitudeMode() NOTHROWS override;
                virtual double getExtrude() NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getName(const char **value) NOTHROWS override;
                virtual StyleEncoding getStyleCoding() NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getRawStyle(RawData *value) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
                virtual Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
            public : // RowIterator
                virtual TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
            private :
                FDB &owner;
                const int idCol;
                const int fsidCol;
                const int nameCol;
                const int geomCol;
                const int styleCol;
                const int attribsCol;
                const int versionCol;
                const int altitudeModeCol;
                const int extrudeCol;

                FeaturePtr_const rowFeature;
                AttributeSetPtr_const rowAttribs;
            };

            /**************************************************************************/
            // FeatureSetCursorImpl

            class FDB::FeatureSetCursorImpl : public FeatureSetCursor2
            {
            private:
                struct LT_featureSetName
                {
                    bool operator()(const std::shared_ptr<const FeatureSetDefn> &a, const std::shared_ptr<const FeatureSetDefn> &b)
                    {
                        if (a->name && b->name) {
                            return (Port::String_strcasecmp(a->name, b->name)<0);
                        }
                        else if (a->name) {
                            return false;
                        }
                        else if (b->name) {
                            return false;
                        }
                        else {
                            return (a->fsid < b->fsid);
                        }
                    }
                };
            public :
                FeatureSetCursorImpl(FDB &owner, Port::Collection<std::shared_ptr<const FeatureSetDefn>> &rows) NOTHROWS;
            public : // FeatureSetCursor2
                virtual Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS;
            public : // RowIterator
                virtual Util::TAKErr moveToNext() NOTHROWS;
            private :
                FDB &owner;
                std::vector<std::shared_ptr<const FeatureSetDefn>> rows;
                FeatureSetPtr_const rowData;
                int pos;
            };
        }
    }
}

#endif
