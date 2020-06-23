#ifndef TAK_ENGINE_MODEL_VERTEXARRAY_H_INCLUDED
#define TAK_ENGINE_MODEL_VERTEXARRAY_H_INCLUDED

#include <cstdlib>

#include <port/Platform.h>

namespace TAK {
    namespace Engine {
        namespace Model {
            struct VertexArray
            {
                Port::DataType type;
                std::size_t offset;
                std::size_t stride;
            };
        }
    }
}

#endif
