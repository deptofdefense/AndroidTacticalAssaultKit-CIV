#ifndef ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLPOLYGON_H_INCLUDED
#define ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLPOLYGON_H_INCLUDED

#include "renderer/feature/GLGeometry.h"
#include "util/MemBuffer.h"

namespace atakmap {
    
    namespace feature {
        class Polygon;
    }
    
    namespace renderer {
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            class GLPolygon : public GLGeometry {
            public:
                GLPolygon(atakmap::feature::Polygon *polygon);
                virtual ~GLPolygon();
                
                std::pair<float *, size_t> getVertices(const atakmap::renderer::map::GLMapView *view, int vertexType, int ring);
                int getNumVertices(int ring);
                int getNumInteriorRings();
                
                std::pair<float *, size_t> getPoints(int ring);

            private:
                atakmap::util::MemBufferT<float> points;
                /** projected coordinate space x,y,z */
                atakmap::util::MemBufferT<float> vertices;
                /** screen pixels x,y */
                atakmap::util::MemBufferT<float> pixels;

                /** number of vertices per ring */
                atakmap::util::MemBufferT<int> ringVerts;
                /** offset in vertices to start of ring */
                atakmap::util::MemBufferT<int> ringOffset;

                int totalPoints;
            };

        }
    }
}

#endif
