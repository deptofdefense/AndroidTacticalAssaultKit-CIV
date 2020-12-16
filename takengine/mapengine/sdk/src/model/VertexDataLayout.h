#ifndef TAK_ENGINE_MODEL_VERTEXDATALAYOUT_H_INCLUDED
#define TAK_ENGINE_MODEL_VERTEXDATALAYOUT_H_INCLUDED

#include "model/VertexArray.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            enum VertexAttribute
            {
                TEVA_Normal =       0x001u,
                TEVA_Color =        0x002u,
                TEVA_Position =     0x004u,
                TEVA_TexCoord0 =    0x008u,
                TEVA_TexCoord1 =    0x010u,
                TEVA_TexCoord2 =    0x020u,
                TEVA_TexCoord3 =    0x040u,
                TEVA_TexCoord4 =    0x080u,
                TEVA_TexCoord5 =    0x100u,
                TEVA_TexCoord6 =    0x200u,
                TEVA_TexCoord7 =    0x400u,
            };

            struct VertexDataLayout
            {
                unsigned int attributes{ 0u };

                VertexArray position;
                VertexArray normal;
                VertexArray color;
                VertexArray texCoord0;
                VertexArray texCoord1;
                VertexArray texCoord2;
                VertexArray texCoord3;
                VertexArray texCoord4;
                VertexArray texCoord5;
                VertexArray texCoord6;
                VertexArray texCoord7;

                bool interleaved;
            };

            typedef std::unique_ptr<VertexDataLayout, void(*)(const VertexDataLayout *)> VertexDataLayoutPtr;
            typedef std::unique_ptr<const VertexDataLayout, void(*)(const VertexDataLayout *)> VertexDataLayoutPtr_const;

            ENGINE_API Util::TAKErr VertexDataLayout_createDefaultInterleaved(VertexDataLayout *value, const unsigned int attrs) NOTHROWS;
            ENGINE_API Util::TAKErr VertexDataLayout_requiredDataSize(std::size_t *value, const VertexDataLayout &layout, const VertexAttribute attr, const std::size_t numVertices) NOTHROWS;
            ENGINE_API Util::TAKErr VertexDataLayout_requiredInterleavedDataSize(std::size_t *value, const VertexDataLayout &layout, const std::size_t numVertices) NOTHROWS;

            /**
             * @param texCoordIndex The texture coordinate index. Valid values are 0 through 7 (inclusive)
             */
            ENGINE_API Util::TAKErr VertexDataLayout_getTexCoordArray(VertexArray *value, const VertexDataLayout &layout, const int texCoordIndex) NOTHROWS;
        }
    }
}

#endif
