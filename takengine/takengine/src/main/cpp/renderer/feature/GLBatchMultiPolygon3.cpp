#include "renderer/feature/GLBatchMultiPolygon3.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace atakmap::renderer;

GLBatchMultiPolygon3::GLBatchMultiPolygon3(TAK::Engine::Core::RenderContext &surface) :
    GLBatchGeometryCollection3(surface, 13, 3)
{}
