#ifndef ATAKMAP_CORE_MAP_SCENE_MODEL2_H_INCLUDED
#define ATAKMAP_CORE_MAP_SCENE_MODEL2_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/MapCamera2.h"
#include "core/MapProjectionDisplayModel.h"
#include "core/Projection2.h"
#include "math/GeometryModel2.h"
#include "math/Matrix2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API MapSceneModel2
            {
            public:
                MapSceneModel2() NOTHROWS;
                MapSceneModel2(double displayDPI, std::size_t width, std::size_t height, int srid, const GeoPoint2 &focusGeo, float focusX,
                    float focusY, double rotation, double tilt, double resolution) NOTHROWS;
                MapSceneModel2(const MapSceneModel2 &other) NOTHROWS;
            public:
                ~MapSceneModel2();
            private:
                Util::TAKErr init(double display_dpi, std::size_t map_width, std::size_t map_height, int srid, const GeoPoint2 &focusGeo, float focus_x,
                    float focus_y, double rotation, double tilt, double resolution) NOTHROWS;
            public:
                Util::TAKErr set(const double display_dpi, const std::size_t map_width, const std::size_t map_height, const int srid, const GeoPoint2 &focus_geo, const float focus_x, const float focus_y, const double rotation, const double tilt, const double resolution) NOTHROWS;
            public:
                Util::TAKErr forward(TAK::Engine::Math::Point2<float>* point, const GeoPoint2& geo) const NOTHROWS;
                Util::TAKErr forward(TAK::Engine::Math::Point2<double> *point, const GeoPoint2& geo) const NOTHROWS;
                Util::TAKErr inverse(GeoPoint2 *geo, const Math::Point2<float> &point) const NOTHROWS;
                Util::TAKErr inverse(GeoPoint2 *geo, const Math::Point2<float> &point, const bool nearestIfOffWorld) const NOTHROWS;
                Util::TAKErr inverse(GeoPoint2  *geo, const Math::Point2<float> &point, const Math::GeometryModel2& model) const NOTHROWS;
            public:
                MapSceneModel2 &operator=(const MapSceneModel2 &other) NOTHROWS;
            private:
                Util::TAKErr inverse(GeoPoint2 *value, const Math::Point2<float> &point,
                    const Math::GeometryModel2& model, const bool nearestIfOffWorld) const NOTHROWS;
                static std::shared_ptr<MapProjectionDisplayModel> getDisplayModel(Projection2& proj) NOTHROWS;
            public:
                Math::GeometryModel2Ptr earth;
                Core::MapCamera2 camera;
                Core::Projection2Ptr projection;
                Math::Matrix2 forwardTransform;
                Math::Matrix2 inverseTransform;

                double displayDpi;
                std::size_t width;
                std::size_t height;
                float focusX;
                float focusY;
                double gsd;

                std::shared_ptr<MapProjectionDisplayModel> displayModel;
            };

            typedef std::unique_ptr<MapSceneModel2, void(*)(const MapSceneModel2 *)> MapSceneModel2Ptr;

			ENGINE_API Util::TAKErr MapSceneModel2_rotateAbout(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta) NOTHROWS;
			ENGINE_API Util::TAKErr MapSceneModel2_tiltAbout(MapSceneModel2Ptr &value, const MapSceneModel2 &scene, const GeoPoint2 &point, const double theta) NOTHROWS;
            ENGINE_API Util::TAKErr MapSceneModel2_setCameraMode(const MapCamera2::Mode mode) NOTHROWS;
            ENGINE_API MapCamera2::Mode MapSceneModel2_getCameraMode() NOTHROWS;

            ENGINE_API Util::TAKErr MapSceneModel2_intersects(bool *value, const MapSceneModel2 &scene, const double minX, const double minY, const double minZ, const double maxX, const double maxY, const double maxZ) NOTHROWS;
        }
    }
}


#endif // ATAKMAP_CORE_MAP_SCENE_MODEL_H_INCLUDED
