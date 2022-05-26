#ifndef ATAKMAP_RENDERER_FEATURE_GEOMETRY_GLGEOMETRY_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GEOMETRY_GLGEOMETRY_H_INCLUDED

namespace atakmap {
    
    namespace feature {
        class Geometry;
    }
    
    namespace renderer {
        namespace feature {
            class GLGeometry
            {
            public:
                static const int VERTICES_PROJECTED = 0;
                static const int VERTICES_PIXEL = 1;

                virtual atakmap::feature::Geometry *getSubject();
                virtual ~GLGeometry();
                static GLGeometry *createRenderer(atakmap::feature::Geometry *geom);

            protected:
                GLGeometry(atakmap::feature::Geometry *geom);

            protected:
                atakmap::feature::Geometry *geometry;

                int verticesSrid;
                int pixelCoordsVersion;
            };
        }
    }
}

#endif
