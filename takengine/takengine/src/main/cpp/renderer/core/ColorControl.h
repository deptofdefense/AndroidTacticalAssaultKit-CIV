#ifndef TAK_ENGINE_RENDERER_CORE_COLORCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_COLORCONTROL_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API ColorControl
                {
                public :
                    enum Mode
                    {
                        /** multiplies source by color */
                        Modulate,
                        /** converts source to luminance and multiplies by color */
                        Colorize,
                        /** replaces source with color */
                        Replace,
                    };
                public :
                    virtual ~ColorControl() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr setColor(const Mode mode, const unsigned int argb) NOTHROWS = 0;
                    virtual unsigned int getColor() const NOTHROWS = 0;
                    virtual Mode getMode() const NOTHROWS = 0;
                };
            }
        }
    }
}
#endif
