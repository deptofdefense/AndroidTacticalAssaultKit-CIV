#ifndef ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLPOINT_H_INCLUDED
#define ATAKMAP_CPP_CLI_RENDERER_FEATURE_GEOMETRY_GLPOINT_H_INCLUDED

#include "renderer/feature/GLGeometry.h"
#include "math/Point.h"

namespace atakmap {
    
    namespace feature {
        class Point;
    }
    
    namespace renderer {
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            class GLPoint : public GLGeometry {
            public:
                GLPoint(atakmap::feature::Point *pt);
                virtual ~GLPoint();

                void getVertex(const atakmap::renderer::map::GLMapView *view, int vertexType, math::PointD *vertex);
            
            private:
                math::PointD point;
                math::PointD vertex;
                math::PointD pixel;
            };
        }
    }
}

#endif
