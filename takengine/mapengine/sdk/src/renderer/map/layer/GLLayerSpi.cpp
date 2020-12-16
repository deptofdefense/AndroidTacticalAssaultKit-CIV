#include "renderer/map/layer/GLLayerSpi.h"

using namespace atakmap::renderer::map::layer;

GLLayerSpiArg::GLLayerSpiArg(atakmap::renderer::map::GLMapView *c, atakmap::core::Layer *l) :
    context(c),
    layer(l)
{}
