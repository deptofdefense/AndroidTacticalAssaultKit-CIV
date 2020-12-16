#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLGEOMCOLLECTIONSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLGEOMCOLLECTIONSTYLE_H_INCLUDED

#include "renderer/feature/GLStyle.h"
#include "renderer/feature/GLStyleSpi.h"

namespace atakmap {
    
    namespace feature {
        class Style;
    }
    
    namespace renderer {
        namespace feature {
            
            class GLGeometry;
            
            class GLGeometryCollectionStyle : public GLStyle {
            public:
                GLGeometryCollectionStyle(const atakmap::feature::Style *style, GLStyleSpi *spi);
                
                virtual ~GLGeometryCollectionStyle();
                
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view,
                                                                atakmap::renderer::feature::GLGeometry *geometry) override;
                
                virtual void releaseRenderContext(StyleRenderContext *context) override;
                
                virtual void draw(const atakmap::renderer::map::GLMapView *view,
                                  GLGeometry *g, StyleRenderContext *context) override;
                
                virtual void batch(const atakmap::renderer::map::GLMapView *view,
                                   GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *g,
                                   StyleRenderContext *context) override;
                
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view,
                                         atakmap::renderer::feature::GLGeometry *g,
                                         StyleRenderContext *context) override;

            private:
                GLStyleSpi *spi;
            };
        }
    }
}

#endif
