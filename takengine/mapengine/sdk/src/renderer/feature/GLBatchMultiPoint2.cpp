#include "renderer/feature/GLBatchMultiPoint2.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace atakmap::renderer;

GLBatchMultiPoint2::GLBatchMultiPoint2(TAK::Engine::Core::RenderContext &surface) NOTHROWS :
    GLBatchGeometryCollection2(surface, 11, 1)
{}
