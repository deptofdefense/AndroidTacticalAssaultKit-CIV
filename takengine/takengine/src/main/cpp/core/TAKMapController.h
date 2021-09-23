#ifndef ATAKMAP_CORE_TAK_MAP_CONTROLLER_H_INCLUDED
#define ATAKMAP_CORE_TAK_MAP_CONTROLLER_H_INCLUDED

#include <set>
#include <stack>

#include "math/Point2.h"
#include "port/Platform.h"
#include "thread/Mutex.h"

namespace TAK
{
    namespace Engine
    {
        namespace Core
        {

            class ENGINE_API TAKMapController; // forward declaration
            class ENGINE_API TAKMapView;
            class ENGINE_API GeoPoint2;

            class ENGINE_API MapControllerFocusPointChangedListener
            {
            protected:
                virtual ~MapControllerFocusPointChangedListener()  NOTHROWS = 0;
            public:
                virtual void mapControllerFocusPointChanged(TAKMapController *controller, const TAK::Engine::Math::Point2<float> * const focus) NOTHROWS = 0;
            };

            class ENGINE_API TAKMapController
            {
            public:
                TAKMapController(TAKMapView *mapView) NOTHROWS;
                ~TAKMapController();
            public:
                void panTo(const GeoPoint2 &point, const bool animate) NOTHROWS;
                void panZoomTo(const GeoPoint2 &point, const double scale, const bool animate) NOTHROWS;
                void panZoomRotateTo(const GeoPoint2 &point, const double scale, const double theta, const bool animate) NOTHROWS;
                void panTo(const GeoPoint2 &point, const float tx, const float ty, const bool animate) NOTHROWS;
                void panByAtScale(const float tx, const float ty, const double scale, const bool animate) NOTHROWS;
                void panBy(const float tx, const float ty, const bool animate) NOTHROWS;
                void zoomTo(const double scale, const bool animate) NOTHROWS;
                void zoomBy(const double scale, const float tx, const float ty, const bool animate) NOTHROWS;
                void rotateTo(const double theta, const bool animate) NOTHROWS;
                void rotateBy(double theta, float xpos, float ypos, bool animate) NOTHROWS;
                void tiltTo(const double tilt, const bool animate) NOTHROWS;
                void tiltBy(const double tilt, const float xpos, const float ypos, const bool animate) NOTHROWS;
                void updateBy(const double scale, const double rotation, const double tilt, const float xpos, const float ypos, const bool animate) NOTHROWS;


                void addFocusPointChangedListener(MapControllerFocusPointChangedListener *listener) NOTHROWS;
                void removeFocusPointChangedListener(MapControllerFocusPointChangedListener *listener) NOTHROWS;

                void setFocusPoint(const TAK::Engine::Math::Point2<float> *focus) NOTHROWS;
                void popFocusPoint() NOTHROWS;
                void resetFocusPoint() NOTHROWS;

                void getFocusPoint(TAK::Engine::Math::Point2<float> *focus) NOTHROWS;

            private:
                void setDefaultFocusPoint(const TAK::Engine::Math::Point2<float> *defaultFocus) NOTHROWS;

                void dispatchFocusPointChanged() NOTHROWS;
                TAK::Engine::Math::Point2<float> getFocusPointInternal() NOTHROWS;
            private:
                TAKMapView *mapView;
                std::set<MapControllerFocusPointChangedListener *> focusChangedListeners;
                TAK::Engine::Thread::Mutex controllerMutex;

                friend class TAKMapView;
            }; // end class TAKMapController

        } // end namespace atakmap::core
    } // end namespace atakmap
}
#endif // ATAKMAP_CORE_ATAK_MAP_CONTROLLER_H_INCLUDED