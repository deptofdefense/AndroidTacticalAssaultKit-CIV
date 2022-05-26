#ifndef TAK_ENGINE_FEATURE_FEATURESETDATABASE_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURESETDATABASE_H_INCLUDED

#include "feature/FDB.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureSetDatabase : public FDB
            {
            public :
                class ENGINE_API Builder;
            public :
                FeatureSetDatabase() NOTHROWS;
                FeatureSetDatabase(int modificationFlags, int visibilityFlags) NOTHROWS;
            public :
                Util::TAKErr insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
            };

            class ENGINE_API FeatureSetDatabase::Builder
            {
            public :
                class ENGINE_API BulkInsertion;
            public :
                Builder() NOTHROWS;
            public :
                Util::TAKErr create(const char *databaseFile) NOTHROWS;
                Util::TAKErr create(const char *databaseFile, int* dbVersion) NOTHROWS;
                Util::TAKErr close() NOTHROWS;
            public :
                Util::TAKErr beginBulkInsertion() NOTHROWS;
                Util::TAKErr endBulkInsertion(const bool successful) NOTHROWS;
                Util::TAKErr insertFeature(int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
                Util::TAKErr insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS;
                Util::TAKErr insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name) NOTHROWS;
                Util::TAKErr insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name, const bool readOnly) NOTHROWS;
                Util::TAKErr setFeatureSetVisible(const int64_t fsid, const bool featureSetVisible) NOTHROWS;
				Util::TAKErr setFeatureVisible(const int64_t fid, const bool visible);
                Util::TAKErr setFeatureVersion(const int64_t fid, const int64_t version);
                Util::TAKErr insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
                Util::TAKErr insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution, const bool readOnly) NOTHROWS;
                Util::TAKErr updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS;
                Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS;
                Util::TAKErr createIndices() NOTHROWS;
            private :
                std::unique_ptr<FeatureSetDatabase> db;
                std::unique_ptr<FDB::Builder> impl;
            };


            class ENGINE_API FeatureSetDatabase::Builder::BulkInsertion : private Util::NonHeapAllocatable
            {
            public:
                BulkInsertion(FeatureSetDatabase::Builder &builder) NOTHROWS;
                ~BulkInsertion() NOTHROWS;
            public:
                Util::TAKErr checkInit() const NOTHROWS;
                Util::TAKErr setSuccessful() NOTHROWS;
            private:
                FeatureSetDatabase::Builder &builder;
                Util::TAKErr valid;
                bool successful;
            };
        }
    }
}

#endif
