#ifndef ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLLINESTRING_H_INCLUDED
#define ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLLINESTRING_H_INCLUDED

#include "renderer/feature/GLGeometry.h"
#include "util/MemBuffer.h"

namespace atakmap {
    
    namespace feature {
        class LineString;
    }
    
    namespace renderer {
        
        namespace map {
            class GLMapView;
        }
            
        namespace feature {
            class GLLineString : public GLGeometry {
            public:
                GLLineString(atakmap::feature::LineString *lineString);
                virtual ~GLLineString();

                const float *getVertices(const atakmap::renderer::map::GLMapView *view, int vertexType);
                size_t getNumVertices();

            private:
                atakmap::util::MemBufferT<float> points;
                atakmap::util::MemBufferT<float> vertices;
                atakmap::util::MemBufferT<float> pixels;

                size_t numPoints;
            };


        }
    }
}

#endif
