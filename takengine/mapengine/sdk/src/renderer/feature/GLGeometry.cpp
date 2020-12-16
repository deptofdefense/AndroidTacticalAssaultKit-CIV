#include "renderer/feature/GLGeometry.h"

#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/LineString.h"
#include "feature/GeometryCollection.h"

#include "renderer/feature/GLPoint.h"
#include "renderer/feature/GLPolygon.h"
#include "renderer/feature/GLLineString.h"
#include "renderer/feature/GLGeometryCollection.h"

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;

GLGeometry::GLGeometry(Geometry *geom)
: geometry(geom), verticesSrid(0), pixelCoordsVersion(0)
{ }

GLGeometry::~GLGeometry() { }

Geometry *GLGeometry::getSubject() {
    return geometry;
}

GLGeometry *GLGeometry::createRenderer(Geometry *geom) {
    Point *pt = dynamic_cast<Point *>(geom);
    if (pt != nullptr)
        return new GLPoint(pt);
    
    Polygon *poly = dynamic_cast<Polygon *>(geom);
    if (poly != nullptr)
        return new GLPolygon(poly);

    LineString *ls = dynamic_cast<LineString *>(geom);
    if (ls != nullptr)
        return new GLLineString(ls);

    GeometryCollection *gc = dynamic_cast<GeometryCollection *>(geom);
    if (gc != nullptr)
        return new GLGeometryCollection(gc);

    throw std::invalid_argument("unknown geometry");
}
