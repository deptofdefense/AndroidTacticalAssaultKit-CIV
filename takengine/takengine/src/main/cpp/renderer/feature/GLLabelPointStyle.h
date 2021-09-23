#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLLABELPOINTSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLLABELPOINTSTYLE_H_INCLUDED

#include "string/String.hh"
#include "feature/Style.h"
#include "renderer/feature/GLStyleSpi.h"
#include "renderer/feature/GLPointStyle.h"

namespace atakmap {
    
    namespace feature {
        class LabelPointStyle;
    }
    
    namespace renderer {
        
        class GLRenderBatch;
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            
            class GLGeometry;

            class GLLabelPointStyle: public GLPointStyle {
            public:
                class Spi;
                
                static GLStyleSpi *getSpi();
                
                GLLabelPointStyle(const atakmap::feature::LabelPointStyle *style);
                virtual ~GLLabelPointStyle();
                
                void drawAt(const atakmap::renderer::map::GLMapView *view, float x, float y, StyleRenderContext *ctx);
                
                void batchAt(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, float xpos, float ypos,
                             StyleRenderContext *ctx);
                
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view,
                                         GLGeometry *geometry,
                                         StyleRenderContext *ctx) override;
                
                virtual StyleRenderContext *createRenderContext(const atakmap::renderer::map::GLMapView *view,
                                                                GLGeometry *geometry) override;
                
                virtual void releaseRenderContext(StyleRenderContext *ctx) override;

                const char *getText() const;
                
                float getTextSize() const;

            private:
                PGSC::String text;
                float textSize;
                int alignX;
                int alignY;
                float textColorR;
                float textColorG;
                float textColorB;
                float textColorA;
                float bgColorR;
                float bgColorG;
                float bgColorB;
                float bgColorA;
                float paddingX;
                float paddingY;
                bool drawBackground;
                atakmap::feature::LabelPointStyle::ScrollMode scrollMode;
            };
            
            class GLLabelPointStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const atakmap::renderer::feature::GLStyleSpiArg &arg);
            };
        }
    }
}


#endif
