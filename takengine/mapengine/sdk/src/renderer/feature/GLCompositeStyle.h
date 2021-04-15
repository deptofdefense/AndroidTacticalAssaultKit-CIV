#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLCOMPOSITESTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLCOMPOSITESTYLE_H_INCLUDED

#include "renderer/feature/GLStyle.h"
#include "renderer/feature/GLStyleSpi.h"
#include "feature/Style.h"

namespace atakmap {
    namespace renderer {
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            class GLCompositeStyle : public GLStyle {
            public:
                GLCompositeStyle(const atakmap::feature::CompositeStyle *style);
                
                virtual ~GLCompositeStyle();
                
                virtual void draw(const atakmap::renderer::map::GLMapView *view,
                                  GLGeometry *geometry, StyleRenderContext *ctx) override;
                
                virtual void batch(const atakmap::renderer::map::GLMapView *view,
                                   GLRenderBatch *batch,
                                   GLGeometry *geometry, StyleRenderContext *ctx) override;
                
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view,
                                         GLGeometry *geometry, StyleRenderContext *ctx) override;
                
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view,
                                                                GLGeometry *geometry) override;
                
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;
            
            protected:
                class CompositeStyleRenderContext : public StyleRenderContext {
                public:
                    std::list<std::pair<GLStyle *, StyleRenderContext *>> styles;

                    virtual ~CompositeStyleRenderContext();
                };
                
            private:
                static GLStyle *spiCreate(int ignored,
                                          const atakmap::renderer::feature::GLStyleSpiArg *spiArg);
            
            public:
                class Spi;
           
                static GLStyleSpi *getSpi();
            };

            class GLCompositeStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const GLStyleSpiArg &style);
            };
        }
    }
}

#endif

