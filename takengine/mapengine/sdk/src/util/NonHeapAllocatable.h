#ifndef TAK_ENGINE_UTIL_NONHEAPALLOCATABLE_H_INCLUDED
#define TAK_ENGINE_UTIL_NONHEAPALLOCATABLE_H_INCLUDED

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            class ENGINE_API NonHeapAllocatable
            {
            private :
                void *operator new(std::size_t) = delete;
                void *operator new[](std::size_t) = delete;
                void operator delete(void *) = delete;
                void operator delete[](void *) = delete;
            };
        }
    }
}

#endif
