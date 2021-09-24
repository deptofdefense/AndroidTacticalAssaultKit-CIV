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
                TEVA_Normal =       0x00000001u,
                TEVA_Color =        0x00000002u,
                TEVA_Position =     0x00000004u,
                TEVA_TexCoord0 =    0x00000008u,
                TEVA_TexCoord1 =    0x00000010u,
                TEVA_TexCoord2 =    0x00000020u,
                TEVA_TexCoord3 =    0x00000040u,
                TEVA_TexCoord4 =    0x00000080u,
                TEVA_TexCoord5 =    0x00000100u,
                TEVA_TexCoord6 =    0x00000200u,
                TEVA_TexCoord7 =    0x00000400u,

                TEVA_Reserved0 =    0x00000800u,
                TEVA_Reserved1 =    0x00001000u,
                TEVA_Reserved2 =    0x00002000u,
                TEVA_Reserved3 =    0x00004000u,
                TEVA_Reserved4 =    0x00008000u,
                TEVA_Reserved5 =    0x00010000u,
                TEVA_Reserved6 =    0x00020000u,
                TEVA_Reserved7 =    0x00040000u,
                TEVA_Reserved8 =    0x00080000u,
                TEVA_Reserved9 =    0x00100000u,
                TEVA_Reserved10 =   0x00200000u,
                TEVA_Reserved11 =   0x00400000u,
                TEVA_Reserved12 =   0x00800000u,
                TEVA_Reserved13 =   0x01000000u,
                TEVA_Reserved14 =   0x02000000u,
                TEVA_Reserved15 =   0x04000000u,
                TEVA_Reserved16 =   0x08000000u,
                TEVA_Reserved17 =   0x10000000u,
                TEVA_Reserved18 =   0x20000000u,
                TEVA_Reserved19 =   0x40000000u,
                TEVA_Reserved20 =   0x80000000u,
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

                VertexArray reserved[21u];

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
