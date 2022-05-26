
#ifndef ATAKMAP_RENDERER_GLRENDERERGLOBALS_H_INCLUDED
#define ATAKMAP_RENDERER_GLRENDERERGLOBALS_H_INCLUDED

#include "renderer/AsyncBitmapLoader.h"

namespace atakmap {
    
    namespace renderer {
     
        class GLTextureAtlas;
        
        class GLRendererGlobals {
        public:
            static AsyncBitmapLoader *getAsyncBitmapLoader();
            
            static GLTextureAtlas *getCommonTextureAtlasForImageSize(int width, int height);
        };
        
    }
    
}

#endif
