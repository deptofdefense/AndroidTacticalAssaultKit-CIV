#include "renderer/raster/RasterDataAccessControl.h"

using namespace TAK::Engine::Renderer::Raster;

RasterDataAccessControl::~RasterDataAccessControl()
{
}

const char *TAK::Engine::Renderer::Raster::RasterDataAccessControl_getType() NOTHROWS
{
    return "TAK.Engine.Renderer.Raster.RasterDataAccessControl";
}
