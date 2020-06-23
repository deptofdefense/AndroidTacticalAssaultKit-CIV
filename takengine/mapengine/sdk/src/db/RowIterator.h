#ifndef TAK_ENGINE_DB_ROWITERATOR_H_INCLUDED
#define TAK_ENGINE_DB_ROWITERATOR_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace DB {
            class ENGINE_API RowIterator
            {
            protected:
                virtual ~RowIterator() NOTHROWS = 0;
            public:
                virtual TAK::Engine::Util::TAKErr moveToNext() NOTHROWS = 0;
            };
        }
    }
}

#endif
