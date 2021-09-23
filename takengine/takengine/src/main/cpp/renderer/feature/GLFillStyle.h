#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLFILLSTYLE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLFILLSTYLE_H_INCLUDED

#include "renderer/feature/GLStyle.h"
#include "feature/Style.h"

namespace atakmap {
    namespace renderer {
        namespace feature {
            class GLFillStyle : public GLStyle {
            public:
                virtual ~GLFillStyle();

            protected:
                GLFillStyle(const atakmap::feature::FillStyle *style);
            };
        }
    }
}


#endif

