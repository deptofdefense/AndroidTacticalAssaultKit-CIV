#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLSTROKESTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLSTROKESTYLE_H_INCLUDED

#include "renderer/feature/GLStyle.h"

namespace atakmap {
    
    namespace feature {
        class StrokeStyle;
    }
    
    namespace renderer {
        namespace feature {
            class GLStrokeStyle : public GLStyle {
            public:
                virtual ~GLStrokeStyle();

            protected:
                GLStrokeStyle(const atakmap::feature::StrokeStyle *style);
            };
        }
    }
}


#endif

