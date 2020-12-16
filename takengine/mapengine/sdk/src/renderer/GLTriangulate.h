#ifndef ATAKMAP_RENDERER_GLTRIANGULATE_H_INCLUDED
#define ATAKMAP_RENDERER_GLTRIANGULATE_H_INCLUDED

#include "core/GeoPoint.h"
#include <map>
#include "port/Platform.h"
#include "util/Memory.h"

namespace atakmap
{
    namespace renderer
    {

        struct ENGINE_API GLTriangulate {
            const static int TRIANGLE_FAN = 0;
            const static int INDEXED = 1;
            const static int STENCIL = 2;

            static int triangulate(core::GeoPoint *verts[], size_t count, short *indices);
            static int triangulate(float *verts, size_t count, short *indices);		
		
		///	<summary> 
		/// Overloaded triangulate to subdivide polygon
		/// Creates new arrays for both verts and indicies. They will need to be deleted, as they are unmanaged
		/// </summary>
		/// <param name="verts"> Input for verticies. Is assumed to be managed memory, and is leaked otherwise. Returns as unmanaged alocated memory containing only new verticies</param>
		/// <param name="vertexCount"> The total number of floats in the vertex array. New vertex entries will begin after this index. This variable will be updated</param>
		/// <param name="indexLength"> The total number of values in the index array. This variable will be updated</param>
			static int triangulate(
				std::unique_ptr<double, void(*)(const double *)>& verts,
				size_t& vertexCount,
                const size_t size,
				size_t stride,
				std::unique_ptr<uint16_t, void(*)(const uint16_t *)>& indices,
				size_t& indexCount,
				std::unique_ptr<uint16_t, void(*)(const uint16_t *)>& perimeterIndicies,
				size_t& perimeterCount,
				float tessellationThreshold,
				bool useGeoPoint
			);

        private:
            // Not intended to be instantiated.
            GLTriangulate();

        };
    }
}

#endif
