#ifndef TAK_ENGINE_CORE_CONTROL_H_INCLUDED
#define TAK_ENGINE_CORE_CONTROL_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            struct ENGINE_API Control
            {
                const char *type {nullptr};
                void *value {nullptr};

                bool operator==(const Control &other) NOTHROWS
                {
                    return (this->value == other.value) && (TAK::Engine::Port::String_strcmp(this->type, other.type) == 0);
                }
            };
        }
    }
}

#endif
