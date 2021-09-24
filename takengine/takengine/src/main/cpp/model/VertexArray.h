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
                std::size_t offset {0u};
                std::size_t stride {0u};
                /**
                 * If not specified, assumed to be norminal default for the
                 * associated attribute type (if well defined)
                 */
                std::size_t size {0u};
                /** */
                bool normalize {false};
            };
        }
    }
}

#endif
