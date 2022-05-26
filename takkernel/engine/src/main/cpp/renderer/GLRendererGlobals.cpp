
#include "renderer/GLRendererGlobals.h"
#include "renderer/GLTextureCache.h"

using namespace atakmap::renderer;

AsyncBitmapLoader *GLRendererGlobals::getAsyncBitmapLoader() {
    //XXX- init once
    static std::unique_ptr<AsyncBitmapLoader> loader(new AsyncBitmapLoader(1));
    return loader.get();
}

GLTextureAtlas *GLRendererGlobals::getCommonTextureAtlasForImageSize(int width, int height) {
    
    return nullptr;
}
