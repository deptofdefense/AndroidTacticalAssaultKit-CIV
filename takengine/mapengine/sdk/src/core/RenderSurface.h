#ifndef TAK_ENGINE_CORE_RENDERSURFACE_H_INCLUDED
#define TAK_ENGINE_CORE_RENDERSURFACE_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API RenderSurface
            {
            public :
                class OnSizeChangedListener;
            public :
                virtual ~RenderSurface() NOTHROWS = 0;
            public :
                virtual double getDpi() const NOTHROWS = 0;
                virtual std::size_t getWidth() const NOTHROWS = 0;
                virtual std::size_t getHeight() const NOTHROWS = 0;

                virtual void addOnSizeChangedListener(OnSizeChangedListener *l) NOTHROWS = 0;
                virtual void removeOnSizedChangedListener(const OnSizeChangedListener &l) NOTHROWS = 0;
            };

            class RenderSurface::OnSizeChangedListener
            {
            public :
                virtual void onSizeChanged(const RenderSurface &surface, const std::size_t width, const std::size_t height) NOTHROWS = 0;
            };
        }
    }
}

#endif
