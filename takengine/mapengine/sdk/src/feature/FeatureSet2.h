#ifndef TAK_ENGINE_FEATURE_FEATURESET2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURESET2_H_INCLUDED

#include <cstdint>
#include <memory>

#include "port/Platform.h"
#include "port/String.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureSet2
            {
            public :
                FeatureSet2(const FeatureSet2 &other) NOTHROWS;
                FeatureSet2(const int64_t id, const char *provider, const char *type, const char *name, const double minGsd, const double maxGsd, const int64_t version) NOTHROWS;
            public :
                const char *getProvider() const NOTHROWS;
                const char *getType() const NOTHROWS;
                const char *getName() const NOTHROWS;
                const int64_t getId() const NOTHROWS;
                const double getMinResolution() const NOTHROWS;
                const double getMaxResolution() const NOTHROWS;
                const int64_t getVersion() const NOTHROWS;
            private :
                int64_t id;
                int64_t version;
                Port::String provider;
                Port::String type;
                Port::String name;
                double minGsd;
                double maxGsd;
            };

            typedef std::unique_ptr<FeatureSet2, void(*)(const FeatureSet2 *)> FeatureSetPtr;
            typedef std::unique_ptr<const FeatureSet2, void(*)(const FeatureSet2 *)> FeatureSetPtr_const;
        }
    }
}

#endif // TAK_ENGINE_FEATURE_FEATURESET2_H_INCLUDED
