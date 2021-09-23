#ifndef TAK_ENGINE_FEATURE_FILTERFEATURESETCURSOR2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FILTERFEATURESETCURSOR2_H_INCLUDED

#include "feature/FeatureSetCursor2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FilterFeatureSetCursor2 : public FeatureSetCursor2
            {
            public :
                FilterFeatureSetCursor2(FeatureSetCursor2Ptr&& impl) NOTHROWS;
                ~FilterFeatureSetCursor2() NOTHROWS;
            public : // FeatureSetCursor2
                Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS override;
            public : // RowIterator
                Util::TAKErr moveToNext() NOTHROWS override;
            protected :
                FeatureSetCursor2Ptr impl;
            }; // FeatureSetCursor
        }
    }
}

#endif
