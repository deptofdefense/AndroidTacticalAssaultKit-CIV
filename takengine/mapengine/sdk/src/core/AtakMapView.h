#ifndef ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED
#define ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED

#include <cstddef>
#include <list>
#include <set>

#include "core/GeoPoint.h"
#include "math/Matrix.h"
#include "thread/Mutex.h"

//XXX-- can we forward decl ProjectionPtr?
#include "core/ProjectionFactory2.h"

namespace atakmap {
namespace core {

class ENGINE_API AtakMapController;
class ENGINE_API Layer;
class ENGINE_API MapSceneModel;
//class Projection;
//class ProjectionSpi;


class ENGINE_API AtakMapView
{
public : // nested types
    struct ENGINE_API MapLayersChangedListener;
    struct ENGINE_API MapMovedListener;
    struct ENGINE_API MapProjectionChangedListener;
    struct ENGINE_API MapResizedListener;
    struct ENGINE_API MapElevationExaggerationFactorListener;
public : // constructor/destructor
    AtakMapView(float width, float height, double displayDPI);
    ~AtakMapView();
public : // member functions
    AtakMapController *getController() const;

    void addLayer(Layer *layer);
    void addLayer(int idx, Layer *layer);
    void removeLayer(Layer *layer);
    void removeAllLayers();
    void setLayerPosition(Layer *layer, const int position);
    std::size_t getNumLayers();
    Layer *getLayer(std::size_t position);
    std::list<Layer *> getLayers();
    void getLayers(std::list<Layer *> &retval);

    void addLayersChangedListener(MapLayersChangedListener *listener);
    void removeLayersChangedListener(MapLayersChangedListener *listener);

    void getPoint(GeoPoint *center) const;
    void getPoint(GeoPoint *center, const bool atFocusAltitude) const;
    double getFocusAltitude() const;
    void getBounds(GeoPoint *upperLeft, GeoPoint *lowerRight);
    double getMapScale() const;
    double getMapRotation() const;
    double getMapResolution() const;
    double getMapTilt() const;
    double getMinMapTilt(double resolution) const;
    double getMaxMapTilt(double resolution) const;
    void setMaxMapTilt(const double value);
    double getMapResolution(const double mapScale) const;
    double getFullEquitorialExtentPixels() const;
    int getProjection() const;

    bool setProjection(int srid);

    void addMapProjectionChangedListener(MapProjectionChangedListener *l);
    void removeMapProjectionChangedListener(MapProjectionChangedListener *l);

    double mapResolutionAsMapScale(const double resolution) const;

    MapSceneModel *createSceneModel();

    void forward(const GeoPoint *geo, atakmap::math::Point<float> *p);
    void inverse(const atakmap::math::Point<float> *p, GeoPoint *geo);

    double getMinLatitude() const;
    double getMaxLatitude() const;
    double getMinLongitude() const;
    double getMaxLongitude() const;

    double getMinMapScale() const;
    double getMaxMapScale() const;

    void setMinMapScale(const double minMapScale);
    void setMaxMapScale(const double maxMapScale);

    void setSize(const float width, const float height);
    float getWidth() const;
    float getHeight() const;
    double getDisplayDpi() const;

    double getElevationExaggerationFactor() const;
    void setElevationExaggerationFactor(double factor);

    void addMapResizedListener(MapResizedListener *l);
    void removeMapResizedListener(MapResizedListener *l);

    void addMapMovedListener(MapMovedListener *l);
    void removeMapMovedListener(MapMovedListener *l);

    void addMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *l);
    void removeMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener *l);
protected : // member functions
    //Deprecated now, use the version that includes the tilt
    void updateView(const GeoPoint *center, const double scale, const double rotation, const bool animate);

    void updateView(const GeoPoint &c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const double focusAltTerminalSlant, const bool anim);

    void dispatchMapResized();

    void dispatchMapMoved();

    void dispatchMapProjectionChanged();
    void dispatchElevationExaggerationFactorChanged();

    void dispatchLayerAdded(Layer *layer);
    void dispatchLayersRemoved(std::list<Layer *> layers);
    void dispatchLayerPositionChanged(Layer *layer, const int oldPos, const int newPos);

friend class ENGINE_API AtakMapController;

private : // member fields
    float width;
    float height;
    double displayResolution;
    double fullEquitorialExtentPixels;
    GeoPoint center;
    double focusAltitude;
    double focusAltTerminalSlant;
    double scale;
    double minMapScale;
    double maxMapScale;
    double rotation;
    bool animate;
    double tilt;
    double maxTilt;
    double elevationExaggerationFactor;
    double displayDpi;
    TAK::Engine::Core::ProjectionPtr2 projection;
    bool computeFocusPoint;

    AtakMapController *controller;

    std::list<Layer *> layers;

    // callbacks
    std::set<MapLayersChangedListener *> layersChangedListeners;
    std::set<MapResizedListener *> resizedListeners;
    std::set<MapMovedListener *> movedListeners;
    std::set<MapProjectionChangedListener *> projectionChangedListeners;
    std::set<MapElevationExaggerationFactorListener *> elExaggerationFactorChangedListeners;

    mutable TAK::Engine::Thread::Mutex mapMutex;
public :
    static float DENSITY;
}; // end class AtakMapView

struct ENGINE_API AtakMapView::MapLayersChangedListener
{
    virtual ~MapLayersChangedListener () throw () = 0;
    virtual void mapLayerAdded(AtakMapView *view, Layer *layer) = 0;
    virtual void mapLayerRemoved(AtakMapView *view, Layer *layer) = 0;
    virtual void mapLayerPositionChanged(AtakMapView *view, Layer *layer, const int oldPos, const int newPos) = 0;
}; // end class MapLayersChangedListener

struct ENGINE_API AtakMapView::MapMovedListener
{
    virtual ~MapMovedListener () throw () = 0;
    virtual void mapMoved(AtakMapView *view, const bool animate) = 0;
}; // end class MapMovedListener

struct ENGINE_API AtakMapView::MapProjectionChangedListener
{
    virtual ~MapProjectionChangedListener () throw () = 0;
    virtual void mapProjectionChanged(AtakMapView *view) = 0;
};

struct ENGINE_API AtakMapView::MapResizedListener
{
    virtual ~MapResizedListener () throw () = 0;
    virtual void mapResized(AtakMapView *view) = 0;
}; // end class MapResizedListener

struct ENGINE_API AtakMapView::MapElevationExaggerationFactorListener
{
    virtual ~MapElevationExaggerationFactorListener() throw () = 0;
    virtual void mapElevationExaggerationFactorChanged(AtakMapView *view, const double factor) = 0;
}; // end class MapResizedListener


} // end namespace atakmap::core
} // end namespace atak

#endif // ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED
