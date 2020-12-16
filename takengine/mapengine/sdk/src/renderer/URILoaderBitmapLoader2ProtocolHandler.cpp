
#include "renderer/URILoaderBitmapLoader2ProtocolHandler.h"
#include "util/URILoader2.h"

using namespace TAK::Engine::Util;

TAK::Engine::Renderer::URILoaderBitmapLoader2ProtocolHandler::URILoaderBitmapLoader2ProtocolHandler() NOTHROWS { }

TAK::Engine::Renderer::URILoaderBitmapLoader2ProtocolHandler::~URILoaderBitmapLoader2ProtocolHandler() { }

TAK::Engine::Util::TAKErr TAK::Engine::Renderer::URILoaderBitmapLoader2ProtocolHandler::handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS {
    return TAK::Engine::Util::URILoader2_openURI(ctx, uri, nullptr);
}