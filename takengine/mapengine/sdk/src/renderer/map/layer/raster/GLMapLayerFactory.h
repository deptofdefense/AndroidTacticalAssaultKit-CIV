#ifndef ATAKMAP_RENDERER_GLMAPLAYERFACTORY_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPLAYERFACTORY_H_INCLUDED

#include <vector>

#include "thread/Mutex.h"

#include "renderer/map/layer/raster/GLMapLayerSpi.h"

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {

                        class GLMapLayerFactory {

                        private:
                            typedef std::vector<GLMapLayerSPI *> GLMapLayerSpiList;
                            static GLMapLayerSpiList spis;
                            static TAK::Engine::Thread::Mutex mutex;

                            GLMapLayerFactory();

                        public:
                            static GLMapLayer *create(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info);

                            static void registerSpi(GLMapLayerSPI *spi);

                            static void unregisterSpi(GLMapLayerSPI *spi);
                        };

                }
            }
        }
    }
}



#endif
