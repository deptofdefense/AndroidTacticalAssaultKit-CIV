#include "renderer/feature/GLBatchMultiLineString2.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;

using namespace atakmap::renderer;

GLBatchMultiLineString2::GLBatchMultiLineString2(RenderContext &surface) NOTHROWS :
    GLBatchGeometryCollection2(surface, 12, 2)
{}
