#ifndef TAK_ENGINE_FEATURE_BRUTEFORCELIMITOFFSETFEATURESETCURSOR_H_INCLUDED
#define TAK_ENGINE_FEATURE_BRUTEFORCELIMITOFFSETFEATURESETCURSOR_H_INCLUDED

#include <list>

#include "feature/FeatureSetCursor2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API BruteForceLimitOffsetFeatureSetCursor : public FeatureSetCursor2
            {
            public :
                BruteForceLimitOffsetFeatureSetCursor(FeatureSetCursor2Ptr &&impl, const std::size_t limit, const std::size_t offset) NOTHROWS;
            public :
                Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS override;
            public :
                Util::TAKErr moveToNext() NOTHROWS override;
            private :
                FeatureSetCursor2Ptr filter;
                std::size_t limit;
                std::size_t offset;
                std::size_t pos;
            };
        }
    }
}

#endif
