#include "renderer/raster/TileCacheControl.h"

using namespace TAK::Engine::Renderer::Raster;

TileCacheControl::~TileCacheControl() NOTHROWS
{}

TileCacheControl::OnTileUpdateListener::~OnTileUpdateListener() NOTHROWS
{}

const char *TAK::Engine::Renderer::Raster::TileCacheControl_getType() NOTHROWS
{
    return "TAK.Engine.Renderer.Raster.TileCacheControl";
}
