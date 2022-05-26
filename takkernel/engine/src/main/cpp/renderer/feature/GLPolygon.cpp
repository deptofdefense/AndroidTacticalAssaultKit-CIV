
#include <algorithm>

#include "math/Point.h"

#include "core/GeoPoint.h"

#include "feature/Polygon.h"

#include "renderer/map/GLMapView.h"

#include "renderer/feature/GLPolygon.h"

using namespace atakmap::math;
using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

GLPolygon::GLPolygon(Polygon *polygon)
: GLGeometry(polygon),
  totalPoints(0)
{
    std::pair<std::vector<LineString>::const_iterator, std::vector<LineString>::const_iterator> interiorRings = polygon->getInteriorRings();
    size_t numRings = 1 + std::distance(interiorRings.first, interiorRings.second);
    ringVerts.resize(numRings);
    ringOffset.resize(numRings);
    
    LineString exteriorRing = polygon->getExteriorRing();
    
    int idx = 0;
    ringVerts[idx] = exteriorRing.getPointCount() & INT_MAX;
    ringOffset[idx] = totalPoints;
    totalPoints += ringVerts[idx];
    idx++;

    for (std::vector<LineString>::const_iterator it = interiorRings.first; it != interiorRings.second; ++it) {
        ringVerts[idx] = it->getPointCount() & INT_MAX;
        ringOffset[idx] = totalPoints;
        totalPoints += ringVerts[idx];
        idx++;
    }
    
    points.resize(totalPoints * 2);
    vertices.resize(totalPoints * 3);
    pixels.resize(totalPoints * 2);
    
    idx = 0;
    for (int i = 0; i < exteriorRing.getPointCount(); i++) {
        points[idx++] = (float)exteriorRing.getX(i);
        points[idx++] = (float)exteriorRing.getY(i);
    }

    for (std::vector<LineString>::const_iterator it = interiorRings.first; it != interiorRings.second; ++it) {
        for (int i = 0; i < it->getPointCount(); i++) {
            points[idx++] = (float)it->getX(i);
            points[idx++] = (float)it->getY(i);
        }
    }
}

GLPolygon::~GLPolygon() { }

std::pair<float *, size_t> GLPolygon::getVertices(const GLMapView *view, int vertexType, int ring) {
    int vertSize = 0;
    float *retval = nullptr;
    size_t retsize = 0;

    switch (vertexType) {
    case VERTICES_PROJECTED:
        if (verticesSrid != view->drawSrid) {
            for (int i = 0; i < totalPoints; i++) {
                GeoPoint scratchGeo(points[i * 2 + 1], points[i * 2]);
                math::Point<double> scratchPoint;
                
                view->scene.projection->forward(&scratchGeo, &scratchPoint);
                vertices[i * 3] = (float)scratchPoint.x;
                vertices[i * 3 + 1] = (float)scratchPoint.y;
                vertices[i * 3 + 2] = (float)scratchPoint.z;
            }
        }
        retval = vertices.access();
        retsize = vertices.limit();
        vertSize = 3;
        break;
    case VERTICES_PIXEL:
        // XXX - if a native bulk matrix multiplication were available
        //       it would probably be much faster to use the projected
        //       vertices but I believe we will be better off using the
        //       source points and going from geodetic straight to pixel
        //       using the bulk forward
        if (pixelCoordsVersion != view->drawVersion) {
            view->forward(points.access(), points.limit() / 2, pixels.access());
            pixelCoordsVersion = view->drawVersion;
        }
        retval = pixels.access();
        retsize = pixels.limit();
        vertSize = 2;
        break;
    default:
        throw std::invalid_argument("vertexType");
    }

    /*TODO-- need copy? array<float> ^retcopy = gcnew array<float>(ringVerts[ring] * vertSize);
    System::Array::Copy(retval, ringOffset[ring] * vertSize, retcopy, 0, retcopy->Length);
    return retcopy;*/
    return std::pair<float *, size_t>(retval, retsize);
}

int GLPolygon::getNumVertices(int ring) {
    return ringVerts[ring];
}

int GLPolygon::getNumInteriorRings() {
    return ringVerts.capacity() - 1 & INT_MAX;
}

std::pair<float *, size_t> GLPolygon::getPoints(int ring) {
    size_t size = ringVerts[ring] * 2;
    return std::pair<float *, size_t>(points.access() + ringOffset[ring] * 2, size);
}

