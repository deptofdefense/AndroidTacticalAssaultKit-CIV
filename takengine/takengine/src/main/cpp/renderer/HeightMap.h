#ifndef TAK_ENGINE_RENDERER_HEIGHTMAP_H_INCLUDED
#define TAK_ENGINE_RENDERER_HEIGHTMAP_H_INCLUDED

#include "math/Point2.h"
#include "math/Vector4.h"
#include "model/VertexArray.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            template<class T>
            ENGINE_API inline Util::TAKErr Heightmap_calculateNormal(Math::Point2<double>* value, const T left, const T right, const T top, const T bottom, const double scaleX = 1.0, const double scaleY = 1.0, const double scaleZ = 1.0) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                if (!value)
                    return Util::TE_InvalidArg;
                const double nx = static_cast<double>(left - right) / (2.0 * scaleX);
                const double ny = static_cast<double>(top - bottom) / (2.0 * scaleY);
                const double nz = scaleZ;
                return Math::Vector2_normalize(value, Math::Point2<double>(nx, ny, nz));
            }

            /**
             * Generates a normal map given a height map.
             *
             * @param value     Normals are written to this buffer, per
             *                  `normalAttribLayout`.
             *                  `normalAttribLayout.offset` is interpreted as
             *                  from the current value of `value.position()`
             * @param src       Heightmap source data
             * @param numPostsX The number of posts in the heightmap along the
             *                  X-axis
             * @param numPostsY The number of posts in the heightmap along the
             *                  Y-axis
             * @param postAttribLayout
             *                  Vertex attribute layout for posts. Heights will
             *                  be interpreted as first vertex element,
             *                  starting at `postAttribLayout.offset` and
             *                  repeating every `postAttribLayout.stride`
             *                  bytes.
             * @param postScanStride
             *                  The number of bytes between consecutive post
             *                  rows in the source heightmap
             * @param normalAttribLayout
             *                  The layout for output the normal vertex
             *                  attribute. If the `normalAttribLayout.type` is
             *                  an integer type, the elements of the normal
             *                  will be mapped to the range of that integer
             *                  type.
             * @param normalScanStride
             *                  The number of bytes between consecutive post
             *                  rows in the output buffer
             * @param clamp     If `true` posts on the edge will use own height
             *                  for neighbors out of bounds; if `false` posts
             *                  on edge will use neighbors out of bounds --
             *                  caller must guarantee input data has additional
             *                  border of one post.
             */
            ENGINE_API Util::TAKErr Heightmap_generateNormals(Util::MemBuffer2& value, const Util::MemBuffer2& src, const std::size_t numPostsX, const std::size_t numPostsY, const Model::VertexArray postAttribLayout, const std::size_t postScanStride, const Model::VertexArray normalAttribLayout, const std::size_t normalScanStride, const bool clamp = true, const double scaleX = 1.0, const double scaleY = 1.0, const double scaleZ = 1.0) NOTHROWS;
        }
    }
}
#endif
