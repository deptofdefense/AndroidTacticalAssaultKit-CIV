#ifndef TAK_ENGINE_FEATURE_FEATURELAYER2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURELAYER2_H_INCLUDED

#include <util/NonCopyable.h>

#include "core/AbstractLayer.h"
#include "core/ServiceManagerBase2.h"
#include "feature/FeatureDataStore2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class FeatureLayer2 : public atakmap::core::AbstractLayer,
                                  public TAK::Engine::Core::ServiceManagerBase2,
                                  TAK::Engine::Util::NonCopyable
            {
            public:
                FeatureLayer2(const char* layerName, FeatureDataStore2Ptr &&dataStore) NOTHROWS;
            public:
                FeatureDataStore2& getDataStore() const NOTHROWS;
            private:
                FeatureDataStore2Ptr dataStore;
            };
        }
    }
}

#endif
