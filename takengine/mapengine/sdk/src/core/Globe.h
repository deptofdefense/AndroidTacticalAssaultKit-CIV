#ifndef TAK_ENGINE_CORE_GLOBE_H_INCLUDED
#define TAK_ENGINE_CORE_GLOBE_H_INCLUDED

#include <list>
#include <set>
#include <vector>

#include "core/Layer2.h"
#include "core/MapSceneModel2.h"
#include "core/Projection.h"
#include "core/ProjectionFactory2.h"
#include "feature/Envelope2.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API Globe
            {
            public: // nested types
                class ENGINE_API LayersChangedListener;
                class ENGINE_API ViewChangedListener;
                class ENGINE_API ProjectionChangedListener;
                class ENGINE_API ViewResizedListener;
                class ENGINE_API FocusPointListener;

            public: // constructor/destructor
                Globe(const std::size_t width, const std::size_t height, const double displayDPI) NOTHROWS;
                ~Globe() NOTHROWS;
            public: // member functions
                Util::TAKErr addLayer(const std::shared_ptr<TAK::Engine::Core::Layer2> &layer) NOTHROWS;
                Util::TAKErr insertLayer(const std::size_t idx, const std::shared_ptr<TAK::Engine::Core::Layer2> &layer) NOTHROWS;
                Util::TAKErr removeLayer(const Layer2 &layer) NOTHROWS;
                Util::TAKErr removeAllLayers() NOTHROWS;
                Util::TAKErr setLayerPosition(const Layer2 &layer, const std::size_t position) NOTHROWS;
                std::size_t getNumLayers() const NOTHROWS;
                Util::TAKErr getLayer(std::shared_ptr<Layer2> &value, const std::size_t position) const NOTHROWS;
                Util::TAKErr getLayers(Port::Collection<std::shared_ptr<Layer2>> &value) const NOTHROWS;
                Util::TAKErr visitLayers(Util::TAKErr(*visitor)(void *opaque, Layer2& layer) NOTHROWS, void *opaque) const NOTHROWS;

                Util::TAKErr addLayersChangedListener(LayersChangedListener &listener) NOTHROWS;
                Util::TAKErr removeLayersChangedListener(LayersChangedListener &listener) NOTHROWS;

                GeoPoint2 getPoint() const NOTHROWS;
                double getMapScale() const NOTHROWS;
                double getMapRotation() const NOTHROWS;
                double getMapResolution() const NOTHROWS;
                double getMapTilt() const NOTHROWS;
                double getMapResolution(const double mapScale) const NOTHROWS;
                double getFullEquitorialExtentPixels() const NOTHROWS;
                int getProjection() const NOTHROWS;

                Util::TAKErr setProjection(const int srid) NOTHROWS;

                Util::TAKErr addMapProjectionChangedListener(ProjectionChangedListener &l) NOTHROWS;
                Util::TAKErr removeMapProjectionChangedListener(ProjectionChangedListener &l) NOTHROWS;

                double mapResolutionAsMapScale(const double resolution) const NOTHROWS;

                Util::TAKErr createSceneModel(MapSceneModel2Ptr &value) const NOTHROWS;

                Util::TAKErr forward(Math::Point2<float> *p, const GeoPoint2 &geo) const NOTHROWS;
                Util::TAKErr inverse(GeoPoint2 *geo, const Math::Point2<float> &p) const NOTHROWS;

                double getMinLatitude() const NOTHROWS;
                double getMaxLatitude() const NOTHROWS;
                double getMinLongitude() const NOTHROWS;
                double getMaxLongitude() const NOTHROWS;

                double getMinMapScale() const NOTHROWS;
                double getMaxMapScale() const NOTHROWS;

                Util::TAKErr setMinMapScale(const double minMapScale) NOTHROWS;
                Util::TAKErr setMaxMapScale(const double maxMapScale) NOTHROWS;

                Util::TAKErr setSize(const std::size_t width, const std::size_t height) NOTHROWS;
                std::size_t getWidth() const NOTHROWS;
                std::size_t getHeight() const NOTHROWS;

                Util::TAKErr addViewResizedListener(ViewResizedListener &l) NOTHROWS;
                Util::TAKErr removeViewResizedListener(ViewResizedListener &l) NOTHROWS;

                Util::TAKErr addViewChangedListener(ViewChangedListener &l) NOTHROWS;
                Util::TAKErr removeViewChangedListener(ViewChangedListener &l) NOTHROWS;

                Util::TAKErr addFocusPointListener(FocusPointListener &l) NOTHROWS;
                Util::TAKErr removeFocusPointListener(FocusPointListener &l) NOTHROWS;

                double getDisplayDPI() const NOTHROWS;
            private: // member functions
                void updateView(const GeoPoint2 &c, double mapScale, double rot, double ptilt, bool anim) NOTHROWS;

                void dispatchMapResized(const std::size_t width, const std::size_t height) const NOTHROWS;

                void dispatchMapMoved(const bool animate) const NOTHROWS;

                void dispatchMapProjectionChanged(const int srid) const NOTHROWS;

                void dispatchLayerAdded(const std::shared_ptr<TAK::Engine::Core::Layer2> &layer) const NOTHROWS;
                void dispatchLayersRemoved(const std::list<std::shared_ptr<TAK::Engine::Core::Layer2>> &layers) const NOTHROWS;
                void dispatchLayerPositionChanged(const std::shared_ptr<TAK::Engine::Core::Layer2> &layer, const std::size_t oldPos, const std::size_t newPos) const NOTHROWS;
            private: // member fields
                float focus_x_;
                float focus_y_;
                std::size_t width_;
                std::size_t height_;
                double display_resolution_;
                double full_equitorial_extent_pixels_;
                GeoPoint2 center_;
                double scale_;
                double min_map_scale_;
                double max_map_scale_;
                double rotation_;
                bool animate_;
                double tilt_;
                double dpi_;
                TAK::Engine::Core::ProjectionPtr2 projection_;

                std::vector<std::shared_ptr<TAK::Engine::Core::Layer2>> layers_;

                // callbacks
                std::set<LayersChangedListener *> layers_changed_listeners_;
                std::set<ViewResizedListener *> resized_listeners_;
                std::set<ViewChangedListener *> moved_listeners_;
                std::set<ProjectionChangedListener *> projection_changed_listeners_;
                std::set<FocusPointListener *> focus_point_listeners_;

                mutable TAK::Engine::Thread::Mutex map_mutex_;
            }; // end class AtakMapView

            class ENGINE_API Globe::LayersChangedListener
            {
            protected:
                virtual ~LayersChangedListener() NOTHROWS = 0;
            public:
                virtual Util::TAKErr layerAdded(const Globe &view, const std::shared_ptr<TAK::Engine::Core::Layer2> &layer) NOTHROWS = 0;
                virtual Util::TAKErr layerRemoved(const Globe &view, const std::shared_ptr<TAK::Engine::Core::Layer2> &layer) NOTHROWS  = 0;
                virtual Util::TAKErr layerPositionChanged(const Globe &view, const std::shared_ptr<TAK::Engine::Core::Layer2> &layer, const std::size_t oldPos, const std::size_t newPos) NOTHROWS = 0;
            }; // end class MapLayersChangedListener

            class ENGINE_API Globe::ViewChangedListener
            {
            protected:
                virtual ~ViewChangedListener() NOTHROWS = 0;
            public:
                virtual Util::TAKErr viewChanged(const Globe &view, const bool animate) NOTHROWS = 0;
            }; // end class ViewChangedListener

            class ENGINE_API Globe::ProjectionChangedListener
            {
            protected:
                virtual ~ProjectionChangedListener() NOTHROWS = 0;
            public:
                virtual Util::TAKErr projectionChanged(const Globe &view, const int srid) NOTHROWS = 0;
            };

            class ENGINE_API Globe::ViewResizedListener
            {
            protected:
                virtual ~ViewResizedListener() NOTHROWS = 0;
            public:
                virtual Util::TAKErr viewResized(const Globe &view, const std::size_t width, const std::size_t height) NOTHROWS = 0;
            }; // end class ViewResizedListener

            class ENGINE_API Globe::FocusPointListener
            {
            protected:
                virtual ~FocusPointListener() NOTHROWS = 0;
            public:
                virtual Util::TAKErr focusChanged(const Globe &view, const float x, const float y) NOTHROWS = 0;
            }; // end class ViewResizedListener
          

            ENGINE_API double Globe_getDefaultDisplayDPI();

        } // end namespace atakmap::core
    } // end namespace atak
}

#endif // TAK_ENGINE_CORE_TAKMAPVIEW_H_INCLUDED
