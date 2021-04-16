#ifndef ATAKMAP_RENDERER_GLLAYERSPI_H_INCLUDED
#define ATAKMAP_RENDERER_GLLAYERSPI_H_INCLUDED

#include <memory>

#include "spi/ServiceProvider.h"
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
                class GLLayer;

                typedef struct GLLayerSpiArg
                {
                    atakmap::renderer::map::GLMapView *context;
                    atakmap::core::Layer *layer;

                    GLLayerSpiArg(atakmap::renderer::map::GLMapView *context, atakmap::core::Layer *layer);
                } GLLayerSpiArg;

                typedef atakmap::spi::PriorityServiceProvider<atakmap::spi::ServiceProvider<GLLayer, GLLayerSpiArg>> GLLayerSpi;

                typedef std::unique_ptr<GLLayerSpi, void(*)(const GLLayerSpi *)> GLLayerSpiPtr;
            }
        }
    }
}

#endif
