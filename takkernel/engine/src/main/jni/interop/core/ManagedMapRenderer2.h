#ifndef TAKENGINEJNI_INTEROP_CORE_MANAGEDLAYER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_CORE_MANAGEDLAYER_H_INCLUDED

#include <core/MapRenderer3.h>

#include "common.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            class ManagedMapRenderer3 : public TAK::Engine::Core::MapRenderer3
            {
            public :
                ManagedMapRenderer3(JNIEnv &env, jobject impl) NOTHROWS;
            public :
                ~ManagedMapRenderer3() NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr registerControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr visitControls(bool *visited, void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer, const char *type) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr visitControls(bool *visited, void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr visitControls(void *opaque, TAK::Engine::Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl)) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr addOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr removeOnControlsChangedListener(OnControlsChangedListener *l) NOTHROWS override;
                virtual TAK::Engine::Core::RenderContext &getRenderContext() const NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &from, const TAK::Engine::Core::GeoPoint2 &at, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr lookFrom(const TAK::Engine::Core::GeoPoint2 &from, const double azimuth, const double elevation, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS override;
                virtual bool isAnimating() const NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode mode) NOTHROWS override;
                virtual TAK::Engine::Core::MapRenderer::DisplayMode getDisplayMode() const NOTHROWS override;
                virtual TAK::Engine::Core::MapRenderer::DisplayOrigin getDisplayOrigin() const NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr setFocusPoint(const float focusx, const float focusy) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getFocusPoint(float *focusx, float *focusy) const NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const TAK::Engine::Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr getMapSceneModel(TAK::Engine::Core::MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) const NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr addOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr removeOnCameraChangedListener(OnCameraChangedListener *l) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr addOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr removeOnCameraChangedListener(OnCameraChangedListener2 *l) NOTHROWS override;
            public :
                jobject impl;
            };
        }
    }
}

#endif // TAKENGINEJNI_INTEROP_CORE_MANAGEDLAYER_H_INCLUDED
