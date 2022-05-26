#include "model/VertexDataLayout.h"

#include <cstring>

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    std::size_t getDataTypeSize(const DataType &type) NOTHROWS
    {
        switch(type) {
        case TEDT_Int8 :
        case TEDT_UInt8 :
            return 1u;
        case TEDT_Int16 :
        case TEDT_UInt16 :
            return 1u;
        case TEDT_Int32 :
        case TEDT_UInt32 :
        case TEDT_Float32 :
            return 4u;
        case TEDT_Float64 :
            return 8u;
        }

        return 0u;
    }
}

TAKErr TAK::Engine::Model::VertexDataLayout_createDefaultInterleaved(VertexDataLayout *value, const unsigned int attrs) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!attrs)
        return TE_InvalidArg;

    memset(value, 0u, sizeof(*value));

    std::size_t off = 0;
#define DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(vao, teva, t, e, n) \
    if(attrs&teva) { \
        value->vao.type = t; \
        value->vao.offset = off; \
        value->vao.size = e; \
        value->vao.normalize = n; \
        off += getDataTypeSize(t)*value->vao.size; \
    }

    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(position, TEVA_Position, TEDT_Float32, 3u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord0, TEVA_TexCoord0, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord1, TEVA_TexCoord1, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord2, TEVA_TexCoord2, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord3, TEVA_TexCoord3, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord4, TEVA_TexCoord4, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord5, TEVA_TexCoord5, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord6, TEVA_TexCoord6, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(texCoord7, TEVA_TexCoord7, TEDT_Float32, 2u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(normal, TEVA_Normal, TEDT_Float32, 3u, false);
    DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS(color, TEVA_Color, TEDT_UInt8, 4u, true);

 #undef DEFAULT_INTERLEAVE_PARAMS_SET_PARAMS

    value->attributes = attrs;
    value->position.stride = off;
    value->normal.stride = off;
    value->color.stride = off;
    value->texCoord0.stride = off;
    value->texCoord1.stride = off;
    value->texCoord2.stride = off;
    value->texCoord3.stride = off;
    value->texCoord4.stride = off;
    value->texCoord5.stride = off;
    value->texCoord6.stride = off;
    value->texCoord7.stride = off;
    value->interleaved = true;
    return TE_Ok;
}

TAKErr TAK::Engine::Model::VertexDataLayout_requiredDataSize(std::size_t *value, const VertexDataLayout &layout, const VertexAttribute attr, const std::size_t numVertices) NOTHROWS
{
    *value = 0u;
    if(numVertices) {
        switch(attr) {
#define TEVA_CASE(vao, teva, elems) \
    case (teva) : \
    { \
        *value = layout.vao.offset + (layout.vao.stride * (numVertices-1u)) + getDataTypeSize(layout.vao.type)*(layout.vao.size ? layout.vao.size : (elems)); \
        break; \
    }
            TEVA_CASE(position, TEVA_Position, 3u);
            TEVA_CASE(texCoord0, TEVA_TexCoord0, 2u);
            TEVA_CASE(texCoord1, TEVA_TexCoord1, 2u);
            TEVA_CASE(texCoord2, TEVA_TexCoord2, 2u);
            TEVA_CASE(texCoord3, TEVA_TexCoord3, 2u);
            TEVA_CASE(texCoord4, TEVA_TexCoord4, 2u);
            TEVA_CASE(texCoord5, TEVA_TexCoord5, 2u);
            TEVA_CASE(texCoord6, TEVA_TexCoord6, 2u);
            TEVA_CASE(texCoord7, TEVA_TexCoord7, 2u);
            TEVA_CASE(normal, TEVA_Normal, 3u);
            TEVA_CASE(color, TEVA_Color, 4u);
            TEVA_CASE(reserved[0u], TEVA_Reserved0, 0u);
            TEVA_CASE(reserved[1u], TEVA_Reserved1, 0u);
            TEVA_CASE(reserved[2u], TEVA_Reserved2, 0u);
            TEVA_CASE(reserved[3u], TEVA_Reserved3, 0u);
            TEVA_CASE(reserved[4u], TEVA_Reserved4, 0u);
            TEVA_CASE(reserved[5u], TEVA_Reserved5, 0u);
            TEVA_CASE(reserved[6u], TEVA_Reserved6, 0u);
            TEVA_CASE(reserved[7u], TEVA_Reserved7, 0u);
            TEVA_CASE(reserved[8u], TEVA_Reserved8, 0u);
            TEVA_CASE(reserved[9u], TEVA_Reserved9, 0u);
            TEVA_CASE(reserved[10u], TEVA_Reserved10, 0u);
            TEVA_CASE(reserved[11u], TEVA_Reserved11, 0u);
            TEVA_CASE(reserved[12u], TEVA_Reserved12, 0u);
            TEVA_CASE(reserved[13u], TEVA_Reserved13, 0u);
            TEVA_CASE(reserved[14u], TEVA_Reserved14, 0u);
            TEVA_CASE(reserved[15u], TEVA_Reserved15, 0u);
            TEVA_CASE(reserved[16u], TEVA_Reserved16, 0u);
            TEVA_CASE(reserved[17u], TEVA_Reserved17, 0u);
            TEVA_CASE(reserved[18u], TEVA_Reserved18, 0u);
            TEVA_CASE(reserved[19u], TEVA_Reserved19, 0u);
            TEVA_CASE(reserved[20u], TEVA_Reserved20, 0u);

#undef CHECK_SIZE
            default :
                return TE_InvalidArg;
        }
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Model::VertexDataLayout_requiredInterleavedDataSize(std::size_t *value, const VertexDataLayout &layout, const std::size_t numVertices) NOTHROWS
{
    if(!layout.interleaved)
        return TE_InvalidArg;

    *value = 0u;
    if(numVertices) {
#define CHECK_SIZE(vao, teva, elems) \
    if(layout.attributes&teva) { \
        const std::size_t maxPos = layout.vao.offset + (layout.vao.stride * (numVertices-1u)) + getDataTypeSize(layout.vao.type)*(layout.vao.size ? layout.vao.size : (elems)); \
        if(maxPos > *value) \
            *value = maxPos; \
    }

        CHECK_SIZE(position, TEVA_Position, 3u);
        CHECK_SIZE(texCoord0, TEVA_TexCoord0, 2u);
        CHECK_SIZE(texCoord1, TEVA_TexCoord1, 2u);
        CHECK_SIZE(texCoord2, TEVA_TexCoord2, 2u);
        CHECK_SIZE(texCoord3, TEVA_TexCoord3, 2u);
        CHECK_SIZE(texCoord4, TEVA_TexCoord4, 2u);
        CHECK_SIZE(texCoord5, TEVA_TexCoord5, 2u);
        CHECK_SIZE(texCoord6, TEVA_TexCoord6, 2u);
        CHECK_SIZE(texCoord7, TEVA_TexCoord7, 2u);
        CHECK_SIZE(normal, TEVA_Normal, 3u);
        CHECK_SIZE(color, TEVA_Color, 4u);
        CHECK_SIZE(reserved[0u], TEVA_Reserved0, 0u);
        CHECK_SIZE(reserved[1u], TEVA_Reserved1, 0u);
        CHECK_SIZE(reserved[2u], TEVA_Reserved2, 0u);
        CHECK_SIZE(reserved[3u], TEVA_Reserved3, 0u);
        CHECK_SIZE(reserved[4u], TEVA_Reserved4, 0u);
        CHECK_SIZE(reserved[5u], TEVA_Reserved5, 0u);
        CHECK_SIZE(reserved[6u], TEVA_Reserved6, 0u);
        CHECK_SIZE(reserved[7u], TEVA_Reserved7, 0u);
        CHECK_SIZE(reserved[8u], TEVA_Reserved8, 0u);
        CHECK_SIZE(reserved[9u], TEVA_Reserved9, 0u);
        CHECK_SIZE(reserved[10u], TEVA_Reserved10, 0u);
        CHECK_SIZE(reserved[11u], TEVA_Reserved11, 0u);
        CHECK_SIZE(reserved[12u], TEVA_Reserved12, 0u);
        CHECK_SIZE(reserved[13u], TEVA_Reserved13, 0u);
        CHECK_SIZE(reserved[14u], TEVA_Reserved14, 0u);
        CHECK_SIZE(reserved[15u], TEVA_Reserved15, 0u);
        CHECK_SIZE(reserved[16u], TEVA_Reserved16, 0u);
        CHECK_SIZE(reserved[17u], TEVA_Reserved17, 0u);
        CHECK_SIZE(reserved[18u], TEVA_Reserved18, 0u);
        CHECK_SIZE(reserved[19u], TEVA_Reserved19, 0u);
        CHECK_SIZE(reserved[20u], TEVA_Reserved20, 0u);
#undef CHECK_SIZE
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Model::VertexDataLayout_getTexCoordArray(VertexArray *value, const VertexDataLayout &layout, const int index) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (index < 0 || index > 7)
        return TE_BadIndex;
    switch (index)
    {
    case 0:
        *value = layout.texCoord0;
        break;
    case 1:
        *value = layout.texCoord1;
        break;
    case 2:
        *value = layout.texCoord2;
        break;
    case 3:
        *value = layout.texCoord3;
        break;
    case 4:
        *value = layout.texCoord4;
        break;
    case 5:
        *value = layout.texCoord5;
        break;
    case 6:
        *value = layout.texCoord6;
        break;
    case 7:
        *value = layout.texCoord7;
        break;
    default :
        return TE_IllegalState;
    }

    return TE_Ok;
}
