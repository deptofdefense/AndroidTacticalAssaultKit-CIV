#ifndef ATAKMAP_CORE_MAP_SCENE_MODEL_H_INCLUDED
#define ATAKMAP_CORE_MAP_SCENE_MODEL_H_INCLUDED

#include "core/AtakMapView.h"
#include "core/GeoPoint.h"
#include "core/MapCamera.h"
#include "math/GeometryModel.h"
#include "math/Matrix.h"
#include "math/Point.h"

#include "util/Error.h"
#include "core/MapSceneModel2.h"

//XXX-- forward decl ProjectionPtr?
#include "core/ProjectionFactory2.h"

namespace atakmap {
    namespace core
    {

        class ENGINE_API MapSceneModel
        {
        public:
            MapSceneModel();
            MapSceneModel(AtakMapView *view);
            MapSceneModel(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double scale);
            MapSceneModel(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double tilt, double scale);
            MapSceneModel(const MapSceneModel &other);
            MapSceneModel(const TAK::Engine::Core::MapSceneModel2 &other);
            ~MapSceneModel();
        public:
            void set(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double scale);
            void set(const AtakMapView *view, int srid, const GeoPoint *focusGeo, float focusX, float focusY, double rotation, double tilt, double scale);
        private:
            void syncWithImpl();
        public:
            bool forward(const GeoPoint *geo, atakmap::math::Point<float> *point) const;
            bool forward(const GeoPoint *geo, atakmap::math::Point<double> *point) const;
            bool inverse(const atakmap::math::Point<float> *point, GeoPoint *geo, const bool nearestIfOffWorld = false) const;
        public:
            MapSceneModel &operator=(const MapSceneModel &other);
            MapSceneModel &operator=(const TAK::Engine::Core::MapSceneModel2 &other);
        public:
            TAK::Engine::Core::MapSceneModel2 impl;
        private:
            std::unique_ptr<atakmap::math::GeometryModel> earthPtr;
        public:
            atakmap::math::GeometryModel *earth;
            MapCamera camera;
            TAK::Engine::Core::ProjectionPtr2 projection;
            atakmap::math::Matrix forwardTransform;
            atakmap::math::Matrix inverseTransform;

        };

    }
}


#endif // ATAKMAP_CORE_MAP_SCENE_MODEL_H_INCLUDED