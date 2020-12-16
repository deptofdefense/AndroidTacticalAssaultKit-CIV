#ifndef ATAKMAP_RENDERER_GLLINESEMULATION_H_INCLUDED
#define ATAKMAP_RENDERER_GLLINESEMULATION_H_INCLUDED

#include "renderer/GLES20FixedPipeline.h"

namespace atakmap {
    namespace renderer {
        class GLLinesEmulation
        {
        private :
            GLLinesEmulation();
            ~GLLinesEmulation();
        public :
            static void emulateLineDrawArrays(const int mode, const int first, const int count, GLES20FixedPipeline *pipeline, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer);
            static void emulateLineDrawArrays(const int mode, const int first, const int count, const float *proj, const float *modelView, const float *texture, const float r, const float g, const float b, const float a, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer);

            static void emulateLineDrawElements(const int mode, const int count, const int type, const void *indices, GLES20FixedPipeline *pipeline, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer);
            static void emulateLineDrawElements(const int mode, const int count, const int type, const void *indices, const float *proj, const float *modelView, const float *texture, const float r, const float g, const float b, const float a, const float width, const ArrayPointer *vertexPointer, const ArrayPointer *texCoordsPointer);
        };
    }
}

#endif // ATAKMAP_RENDERER_GLLINESEMULATION_H_INCLUDED
