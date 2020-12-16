#ifndef ATAKMAP_RENDERER_FEATURE_GLBASICSTROKSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GLBASICSTROKSTYLE_H_INCLUDED

#include "renderer/feature/GLStrokeStyle.h"
#include "renderer/feature/GLStyleSpi.h"
#include "feature/Style.h"

namespace atakmap {
    namespace renderer {
        namespace feature {
            
            class GLBasicStrokeStyle : public GLStrokeStyle {
            public:
                GLBasicStrokeStyle(const atakmap::feature::BasicStrokeStyle *style);
                virtual ~GLBasicStrokeStyle();

                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view, GLGeometry *geom) override;
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;

            public:
                class Spi;
                
                static GLStyleSpi *getSpi();
                
            protected:
                void drawImpl(const atakmap::renderer::map::GLMapView *view, const float *vertices, int size, int count);
                void batchImpl(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, const float *vertices, size_t count);

                float strokeWidth;
                float strokeColorR;
                float strokeColorG;
                float strokeColorB;
                float strokeColorA;
            };
            
            class GLBasicStrokeStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const atakmap::renderer::feature::GLStyleSpiArg &arg);
            };
        }
    }
}

#endif

