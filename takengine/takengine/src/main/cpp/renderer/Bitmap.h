#ifndef ATAKMAP_RENDERER_BITMAP_H_INCLUDED
#define ATAKMAP_RENDERER_BITMAP_H_INCLUDED

#include <stdlib.h> // size_t
#include "renderer/GL.h"

#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "util/Error.h"

namespace atakmap
{
    namespace renderer
    {
        struct Bitmap {
            int width;
            int height;
            void *data;
            size_t dataLen;
            GLenum dataType;
            GLenum format;
            void *opaque;

            Bitmap (*getScaledInstance)(Bitmap b, int w, int h);
            void (*releaseData)(Bitmap b);
        };

        /**
         * the returned bitmap is valid only so long as the supplied reference
         * remains valid. The returned instance may or may not have made a copy
         * of the data. In either case, its 'releaseData' function will do the
         * appropriate thing and must always be invoked.
         */
        Bitmap ENGINE_API Bitmap_adapt(const TAK::Engine::Renderer::Bitmap2 &other) NOTHROWS;

        /**
         * Returns a new legacy Bitmap instance by taking ownership of the
         * supplied Bitmap2 pointer. The returned instance may or may not have
         * made a copy of the data. In either case, its 'releaseData' function
         * will do the appropriate thing and must always be invoked.
         */
        Bitmap ENGINE_API Bitmap_adapt(TAK::Engine::Renderer::BitmapPtr &&other) NOTHROWS;

        /**
         * Returns a new legacy Bitmap instance by taking ownership of the
         * supplied Bitmap2 pointer. The returned instance may or may not have
         * made a copy of the data. In either case, its 'releaseData' function
         * will do the appropriate thing and must always be invoked.
         */
        Bitmap Bitmap_adapt(TAK::Engine::Renderer::BitmapPtr_const &&other) NOTHROWS;

        /**
        * Returns a new Bitmap2 instance created by copying the data of the
        * supplied legacy bitmap instance.
        */
        TAK::Engine::Renderer::Bitmap2 Bitmap_adapt(const Bitmap &other) NOTHROWS;
    }
}

#endif
