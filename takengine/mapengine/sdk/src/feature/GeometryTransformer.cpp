#include "feature/GeometryTransformer.h"

#include "core/ProjectionFactory3.h"
#include "feature/GeometryCollection2.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "feature/Polygon2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

namespace
{
    TAKErr Geometry_transform_2D(Geometry2Ptr &value, const Geometry2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr Geometry_transform_3D(Geometry2Ptr &value, const Geometry2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr Point_transform_2D(Point2 *value, const Point2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr Point_transform_3D(Point2 *value, const Point2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr LineString_transform_2D(LineString2 *value, const LineString2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr LineString_transform_3D(LineString2 *value, const LineString2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr Polygon_transform_2D(Polygon2 *value, const Polygon2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr Polygon_transform_3D(Polygon2 *value, const Polygon2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr GeometryCollection_transform_2D(GeometryCollection2 *value, const GeometryCollection2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
    TAKErr GeometryCollection_transform_3D(GeometryCollection2 *value, const GeometryCollection2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS;
}

TAKErr TAK::Engine::Feature::GeometryTransformer_transform(Geometry2Ptr &value, const Geometry2 &src, const int srcSrid, const int dstSrid) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (srcSrid == dstSrid)
        return Geometry_clone(value, src);

    Projection2Ptr srcProj(nullptr, nullptr);
    code = ProjectionFactory3_create(srcProj, srcSrid);
    TE_CHECKRETURN_CODE(code);

    Projection2Ptr dstProj(nullptr, nullptr);
    code = ProjectionFactory3_create(dstProj, dstSrid);
    TE_CHECKRETURN_CODE(code);

    if (src.getDimension() == 2u)
        return Geometry_transform_2D(value, src, *srcProj, *dstProj);
    else if (src.getDimension() == 3u)
        return Geometry_transform_3D(value, src, *srcProj, *dstProj);
    else
        return TE_IllegalState;

}
TAKErr TAK::Engine::Feature::GeometryTransformer_transform(Geometry2Ptr_const &const_value, const Geometry2 &src, const int srcSrid, const int dstSrid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Geometry2Ptr value(nullptr, nullptr);
    code = GeometryTransformer_transform(value, src, srcSrid, dstSrid);
    TE_CHECKRETURN_CODE(code);

    const_value = Geometry2Ptr_const(value.release(), value.get_deleter());
    return code;
}
TAKErr TAK::Engine::Feature::GeometryTransformer_transform(Envelope2 *value, const Envelope2 &src, const int srcSrid, const int dstSrid) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!value)
        return TE_InvalidArg;

    // no-op
    if (srcSrid == dstSrid) {
        *value = src;
        return TE_Ok;
    }

    Projection2Ptr srcProj(nullptr, nullptr);
    code = ProjectionFactory3_create(srcProj, srcSrid);
    TE_CHECKRETURN_CODE(code);

    Projection2Ptr dstProj(nullptr, nullptr);
    code = ProjectionFactory3_create(dstProj, dstSrid);
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Feature::Point2 pts[8u] =
    {
        TAK::Engine::Feature::Point2(src.minX, src.minY, src.minZ),
        TAK::Engine::Feature::Point2(src.maxX, src.minY, src.minZ),
        TAK::Engine::Feature::Point2(src.minX, src.maxY, src.minZ),
        TAK::Engine::Feature::Point2(src.maxX, src.maxY, src.minZ),
        TAK::Engine::Feature::Point2(src.minX, src.minY, src.maxZ),
        TAK::Engine::Feature::Point2(src.maxX, src.minY, src.maxZ),
        TAK::Engine::Feature::Point2(src.minX, src.maxY, src.maxZ),
        TAK::Engine::Feature::Point2(src.maxX, src.maxY, src.maxZ)
    };

    Point_transform_3D(&pts[0u], pts[0u], *srcProj, *dstProj);
    value->minX = pts[0].x;
    value->minY = pts[0].y;
    value->minZ = pts[0].z;
    value->maxX = pts[0].x;
    value->maxY = pts[0].y;
    value->maxZ = pts[0].z;

    for (std::size_t i = 1u; i < 8u; i++) {
        Point_transform_3D(&pts[i], pts[i], *srcProj, *dstProj);
        if(pts[i].x < value->minX)
            value->minX = pts[i].x;
        if(pts[i].y < value->minY)
            value->minY = pts[i].y;
        if(pts[i].z < value->minZ)
            value->minZ = pts[i].z;
        if(pts[i].x > value->maxX)
            value->maxX = pts[i].x;
        if(pts[i].y > value->maxY)
            value->maxY = pts[i].y;
        if(pts[i].z > value->maxZ)
            value->maxZ = pts[i].z;
    }

    return code;
}

namespace
{
    TAKErr Geometry_transform_2D(Geometry2Ptr &value, const Geometry2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        switch (src.getClass()) {
        case TEGC_Point :
            value = Geometry2Ptr(new Point2(0, 0), Memory_deleter_const<Geometry2>);
            return Point_transform_2D(static_cast<Point2 *>(value.get()), static_cast<const Point2 &>(src), srcProj, dstProj);
        case TEGC_LineString :
            value = Geometry2Ptr(new LineString2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return LineString_transform_2D(static_cast<LineString2 *>(value.get()), static_cast<const LineString2 &>(src), srcProj, dstProj);
        case TEGC_Polygon :
            value = Geometry2Ptr(new Polygon2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return Polygon_transform_2D(static_cast<Polygon2 *>(value.get()), static_cast<const Polygon2 &>(src), srcProj, dstProj);
        case TEGC_GeometryCollection :
            value = Geometry2Ptr(new GeometryCollection2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return GeometryCollection_transform_2D(static_cast<GeometryCollection2 *>(value.get()), static_cast<const GeometryCollection2 &>(src), srcProj, dstProj);
        default :
            return TE_IllegalState;
        }
        return code;
    }
    TAKErr Geometry_transform_3D(Geometry2Ptr &value, const Geometry2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        switch (src.getClass()) {
        case TEGC_Point :
            value = Geometry2Ptr(new Point2(0, 0), Memory_deleter_const<Geometry2>);
            return Point_transform_3D(static_cast<Point2 *>(value.get()), static_cast<const Point2 &>(src), srcProj, dstProj);
        case TEGC_LineString :
            value = Geometry2Ptr(new LineString2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return LineString_transform_3D(static_cast<LineString2 *>(value.get()), static_cast<const LineString2 &>(src), srcProj, dstProj);
        case TEGC_Polygon :
            value = Geometry2Ptr(new Polygon2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return Polygon_transform_3D(static_cast<Polygon2 *>(value.get()), static_cast<const Polygon2 &>(src), srcProj, dstProj);
        case TEGC_GeometryCollection :
            value = Geometry2Ptr(new GeometryCollection2(), Memory_deleter_const<Geometry2>);
            code = value->setDimension(src.getDimension());
            TE_CHECKBREAK_CODE(code);
            return GeometryCollection_transform_3D(static_cast<GeometryCollection2 *>(value.get()), static_cast<const GeometryCollection2 &>(src), srcProj, dstProj);
        default :
            return TE_IllegalState;
        }
        return code;
    }

    TAKErr Point_transform_2D(Point2 *value, const Point2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 g;
        TAK::Engine::Math::Point2<double> p;

        code = srcProj.inverse(&g, TAK::Engine::Math::Point2<double>(src.x, src.y));
        TE_CHECKRETURN_CODE(code);
        code = dstProj.forward(&p, g);
        TE_CHECKRETURN_CODE(code);

        value->x = p.x;
        value->y = p.y;
        return code;
    }
    TAKErr Point_transform_3D(Point2 *value, const Point2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 g;
        TAK::Engine::Math::Point2<double> p;

        code = srcProj.inverse(&g, TAK::Engine::Math::Point2<double>(src.x, src.y, src.z));
        TE_CHECKRETURN_CODE(code);
        code = dstProj.forward(&p, g);
        TE_CHECKRETURN_CODE(code);

        value->x = p.x;
        value->y = p.y;
        value->z = p.z;
        return code;
    }

    TAKErr LineString_transform_2D(LineString2 *value, const LineString2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 g;
        TAK::Engine::Math::Point2<double> p;

        for (std::size_t i = 0; i < src.getNumPoints(); i++) {
            code = src.getX(&p.x, i);
            TE_CHECKBREAK_CODE(code);
            code = src.getY(&p.y, i);
            TE_CHECKBREAK_CODE(code);

            code = srcProj.inverse(&g, p);
            TE_CHECKRETURN_CODE(code);
            code = dstProj.forward(&p, g);
            TE_CHECKRETURN_CODE(code);

            code = value->addPoint(p.x, p.y);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr LineString_transform_3D(LineString2 *value, const LineString2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        GeoPoint2 g;
        TAK::Engine::Math::Point2<double> p;

        for (std::size_t i = 0; i < src.getNumPoints(); i++) {
            code = src.getX(&p.x, i);
            TE_CHECKBREAK_CODE(code);
            code = src.getY(&p.y, i);
            TE_CHECKBREAK_CODE(code);
            code = src.getZ(&p.z, i);
            TE_CHECKBREAK_CODE(code);

            code = srcProj.inverse(&g, p);
            TE_CHECKRETURN_CODE(code);
            code = dstProj.forward(&p, g);
            TE_CHECKRETURN_CODE(code);

            code = value->addPoint(p.x, p.y, p.z);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr Polygon_transform_2D(Polygon2 *value, const Polygon2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);

        std::shared_ptr<LineString2> srcRing;
        std::shared_ptr<LineString2> dstRing;

        code = src.getExteriorRing(srcRing);
        TE_CHECKRETURN_CODE(code);
        code = value->getExteriorRing(dstRing);

        code = LineString_transform_2D(dstRing.get(), *srcRing, srcProj, dstProj);
        TE_CHECKRETURN_CODE(code);

        for (std::size_t i = 0u; i < src.getNumInteriorRings(); i++) {
            code = src.getInteriorRing(srcRing, i);
            TE_CHECKBREAK_CODE(code);

            code = value->addInteriorRing();
            TE_CHECKBREAK_CODE(code);

            code = value->getInteriorRing(dstRing, i);
            TE_CHECKBREAK_CODE(code);

            code = LineString_transform_2D(dstRing.get(), *srcRing, srcProj, dstProj);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr Polygon_transform_3D(Polygon2 *value, const Polygon2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);

        std::shared_ptr<LineString2> srcRing;
        std::shared_ptr<LineString2> dstRing;

        code = src.getExteriorRing(srcRing);
        TE_CHECKRETURN_CODE(code);
        code = value->getExteriorRing(dstRing);

        code = LineString_transform_3D(dstRing.get(), *srcRing, srcProj, dstProj);
        TE_CHECKRETURN_CODE(code);

        for (std::size_t i = 0u; i < src.getNumInteriorRings(); i++) {
            code = src.getInteriorRing(srcRing, i);
            TE_CHECKBREAK_CODE(code);

            code = value->addInteriorRing();
            TE_CHECKBREAK_CODE(code);

            code = value->getInteriorRing(dstRing, i);
            TE_CHECKBREAK_CODE(code);

            code = LineString_transform_3D(dstRing.get(), *srcRing, srcProj, dstProj);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr GeometryCollection_transform_2D(GeometryCollection2 *value, const GeometryCollection2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        for (std::size_t i = 0u; i < src.getNumGeometries(); i++) {
            std::shared_ptr<Geometry2> srcChild;
            code = src.getGeometry(srcChild, i);
            TE_CHECKBREAK_CODE(code);

            Geometry2Ptr dstChild(nullptr, nullptr);
            code = Geometry_transform_2D(dstChild, *srcChild, srcProj, dstProj);
            TE_CHECKBREAK_CODE(code);

            code = value->addGeometry(std::move(dstChild));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr GeometryCollection_transform_3D(GeometryCollection2 *value, const GeometryCollection2 &src, const Projection2 &srcProj, const Projection2 &dstProj) NOTHROWS
    {
        TAKErr code(TE_Ok);
        for (std::size_t i = 0u; i < src.getNumGeometries(); i++) {
            std::shared_ptr<Geometry2> srcChild;
            code = src.getGeometry(srcChild, i);
            TE_CHECKBREAK_CODE(code);

            Geometry2Ptr dstChild(nullptr, nullptr);
            code = Geometry_transform_3D(dstChild, *srcChild, srcProj, dstProj);
            TE_CHECKBREAK_CODE(code);

            code = value->addGeometry(std::move(dstChild));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}
