#ifndef TAK_ENGINE_PORT_VECTOR_H_INCLUDED
#define TAK_ENGINE_PORT_VECTOR_H_INCLUDED

#include <cstddef>

#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Port {
            template<class T>
            class Vector : public Collection<T>
            {
            public:
                virtual TAK::Engine::Util::TAKErr insert(T value, const std::size_t idx) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr erase(const std::size_t idx) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr get(T &value, const std::size_t idx) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr set(T value, const std::size_t idx) NOTHROWS = 0;

                virtual std::size_t size() NOTHROWS = 0;
                virtual bool empty() NOTHROWS = 0;
            };
        }
    }
}

#endif // TAK_ENGINE_PORT_VECTOR_H_INCLUDED
