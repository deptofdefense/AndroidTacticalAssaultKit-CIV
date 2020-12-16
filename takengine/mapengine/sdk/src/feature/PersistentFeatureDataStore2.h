#ifndef TAK_ENGINE_FEATURE_PERSISTENTFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_PERSISTENTFEATUREDATASTORE2_H_INCLUDED

#include "feature/FeatureSetDatabase.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            
            class PersistentFeatureDataStore2 : public FeatureSetDatabase {
            public:
                virtual ~PersistentFeatureDataStore2() NOTHROWS;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name, const char *type, const double minResolution, const double maxResolution) NOTHROWS;

            protected:
                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS;
                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS;
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS;
                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS;
                
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *ref, const char *provider, const char *type, const char *name, double minResolution, double maxResolution) NOTHROWS;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS;
                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS;
                virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS;
                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS;
            };
            
        }
    }
}

#endif
