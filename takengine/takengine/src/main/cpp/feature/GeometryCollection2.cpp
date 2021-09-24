#include "feature/GeometryCollection2.h"

#include <cmath>
#include <list>
#include <cmath>

#include "feature/LineString2.h"
#include "feature/Polygon2.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    bool isValid(const Envelope2 &mbb) NOTHROWS;
    bool isEmpty(const Geometry2 &geometry) NOTHROWS;
    TAKErr flattenImpl(std::vector<Geometry2Ptr> &geometries, const GeometryCollection2 &collection) NOTHROWS;
}

GeometryCollection2::GeometryCollection2() NOTHROWS :
    Geometry2(TEGC_GeometryCollection),
    dimension(2)
{}

GeometryCollection2::GeometryCollection2(const GeometryCollection2 &other) NOTHROWS :
    Geometry2(TEGC_GeometryCollection),
    dimension(other.dimension)
{
    this->geometries.reserve(other.getNumGeometries());

    std::vector<std::shared_ptr<Geometry2>>::const_iterator it;
    for (it = other.geometries.begin(); it != other.geometries.end(); it++) {
        // note error code is ignored here; we know GeometryCollection may only contain supported geometry types
        Geometry2Ptr child(nullptr, nullptr);
        Geometry_clone(child, *(*it));

        this->geometries.push_back(std::move(child));
    }
}

TAKErr GeometryCollection2::addGeometry(const Geometry2 &geometry) NOTHROWS
{
    TAKErr code(TE_Ok);
    Geometry2Ptr clone(nullptr, nullptr);
    code = Geometry_clone(clone, geometry);
    TE_CHECKRETURN_CODE(code);

    code = this->addGeometry(std::move(clone));
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GeometryCollection2::addGeometry(Geometry2Ptr &&geometry) NOTHROWS
{
    if (geometry->owned)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    geometry->owned = true;
    this->geometries.push_back(std::move(geometry));
    return code;
}

TAKErr GeometryCollection2::removeGeometry(const std::size_t i) NOTHROWS
{
    if (i >= this->geometries.size())
        return TE_BadIndex;
    std::shared_ptr<Geometry2> ring = this->geometries[i];
    this->geometries.erase(this->geometries.begin() + i);
    ring->owned = false;
    return TE_Ok;
}

TAKErr GeometryCollection2::removeGeometry(const Geometry2 &geometry) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::vector<std::shared_ptr<Geometry2>>::iterator it;
    code = TE_InvalidArg;
    for (it = this->geometries.begin(); it != this->geometries.end(); it++) {
        if ((*it).get() == &geometry) {
            (*it)->owned = false;
            this->geometries.erase(it);
            code = TE_Ok;
            break;
        }
    }

    return code;
}

void GeometryCollection2::clear() NOTHROWS
{
    std::vector<std::shared_ptr<Geometry2>>::iterator it;
    for (it = this->geometries.begin(); it != this->geometries.end(); it++)
        (*it)->owned = false;
    this->geometries.clear();
}

TAKErr GeometryCollection2::getGeometries(Collection<std::shared_ptr<Geometry2>> &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    std::vector<std::shared_ptr<Geometry2>>::const_iterator it;
    for (it = this->geometries.begin(); it != this->geometries.end(); it++) {
        code = value.add(*it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    return code;
}

TAKErr GeometryCollection2::getGeometry(std::shared_ptr<Geometry2> &value, const std::size_t i) const NOTHROWS
{
    if (i >= this->geometries.size())
        return TE_BadIndex;
    value = this->geometries[i];
    return TE_Ok;
}

std::size_t GeometryCollection2::getNumGeometries() const NOTHROWS
{
    return this->geometries.size();
}
std::size_t GeometryCollection2::getDimension() const NOTHROWS
{
    return this->dimension;
}

TAKErr GeometryCollection2::getEnvelope(Envelope2 *value) const NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    value->minX = NAN;
    value->minY = NAN;
    value->minZ = NAN;
    value->maxX = NAN;
    value->maxY = NAN;
    value->maxZ = NAN;

    if (!this->geometries.empty()) {
        std::vector<std::shared_ptr<Geometry2>>::const_iterator it;
        for (it = this->geometries.begin(); it != this->geometries.end(); it++) {
            Envelope2 e;
            code = (*it)->getEnvelope(&e);
            TE_CHECKBREAK_CODE(code);
            if (!isValid(e))
                continue;
            if (!isValid(*value)) {
                *value = e;
            } else {
                if (e.minX < value->minX)
                    value->minX = e.minX;
                if (e.maxX > value->maxX)
                    value->maxX = e.maxX;
                if (e.minY < value->minY)
                    value->minY = e.minY;
                if (e.maxY > value->maxY)
                    value->maxY = e.maxY;
                if (e.minZ < value->minZ)
                    value->minZ = e.minZ;
                if (e.maxZ > value->maxZ)
                    value->maxZ = e.maxZ;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}

TAKErr GeometryCollection2::setDimensionImpl(const std::size_t dimension_) NOTHROWS
{
    if (dimension_ != 2u && dimension_ != 3u)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    std::vector<std::shared_ptr<Geometry2>>::iterator it;
    for (it = this->geometries.begin(); it != this->geometries.end(); it++) {
        code = (*it)->setDimensionImpl(dimension_);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    this->dimension = dimension_;
    return code;
}

bool GeometryCollection2::equalsImpl(const Geometry2 &o) NOTHROWS
{
    const auto &other = static_cast<const GeometryCollection2 &>(o);
    if(this->geometries.size() != other.geometries.size())
        return false;

    std::list<Geometry2 *> otherdup;
    for(std::size_t i = 0; i < other.geometries.size(); i++)
        otherdup.push_back(other.geometries[i].get());

    for(std::size_t i = 0u; i < geometries.size(); i++) {
        bool found = false;
        for(auto it = otherdup.begin(); it != otherdup.end(); it++) {
            if(*(*it) == *geometries[i]) {
                found = true;
                otherdup.erase(it);
                break;
            }
        }
        if(!found)
            return false;
    }

    return otherdup.empty();
}

TAKErr TAK::Engine::Feature::GeometryCollection_flatten(Geometry2Ptr &value, const GeometryCollection2 &collection) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::vector<Geometry2Ptr> geometries;
    code = flattenImpl(geometries, collection);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<GeometryCollection2> retval(new GeometryCollection2());
    code = retval->setDimension(collection.getDimension());
    TE_CHECKRETURN_CODE(code);

    std::vector<Geometry2Ptr>::iterator it;
    for (it = geometries.begin(); it != geometries.end(); it++) {
        code = retval->addGeometry(std::move(*it));
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    value = Geometry2Ptr(retval.release(), Memory_deleter_const<Geometry2>);

    return code;
}

namespace
{
    bool isValid(const Envelope2 &mbb) NOTHROWS
    {
        return !TE_ISNAN(mbb.minX) && !TE_ISNAN(mbb.minY) && !TE_ISNAN(mbb.minZ) &&
               !TE_ISNAN(mbb.maxX) && !TE_ISNAN(mbb.maxY) && !TE_ISNAN(mbb.maxZ);
    }

    bool isEmpty(const Geometry2 &geometry) NOTHROWS
    {
        switch (geometry.getClass())
        {
            case TEGC_GeometryCollection:
            {
                return !static_cast<const GeometryCollection2&>(geometry).getNumGeometries();
            }
            case TEGC_LineString:
            {
                return !static_cast<const LineString2&>(geometry).getNumPoints();
            }
            case TEGC_Polygon:
            {
                const auto &polygon = static_cast<const Polygon2&>(geometry);
                std::shared_ptr<LineString2> ring;
                polygon.getExteriorRing(ring);
                return !ring->getNumPoints();
            }
            case TEGC_Point:
            {
                return false;
            }
            default:
            {
                return true;
            }
        }
    }

    TAKErr flattenImpl(std::vector<Geometry2Ptr> &geometries, const GeometryCollection2 &collection) NOTHROWS
    {
        TAKErr code(TE_Ok);

        geometries.reserve(geometries.size() + collection.getNumGeometries());

        for (std::size_t i = 0u; i < collection.getNumGeometries(); i++) {
            std::shared_ptr<Geometry2> child;
            code = collection.getGeometry(child, i);
            TE_CHECKBREAK_CODE(code);

            if (child->getClass() != TEGC_GeometryCollection) {
                Geometry2Ptr copy(nullptr, nullptr);
                code = Geometry_clone(copy, *child);
                TE_CHECKBREAK_CODE(code);

                geometries.push_back(std::move(copy));
            } else {
                code = flattenImpl(geometries, static_cast<GeometryCollection2 &>(*child));
                TE_CHECKBREAK_CODE(code);
            }
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}
