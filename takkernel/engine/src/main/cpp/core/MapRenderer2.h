#ifndef TAK_ENGINE_CORE_MAPRENDERER2_H_INCLUDED
#define TAK_ENGINE_CORE_MAPRENDERER2_H_INCLUDED

#include "core/Control.h"
#include "core/Layer2.h"
#include "core/MapRenderer.h"
#include "core/MapSceneModel2.h"
#include "core/RenderContext.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API MapRenderer2 : public MapRenderer
            {
            public :
                class OnCameraChangedListener;
            public :
                virtual ~MapRenderer2() NOTHROWS = 0;
            public :
                // Camera management
                /**
                 * Looks from the specified location at the specified location
                 */
                virtual Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &from, const TAK::Engine::Core::GeoPoint2 &at, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS = 0;
                /**
                 * Looks at the specified location
                 *
                 * @param at            The location to look at
                 * @param resolution    The nominal display resolution at the location
                 * @param azimuth       The rotation, degrees from north CW
                 * @param tilt          The tilt angle from the surface tangent plane at the location
                 */
                virtual Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS = 0;
                /**
                 * Looks from the specified location per the specified angles.
                 *
                 * @param azimuth   Rotation from north, clockwise in degrees
                 * @param elevation Angle in degrees. Zero is horizontal to the tangent
                 *                  plane at the from location, greater than zero is
                 *                  upward with 90 straight up, less than zero is downward
                 *                  with -90 straight down
                 */
                virtual Util::TAKErr lookFrom(const TAK::Engine::Core::GeoPoint2 &from, const double azimuth, const double elevation, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS = 0;
                virtual bool isAnimating() const NOTHROWS = 0;
                virtual Util::TAKErr setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode mode) NOTHROWS = 0;
                virtual TAK::Engine::Core::MapRenderer::DisplayMode getDisplayMode() const NOTHROWS = 0;
                virtual TAK::Engine::Core::MapRenderer::DisplayOrigin getDisplayOrigin() const NOTHROWS = 0;
                virtual Util::TAKErr setFocusPoint(const float focusx, const float focusy) NOTHROWS = 0;
                virtual Util::TAKErr getFocusPoint(float *focusx, float *focusy) const NOTHROWS = 0;
                virtual Util::TAKErr setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS = 0;
                virtual Util::TAKErr inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin) NOTHROWS = 0;

                /**
                 * Retrieves the model for the scene.
                 *
                 * @param instant   If <code>true</code>, returns the current, intra
                 *                  animation state. If <code>false</code> returns the
                 *                  target state for any animation that is currently
                 *                  processing.
                 * @param origin    The desired origin representation for the returned
                 *                  scene model
                 * @return  The scene model
                 */
                virtual Util::TAKErr getMapSceneModel(MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) NOTHROWS = 0;

                virtual Util::TAKErr addOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS = 0;
                virtual Util::TAKErr removeOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS = 0;
            };

            class ENGINE_API MapRenderer2::OnCameraChangedListener
            {
            public :
                virtual ~OnCameraChangedListener() NOTHROWS = 0;
            public :
                /**
                 * Invoked when the camera changes.
                 *
                 * @param renderer  The renderer whose camera has changed
                 */
                virtual Util::TAKErr onCameraChanged(const MapRenderer2 &renderer) NOTHROWS = 0;
            };
        }
    }
}

#endif
