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
                Util::TAKErr open(const char *db, bool buildIndices) NOTHROWS;
            private :
                Util::TAKErr buildTables(bool indices) NOTHROWS;
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
                //public Feature getFeature(int64_t fid) {
                virtual Util::TAKErr getFeature(FeaturePtr_const &value, const int64_t fid) NOTHROWS override;

                //public FeatureCursor queryFeatures(FeatureQueryParameters params) {
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result) NOTHROWS;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result, const FeatureQueryParameters &params) NOTHROWS;

                //public int queryFeaturesCount(FeatureQueryParameters params) {
                virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS;
                virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS;
                
                //public synchronized FeatureSet getFeatureSet(int64_t fsid)
                virtual Util::TAKErr getFeatureSet(FeatureSetPtr_const &value, const int64_t fsid) NOTHROWS override;
            private :
                Util::TAKErr getFeatureSetImpl(FeatureSetPtr_const &value, const FeatureSetDefn &defn) NOTHROWS;
            protected :
                //protected Collection<FeatureSetDefn> filterNoSync(FeatureSetQueryParameters params, boolean softVisibilityCheck) {
                virtual Util::TAKErr filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &result, const FeatureSetQueryParameters *params, const bool softVisibilityCheck) NOTHROWS;
                //protected Collection<FeatureSetDefn> filterNoSync(FeatureQueryParameters params, boolean softChecks) {
                virtual Util::TAKErr filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &result, const FeatureQueryParameters *params, const bool softVisibilityCheck) NOTHROWS;
            public :
                //public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result) NOTHROWS;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result, const FeatureSetQueryParameters &params) NOTHROWS;

                //public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
                virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS;
                virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS;

                //public synchronized boolean isFeatureVisible(int64_t fid) {
                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS;

                //public synchronized boolean isFeatureSetVisible(int64_t fsid) {
                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS;

                //public synchronized boolean isAvailable() {
                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS;

                //public synchronized void refresh() {
                virtual Util::TAKErr refresh() NOTHROWS;
                
                //public String getUri() {
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS;

                virtual Util::TAKErr close() NOTHROWS;
            private :
                Util::TAKErr closeImpl() NOTHROWS;
            protected :
                //protected boolean setFeatureVisibleImpl(int64_t fid, boolean visible) {
                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS;

                //protected boolean setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS;
                
                //protected boolean setFeatureSetVisibleImpl(int64_t fsid, boolean visible)
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS;

                //protected boolean setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible)
                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS;

                virtual Util::TAKErr setFeatureVersionImpl(const int64_t fid, const int64_t version) NOTHROWS;

                //protected void beginBulkModificationImpl()
                virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS;

                //protected boolean endBulkModificationImpl(boolean successful)
                virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS;

                //protected final boolean insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, FeatureSet[] returnRef)
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *ref, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;

            private :
                //private void insertFeatureSetImpl(int64_t fsid, String provider, String type, String name, double minResolution, double maxResolution)
                virtual Util::TAKErr insertFeatureSetImpl(const int64_t fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
            protected :
                    //protected boolean updateFeatureSetImpl(int64_t fsid, String name)
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS;

                    //protected boolean updateFeatureSetImpl(int64_t fsid, double minResolution, double maxResolution)
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS;

                    //protected boolean updateFeatureSetImpl(int64_t fsid, String name, double minResolution, double maxResolution)
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
                
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const char *type, const double minResolution, const double maxResolution) NOTHROWS;

                    //protected boolean deleteFeatureSetImpl(int64_t fsid)
                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS;

                    //protected boolean deleteAllFeatureSetsImpl()
                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS;

                    //protected boolean insertFeatureImpl(int64_t fsid, String name, Geometry geom, Style style, AttributeSet attributes, Feature[] returnRef)
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS;

                    //protected boolean insertFeatureImpl(int64_t fsid, FeatureDefinition def, Feature[] returnRef)
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
            private :
                //int64_t insertFeatureImpl(InsertContext ctx, int64_t fsid, FeatureDefinition def)
                Util::TAKErr insertFeatureImpl(int64_t *fid, InsertContext &ctx, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
            protected :
                    //protected boolean updateFeatureImpl(int64_t fid, String name)
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS;

                    //protected boolean updateFeatureImpl(int64_t fid, Geometry geom)
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS;

                    //protected boolean updateFeatureImpl(int64_t fid, Style style)
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS;

                    //protected boolean updateFeatureImpl(int64_t fid, AttributeSet attributes)
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS;

                    //protected boolean updateFeatureImpl(int64_t fid, String name, Geometry geom, Style style, AttributeSet attributes)
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS;

                    //protected boolean deleteFeatureImpl(int64_t fid)
                virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS;

                    //protected boolean deleteAllFeaturesImpl(int64_t fsid)
                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS;


                /**************************************************************************/
            private :
                //private boolean buildParamsWhereClauseNoCheck(FeatureQueryParameters params, Collection<FeatureSetDefn> fs, WhereClauseBuilder whereClause)
                Util::TAKErr buildParamsWhereClauseNoCheck(bool *emptyResults, const FeatureQueryParameters &params, Port::Collection<std::shared_ptr<const FeatureSetDefn>> &fs, DB::WhereClauseBuilder2 &whereClause) NOTHROWS;
                //private boolean buildParamsWhereClauseCheck(FeatureQueryParameters params, FeatureSetDefn defn, WhereClauseBuilder whereClause)
                Util::TAKErr buildParamsWhereClauseCheck(bool *emptyResults, const FeatureQueryParameters &params, const FeatureSetDefn &fs, DB::WhereClauseBuilder2 &whereClause) NOTHROWS;

                Util::TAKErr getMaxFeatureVersion(const int64_t fsid, int64_t *version) NOTHROWS;
                /**************************************************************************/
            protected :
                //protected static void encodeAttributes(FDB impl, InsertContext ctx, AttributeSet metadata)
                static Util::TAKErr encodeAttributes(FDB &impl, InsertContext &ctx, const atakmap::util::AttributeSet &metadata) NOTHROWS;

            private :
                //private static AttributeSet decodeAttributes(byte[] attribsBlob, Map<Long, AttributeSpec> schema)
                static Util::TAKErr decodeAttributes(AttributeSetPtr_const &result, const uint8_t *blob, const std::size_t blobLen, IdAttrSchemaMap &schema) NOTHROWS;
                //private static AttributeSet decodeAttributesImpl(DataInputStream dis, Map<Long, AttributeSpec> schema)
                static Util::TAKErr decodeAttributesImpl(AttributeSetPtr_const &result, Util::DataInput2 &dis, IdAttrSchemaMap &schema) NOTHROWS;

                static Util::TAKErr insertAttrSchema(std::shared_ptr<AttributeSpec> &retval, InsertContext &ctx, DB::Database2 &database, const char *key, const atakmap::util::AttributeSet &metadata) NOTHROWS;

                /**************************************************************************/


            private :
                Port::String databaseFile;

                bool spatialIndexEnabled;

                DB::DatabasePtr database;

                std::map<int64_t, std::shared_ptr<FeatureSetDefn>> featureSets;
                
                bool infoDirty;
                bool visible;
                bool visibleCheck;
                int minLod;
                int maxLod;
                bool lodCheck;

                IdAttrSchemaMap idToAttrSchema;
                KeyAttrSchemaMap keyToAttrSchema;
                bool attrSchemaDirty;

                friend class FeatureSetDatabase;
                friend class PersistentDataSourceFeatureDataStore2;
            };

            struct FDB::FeatureSetDefn
            {
                bool visible;
                int visibleVersion;
                bool visibleCheck;
                int minLod;
                int maxLod;
                int lodVersion;
                bool lodCheck;
                int nameVersion;
                int64_t fsid;
                Port::String name;
                Port::String type;
                Port::String provider;
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
                Util::TAKErr insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS;
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
                FeatureCursorImpl(FDB &owner, DB::QueryPtr &&filter, const int idCol, const int fsidCol, const int versionCol, const int nameCol, const int geomCol, const int styleCol, const int attribsCol) NOTHROWS;
            public: // FeatureCursor2
                virtual TAK::Engine::Util::TAKErr getId(int64_t *value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getVersion(int64_t *value) NOTHROWS;
            public: // FeatureDefinition2
                virtual TAK::Engine::Util::TAKErr getRawGeometry(RawData *value) NOTHROWS;
                virtual GeometryEncoding getGeomCoding() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getName(const char **value) NOTHROWS;
                virtual StyleEncoding getStyleCoding() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getRawStyle(RawData *value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr get(const Feature2 **feature) NOTHROWS;
                virtual Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS;
            public : // RowIterator
                virtual TAK::Engine::Util::TAKErr moveToNext() NOTHROWS;
            private :
                FDB &owner;
                const int idCol;
                const int fsidCol;
                const int nameCol;
                const int geomCol;
                const int styleCol;
                const int attribsCol;
                const int versionCol;

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
