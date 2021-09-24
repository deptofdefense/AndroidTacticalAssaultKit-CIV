
#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLSTYLE_H_INCLUDED

//#include  "feature/Style.h"

/*#include "renderer/core/GLMapView_CLI.h"
#include "renderer/GLRenderBatch_CLI.h"
#include "renderer/feature/geometry/GLGeometry_CLI.h"*/

namespace atakmap {
    
    namespace feature {
        class Style;
    }
    
    namespace renderer {
        
        class GLRenderBatch;
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            
            class GLGeometry;
            
            class StyleRenderContext {
            public:
                virtual ~StyleRenderContext() {};
            };

            class GLStyle {
            public:
                GLStyle(const atakmap::feature::Style *style);
                virtual ~GLStyle();

                virtual void draw(const atakmap::renderer::map::GLMapView *view,
                                  GLGeometry *geometry,
                                  StyleRenderContext *ctx) = 0;
                
                virtual void batch(const atakmap::renderer::map::GLMapView *view,
                                   GLRenderBatch *batch,
                                   GLGeometry *geometry,
                                   StyleRenderContext *ctx) = 0;
                
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view,
                                         GLGeometry *geometry,
                                         StyleRenderContext *ctx) = 0;

                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view,
                                                                GLGeometry *geometry) = 0;
                
                virtual void releaseRenderContext(StyleRenderContext *ctx) = 0;

            protected:
                const atakmap::feature::Style *style;
            };
        }
    }
}

#endif
