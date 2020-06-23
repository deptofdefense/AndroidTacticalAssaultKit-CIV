#ifndef TAK_ENGINE_UTIL_NONCOPYABLE_H_INCLUDED
#define TAK_ENGINE_UTIL_NONCOPYABLE_H_INCLUDED

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            class NonCopyable
            {
            protected :
                NonCopyable() NOTHROWS = default;
            public :
                NonCopyable(const NonCopyable &) NOTHROWS = delete;
            };
        }
    }
}
#endif
