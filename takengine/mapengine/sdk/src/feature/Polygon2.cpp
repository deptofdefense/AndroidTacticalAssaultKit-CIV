#include "feature/Polygon2.h"

#include <list>

#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

Polygon2::Polygon2() NOTHROWS :
    Geometry2(TEGC_Polygon),
    exteriorRing(std::unique_ptr<LineString2>(new LineString2())),
    dimension(2u)
{
    exteriorRing->setDimension(2u);
    exteriorRing->owned = true;
}
Polygon2::Polygon2(const LineString2 &exteriorRing_) NOTHROWS :
    Geometry2(TEGC_Polygon),
    exteriorRing(std::unique_ptr<LineString2>(new LineString2(exteriorRing_))),
    dimension(exteriorRing_.getDimension())
{
    exteriorRing->owned = true;
}
Polygon2::Polygon2(const Polygon2 &other) NOTHROWS :
    Geometry2(TEGC_Polygon),
    exteriorRing(std::unique_ptr<LineString2>(new LineString2(*other.exteriorRing))),
    dimension(other.getDimension())
{
    exteriorRing->owned = true;

    if (!other.interiorRings.empty()) {
        this->interiorRings.reserve(other.interiorRings.size());

        std::vector<std::shared_ptr<LineString2>>::const_iterator it;
        for (it = other.interiorRings.begin(); it != other.interiorRings.end(); it++) {
            std::unique_ptr<LineString2> ring(new LineString2(*(*it)));
            ring->owned = true;
            this->interiorRings.push_back(std::move(ring));
        }
    }
}

TAKErr Polygon2::addInteriorRing() NOTHROWS
{
    TAKErr code(TE_Ok);
    std::unique_ptr<LineString2> ring(new LineString2());
    code = ring->setDimension(this->dimension);
    TE_CHECKRETURN_CODE(code);
    ring->owned = true;
    this->interiorRings.push_back(std::move(ring));
    return code;
}
TAKErr Polygon2::addInteriorRing(const LineString2 &ring_) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::unique_ptr<LineString2> ring(new LineString2(ring_));
    code = ring->setDimension(this->dimension);
    TE_CHECKRETURN_CODE(code);
    
    ring->owned = true;
    this->interiorRings.push_back(std::move(ring));
    return TE_Ok;
}
TAKErr Polygon2::removeInteriorRing(const std::size_t i) NOTHROWS
{
    if (i >= this->interiorRings.size())
        return TE_BadIndex;
    std::shared_ptr<LineString2> ring = this->interiorRings[i];
    this->interiorRings.erase(this->interiorRings.begin() + i);
    ring->owned = false;
    return TE_Ok;
}
TAKErr Polygon2::removeInteriorRing(const LineString2 &ring) NOTHROWS
{
    TAKErr code(TE_InvalidArg);
    std::vector<std::shared_ptr<LineString2>>::iterator it;
    for (it = this->interiorRings.begin(); it != this->interiorRings.end(); it++) {
        if ((*it).get() == &ring) {
            code = TE_Ok;
            (*it)->owned = false;
            this->interiorRings.erase(it);
            break;
        }
    }

    return code;
}
void Polygon2::clear() NOTHROWS
{
    this->exteriorRing->clear();
    std::vector<std::shared_ptr<LineString2>>::const_iterator it;
    for (it = this->interiorRings.begin(); it != this->interiorRings.end(); it++) {
        (*it)->owned = false;
    }
    this->interiorRings.clear();
}
TAKErr Polygon2::getExteriorRing(std::shared_ptr<LineString2> &value) const NOTHROWS
{
    value = this->exteriorRing;
    return TE_Ok;
}
TAKErr Polygon2::getInteriorRings(TAK::Engine::Port::Collection<std::shared_ptr<LineString2>> &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    std::vector<std::shared_ptr<LineString2>>::const_iterator it;
    for (it = this->interiorRings.begin(); it != this->interiorRings.end(); it++) {
        code = value.add(*it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr Polygon2::getInteriorRing(std::shared_ptr<LineString2> &value, const std::size_t i) const NOTHROWS
{
    if (i >= this->interiorRings.size())
        return TE_BadIndex;
    value = this->interiorRings[i];
    return TE_Ok;
}
std::size_t Polygon2::getNumInteriorRings() const NOTHROWS
{
    return this->interiorRings.size();
}

std::size_t Polygon2::getDimension() const NOTHROWS
{
    return this->dimension;
}
TAKErr Polygon2::getEnvelope(Envelope2 *value) const NOTHROWS
{
    return this->exteriorRing->getEnvelope(value);
}

TAKErr Polygon2::setDimensionImpl(const std::size_t dimension_) NOTHROWS
{
    if (dimension_ != 2u && dimension_ != 3u)
        return TE_InvalidArg;

    TAKErr code;

    code = static_cast<Geometry2 &>(*this->exteriorRing).setDimensionImpl(dimension_);
    TE_CHECKRETURN_CODE(code);

    std::vector<std::shared_ptr<LineString2>>::iterator it;
    for (it = this->interiorRings.begin(); it != this->interiorRings.end(); it++) {
        code = static_cast<Geometry2 &>(*(*it)).setDimensionImpl(dimension_);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    this->dimension = dimension_;
    return code;
}

TAKErr TAK::Engine::Feature::Polygon2_fromEnvelope(Geometry2Ptr_const &value, const Envelope2 &e) NOTHROWS
{
    Geometry2Ptr mutableValue(nullptr, nullptr);

    TAKErr ret = Polygon2_fromEnvelope(mutableValue, e);
    if (ret == TE_Ok) {
        value = std::move(mutableValue);
    }
    return ret;
}

TAKErr TAK::Engine::Feature::Polygon2_fromEnvelope(Geometry2Ptr &value, const Envelope2 &e) NOTHROWS {
    LineString2 mbr;
    mbr.addPoint(e.minX, e.minY);
    mbr.addPoint(e.minX, e.maxY);
    mbr.addPoint(e.maxX, e.maxY);
    mbr.addPoint(e.maxX, e.minY);
    mbr.addPoint(e.minX, e.minY);

    value = Geometry2Ptr(new(std::nothrow) Polygon2(mbr), Memory_deleter_const<Geometry2, Polygon2>);
    if(!value.get())
        return TE_OutOfMemory;

    return TE_Ok;
}

bool Polygon2::equalsImpl(const Geometry2 &o) NOTHROWS
{
    const auto &other = static_cast<const Polygon2 &>(o);

    if(interiorRings.size() != other.interiorRings.size())
        return false;
    if(*exteriorRing != *other.exteriorRing)
        return false;

    std::list<LineString2 *> otherdup;
    for(std::size_t i = 0; i < other.interiorRings.size(); i++)
        otherdup.push_back(other.interiorRings[i].get());

    for(std::size_t i = 0u; i < interiorRings.size(); i++) {
        bool found = false;
        for(auto it = otherdup.begin(); it != otherdup.end(); it++) {
            if(*(*it) == *interiorRings[i]) {
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
