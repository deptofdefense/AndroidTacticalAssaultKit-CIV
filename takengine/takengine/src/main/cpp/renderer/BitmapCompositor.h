
#ifndef ATAKMAP_RENDERER_BITMAPCOMPOSITOR_H_INCLUDED
#define ATAKMAP_RENDERER_BITMAPCOMPOSITOR_H_INCLUDED

#include "renderer/Bitmap.h"

namespace atakmap {
    namespace renderer {
        
        class BitmapCompositor {
        public:
            virtual ~BitmapCompositor();
            
            void composite(float destX, float destY, float destW, float destH,
                           float srcX, float srcY, float srcW, float srcH,
                           const Bitmap &src);
        
            void debugText(float destX, float destY, float destW, float destH, const char *text);
            
            // Implemented on platform
            static BitmapCompositor *create(const Bitmap &dstBitmap);
            
        protected:
            BitmapCompositor();
            
            virtual void compositeImpl(float destX, float destY, float destW, float destH,
                                       float srcX, float srcY, float srcW, float srcH,
                                       const Bitmap &src) = 0;
            
            virtual void debugTextImpl(float destX, float destY, float destW, float destH, const char *text) = 0;
        };
        
    }
}

#endif