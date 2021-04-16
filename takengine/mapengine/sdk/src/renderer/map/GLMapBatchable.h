#ifndef ATAKMAP_RENDERER_GLMAPBATCHABLE_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPBATCHABLE_H_INCLUDED

#include "GLMapView.h"
#include "../GLRenderBatch.h"

namespace atakmap
{
    namespace renderer
    {
        namespace map {

            class GLMapBatchable {
            public:
                virtual ~GLMapBatchable() {};
                virtual bool isBatchable(const GLMapView *view) = 0;
                virtual void batch(const GLMapView *view, GLRenderBatch *batch) = 0;
            };
        }
    }
}

#endif
