#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLBASICPOINTSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLBASICPOINTSTYLE_H_INCLUDED

#include "renderer/feature/GLPointStyle.h"
#include "renderer/feature/GLStyleSpi.h"
#include "feature/Style.h"

namespace atakmap {
    namespace renderer {
        namespace feature {

            class GLGeometry;
            
            class GLBasicPointStyle : public GLPointStyle {
            public:
                GLBasicPointStyle(const atakmap::feature::BasicPointStyle *style);

                void drawAt(const atakmap::renderer::map::GLMapView *view, float xpos, float ypos, StyleRenderContext *ctx);
            
                void batchAt(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, float xpos, float ypos, StyleRenderContext *ctx);
            public:
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override;
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry) override;
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;
            
            public: // public class members
                class Spi;
                
                static GLStyleSpi *getSpi();
                
                const float colorR;
                const float colorG;
                const float colorB;
                const float colorA;

                const int size;
            };
            
            class GLBasicPointStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const atakmap::renderer::feature::GLStyleSpiArg &arg);
            };
        }
    }
}

#endif 

