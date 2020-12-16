
#include "core/GeoPoint.h"
#include "feature/Point.h"
#include "renderer/feature/GLPoint.h"
#include "renderer/map/GLMapView.h"

using namespace atakmap::renderer::feature;
using namespace atakmap::renderer;
using namespace atakmap::feature;
using namespace atakmap::math;
using namespace atakmap::core;

GLPoint::GLPoint(atakmap::feature::Point *pt) : GLGeometry(pt),
    point(pt->x, pt->y, pt->z),
    vertex(0, 0, 0),
    pixel(0, 0, 0) { }

GLPoint::~GLPoint() {

}

void GLPoint::getVertex(const atakmap::renderer::map::GLMapView *view, int vertexType, PointD *vertex) {
    if (verticesSrid != view->drawSrid) {
        GeoPoint scratchGeo(point.y, point.x);
        view->scene.projection->forward(&scratchGeo, &this->vertex);
        verticesSrid = view->drawSrid;
    }
    switch (vertexType) {
    case GLGeometry::VERTICES_PIXEL:
        if (pixelCoordsVersion != view->drawVersion) {
            view->scene.forwardTransform.transform(&this->vertex, &pixel);
            pixelCoordsVersion = view->drawVersion;
        }
        vertex->x = pixel.x;
        vertex->y = pixel.y;
        vertex->z = pixel.z;
        break;
    case GLGeometry::VERTICES_PROJECTED:
        vertex->x = this->vertex.x;
        vertex->y = this->vertex.y;
        vertex->z = this->vertex.z;
        break;
    default:
        throw std::invalid_argument("vertexType");
    }
}


