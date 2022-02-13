
#include "core/GeoPoint.h"

#include "feature/LineString.h"

#include "renderer/map/GLMapView.h"
#include "renderer/feature/GLLineString.h"

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

GLLineString::GLLineString(LineString *lineString)
: GLGeometry(lineString),
numPoints(lineString->getPointCount()) {
    
    points.resize(numPoints * 2);
    vertices.resize(numPoints * 3);
    pixels.resize(numPoints * 2);

    for (int i = 0; i < numPoints; i++) {
        points[i * 2] = (float)lineString->getX(i);
        points[i * 2 + 1] = (float)lineString->getY(i);
    }

}

GLLineString::~GLLineString() { }

const float *GLLineString::getVertices(const GLMapView *view, int vertexType) {
    switch (vertexType) {
    case GLGeometry::VERTICES_PROJECTED:
        if (verticesSrid != view->drawSrid) {
            for (int i = 0; i < numPoints; i++) {
                GeoPoint scratchGeo(points[i * 2 + 1], points[i * 2]);
                math::PointD scratchPoint;
                view->scene.projection->forward(&scratchGeo, &scratchPoint);
                vertices[i * 3] = (float)scratchPoint.x;
                vertices[i * 3 + 1] = (float)scratchPoint.y;
                vertices[i * 3 + 2] = 0;
            }
        }
        return vertices.access();
    case GLGeometry::VERTICES_PIXEL:
        // XXX - if a native bulk matrix multiplication were available
        //       it would probably be much faster to use the projected
        //       vertices but I believe we will be better off using the
        //       source points and going from geodetic straight to pixel
        //       using the bulk forward
        if (pixelCoordsVersion != view->drawVersion) {
            view->forward(points.access(), numPoints, pixels.access());
            pixelCoordsVersion = view->drawVersion;
        }
        return pixels.access();
    default:
        throw std::invalid_argument("vertexType");
    }
}

size_t GLLineString::getNumVertices() {
    return numPoints;
}
