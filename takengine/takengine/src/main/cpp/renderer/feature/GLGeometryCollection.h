#ifndef ATAKMAP_RENDERER_FEATURE_GEOMETRY_GLGEOMETRYCOLLECTION_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GEOMETRY_GLGEOMETRYCOLLECTION_H_INCLUDED

#include "renderer/map/GLMapView.h"
#include "renderer/feature/GLGeometry.h"
#include "feature/GeometryCollection.h"
#include "port/Iterator.h"

namespace atakmap {
    
    namespace feature {
        class GeometryCollection;
    }
    
    namespace renderer {
        namespace feature {
            
            class GLGeometryCollection : public GLGeometry {
            public:
                GLGeometryCollection(atakmap::feature::GeometryCollection *collection);
                virtual ~GLGeometryCollection();

                atakmap::port::Iterator<GLGeometry *> *getIterator();

            public:
                typedef std::vector<GLGeometry *> GeometryList;

            public:
                class GLGeometryIterator : public atakmap::port::Iterator<GLGeometry *> {
                public:
                    GLGeometryIterator(GeometryList::iterator first, GeometryList::iterator last);
                    virtual ~GLGeometryIterator();
                    virtual bool hasNext();
                    virtual GLGeometry *next();
                    virtual GLGeometry *get();
                private:
                    GeometryList::iterator pos;
                    GeometryList::iterator end;
                };
                
            private:
                GeometryList geometries;
            };
        }
    }
}

#endif
