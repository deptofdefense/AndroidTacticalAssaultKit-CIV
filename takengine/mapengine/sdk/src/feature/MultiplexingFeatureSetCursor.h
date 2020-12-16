#ifndef TAK_ENGINE_FEATURE_MULTIPLEXINGFEATURESETCURSOR_H_INCLUDED
#define TAK_ENGINE_FEATURE_MULTIPLEXINGFEATURESETCURSOR_H_INCLUDED

#include <list>

#include <util/NonCopyable.h>

#include "feature/FeatureSetCursor2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API MultiplexingFeatureSetCursor : public FeatureSetCursor2
            {
            public :
                MultiplexingFeatureSetCursor() NOTHROWS;
            private :
                MultiplexingFeatureSetCursor (const MultiplexingFeatureSetCursor&) = delete;
            public :
                Util::TAKErr add(FeatureSetCursor2Ptr &&result) NOTHROWS;
            public :
                Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS;
            public :
                Util::TAKErr moveToNext() NOTHROWS;
            private :
                const MultiplexingFeatureSetCursor& operator= (const MultiplexingFeatureSetCursor&) = delete;
            private :
                std::list<FeatureSetCursor2Ptr> results;
            };
        }
    }
}

#endif
