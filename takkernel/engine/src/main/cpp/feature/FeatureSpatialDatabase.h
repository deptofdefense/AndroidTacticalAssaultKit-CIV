#ifndef TAK_ENGINE_FEATURE_FEATURESPATIALDATABASE_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURESPATIALDATABASE_H_INCLUDED

#include "currency/CatalogDatabase2.h"
#include "db/Database2.h"
#include "feature/FeatureDataSource.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class FeatureSpatialDatabase : public TAK::Engine::Currency::CatalogDatabase2
            {
            public :
                FeatureSpatialDatabase(TAK::Engine::Currency::CatalogCurrencyRegistry2 &r) NOTHROWS;
            private :
                TAK::Engine::DB::Database2 *getDatabase() NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr insertGroup(int64_t *rowId, const int64_t catalogId, const char *provider, const char *type, const char *groupName, const int minLod, const int maxLod) NOTHROWS;
                TAK::Engine::Util::TAKErr insertStyle(int64_t *rowId, const int64_t catalogId, const char *styleRep) NOTHROWS;
                TAK::Engine::Util::TAKErr insertFeature(const int64_t catalogId, const int64_t groupId, const atakmap::feature::FeatureDataSource::FeatureDefinition &feature, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS;
            private :
                TAK::Engine::Util::TAKErr insertFeatureBlob(const int64_t catalogId, const int64_t groupId, const char *name, const uint8_t *blob, const std::size_t blobLen, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS;
                TAK::Engine::Util::TAKErr insertFeatureWkt(const int64_t catalogId, const int64_t groupId, const char *name, const char *wkt, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS;
                TAK::Engine::Util::TAKErr insertFeatureWkb(const int64_t catalogId, const int64_t groupId, const char *name, const uint8_t *wkb, const std::size_t wkbLen, const int64_t styleId, const int minLod, const int maxLod) NOTHROWS;
                TAK::Engine::Util::TAKErr createIndicesNoSync() NOTHROWS;
                TAK::Engine::Util::TAKErr dropIndicesNoSync() NOTHROWS;
                TAK::Engine::Util::TAKErr createTriggersNoSync() NOTHROWS;
                TAK::Engine::Util::TAKErr dropTriggersNoSync() NOTHROWS;

                /**************************************************************************/
                // Catalog Database
            protected :
                virtual TAK::Engine::Util::TAKErr checkDatabaseVersion(bool *value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr setDatabaseVersion() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr dropTables() NOTHROWS;

            private :
                TAK::Engine::Util::TAKErr dropTablesLegacy() NOTHROWS;
            protected :
                virtual TAK::Engine::Util::TAKErr buildTables() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr onCatalogEntryRemoved(int64_t catalogId, bool automated) NOTHROWS;
            private :
                static int databaseVersion() NOTHROWS;
            public :
                static TAK::Engine::Util::TAKErr getSpatialiteMajorVersion(int *value, TAK::Engine::DB::Database2 &db) NOTHROWS;
                static TAK::Engine::Util::TAKErr getSpatialiteMinorVersion(int *value, TAK::Engine::DB::Database2 &db) NOTHROWS;
                static TAK::Engine::Util::TAKErr getSpatialiteVersion(int *major, int *minor, TAK::Engine::DB::Database2 &db) NOTHROWS;
            private :
                TAK::Engine::DB::StatementPtr insertFeatureBlobStatement;
                TAK::Engine::DB::StatementPtr insertFeatureWktStatement;
                TAK::Engine::DB::StatementPtr insertFeatureWkbStatement;
                TAK::Engine::DB::StatementPtr insertStyleStatement;

                friend class PersistentDataSourceFeatureDataStore2;
            };
        }
    }
}


#endif // TAK_ENGINE_FEATURE_FEATURESPATIALDATABASE_H_INCLUDED
