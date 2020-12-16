#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLLABELEDICONSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLLABELEDICONSTYLE_H_INCLUDED

#include "renderer/feature/GLCompositeStyle.h"
#include "feature/Style.h"

namespace atakmap {
    namespace renderer {
        namespace feature {

            class GLLabeledIconStyle : public GLCompositeStyle {
            public:
                GLLabeledIconStyle(const atakmap::feature::CompositeStyle *style,
                                   const atakmap::feature::IconPointStyle *iconStyle,
                                   const atakmap::feature::LabelPointStyle *labelStyle);
                
                virtual ~GLLabeledIconStyle();
                                                
            public:
                class Spi;
                static GLStyleSpi *getSpi();
                
            private:
                const atakmap::feature::IconPointStyle *iconStyle;
                const atakmap::feature::LabelPointStyle *labelStyle;
            };
            
            class GLLabeledIconStyle::Spi : public GLStyleSpi {
            public:
                virtual ~Spi();
                virtual GLStyle *create(const GLStyleSpiArg &style);
            };

        }
    }
}

#endif
