#include "AABB.h"
#include "math/Vector4.h"
#include "util/Memory.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

AABB::AABB(const Point2<double> &min, const Point2<double> &max) NOTHROWS :
    minX(min.x), minY(min.y), minZ(min.z),
    maxX(max.x), maxY(max.y), maxZ(max.z)
{}

AABB::AABB(const Point2<double> *points, const std::size_t count) NOTHROWS :
    minX(NAN), minY(NAN), minZ(NAN),
    maxX(NAN), maxY(NAN), maxZ(NAN)
{
    if (!count)
        return;
    minX = points[0].x;
    minY = points[0].y;
    minZ = points[0].z;
    maxX = points[0].x;
    maxY = points[0].y;
    maxZ = points[0].z;

    for (std::size_t i = 1u; i < count; i++) {
        if (points[i].x < minX) minX = points[i].x;
        else if (points[i].x > maxX) maxX = points[i].x;
        if (points[i].y < minY) minY = points[i].y;
        else if (points[i].y > maxY) maxY = points[i].y;
        if (points[i].z < minZ) minZ = points[i].z;
        else if (points[i].z > maxZ) maxZ = points[i].z;
    }
}

bool AABB::contains(const Point2<double> &point) const NOTHROWS
{
    return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY && point.z >= minZ && point.z <= maxZ;
}

//This is heavily based off of various algorithms using 
//"An Efficient and Robust Ray-Box Intersection Algorithm" Amy Williams et al 2004 http://www.cs.utah.edu/~awilliam/box/box.pdf
//Most notably https://gist.github.com/aadnk/7123926 as it returns the intersection point.
bool AABB::intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const
{
    const std::size_t signDirX = ray.direction.x < 0 ? 1u : 0u;
    const std::size_t signDirY = ray.direction.y < 0 ? 1u : 0u;
    const std::size_t signDirZ = ray.direction.z < 0 ? 1u : 0u;

    const Point2<double> minPt(minX, minY, minZ);
    const Point2<double> maxPt(maxX, maxY, maxZ);

    const Point2<double> pts[2u] { minPt, maxPt };

    //This differs from Amy et Al's algorithm, but it does save making the int[3] on creation of the
    //AABB object. If it's slower it may be worth swapping over?
    double tmin = (pts[signDirX].x - ray.origin.x) / ray.direction.x;
    double tmax = (pts[1u-signDirX].x - ray.origin.x) / ray.direction.x;
    double tymin = (pts[signDirY].y - ray.origin.y) / ray.direction.y;
    double tymax = (pts[1u-signDirY].y - ray.origin.y) / ray.direction.y;
    if ((tmin > tymax) || (tymin > tymax))
    {
        return false;
    }
    if (tymin > tmin)
    {
        tmin = tymin;
    }
    if (tymax < tmax)
    {
        tmax = tymax;
    }

    double tzmin = (pts[signDirZ].z - ray.origin.z) / ray.direction.z;
    double tzmax = (pts[1u-signDirZ].z - ray.origin.z) / ray.direction.z;
    if ((tmin > tzmax) || (tzmin > tmax)) 
    {
        return false;
    }
    if (tzmin > tmin) 
    {
        tmin = tzmin;
    }
    if (tzmax < tmax) 
    {
        tmax = tzmax;
    }

    //The Amy et al and link above check an intersection interval for validity before returning true
    //Our intersect doesn't take those in, possibly should?
    Vector4<double> isectVector(0, 0, 0);
    //Get the isectPoint
    ray.direction.multiply(tmin, &isectVector);
    isectPoint->x = ray.origin.x + isectVector.x;
    isectPoint->y = ray.origin.y + isectVector.y;
    isectPoint->z = ray.origin.z + isectVector.z;

    return true;
}

GeometryModel2::GeometryClass AABB::getGeomClass() const
{
    return GeometryModel2::GeometryClass::AABB;
}
void AABB::clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const
{
    value = GeometryModel2Ptr(new AABB(*this), Memory_deleter_const<GeometryModel2, AABB>);
}
