#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLICONPOINTSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLICONPOINTSTYLE_H_INCLUDED

#include "renderer/GLTextureAtlas.h"
#include "renderer/feature/GLPointStyle.h"
#include "renderer/feature/GLStyle.h"
#include "renderer/feature/GLStyleSpi.h"

namespace atakmap {
    
    namespace feature {
        class IconPointStyle;
    }

    namespace renderer {
        namespace feature {

            class GLGeometry;
            
            class GLIconPointStyle : public GLPointStyle {
            public:
                GLIconPointStyle(const atakmap::feature::IconPointStyle *style);

                // XXX - NEED TO IMPLEMENT ROTATION  !!!!!
                void drawAt(const atakmap::renderer::map::GLMapView *view, float xpos, float ypos, StyleRenderContext *ctx);
                
                void batchAt(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, float xpos, float ypos, StyleRenderContext *ctx);
            
            public:
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override;
                
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry) override;
                
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;
            
            public:
                class Spi;
                
                static GLStyleSpi *getSpi();
                
            public: // public class members
                const float colorR;
                const float colorG;
                const float colorB;
                const float colorA;

                const float iconWidth;
                const float iconHeight;

                const int alignX;
                const int alignY;

                const float rotation;
                const bool rotationAbsolute;
            };
            
            class GLIconPointStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const atakmap::renderer::feature::GLStyleSpiArg &arg);
            };
        }
    }
}

#endif // ATAKMAP_RENDERER_FEATURE_STYLE_GLICONPOINTSTYLE_H_INCLUDED

