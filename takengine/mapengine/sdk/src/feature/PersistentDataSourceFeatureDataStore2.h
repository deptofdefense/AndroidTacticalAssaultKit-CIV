#ifndef TAK_ENGINE_FEATURE_PERSISTENTDATASOURCEFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_PERSISTENTDATASOURCEFEATUREDATASTORE2_H_INCLUDED

#include <map>

#include "db/Database2.h"
#include "db/WhereClauseBuilder2.h"
#include "currency/CatalogDatabase2.h"
#include "currency/CurrencyRegistry2.h"
#include "feature/AbstractDataSourceFeatureDataStore2.h"
#include "feature/DataSourceFeatureDataStore3.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureDataSource2.h"
#include "feature/FeatureSetCursor2.h"
#include "port/Platform.h"
#include "thread/Cond.h"
#include "util/Error.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API PersistentDataSourceFeatureDataStore2 : public AbstractFeatureDataStore2,
                                                          public DataSourceFeatureDataStore3
            {
            private :
                class Index;
                class FeatureDb;
                class DatabaseRef;
                class DistributedFeatureCursorImpl;
                class FeatureSetCursorImpl;
                class AddMgr;
            public :
                PersistentDataSourceFeatureDataStore2() NOTHROWS;
            public :
                ~PersistentDataSourceFeatureDataStore2() NOTHROWS;

            public :
                Util::TAKErr open(const char *path) NOTHROWS;
                /**************************************************************************/
            public :
                virtual Util::TAKErr getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS;
            private :
                //Map<DatabaseRef, FeatureQueryParameters> prepareQuery(FeatureQueryParameters params);
                Util::TAKErr prepareQuery(std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> &value, const FeatureQueryParameters *params) NOTHROWS;

                //Map<DatabaseRef, FeatureSetQueryParameters> prepareQuery(FeatureSetQueryParameters params);
                Util::TAKErr prepareQuery(std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureSetQueryParameters>> &value, const FeatureSetQueryParameters *params) NOTHROWS;

                Util::TAKErr insertFeatureImpl(FeaturePtr_const *value, const int64_t fsid, const FeatureDefinition2 &def) NOTHROWS;
            public :
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor) NOTHROWS override;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS override;

                virtual Util::TAKErr getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS override;

                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS override;
                virtual Util::TAKErr refresh() NOTHROWS override;
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                virtual Util::TAKErr close() NOTHROWS override;
            private :
                virtual Util::TAKErr closeImpl() NOTHROWS;
            protected:
                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t setId, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS override;
                virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS override;
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS override;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr deleteFeatureImpl(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS override;

                /********************************/
            public:
                virtual Util::TAKErr contains(bool *value, const char *file) NOTHROWS override;
                virtual Util::TAKErr getFile(Port::String &file, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr add(const char *file) NOTHROWS override;
                virtual Util::TAKErr add(const char *file, const char *hint) NOTHROWS override;
                virtual Util::TAKErr remove(const char *) NOTHROWS override;
                virtual Util::TAKErr update(const char *file) NOTHROWS override;
                virtual Util::TAKErr update(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr queryFiles(FileCursorPtr &cursor) NOTHROWS override;
                virtual Util::TAKErr isModified(bool *value, const char *file) NOTHROWS override;
                virtual Util::TAKErr queryFiles(FileCursorPtr &cursor, bool modifiedOnly) NOTHROWS override;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result, const char *file) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result, const char *file) NOTHROWS override;
                virtual Util::TAKErr insertFeatureSet(FeatureSetPtr_const *featureSet, const char *file, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
            private :
                Util::TAKErr addImpl(const char *file, const char *hint) NOTHROWS;
                Util::TAKErr updateImpl(const char *file, const char *hint) NOTHROWS;
                Util::TAKErr getFileImpl(Port::String &value, const int64_t fsid) NOTHROWS;
                Util::TAKErr generateFDB(std::map<std::string, std::set<std::shared_ptr<FeatureDb>>> &dbs,
                                         std::map<std::string, std::tuple<int64_t, int64_t>> *replacementFsids,
                                         bool *atLimit, const char *file, const int64_t catalogId, FeatureDataSource2::Content &content,
                                         AddMgr &pendingMgr, const std::size_t featureSetLimit) NOTHROWS;
                Util::TAKErr markFileDirty(const int64_t fsid) NOTHROWS;
            private :
                static Util::TAKErr matches(bool *matched, const FeatureDb &db, const FeatureQueryParameters *params) NOTHROWS;
                static Util::TAKErr matches(bool *matched, const FeatureDb &db, const FeatureSetQueryParameters *params) NOTHROWS;
                static Util::TAKErr filter(std::shared_ptr<FeatureQueryParameters> &retval, const FeatureDb &db, const FeatureQueryParameters *params, const bool shared) NOTHROWS;
                static Util::TAKErr filter(std::shared_ptr<FeatureSetQueryParameters> &retval, const FeatureDb &db, const FeatureSetQueryParameters *params, const bool shared) NOTHROWS;
            private :
                std::map<int64_t, std::shared_ptr<FeatureDb>> fsid_to_feature_db_;
                Index *index_;
                DB::DatabasePtr index_db_;
                Port::String database_dir_;
                std::set<std::string> pending_;
                Port::String fdbs_dir_;
                Port::String fdb_template_;
                int fdb_template_version_;
                std::set<std::shared_ptr<DatabaseRef>> shared_dbs_;

                TAK::Engine::Thread::Mutex mutex_;
                TAK::Engine::Thread::CondVar cond_;
                Currency::CatalogCurrencyRegistry2 currency_;

                friend class Index;
            };


            class PersistentDataSourceFeatureDataStore2::FeatureDb
            {
            public :
                FeatureDb();
            public :
                std::shared_ptr<DatabaseRef> database;
                int64_t fsid;
                Port::String name;
                Port::String provider;
                Port::String type;
                double minResolution;
                double maxResolution;
                int64_t version;
                int64_t catalogId;
                Port::String fdbFile;
            };

            /**************************************************************************/

            /**
            *
            * <P>Thread Safety: All operations are externally synchronized on owning
            * PersistentDataSourceFeatureDataStore.
            *
            * @author Developer
            */
            class PersistentDataSourceFeatureDataStore2::Index : public Currency::CatalogDatabase2
            {
            public :
                Index(PersistentDataSourceFeatureDataStore2 &owner, Currency::CatalogCurrencyRegistry2 &currencyRegistry);
            public :
                Util::TAKErr open(DB::Database2 *db) NOTHROWS;
                Util::TAKErr invalidateCatalogEntry(const int64_t catalogId) NOTHROWS;
                Util::TAKErr finalizeCatalogEntry(const int64_t catalogId) NOTHROWS;
                Util::TAKErr query(CatalogCursorPtr &ptr, const int64_t fsid) NOTHROWS;
                Util::TAKErr insertFeatureSet(int64_t *value, const int64_t catalogId, const char *name, const char *fdb, const char *provider, const char *type) NOTHROWS;
                Util::TAKErr reserveFeatureSet(int64_t *value, const char *name, const char *fdb, const char *provider, const char *type) NOTHROWS;
                Util::TAKErr reserveFeatureSet(const int64_t value, const char *name, const char *fdb, const char *provider, const char *type) NOTHROWS;
                Util::TAKErr setFeatureSetCatalogId(const int64_t value, const int64_t catalogId) NOTHROWS;

                /**************************************************************************/
                // CatalogDatabase
            protected :
                virtual Util::TAKErr validateCatalogNoSync(CatalogCursor &result) NOTHROWS;
                virtual Util::TAKErr validateCatalogRowNoSync(bool *value, CatalogCursor &result) NOTHROWS;
                virtual Util::TAKErr checkDatabaseVersion(bool *value) NOTHROWS;
                virtual Util::TAKErr setDatabaseVersion() NOTHROWS;
                virtual Util::TAKErr dropTables() NOTHROWS;
                virtual Util::TAKErr buildTables() NOTHROWS;
                virtual Util::TAKErr onCatalogEntryRemoved(int64_t catalogId, bool automated) NOTHROWS;
            private :
                int databaseVersion() NOTHROWS;
            private :
                PersistentDataSourceFeatureDataStore2 &owner;
            };

            /**************************************************************************/

            class PersistentDataSourceFeatureDataStore2::DatabaseRef
            {
            private :
                typedef std::unique_ptr<FeatureDataStore2, void(*)(const FeatureDataStore2 *)> FeatureDataStorePtr;
            public :
                DatabaseRef(FeatureDataStorePtr &&value, const char *dbFile) NOTHROWS;
                ~DatabaseRef();
            public :
                void markForDelete() NOTHROWS;
            private :
                Port::String dbFile;
                bool deleteDbFile;
                FeatureDataStorePtr valuePtr;
            public :
                FeatureDataStore2 * const value;
            };

            class PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl : public FeatureCursor2
            {
            public:
                DistributedFeatureCursorImpl(FeatureCursorPtr &&cursor, std::shared_ptr<DatabaseRef> db) NOTHROWS;
            public: // FeatureCursor2
                virtual Util::TAKErr getId(int64_t *value) NOTHROWS override;
                virtual Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
                virtual Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
            public: // FeatureDefinition2
                virtual Util::TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
                virtual FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
                virtual AltitudeMode getAltitudeMode() NOTHROWS override;
                virtual double getExtrude() NOTHROWS override;
                virtual Util::TAKErr getName(const char **value) NOTHROWS override;
                virtual FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
                virtual Util::TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
                virtual Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
                virtual Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
            public: // RowIterator
                virtual Util::TAKErr moveToNext() NOTHROWS override;
            private:
                std::shared_ptr<DatabaseRef> db;
                std::unique_ptr<Feature2> rowData;
                FeatureCursorPtr filter;
            };

            class PersistentDataSourceFeatureDataStore2::FeatureSetCursorImpl : public FeatureSetCursor2
            {
            private :
                struct LT_featureSetName
                {
                    bool operator()(const std::shared_ptr<const FeatureDb> &a, const std::shared_ptr<const FeatureDb> &b) const
                    {
                        if (a->name && b->name) {
                            return (TAK::Engine::Port::String_strcasecmp(a->name, b->name)<0);
                        } else if (a->name) {
                            return false;
                        } else if (b->name) {
                            return false;
                        } else {
                            return (a->fsid < b->fsid);
                        }
                    }
                };
            public:
                FeatureSetCursorImpl(std::set<std::shared_ptr<const FeatureDb>, LT_featureSetName> featureSets) NOTHROWS;
                virtual ~FeatureSetCursorImpl() NOTHROWS;
            public: // FeatureSetCursor2
                virtual Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS;
            public : // RowIterator
                virtual Util::TAKErr moveToNext() NOTHROWS;
            private :
                std::set<std::shared_ptr<const FeatureDb>, LT_featureSetName> featureSets;
                std::set<std::shared_ptr<const FeatureDb>, LT_featureSetName>::iterator iter;
                std::unique_ptr<FeatureSet2> rowData;

                friend class PersistentDataSourceFeatureDataStore2;
            }; // FeatureSetCursor

            class PersistentDataSourceFeatureDataStore2::AddMgr
            {
            public:
                AddMgr(PersistentDataSourceFeatureDataStore2 &owner, bool update = false) NOTHROWS;
                ~AddMgr() NOTHROWS;
            public:
                void markPending(const char *f) NOTHROWS;
                void setCatalogId(const int64_t catalogId) NOTHROWS;
                void addFDB(const char *fdbFile) NOTHROWS;
                void setSuccessful() NOTHROWS;
            private:
                PersistentDataSourceFeatureDataStore2 &owner_;

                bool marked_;
                bool update_;
                std::string path_;
                int64_t catalog_id_;
                std::list<std::string> fdb_files_;
                bool successful_;
            };
        }
    }
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

#endif
