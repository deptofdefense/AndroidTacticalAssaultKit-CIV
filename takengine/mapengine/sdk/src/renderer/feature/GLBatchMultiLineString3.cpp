#include "renderer/feature/GLBatchMultiLineString3.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace atakmap::renderer;

GLBatchMultiLineString3::GLBatchMultiLineString3(TAK::Engine::Core::RenderContext &surface) NOTHROWS :
    GLBatchGeometryCollection3(surface, 12, 2)
{}
