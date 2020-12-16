#ifndef ATAKMAP_RENDERER_GLMAPLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPLAYER_H_INCLUDED

#include <string>
#include "renderer/map/GLMapRenderable.h"

namespace atakmap {
    namespace raster {
        class DatasetDescriptor;
    }
}

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {

                    class GLMapLayer : public GLMapRenderable {
                    public:
                        virtual ~GLMapLayer();
                        virtual const char *getLayerUri() const = 0;

                        virtual const atakmap::raster::DatasetDescriptor *getInfo() const = 0;
                    };
                }
            }
        }
    }
}

#endif
