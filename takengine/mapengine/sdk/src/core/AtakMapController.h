#ifndef ATAKMAP_CORE_ATAK_MAP_CONTROLLER_H_INCLUDED
#define ATAKMAP_CORE_ATAK_MAP_CONTROLLER_H_INCLUDED

#include <set>
#include <stack>

#include "math/Point.h"
#include "port/Platform.h"
#include "thread/Mutex.h"

namespace atakmap
{
namespace core
{

class ENGINE_API AtakMapController; // forward declaration
class ENGINE_API AtakMapView;
class ENGINE_API GeoPoint;

class ENGINE_API MapControllerFocusPointChangedListener
{
public :
    virtual ~MapControllerFocusPointChangedListener() NOTHROWS = 0;
    virtual void mapControllerFocusPointChanged(AtakMapController *controller, const atakmap::math::Point<float> * const focus) = 0;
};

class ENGINE_API AtakMapController
{
public :
    AtakMapController(AtakMapView *mapView);
    ~AtakMapController();
public :
    void panTo(const GeoPoint *point, const bool animate);
    void panZoomTo(const GeoPoint *point, const double scale, const bool animate);
    void panZoomRotateTo(const GeoPoint *point, const double scale, const double theta, const bool animate);
    void panTo(const GeoPoint *point, const float tx, const float ty, const bool animate);
    void panByAtScale(const float tx, const float ty, const double scale, const bool animate);
    void panBy(const float tx, const float ty, const bool animate);
    void zoomTo(const double scale, const bool animate);
    void zoomBy(const double scale, const float tx, const float ty, const bool animate);
    void rotateTo(const double theta, const bool animate);
    void rotateBy(double theta, float xpos, float ypos, bool animate);
    void rotateTo(double theta, const GeoPoint &point, bool animate);
    void rotateBy(double theta, const GeoPoint &point, bool animate);
    void tiltTo(const double tilt, const bool animate);
    void tiltTo(const double tilt, const double rotation, const bool animate);
    void tiltBy(const double tilt, const float xpos, const float ypos, const bool animate);
    void tiltTo(const double tilt, const GeoPoint &point, const bool animate);
    void tiltBy(const double tilt, const GeoPoint &point, const bool animate);
    void updateBy(const double scale, const double rotation, const double tilt, const float xpos, const float ypos, const bool animate);
    void updateBy(const double scale, const double rotation, const double tilt, const GeoPoint &point, const bool animate);


    void addFocusPointChangedListener(MapControllerFocusPointChangedListener *listener);
    void removeFocusPointChangedListener(MapControllerFocusPointChangedListener *listener);

    void setFocusPoint(const atakmap::math::Point<float> *focus);
    void popFocusPoint();
    void resetFocusPoint();

    void getFocusPoint(atakmap::math::Point<float> *focus);

    void setFocusPointOffset(const atakmap::math::Point<float> *focusOffset);
public :
    void panByScaleRotate(const float x, const float y, const double scale, const double rotation, const bool animate);
private :
    void setDefaultFocusPoint(const atakmap::math::Point<float> *defaultFocus);

    void dispatchFocusPointChanged();
    atakmap::math::Point<float> getFocusPointInternal() const;
private :
    AtakMapView *mapView;
    atakmap::math::Point<float> defaultFocusPoint;
    atakmap::math::Point<float> focusPointOffset;
    std::set<MapControllerFocusPointChangedListener *> focusChangedListeners;
    std::stack<atakmap::math::Point<float>> focusPointQueue;

    TAK::Engine::Thread::Mutex controllerMutex;

    friend class ENGINE_API AtakMapView;
}; // end class AtakMapController

} // end namespace atakmap::core
} // end namespace atakmap

#endif // ATAKMAP_CORE_ATAK_MAP_CONTROLLER_H_INCLUDED
