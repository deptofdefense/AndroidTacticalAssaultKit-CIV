#ifndef ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED
#define ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED

#include <cstddef>
#include <list>
#include <set>

#include "core/GeoPoint.h"
#include "core/MapRenderer3.h"
#include "math/Matrix.h"
#include "thread/Mutex.h"
#include "thread/RWMutex.h"

//XXX-- can we forward decl ProjectionPtr?
#include "core/ProjectionFactory2.h"

namespace atakmap {
    namespace core {

        class AtakMapController;
        class Layer;
        class MapSceneModel;

        class ENGINE_API AtakMapView :
            public TAK::Engine::Core::MapRenderer3::OnCameraChangedListener2
        {
        public: // nested types
            struct ENGINE_API MapLayersChangedListener;
            struct ENGINE_API MapMovedListener;
            struct ENGINE_API MapProjectionChangedListener;
            struct ENGINE_API MapResizedListener;
            struct ENGINE_API MapElevationExaggerationFactorListener;
            struct ENGINE_API MapContinuousScrollListener;
        public: // constructor/destructor
            AtakMapView(float width, float height, double displayDPI);
            ~AtakMapView();
        public: // member functions
            AtakMapController* getController() const;

            void addLayer(Layer* layer);
            void addLayer(int idx, Layer* layer);
            void removeLayer(Layer* layer);
            void removeAllLayers();
            void setLayerPosition(Layer* layer, const int position);
            std::size_t getNumLayers();
            Layer* getLayer(std::size_t position);
            std::list<Layer*> getLayers();
            void getLayers(std::list<Layer*>& retval);

            void addLayersChangedListener(MapLayersChangedListener* listener);
            void removeLayersChangedListener(MapLayersChangedListener* listener);

            void getPoint(GeoPoint* center) const;
            void getPoint(GeoPoint* center, const bool atFocusAltitude) const;
            double getFocusAltitude() const;
            void getBounds(GeoPoint* upperLeft, GeoPoint* lowerRight);
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

            void addMapProjectionChangedListener(MapProjectionChangedListener* l);
            void removeMapProjectionChangedListener(MapProjectionChangedListener* l);

            double mapResolutionAsMapScale(const double resolution) const;

            MapSceneModel* createSceneModel();

            void forward(const GeoPoint* geo, atakmap::math::Point<float>* p);
            void inverse(const atakmap::math::Point<float>* p, GeoPoint* geo);

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
            void setDisplayDpi(double dpi);
            double getDisplayDpi() const;

            void setFocusPointOffset(float x, float y) NOTHROWS;

            bool isAnimating() const NOTHROWS;
            bool isContinuousScrollEnabled() const NOTHROWS;
            void setContinuousScrollEnabled(const bool v) NOTHROWS;

            double getElevationExaggerationFactor() const;
            void setElevationExaggerationFactor(double factor);

            void addMapResizedListener(MapResizedListener* l);
            void removeMapResizedListener(MapResizedListener* l);

            void addMapMovedListener(MapMovedListener* l);
            void removeMapMovedListener(MapMovedListener* l);

            void addMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener* l);
            void removeMapElevationExaggerationFactorListener(MapElevationExaggerationFactorListener* l);

            void addMapContinuousScrollListener(MapContinuousScrollListener* l);
            void removeMapContinuousScrollListener(MapContinuousScrollListener* l);
        protected: // member functions
            //Deprecated now, use the version that includes the tilt
            void updateView(const GeoPoint* center, const double scale, const double rotation, const bool animate);
        public:
            void updateView(const GeoPoint& c, const double mapScale, const double rot, const double ptilt, const double focusAlt, const double focusAltTerminalSlant, const bool anim);
        protected:
            void dispatchMapMoved(const bool animate);

            void dispatchLayerAddedNoSync(Layer* layer);
            void dispatchLayersRemovedNoSync(std::list<Layer*> layers);

            friend class ENGINE_API AtakMapController;

        public:
            void attachRenderer(TAK::Engine::Core::MapRenderer3& renderer, const bool adoptState) NOTHROWS;
            void detachRenderer(const TAK::Engine::Core::MapRenderer3& renderer) NOTHROWS;
        public:
            TAK::Engine::Util::TAKErr onCameraChanged(const TAK::Engine::Core::MapRenderer2 &renderer) NOTHROWS override;
            TAK::Engine::Util::TAKErr onCameraChangeRequested(const TAK::Engine::Core::MapRenderer2 &renderer) NOTHROWS override;
        private:
            TAK::Engine::Core::MapRenderer3& state() const NOTHROWS;
        private: // member fields
            double full_equitorial_extent_pixels_;
            double min_map_scale_;
            double max_map_scale_;
            double max_tilt_;
            struct {
                double value{ 1.0 };
                std::set<MapElevationExaggerationFactorListener *> listeners;
                mutable TAK::Engine::Thread::Mutex mutex;
            } elevation_exaggeration_factor_;

            AtakMapController *controller_;

            struct {
                std::list<Layer*> value;
                mutable TAK::Engine::Thread::Mutex mutex {TAK::Engine::Thread::TEMT_Recursive };
                std::set<MapLayersChangedListener *> listeners;
            } layers_;

            TAK::Engine::Core::MapRenderer3 *state_;
            std::list<TAK::Engine::Core::MapRenderer3 *> renderers_;

            // callbacks            
            std::set<MapResizedListener *> resized_listeners_;
            struct {
                mutable TAK::Engine::Thread::RWMutex mutex;
                std::set<MapMovedListener*> value;
            } moved_listeners_;
            struct {
                mutable TAK::Engine::Thread::RWMutex mutex;
                std::set<MapProjectionChangedListener*> value;
            } projection_changed_listeners_;
            std::set<MapElevationExaggerationFactorListener *> el_exaggeration_factor_changed_listeners_;

            mutable TAK::Engine::Thread::RWMutex map_mutex_;
            
        public :
            static float DENSITY;
        }; // end class AtakMapView

        ENGINE_API double AtakMapView_getFullEquitorialExtentPixels(const double dpi) NOTHROWS;
        ENGINE_API double AtakMapView_getMapResolution(const double dpi, const double scale) NOTHROWS;
        ENGINE_API double AtakMapView_getMapScale(const double dpi, const double resolution) NOTHROWS;

        struct ENGINE_API AtakMapView::MapLayersChangedListener
        {
            virtual ~MapLayersChangedListener () NOTHROWS = 0;
            virtual void mapLayerAdded(AtakMapView *view, Layer *layer) = 0;
            virtual void mapLayerRemoved(AtakMapView *view, Layer *layer) = 0;
            virtual void mapLayerPositionChanged(AtakMapView *view, Layer *layer, const int oldPos, const int newPos) = 0;
        }; // end class MapLayersChangedListener

        struct ENGINE_API AtakMapView::MapMovedListener
        {
            virtual ~MapMovedListener () NOTHROWS = 0;
            virtual void mapMoved(AtakMapView *view, const bool animate) = 0;
        }; // end class MapMovedListener

        struct ENGINE_API AtakMapView::MapProjectionChangedListener
        {
            virtual ~MapProjectionChangedListener () NOTHROWS = 0;
            virtual void mapProjectionChanged(AtakMapView *view) = 0;
        };

        struct ENGINE_API AtakMapView::MapResizedListener
        {
            virtual ~MapResizedListener () NOTHROWS = 0;
            virtual void mapResized(AtakMapView *view) = 0;
        }; // end class MapResizedListener

        struct ENGINE_API AtakMapView::MapElevationExaggerationFactorListener
        {
            virtual ~MapElevationExaggerationFactorListener() NOTHROWS = 0;
            virtual void mapElevationExaggerationFactorChanged(AtakMapView *view, const double factor) = 0;
        }; // end class MapResizedListener

        struct ENGINE_API AtakMapView::MapContinuousScrollListener
        {
            virtual ~MapContinuousScrollListener() NOTHROWS = 0;
            virtual void mapContinuousScrollEnabledChanged(AtakMapView *view, const bool enabled) = 0;
        }; // end class MapResizedListener


    } // end namespace atakmap::core
} // end namespace atak

#endif // ATAKMAP_CORE_ATAK_MAP_VIEW_H_INCLUDED
