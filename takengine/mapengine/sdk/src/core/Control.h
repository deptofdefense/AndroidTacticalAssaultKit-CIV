#ifndef TAK_ENGINE_CORE_CONTROL_H_INCLUDED
#define TAK_ENGINE_CORE_CONTROL_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            struct ENGINE_API Control
            {
                const char *type;
                void *value;
            };
        }
    }
}

#endif
