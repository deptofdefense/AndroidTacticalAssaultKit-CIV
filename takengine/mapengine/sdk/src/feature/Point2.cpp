#include "feature/Point2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

Point2::Point2(const double x_, const double y_) NOTHROWS :
    Geometry2(TEGC_Point),
    x(x_),
    y(y_),
    z(0.0),
    dimension(2u)
{}

Point2::Point2(const double x_, const double y_, const double z_) NOTHROWS :
    Geometry2(TEGC_Point),
    x(x_),
    y(y_),
    z(z_),
    dimension(3u)
{}

std::size_t Point2::getDimension() const NOTHROWS
{
    return dimension;
}

TAKErr Point2::getEnvelope(Envelope2 *value) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    value->minX = x;
    value->minY = y;
    value->minZ = z;
    value->maxX = x;
    value->maxY = y;
    value->maxZ = z;

    return TE_Ok;
}

TAKErr Point2::setDimensionImpl(const std::size_t dimension_) NOTHROWS
{
    if (dimension_ != 2 && dimension_ != 3)
        return TE_InvalidArg;
    dimension = dimension_;
    return TE_Ok;
}

bool Point2::equalsImpl(const Geometry2 &o) NOTHROWS
{
    const auto &other = static_cast<const Point2 &>(o);

    return (this->x == other.x) &&
           (this->y == other.y) &&
           ((this->dimension == 2) || (this->z == other.z));
}
