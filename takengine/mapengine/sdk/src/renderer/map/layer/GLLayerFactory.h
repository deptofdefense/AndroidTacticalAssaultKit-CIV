#ifndef ATAKMAP_RENDERER_GLLAYERFACTORY_H_INCLUDED
#define ATAKMAP_RENDERER_GLLAYERFACTORY_H_INCLUDED

#include "renderer/map/layer/GLLayerSpi.h"
#include "spi/ServiceProviderRegistry.h"

namespace atakmap
{
    namespace core {
        class Layer;
    }

    namespace renderer
    {
        namespace map
        {
            class GLMapView;

            namespace layer
            {
                class GLLayerFactory
                {
                private:
                    // Not instantiated
                    GLLayerFactory() {};
                public :
                    static void registerSpi(GLLayerSpi *spi, int priority);
                    static void unregisterSpi(GLLayerSpi *spi);

                    static GLLayer *create(GLMapView *context, atakmap::core::Layer *layer);
                };
            }
        }
    }
}

#endif
