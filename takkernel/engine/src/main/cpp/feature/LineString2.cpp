#include "feature/LineString2.h"

#include <algorithm>
#include <cmath>
#include <cstring>

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

LineString2::LineString2() NOTHROWS :
    Geometry2(TEGC_LineString),
    dimension(2u),
    numPoints(0u),
    points(nullptr),
    pointsLength(0u)
{}

LineString2::LineString2(const LineString2 &other_) NOTHROWS :
    Geometry2(TEGC_LineString),
    dimension(other_.dimension),
    numPoints(other_.numPoints),
    points(nullptr),
    pointsLength(other_.dimension*other_.numPoints)
{
    if (pointsLength) {
        points.reset(new double[pointsLength]);
        memcpy(points.get(), other_.points.get(), pointsLength*sizeof(double));
    }
}

void LineString2::growPoints(const std::size_t newPointCapacity) NOTHROWS
{
    array_ptr<double> tmp(new double[newPointCapacity*this->dimension]);
    memcpy(tmp.get(), this->points.get(), this->numPoints*this->dimension*sizeof(double));
    this->points.reset(tmp.release());
    this->pointsLength = newPointCapacity*this->dimension;
}

TAKErr LineString2::addPoint(const double x, const double y) NOTHROWS
{
    if ((this->pointsLength / this->dimension) == this->numPoints)
        this->growPoints(this->numPoints + 10);
    std::size_t idx = this->numPoints*this->dimension;
    this->points[idx++] = x;
    this->points[idx++] = y;
    if (this->dimension == 3u)
        this->points[idx++] = 0.0;
    this->numPoints++;
    return TE_Ok;
}
TAKErr LineString2::addPoint(const double x, const double y, const double z) NOTHROWS
{
    if (this->dimension != 3u)
        return TE_IllegalState;
    if ((this->pointsLength / this->dimension) == this->numPoints)
        this->growPoints(this->numPoints + 10);
    std::size_t idx = this->numPoints*this->dimension;
    this->points[idx++] = x;
    this->points[idx++] = y;
    this->points[idx++] = z;
    this->numPoints++;
    return TE_Ok;
}
TAKErr LineString2::addPoints(const double *pts, const std::size_t numPts, const std::size_t ptsDim) NOTHROWS
{
    if (ptsDim > this->dimension)
        return TE_InvalidArg;
    if ((this->numPoints + numPts)*this->dimension > this->pointsLength)
        this->growPoints(this->numPoints + numPts);
    if (ptsDim == this->dimension) {
        memcpy(this->points.get(), pts, numPts*ptsDim*sizeof(double));
        this->numPoints += numPts;
    } else {
        if (ptsDim < this->dimension) {
            memset(this->points.get() + (this->numPoints*this->dimension), 0u, (numPts*this->dimension)*sizeof(double));
        }

        std::size_t srcIdx = 0;
        std::size_t dstIdx = 0;
        for (std::size_t i = 0; i < numPts; i++) {
            memcpy(this->points.get() + (this->numPoints*this->dimension) + dstIdx, pts + srcIdx, std::min(this->dimension, ptsDim)*sizeof(double));
            srcIdx += ptsDim;
            dstIdx += this->dimension;
        }
        this->numPoints += numPts;
    }
    return TE_Ok;
}
TAKErr LineString2::removePoint(const std::size_t i) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;

    if (i < (this->numPoints - 1)) {
        memcpy(this->points.get() + (i*this->dimension),
               this->points.get() + ((i + 1)*this->dimension),
               ((this->numPoints - (i+1))*this->dimension)*sizeof(double));
    }
    this->numPoints--;

    return TE_Ok;
}
TAKErr LineString2::removePoints(const std::size_t offset, const std::size_t count) NOTHROWS
{
    if ((offset + count - 1) >= this->numPoints)
        return TE_InvalidArg;

    if (count == this->numPoints) {
        this->numPoints = 0;
    } else {
        if ((offset+count-1) < (this->numPoints - 1)) {
            memcpy(this->points.get() + (offset*this->dimension),
                   this->points.get() + ((offset + count)*this->dimension),
                   ((this->numPoints - (offset+count))*this->dimension)*sizeof(double));
        }
        this->numPoints -= count;
    }
    return TE_Ok;
}
void LineString2::clear() NOTHROWS
{
    numPoints = 0;
}


std::size_t LineString2::getNumPoints() const NOTHROWS
{
    return this->numPoints;
}

TAKErr LineString2::getX(double *value, const std::size_t i) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (i >= this->numPoints)
        return TE_BadIndex;
    *value = this->points[(i*this->dimension)];
    return TE_Ok;
}
TAKErr LineString2::getY(double *value, const std::size_t i) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (i >= this->numPoints)
        return TE_BadIndex;
    *value = this->points[(i*this->dimension) + 1];
    return TE_Ok;
}
TAKErr LineString2::getZ(double *value, const std::size_t i) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (this->dimension < 3)
        return TE_IllegalState;
    if (i >= this->numPoints)
        return TE_BadIndex;
    *value = this->points[(i*this->dimension) + 2];
    return TE_Ok;
}
TAKErr LineString2::get(Point2 *value, const std::size_t i) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (i >= this->numPoints)
        return TE_BadIndex;

    TAKErr code(TE_Ok);
    code = value->setDimension(this->dimension);
    TE_CHECKRETURN_CODE(code);

    value->x = this->points[i*this->dimension];
    value->y = this->points[i*this->dimension + 1];
    if (this->dimension > 2)
        value->z = this->points[i*this->dimension + 2];

    return code;
}

TAKErr LineString2::setX(const std::size_t i, const double x) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;

    this->points[(i*this->dimension)] = x;
    return TE_Ok;
}
TAKErr LineString2::setY(const std::size_t i, const double y) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;

    this->points[(i*this->dimension)+1] = y;
    return TE_Ok;
}
TAKErr LineString2::setZ(const std::size_t i, const double z) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;
    if (this->dimension < 3)
        return TE_IllegalState;

    this->points[(i*this->dimension)+2] = z;
    return TE_Ok;
}
TAKErr LineString2::set(const std::size_t i, const double x, const double y) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;

    this->points[(i*this->dimension)] = x;
    this->points[(i*this->dimension) + 1] = y;
    return TE_Ok;
}
TAKErr LineString2::set(const std::size_t i, const double x, const double y, const double z) NOTHROWS
{
    if (i >= this->numPoints)
        return TE_BadIndex;
    if (this->dimension < 3)
        return TE_IllegalState;

    this->points[(i*this->dimension)] = x;
    this->points[(i*this->dimension) + 1] = y;
    this->points[(i*this->dimension) + 2] = z;
    return TE_Ok;
}
TAKErr LineString2::set(const std::size_t i, const Point2 &point) NOTHROWS
{
    if (point.getDimension() == 2)
        return this->set(i, point.x, point.y);
    else if (point.getDimension() == 3)
        return this->set(i, point.x, point.y, point.z);
    else
        return TE_IllegalState;
}

TAKErr LineString2::isClosed(bool *value) const NOTHROWS
{
    if (!this->numPoints) {
        *value = true;
        return TE_Ok;
    }

    const std::size_t lastPointIdx = this->numPoints - 1u;
    switch (this->dimension) {
    case 3:
        // check z then fall through to check x,y
        if (this->points[2] != this->points[(lastPointIdx*this->dimension)+2]) {
            *value = false;
            return TE_Ok;
        }
    case 2:
        *value = (this->points[0] == this->points[(lastPointIdx*this->dimension)]) &&
                 (this->points[1] == this->points[(lastPointIdx*this->dimension)+1]);
        return TE_Ok;
    default:
        return TE_IllegalState;
    }
}
std::size_t LineString2::getDimension() const NOTHROWS
{
    return dimension;
}
TAKErr LineString2::getEnvelope(Envelope2 *value) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    if (this->numPoints) {
        code = this->getX(&value->minX, 0);
        TE_CHECKRETURN_CODE(code);
        code = this->getY(&value->minY, 0);
        TE_CHECKRETURN_CODE(code);
        value->minZ = 0.0;

        code = this->getX(&value->maxX, 0);
        TE_CHECKRETURN_CODE(code);
        code = this->getY(&value->maxY, 0);
        TE_CHECKRETURN_CODE(code);
        value->maxZ = 0.0;

        if (this->dimension > 2) {
            code = this->getZ(&value->minZ, 0);
            TE_CHECKRETURN_CODE(code);
            code = this->getZ(&value->maxZ, 0);
            TE_CHECKRETURN_CODE(code);
        }

        double v;
        for (std::size_t i = 1u; i < this->numPoints; i++) {
            code = this->getX(&v, i);
            TE_CHECKBREAK_CODE(code);
            if (v < value->minX)
                value->minX = v;
            else if (v > value->maxX)
                value->maxX = v;
            code = this->getY(&v, i);
            TE_CHECKBREAK_CODE(code);
            if (v < value->minY)
                value->minY = v;
            else if (v > value->maxY)
                value->maxY = v;
            if (this->dimension == 2)
                continue;
            code = this->getZ(&v, i);
            TE_CHECKBREAK_CODE(code);
            if (v < value->minZ)
                value->minZ = v;
            else if (v > value->maxZ)
                value->maxZ = v;
        }
        TE_CHECKRETURN_CODE(code);
    } else {
        value->minX = NAN;
        value->minY = NAN;
        value->minZ = NAN;
        value->maxX = NAN;
        value->maxY = NAN;
        value->maxZ = NAN;
    }

    return code;
}

TAKErr LineString2::setDimensionImpl(const std::size_t dimension_) NOTHROWS
{
    if (dimension_ == this->dimension)
        return TE_Ok;
    if (dimension_ != 2 && dimension_ != 3)
        return TE_InvalidArg;

    array_ptr<double> redim(new double[this->numPoints*dimension_]);
    if(dimension_ == 2u && this->dimension == 3u) {
        for (std::size_t i = 0u; i < this->numPoints; i++) {
            redim[i*dimension_] = this->points[i*this->dimension];
            redim[i*dimension_ + 1u] = this->points[i*this->dimension + 1u];
        }
    } else if(dimension_ == 3u && this->dimension == 2u) {
        for (std::size_t i = 0u; i < this->numPoints; i++) {
            redim[i*dimension_] = this->points[i*this->dimension];
            redim[i*dimension_ + 1u] = this->points[i*this->dimension + 1u];
            redim[i*dimension_ + 2u] = 0.0;
        }
    } else {
        return TE_IllegalState;
    }

    this->points.reset(redim.release());
    this->dimension = dimension_;
    return TE_Ok;
}

bool LineString2::equalsImpl(const Geometry2 &o) NOTHROWS
{
    const auto &other = static_cast<const LineString2 &>(o);
    if(this->numPoints != other.numPoints)
        return false;

    return (memcmp(this->points.get(), other.points.get(), sizeof(double)*dimension*numPoints) == 0);
}
