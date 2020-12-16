#ifndef TAK_ENGINE_RENDERER_GLTRIANGULATE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTRIANGULATE2_H_INCLUDED

#include <cstdlib>
#include <cstdint>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            /**
             * Performs a 2D tessellation of the specified polygon.
             *
             * @param indices   Returns the indices, must have a capacity of at least <code>(numVerts-2)*3</code> elements
             * @param idxCount  Returns the number of indices computed
             * @param vertices  The vertices buffer, ordered x,y
             * @param stride    The stride, in elements between coordinate pairs
             * @param numVerts  The number of vertices in the polygon
             *
             * @return  TE_Ok on success
             */
            Util::TAKErr GLTriangulate2_triangulate(uint16_t *indices, std::size_t *idxCount, const float *vertices, const std::size_t stride, const std::size_t numVerts) NOTHROWS;

            /**
             * Performs a 2D tessellation of the specified polygon.
             *
             * @param indices   Returns the indices, must have a capacity of at least <code>(numVerts-2)*3</code> elements
             * @param idxCount  Returns the number of indices computed
             * @param vertices  The vertices buffer, ordered x,y
             * @param stride    The stride, in elements between coordinate pairs
             * @param numVerts  The number of vertices in the polygon
             *
             * @return  TE_Ok on success
             */
            Util::TAKErr GLTriangulate2_triangulate(uint16_t *indices, std::size_t *idxCount, const double *vertices, const std::size_t stride, const std::size_t numVerts) NOTHROWS;
        }
    }
}
#endif
