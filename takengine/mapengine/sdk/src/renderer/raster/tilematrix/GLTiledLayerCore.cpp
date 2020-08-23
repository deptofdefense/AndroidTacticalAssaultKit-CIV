#include "renderer/raster/tilematrix/GLTiledLayerCore.h"

#include "math/Point2.h"
#include "core/GeoPoint2.h"
#include "core/ProjectionFactory2.h"
#include "core/ProjectionFactory3.h"
#include "math/Utils.h"

using namespace TAK::Engine::Renderer::Raster::TileMatrix;

namespace {
    TAK::Engine::Core::Projection2Ptr getProjection(int srid) {
        TAK::Engine::Core::Projection2Ptr value(nullptr, nullptr);

        // Partial port of Java's MobileImageryRasterLayer2.getProjection(srid);
        // based on input from Chris
        switch (srid) {
            case 900913:
                srid = 3857;
                break;
            case 90094326:
                srid = 4326;
                break;
        }
        ProjectionFactory3_create(value, srid);
        return value;
    }

    class ProjectionDatasetProjection2 : public atakmap::raster::DatasetProjection {
        GLTiledLayerCore *core;

      public:
        ProjectionDatasetProjection2(GLTiledLayerCore *core) : core(core) { }

        void release() {
        }

        bool imageToGround(const atakmap::math::PointD &image, atakmap::core::GeoPoint *ground) const override {
            TAK::Engine::Core::GeoPoint2 gp2;
            TAK::Engine::Math::Point2<double> p2(image.x, image.y, image.z);
            core->proj->inverse(&gp2, p2);
            *ground = atakmap::core::GeoPoint(gp2);
            return true;
        }

        bool groundToImage(const atakmap::core::GeoPoint &ground, atakmap::math::PointD *image) const override {
            TAK::Engine::Core::GeoPoint2 gp2;
            atakmap::core::GeoPoint_adapt(&gp2, ground);
            TAK::Engine::Math::Point2<double> p2;
            core->proj->forward(&p2, gp2);
            *image = atakmap::math::PointD(p2.x, p2.y, p2.z);
            return true;
        }
    };

}


GLTiledLayerCore::GLTiledLayerCore(const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix, const char *uri)
    : 
      clientSourceUri(uri), proj(nullptr, nullptr), 
      debugDraw(false), textureCache(nullptr), bitmapLoader(nullptr), r(1.0f), g(1.0f),
      b(1.0f), a(1.0f), fullExtentMinLat(0), fullExtentMinLng(0), fullExtentMaxLat(0),
      fullExtentMaxLng(0),
      proj2geo(new ProjectionDatasetProjection2(this)),
      matrix(matrix),
      fullExtent(),
      refreshInterval(0),
      tileDrawVersion(0), lastRefresh(0)
{
    proj = getProjection(this->matrix->getSRID());
    this->matrix->getBounds(&fullExtent);
        
    Math::Point2<double> scratchD(0.0, 0.0, 0.0);
    Core::GeoPoint2 scratchG;

    scratchD.x = fullExtent.minX;
    scratchD.y = fullExtent.minY;
    proj->inverse(&scratchG, scratchD);
    double lat0 = scratchG.latitude;
    double lng0 = scratchG.longitude;
    scratchD.x = fullExtent.minX;
    scratchD.y = fullExtent.maxY;
    proj->inverse(&scratchG, scratchD);
    double lat1 = scratchG.latitude;
    double lng1 = scratchG.longitude;
    scratchD.x = fullExtent.maxX;
    scratchD.y = fullExtent.maxY;
    proj->inverse(&scratchG, scratchD);
    double lat2 = scratchG.latitude;
    double lng2 = scratchG.longitude;
    scratchD.x = fullExtent.maxX;
    scratchD.y = fullExtent.minY;
    proj->inverse(&scratchG, scratchD);
    double lat3 = scratchG.latitude;
    double lng3 = scratchG.longitude;
        
    fullExtentMinLat = atakmap::math::min(lat0, lat1, lat2, lat3);
    fullExtentMinLng = atakmap::math::min(lng0, lng1, lng2, lng3);
    fullExtentMaxLat = atakmap::math::max(lat0, lat1, lat2, lat3);
    fullExtentMaxLng = atakmap::math::max(lng0, lng1, lng2, lng3);
        
    lastRefresh = Port::Platform_systime_millis();
}

GLTiledLayerCore::~GLTiledLayerCore()
{
    delete proj2geo;
}
    
void GLTiledLayerCore::requestRefresh() {
    tileDrawVersion++;
}

void GLTiledLayerCore::drawPump() {
    if(refreshInterval <= 0L)
        return;
    // if the refresh interval has elapsed since the last refresh, bump the
    // version
    int64_t currentTime = Port::Platform_systime_millis(); 
    if((currentTime - lastRefresh) > refreshInterval) {
        tileDrawVersion++;
        lastRefresh = Port::Platform_systime_millis();
    }
}
