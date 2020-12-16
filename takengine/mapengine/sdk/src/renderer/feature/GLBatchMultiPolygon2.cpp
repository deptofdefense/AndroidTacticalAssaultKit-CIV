#include "renderer/feature/GLBatchMultiPolygon2.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace atakmap::renderer;

GLBatchMultiPolygon2::GLBatchMultiPolygon2(TAK::Engine::Core::RenderContext &surface) :
    GLBatchGeometryCollection2(surface, 13, 3)
{}
