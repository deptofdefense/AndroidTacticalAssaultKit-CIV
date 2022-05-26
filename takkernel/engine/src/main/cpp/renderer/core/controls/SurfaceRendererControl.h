#ifndef TAK_ENGINE_RENDERER_CORE_CONTROLS_SURFACERENDERERCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_CONTROLS_SURFACERENDERERCONTROL_H_INCLUDED

#include "feature/Envelope2.h"
#include "model/Mesh.h"
#include "renderer/core/ColorControl.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                namespace Controls {
                    class ENGINE_API SurfaceRendererControl
                    {
                    protected :
                        ~SurfaceRendererControl() NOTHROWS = default;
                    public :
                        /**
                         * Marks the visible region of the surface as dirty.
                         */
                        virtual void markDirty() NOTHROWS = 0;

                        /**
                         * Marks the specified region of the surface as dirty. If the region is not
                         * part of the visible surface, it will be marked as invalid, but may not
                         * be updated until it becomes visible.
                         *
                         * @param region    The dirty region
                         * @param streaming If <code>true</code>, instructs the render to attempt
                         *                  to refresh the region at frame rates.
                         */
                        virtual void markDirty(const TAK::Engine::Feature::Envelope2 region, const bool streaming) NOTHROWS = 0;
                        virtual Util::TAKErr enableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS = 0;
                        virtual Util::TAKErr disableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS = 0;
                        virtual bool isDrawModeEnabled(const TAK::Engine::Model::DrawMode mode) const NOTHROWS = 0;
                        virtual Util::TAKErr setColor(const TAK::Engine::Model::DrawMode drawMode, const unsigned int color, const TAK::Engine::Renderer::Core::ColorControl::Mode colorMode) NOTHROWS = 0;
                        virtual unsigned int getColor(const TAK::Engine::Model::DrawMode mode) const NOTHROWS = 0;
                        virtual Util::TAKErr getColorMode(TAK::Engine::Renderer::Core::ColorControl::Mode *value, const TAK::Engine::Model::DrawMode mode) const NOTHROWS = 0;

                        virtual Util::TAKErr setCameraCollisionRadius(double radius) NOTHROWS = 0;
                        virtual double getCameraCollisionRadius() const NOTHROWS = 0;

                        /**
                         * <P>May only be invoked on GL thread.
                         * @return
                         */
                        virtual Util::TAKErr getSurfaceBounds(TAK::Engine::Port::Collection<TAK::Engine::Feature::Envelope2> &value) const NOTHROWS = 0;

                        /**
                         * Sets the minimum refresh interval for the surface, in milliseconds. The
                         * visible tiles on the surface will be refreshed if the specified amount
                         * of time has elapsed since the last update, whether or not they have been
                         * marked dirty.
                         * @param millis    The minimum refresh interval, in milliseconds. If
                         *                  less-then-or-equal to <code>0</code>, the refresh
                         *                  interval is disabled.
                         */
                        virtual void setMinimumRefreshInterval(const int64_t millis) NOTHROWS = 0;
                        virtual int64_t getMinimumRefreshInterval() const NOTHROWS = 0;
                    };

                    const char* SurfaceRendererControl_getType() NOTHROWS;
                }
            }
        }
    }
}

#endif