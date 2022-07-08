#ifndef TAK_ENGINE_CORE_CAMERACONTROLLER_H_INCLUDED
#define TAK_ENGINE_CORE_CAMERACONTROLLER_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/MapRenderer2.h"
#include "math/Plane2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            /**
             * Pan the map a given number of pixels.
             *
             * @param tx        Horizontal pixels to pan
             * @param ty        Vertical pixels to pan
             * @param animate   Pan smoothly if true; immediately if false
             */
            ENGINE_API Util::TAKErr CameraController_panBy(MapRenderer2 &renderer,
                                                const float tx,
                                                const float ty,
                                                const MapRenderer::CameraCollision collide,
                                                const bool animate) NOTHROWS;
            /**
             * Pans the specified location to the specified offset within the
             * viewport. Screen position is interpreted as upper-left origin
             * @param renderer
             * @param focus
             * @param x
             * @param y
             * @param animate
             */
            ENGINE_API Util::TAKErr CameraController_panTo(MapRenderer2 &renderer,
                                                const GeoPoint2 &focus,
                                                const float x,
                                                const float y,
                                                const MapRenderer::CameraCollision collide,
                                                const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_zoomBy(MapRenderer2 &renderer,
                                                 const double scaleFactor,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_zoomBy(MapRenderer2 &renderer,
                                                 const double scaleFactor,
                                                 const GeoPoint2 &focus,
                                                 const float focusx,
                                                 const float focusy,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            /**
             * Zooms the current scene to the specified resolution. The provided
             * focus location will be positioned at the specified screenspace
             * location on completion of the motion.
             *
             * @param renderer
             * @param gsd
             * @param focus
             * @param focusx
             * @param focusy
             * @param collide
             * @param animate
             */
            ENGINE_API Util::TAKErr CameraController_zoomTo(MapRenderer2 &renderer,
                                                 const double gsd,
                                                 const GeoPoint2 &focus,
                                                 const float focusx,
                                                 const float focusy,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_rotateBy(MapRenderer2 &renderer,
                                                   const double theta,
                                                   const GeoPoint2 &focus,
                                                   const MapRenderer::CameraCollision collide,
                                                   const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_rotateTo(MapRenderer2 &renderer,
                                                   const double theta,
                                                   const GeoPoint2 &focus,
                                                   const float focusx,
                                                   const float focusy,
                                                   const MapRenderer::CameraCollision collide,
                                                   const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_tiltBy(MapRenderer2 &renderer,
                                                 const double theta,
                                                 const GeoPoint2 &focus,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_tiltBy(MapRenderer2 &renderer,
                                                 const double theta,
                                                 const GeoPoint2 &focus,
                                                 const float focusx,
                                                 const float focusy,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_tiltTo(MapRenderer2 &renderer,
                                                 const double theta,
                                                 const GeoPoint2 &focus,
                                                 const float focusx,
                                                 const float focusy,
                                                 const MapRenderer::CameraCollision collide,
                                                 const bool animate) NOTHROWS;

            /**
             * Pans the map to the specified location as the new focus point.
             * Rotation, tilt and zoom are preserved.
             * @param renderer
             * @param focus
             * @param animate
             */
            ENGINE_API Util::TAKErr CameraController_panTo(MapRenderer2 &renderer, const GeoPoint2 &focus, const bool animate) NOTHROWS;

            /**
             * Sets the map to the specified rotation
             *
             * @param rotation  The new rotation of the map
             * @param animate   Rotate smoothly if true; immediately if false
             */
            ENGINE_API Util::TAKErr CameraController_rotateTo(MapRenderer2 &renderer,
                                                   const double rotation,
                                                   const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_tiltTo(MapRenderer2 &renderer,
                                                 const double tilt,
                                                 const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_zoomTo(MapRenderer2 &renderer, const double gsd, const bool animate) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_tiltTo(MapRenderer2 &renderer,
                                                 const double theta,
                                                 const GeoPoint2 &focus,
                                                 const bool animate) NOTHROWS;

            ENGINE_API double CameraController_computeRelativeDensityRatio(const MapSceneModel2 &sm, const float x, const float y) NOTHROWS;

            /**
             * Creates a tangent plane at the specified focus point.
             *
             * @param sm
             * @param focus
             * @return
             */
            ENGINE_API Math::Plane2 CameraController_createTangentPlane(const MapSceneModel2 &sm, const GeoPoint2 &focus) NOTHROWS;

            ENGINE_API Util::TAKErr CameraController_createFocusAltitudeModel(Math::GeometryModel2Ptr &value, const MapSceneModel2 &sm, const GeoPoint2 &focus) NOTHROWS;
        }
    }
}

#endif
