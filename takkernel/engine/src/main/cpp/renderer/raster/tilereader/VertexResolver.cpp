#ifdef MSVC
#include "renderer/raster/tilereader/VertexResolver.h"

#include "renderer/raster/tilereader/GLQuadTileNode3.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

VertexResolver::VertexResolver(DatasetProjection2& i2g_) NOTHROWS :
    i2g(i2g_)
{}

void VertexResolver::beginDraw(const GLGlobeBase& view) NOTHROWS
{}
void VertexResolver::endDraw(const Core::GLGlobeBase &view) NOTHROWS
{}
void VertexResolver::beginNode(const GLQuadTileNode3 &node) NOTHROWS
{}
void VertexResolver::endNode(const GLQuadTileNode3 &node) NOTHROWS
{}
TAKErr VertexResolver::project(GeoPoint2 *value, bool *resolved, const int64_t imgSrcX, const int64_t imgSrcY) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!value)
        return TE_InvalidArg;
    code = i2g.imageToGround(value, Point2<double>((double)imgSrcX, (double)imgSrcY));
    TE_CHECKRETURN_CODE(code);
    if (resolved)
        *resolved = true;
    return code;
}
#endif