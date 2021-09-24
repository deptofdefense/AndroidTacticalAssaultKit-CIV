#include "math/Triangle.h"
#include "util/Memory.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

#define SMALL_NUM 1e-13

Triangle::Triangle(const Point2<double> &a, const Point2<double> &b, const Point2<double> &c) NOTHROWS :
    V0(a.x, a.y, a.z),
    V1(b.x, b.y, b.z),
    V2(c.x, c.y, c.z)
{}

Triangle::~Triangle()
{}

GeometryModel2::GeometryClass Triangle::getGeomClass() const NOTHROWS
{
    return GeometryModel2::TRIANGLE;
}

void Triangle::clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const NOTHROWS
{
    value = GeometryModel2Ptr(new Triangle(*this), Memory_deleter_const<GeometryModel2, Triangle>);
}

bool Triangle::intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const NOTHROWS
{
    // derived from http://geomalgorithms.com/a06-_intersect-2.html#intersect3D_RayTriangle()
    Point2<double>    u, v, n;              // triangle vectors
    Point2<double>    dir(ray.direction.x, ray.direction.y, ray.direction.z), w0, w;           // ray vectors
    double     r, a, b;              // params to calc ray-plane intersect

    // get triangle edge vectors and plane normal
    //u = T.V1 - T.V0;
    Vector2_subtract(&u, V1, V0);
    //v = T.V2 - T.V0;
    Vector2_subtract(&v, V2, V0);
    //n = u * v;              // cross product
    Vector2_cross(&n, u, v);

    /*if (n == (Vector)0)   */          // triangle is degenerate
    if (n.x == 0.0 && n.y == 0.0 && n.z == 0.0)
        return false;                  // do not deal with this case

    //dir = R.P1 - R.P0;              // ray direction vector
    //dir = ray.direction;
    //w0 = R.P0 - T.V0;
    Vector2_subtract(&w0, ray.origin, V0);
    //a = -dot(n,w0);
    Vector2_dot(&a, n, w0);
    a = -a;
    //b = dot(n,dir);
    //b = n.dot(&dir);
    Vector2_dot(&b, n, dir);
    if (std::abs(b) < SMALL_NUM) {     // ray is  parallel to triangle plane
        if (a == 0)                 // ray lies in triangle plane
            return false;
        else return false;              // ray disjoint from plane
    }

    // get intersect point of ray with triangle plane
    r = a / b;
    if (r < 0.0)                    // ray goes away from triangle
        return false;                   // => no intersect
                                     // for a segment, also test if (r > 1.0) => no intersect

    //*I = R.P0 + r * dir;            // intersect point of ray and plane
    Point2<double> holder;
    //dir.multiply(r, &holder);
    Vector2_multiply(&holder, dir, r);
    Point2<double> I; 
    //RP0.add(&holder, &I);
    Vector2_add(&I, ray.origin, holder);

    // is I inside T?
    double    uu, uv, vv, wu, wv, D;
    //uu = u.dot(&u);
    Vector2_dot(&uu, u, u);
    //uv = u.dot(&v);
    Vector2_dot(&uv, u, v);
    //vv = v.dot(&v);
    Vector2_dot(&vv, v, v);
    //w = *I - T.V0;
    //I.subtract(&V0, &w);
    Vector2_subtract(&w, I, V0);
    //wu = w.dot(&u);
    Vector2_dot(&wu, w, u);
    //wv = w.dot(&v);
    Vector2_dot(&wv, w, v);

    D = uv * uv - uu * vv;

    // get and test parametric coords
    double s, t;
    s = (uv * wv - vv * wu) / D;
    if (s < 0.0 || s > 1.0)         // I is outside T
        return false;
    t = (uv * wu - uu * wv) / D;
    if (t < 0.0 || (s + t) > 1.0)  // I is outside T
        return false;

    //return 1;                       // I is in T
    isectPoint->x = I.x;
    isectPoint->y = I.y;
    isectPoint->z = I.z;
    return true;
}

TAKErr TAK::Engine::Math::Triangle_intersect(Point2<double> *value,
                                             const double V0x, const double V0y, const double V0z,
                                             const double V1x, const double V1y, const double V1z,
                                             const double V2x, const double V2y, const double V2z,
                                              const Ray2<double> &ray) NOTHROWS
{
    // derived from http://geomalgorithms.com/a06-_intersect-2.html#intersect3D_RayTriangle()
    Vector4<double>    u(0.0, 0.0, 0.0), v(0.0, 0.0, 0.0), n(0.0, 0.0, 0.0);              // triangle vectors
    Vector4<double>    dir(0.0, 0.0, 0.0), w0(0.0, 0.0, 0.0), w(0.0, 0.0, 0.0);           // ray vectors
    double     r, a, b;              // params to calc ray-plane intersect

                                     // get triangle edge vectors and plane normal
                                     //u = T.V1 - T.V0;
                                     //u = V1.subtract(V0);
    u = Vector4<double>(V1x - V0x, V1y - V0y, V1z - V0z);
    //v = T.V2 - T.V0;
    //v = V2.subtract(V0);
    v = Vector4<double>(V2x - V0x, V2y - V0y, V2z - V0z);
    //n = u * v;              // cross product
    u.cross(&v, &n);
    //if (n == (Vector)0)             // triangle is degenerate
    if (n.x == 0.0 && n.y == 0.0 && n.z == 0.0)
        return TE_Err;                  // do not deal with this case

                                    //dir = R.P1 - R.P0;              // ray direction vector
    dir = ray.direction;
    //w0 = R.P0 - T.V0;
    //Vector3D RP0 = new Vector3D(ray.origin.x, ray.origin.y, ray.origin.z);
    //w0 = RP0.subtract(V0);
    w0 = Vector4<double>(ray.origin.x - V0x, ray.origin.y - V0y, ray.origin.z - V0z);
    //a = -dot(n,w0);
    a = -n.dot(&w0);
    //b = dot(n,dir);
    b = n.dot(&dir);
    if (std::abs(b) < SMALL_NUM) {     // ray is  parallel to triangle plane
        if (a == 0)                 // ray lies in triangle plane
            return TE_Err;
        else return TE_Err;              // ray disjoint from plane
    }

    // get intersect point of ray with triangle plane
    r = a / b;
    if (r < 0.0)                    // ray goes away from triangle
        return TE_Err;                   // => no intersect
                                     // for a segment, also test if (r > 1.0) => no intersect

                                     //*I = R.P0 + r * dir;            // intersect point of ray and plane
                                     //Vector3D I = RP0.add(dir.multiply(r));
    Vector4<double> I = Vector4<double>(ray.origin.x + (dir.x*r), ray.origin.y + (dir.y*r), ray.origin.z + (dir.z*r));

    // is I inside T?
    double    uu, uv, vv, wu, wv, D;
    uu = u.dot(&u);
    uv = u.dot(&v);
    vv = v.dot(&v);
    //w = *I - T.V0;
    //w = I.subtract(V0);
    w = Vector4<double>(I.x - V0x, I.y - V0y, I.z - V0z);
    wu = w.dot(&u);
    wv = w.dot(&v);
    D = uv * uv - uu * vv;

    // get and test parametric coords
    double s, t;
    s = (uv * wv - vv * wu) / D;
    if (s < 0.0 || s > 1.0)         // I is outside T
        return TE_Err;
    t = (uv * wu - uu * wv) / D;
    if (t < 0.0 || (s + t) > 1.0)  // I is outside T
        return TE_Err;

    //return 1;                       // I is in T
    value->x = I.x;
    value->y = I.y;
    value->z = I.z;
    return TE_Ok;
}

TAKErr TAK::Engine::Math::Triangle_intersect(Point2<double> *value,
                                             const double ULx, const double ULy, const double ULz,
                                             const double URx, const double URy, const double URz,
                                             const double LRx, const double LRy, const double LRz,
                                             const double LLx, const double LLy, const double LLz,
                                             const Ray2<double> &ray) NOTHROWS
{
    TAKErr err(TE_Err);

    err = Triangle_intersect(value,
        ULx, ULy, ULz,
        LLx, LLy, LLz,
        URx, URy, URz,
        ray);
    if (err == TE_Ok)
        return err;

    err = Triangle_intersect(value,
        LRx, LRy, LRz,
        URx, URy, URz,
        LLx, LLy, LLz,
        ray);

    if (err == TE_Ok)
        return err;

    return err;
}
