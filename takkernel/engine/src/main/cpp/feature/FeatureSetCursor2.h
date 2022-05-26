#ifndef TAK_ENGINE_FEATURE_FEATURESETCURSOR2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURESETCURSOR2_H_INCLUDED

#include <memory>

#include "db/RowIterator.h"
#include "feature/FeatureSet2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureSetCursor2 : public TAK::Engine::DB::RowIterator
            {
            protected :
                virtual ~FeatureSetCursor2() NOTHROWS = 0;
            public :
                /**
                * Returns the {@link FeatureSet} corresponding to the current row.
                *
                * @return  The {@link FeatureSet} corresponding to the current row.
                */
                virtual TAK::Engine::Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS = 0;
            }; // FeatureSetCursor

            typedef std::unique_ptr<FeatureSetCursor2, void(*)(const FeatureSetCursor2 *)> FeatureSetCursor2Ptr;
        }
    }
}

#endif
