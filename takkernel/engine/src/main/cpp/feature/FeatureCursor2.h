#ifndef TAK_ENGINE_FEATURE_FEATURECURSOR2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURECURSOR2_H_INCLUDED

#include <cstdint>

#include "db/RowIterator.h"
#include "feature/FeatureDefinition2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureCursor2 : public TAK::Engine::DB::RowIterator,
                                   public FeatureDefinition2
            {
            protected :
                virtual ~FeatureCursor2() NOTHROWS = 0;
            public :
                virtual TAK::Engine::Util::TAKErr getId(int64_t *value) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getVersion(int64_t *value) NOTHROWS = 0;
            }; // FeatureCursor
        }
    }
}

#endif // TAK_ENGINE_FEATURE_FEATURECURSOR2_H_INCLUDED
