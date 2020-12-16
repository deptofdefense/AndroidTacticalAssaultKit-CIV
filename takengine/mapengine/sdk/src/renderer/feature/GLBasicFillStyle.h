#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLBASICFILLSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLBASICFILLSTYLE_H_INCLUDED

#include "renderer/feature/GLFillStyle.h"
#include "renderer/feature/GLStyleSpi.h"

namespace atakmap {
    namespace renderer {
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            class GLBasicFillStyle : public GLFillStyle {
            public:
                GLBasicFillStyle(const atakmap::feature::BasicFillStyle *style);
                virtual ~GLBasicFillStyle();
                
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view, GLGeometry *geom) override;
                
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;

                class Spi;
                static GLStyleSpi *getSpi();

            protected:
                float fillColorR;
                float fillColorG;
                float fillColorB;
                float fillColorA;
            };
            
            class GLBasicFillStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const atakmap::renderer::feature::GLStyleSpiArg &arg);
            };
        }
    }
}

#endif
