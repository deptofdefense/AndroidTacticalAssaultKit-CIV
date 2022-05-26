#include "renderer/HeightMap.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    template<class PostType, class NormalType>
    TAKErr generateNormalsImpl(MemBuffer2& value, const MemBuffer2& src, const std::size_t numPostsX, const std::size_t numPostsY, const VertexArray postAttribLayout, const std::size_t postScanStride, const VertexArray normalAttribLayout, const std::size_t normalScanStride, const bool clamp, const double scaleX, const double scaleY, const double scaleZ, const double mapRange) NOTHROWS;
}

TAKErr TAK::Engine::Renderer::Heightmap_generateNormals(MemBuffer2& value, const MemBuffer2& src, const std::size_t numPostsX, const std::size_t numPostsY, const VertexArray postAttribLayout, const std::size_t postScanStride, const VertexArray normalAttribLayout, const std::size_t normalScanStride, const bool clamp, const double scaleX, const double scaleY, const double scaleZ) NOTHROWS
{
    // confirm source elements
    const std::size_t srcrem = src.remaining();
    const std::size_t required = (((numPostsY - 1u) * postScanStride) + (numPostsX * postAttribLayout.stride));
    if (src.remaining() < required)
        return TE_InvalidArg;

    switch (postAttribLayout.type) {
#define NORMAL_CASE_IMPL(pt, nt, mr) \
    return generateNormalsImpl<pt, nt>(value, src, numPostsX, numPostsY, postAttribLayout, postScanStride, normalAttribLayout, normalScanStride, clamp, scaleX, scaleY, scaleZ, mr);
#define POST_CASE_IMPL(pt) \
    switch(normalAttribLayout.type) { \
        case TEDT_UInt8 : NORMAL_CASE_IMPL(pt, uint8_t, (double)0xFFu) \
        case TEDT_Int8 : NORMAL_CASE_IMPL(pt, int8_t, (double)0x7F) \
        case TEDT_UInt16 : NORMAL_CASE_IMPL(pt, uint16_t, (double)0xFFFFu) \
        case TEDT_Int16 : NORMAL_CASE_IMPL(pt, int16_t, (double)0x7FFF) \
        case TEDT_UInt32 : NORMAL_CASE_IMPL(pt, uint32_t, (double)0xFFFFFFFFu) \
        case TEDT_Int32 : NORMAL_CASE_IMPL(pt, int32_t, (double)0x7FFFFFFF) \
        case TEDT_UInt64 : NORMAL_CASE_IMPL(pt, uint64_t, (double)0xFFFFFFFFFFFFFFFFULL) \
        case TEDT_Int64 : NORMAL_CASE_IMPL(pt, int64_t, (double)0x7FFFFFFFFFFFFFFFLL) \
        case TEDT_Float32 : NORMAL_CASE_IMPL(pt, float, 1.0) \
        case TEDT_Float64 : NORMAL_CASE_IMPL(pt, double, 1.0) \
        default : \
            return TE_InvalidArg; \
    }

    case TEDT_UInt8: POST_CASE_IMPL(uint8_t) break;
    case TEDT_Int8: POST_CASE_IMPL(int8_t) break;
    case TEDT_UInt16: POST_CASE_IMPL(uint16_t) break;
    case TEDT_Int16: POST_CASE_IMPL(int16_t) break;
    case TEDT_UInt32: POST_CASE_IMPL(uint32_t) break;
    case TEDT_Int32: POST_CASE_IMPL(int32_t) break;
    case TEDT_UInt64: POST_CASE_IMPL(uint64_t) break;
    case TEDT_Int64: POST_CASE_IMPL(int64_t) break;
    case TEDT_Float32: POST_CASE_IMPL(float) break;
    case TEDT_Float64: POST_CASE_IMPL(double) break;
    default: return TE_InvalidArg;
#undef CASE_IMPL
    }
}

namespace
{
    template<class PostType, class NormalType>
    TAKErr generateNormalsImpl(MemBuffer2& value, const MemBuffer2& srcbuf, const std::size_t numPostsX, const std::size_t numPostsY, const VertexArray postAttribLayout, const std::size_t postScanStride, const VertexArray normalAttribLayout, const std::size_t normalScanStride, const bool clamp, const double scaleX, const double scaleY, const double scaleZ, const double mapRange) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const std::size_t valueInitPos = value.position();

        const uint8_t* src = srcbuf.get() + srcbuf.position() + postAttribLayout.offset;
        for (int y = 0u; y < (int)numPostsY; y++) {
            const int topOff = (y||!clamp) ? y - 1u : y;
            const int bottomOff = (y < (numPostsY - 1u) || !clamp) ? y + 1 : y;
            for (int x = 0u; x < (int)numPostsX; x++) {
                const int leftOff = (x||!clamp) ? x - 1 : x;
                const int rightOff = (x < (numPostsX - 1) || !clamp) ? x + 1 : x;

                PostType left = *reinterpret_cast<const PostType*>(src + (postScanStride * y) + (leftOff * postAttribLayout.stride));
                PostType right = *reinterpret_cast<const PostType*>(src + (postScanStride * y) + (rightOff * postAttribLayout.stride));
                PostType top = *reinterpret_cast<const PostType*>(src + (postScanStride * topOff) + (x * postAttribLayout.stride));
                PostType bottom = *reinterpret_cast<const PostType*>(src + (postScanStride * bottomOff) + (x * postAttribLayout.stride));

                Point2<double> normal;
                code = Heightmap_calculateNormal(&normal, left, right, top, bottom, scaleX, scaleY, scaleZ);
                TE_CHECKBREAK_CODE(code);

                // position for the normal write
                code = value.position(valueInitPos + normalAttribLayout.offset + (y * normalScanStride) + (x * normalAttribLayout.stride));
                TE_CHECKBREAK_CODE(code);

                NormalType n[3u];
                n[0u] = (NormalType)(normal.x*mapRange);
                n[1u] = (NormalType)(normal.y*mapRange);
                n[2u] = (NormalType)(normal.z*mapRange);
                code = value.put(n, 3u);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}
