#include "feature/Geometry2.h"

#include "feature/GeometryCollection2.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "feature/Polygon2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

Geometry2::Geometry2(const GeometryClass clazz_) NOTHROWS :
    clazz(clazz_),
    owned(false)
{}

Geometry2::Geometry2(const Geometry2 &other_) NOTHROWS :
    clazz(other_.clazz),
    owned(false)
{}

Geometry2::~Geometry2() NOTHROWS
{}

GeometryClass Geometry2::getClass() const NOTHROWS
{
    return clazz;
}

TAKErr Geometry2::setDimension(const std::size_t dimension) NOTHROWS
{
    if (this->getDimension() == dimension)
        return TE_Ok;
    if (owned)
        return TE_IllegalState;
    return setDimensionImpl(dimension);
}

TAKErr TAK::Engine::Feature::Geometry_clone(Geometry2Ptr &value, const Geometry2 &geometry) NOTHROWS
{
    TAKErr code(TE_Ok);

    switch (geometry.getClass())
    {
    case TEGC_Point :
        value = Geometry2Ptr(new Point2(static_cast<const Point2 &>(geometry)), Memory_deleter_const<Geometry2>);
        break;
    case TEGC_LineString :
        value = Geometry2Ptr(new LineString2(static_cast<const LineString2 &>(geometry)), Memory_deleter_const<Geometry2>);
        break;
    case TEGC_Polygon :
        value = Geometry2Ptr(new Polygon2(static_cast<const Polygon2 &>(geometry)), Memory_deleter_const<Geometry2>);
        break;
    case TEGC_GeometryCollection :
        value = Geometry2Ptr(new GeometryCollection2(static_cast<const GeometryCollection2 &>(geometry)), Memory_deleter_const<Geometry2>);
        break;
    default :
        code = TE_IllegalState;
        break;
    }

    return code;
}
bool Geometry2::operator==(const Geometry2 &other) NOTHROWS
{
    return (this == &other) || // sameness check
           // equality check
           ((this->getClass() == other.getClass()) &&
            (this->getDimension() == other.getDimension()) &&
            equalsImpl(other));
}
bool Geometry2::operator!=(const Geometry2 &other) NOTHROWS
{
    const bool retval = (*this == other);
    return !retval;
}

TAKErr TAK::Engine::Feature::Geometry_clone(Geometry2Ptr_const &value, const Geometry2 &geometry) NOTHROWS
{
    TAKErr code(TE_Ok);

    Geometry2Ptr clone(nullptr, nullptr);
    code = Geometry_clone(clone, geometry);
    TE_CHECKRETURN_CODE(code);

    value = Geometry2Ptr_const(clone.release(), clone.get_deleter());
    return code;
}
