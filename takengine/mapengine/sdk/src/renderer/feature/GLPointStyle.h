#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLPOINTSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLPOINTSTYLE_H_INCLUDED

#include "renderer/feature/GLStyle.h"

namespace atakmap {
    
    namespace feature {
        class PointStyle;
    }
    
    namespace renderer {
        namespace feature {
            class GLPointStyle : public GLStyle {
            public:
                virtual ~GLPointStyle();

            protected:
                GLPointStyle(const atakmap::feature::PointStyle *style);
            };
        }
    }
}


#endif

