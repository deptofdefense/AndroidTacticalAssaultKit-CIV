#include "renderer/feature/GLBatchMultiPoint3.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;

using namespace atakmap::renderer;

GLBatchMultiPoint3::GLBatchMultiPoint3(RenderContext &surface) NOTHROWS :
    GLBatchGeometryCollection3(surface, 11, 1)
{}
