#ifndef TAK_ENGINE_FEATURE_DATASOURCEFEATUREDATASTORE3_H_INCLUDED
#define TAK_ENGINE_FEATURE_DATASOURCEFEATUREDATASTORE3_H_INCLUDED

#include "feature/DataSourceFeatureDataStore2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class DataSourceFeatureDataStore3 : public virtual DataSourceFeatureDataStore2
            {
            protected:
                virtual ~DataSourceFeatureDataStore3() NOTHROWS = 0;
            public:
                /**
                * Returns <code>true</code> if the content for the specified
                * file has been modified, <code>false</code> otherwise.
                *
                * @param file  A file
                *
                * @return   <code>true</code> if the content for the specified
                *           file are has been modified, <code>false</code>
                *           otherwise.
                */
                virtual Util::TAKErr isModified(bool *value, const char *file) NOTHROWS = 0;

                /**
                * Returns all of the files with content currently managed by the data
                * store.
                *
                * @param modifiedOnly   If <code>true</code> only those files
                *                       with modifications will be returned.
                *
                * @return  The files with content currently managed by the data store.
                */
                virtual Util::TAKErr queryFiles(FileCursorPtr &cursor, bool modifiedOnly) NOTHROWS = 0;

                /**
                * Returns all of the features associated with the specified file.
                *
                * @return  The features associated with the specified file.
                */
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &result, const char *file) NOTHROWS = 0;
                /**
                * Returns all of the feature sets associated with the specified file.
                *
                * @return  The feature sets associated with the specified file.
                */
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &result, const char *file) NOTHROWS = 0;

                virtual Util::TAKErr insertFeatureSet(FeatureSetPtr_const *featureSet, const char *file, const char *name, const double minResolution, const double maxResolution) NOTHROWS = 0;

            }; // DataSourceFeatureDataStore
        }
    }
}

#endif
