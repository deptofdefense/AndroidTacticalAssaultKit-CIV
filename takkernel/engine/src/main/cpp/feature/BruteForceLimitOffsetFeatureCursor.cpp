#include "feature/BruteForceLimitOffsetFeatureCursor.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

BruteForceLimitOffsetFeatureCursor::BruteForceLimitOffsetFeatureCursor(FeatureCursorPtr &&filter_, std::size_t limit_, std::size_t offset_) NOTHROWS :
    filter(std::move(filter_)),
    limit((limit_ > 0) ? limit_ : std::numeric_limits<std::size_t>::max()),
    offset(offset_),
    pos(0)
{}

TAKErr BruteForceLimitOffsetFeatureCursor::getId(int64_t *value) NOTHROWS
{
    return filter->getId(value);
}

TAKErr BruteForceLimitOffsetFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
{
    return filter->getFeatureSetId(value);
}

TAKErr BruteForceLimitOffsetFeatureCursor::getVersion(int64_t *value) NOTHROWS
{
    return filter->getVersion(value);
}

TAKErr BruteForceLimitOffsetFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    return filter->getRawGeometry(value);
}

FeatureDefinition2::GeometryEncoding BruteForceLimitOffsetFeatureCursor::getGeomCoding() NOTHROWS
{
    return filter->getGeomCoding();
}

AltitudeMode BruteForceLimitOffsetFeatureCursor::getAltitudeMode() NOTHROWS 
{
    return filter->getAltitudeMode();
}

double BruteForceLimitOffsetFeatureCursor::getExtrude() NOTHROWS 
{
    return filter->getExtrude();
}

TAKErr BruteForceLimitOffsetFeatureCursor::getName(const char **value) NOTHROWS
{
    return filter->getName(value);
}

FeatureDefinition2::StyleEncoding BruteForceLimitOffsetFeatureCursor::getStyleCoding() NOTHROWS
{
    return filter->getStyleCoding();
}

TAKErr BruteForceLimitOffsetFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    return filter->getRawStyle(value);
}

TAKErr BruteForceLimitOffsetFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    return filter->getAttributes(value);
}

TAKErr BruteForceLimitOffsetFeatureCursor::get(const Feature2 **feature) NOTHROWS
{
    return filter->get(feature);
}

TAKErr BruteForceLimitOffsetFeatureCursor::moveToNext() NOTHROWS
{
    TAKErr code;

    // if we've exceeded the limit, return false
    size_t limitCompare = this->pos < this->offset ? this->offset : this->pos;
    if ((limitCompare - this->offset) >= this->limit)
        return TE_Done;

    code = TE_Ok;

    // fast forward to the first offset record
    while (this->pos < (this->offset /*- 1*/)) {
        code = filter->moveToNext();
        TE_CHECKBREAK_CODE(code);

        this->pos++;
    }
    TE_CHECKRETURN_CODE(code);

    // input exhausted
    code = filter->moveToNext();
    TE_CHECKRETURN_CODE(code);

    // XXX - looks broken

    // update the position and make sure we haven't exceeded the limit
    this->pos++;
    if ((this->pos - this->offset) <= this->limit)
        return TE_Ok;
    else
        return TE_Done;
}
