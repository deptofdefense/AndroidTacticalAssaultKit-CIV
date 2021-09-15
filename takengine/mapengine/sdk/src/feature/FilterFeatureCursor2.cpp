#include "feature/FilterFeatureCursor2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;


FilterFeatureCursor2::FilterFeatureCursor2(FeatureCursorPtr &&cursor_) NOTHROWS :
    impl(std::move(cursor_))
{}
FilterFeatureCursor2::~FilterFeatureCursor2() NOTHROWS
{}

TAKErr FilterFeatureCursor2::moveToNext() NOTHROWS
{
    return this->impl->moveToNext();
}
TAKErr FilterFeatureCursor2::getId(int64_t *value) NOTHROWS
{
    return this->impl->getId(value);
}
TAKErr FilterFeatureCursor2::getFeatureSetId(int64_t *value) NOTHROWS
{
    return this->impl->getFeatureSetId(value);
}
TAKErr FilterFeatureCursor2::getVersion(int64_t *value) NOTHROWS
{
    return this->impl->getVersion(value);
}
TAKErr FilterFeatureCursor2::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    return this->impl->getRawGeometry(value);
}
FeatureDefinition2::GeometryEncoding FilterFeatureCursor2::getGeomCoding() NOTHROWS
{
    return this->impl->getGeomCoding();
}
AltitudeMode FilterFeatureCursor2::getAltitudeMode() NOTHROWS
{
    return this->impl->getAltitudeMode();
}
double FilterFeatureCursor2::getExtrude() NOTHROWS
{
    return this->impl->getExtrude();
}
TAKErr FilterFeatureCursor2::getName(const char **value) NOTHROWS
{
    return this->impl->getName(value);
}
FeatureDefinition2::StyleEncoding FilterFeatureCursor2::getStyleCoding() NOTHROWS
{
    return this->impl->getStyleCoding();
}
TAKErr FilterFeatureCursor2::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    return this->impl->getRawStyle(value);
}
TAKErr FilterFeatureCursor2::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    return this->impl->getAttributes(value);
}
TAKErr FilterFeatureCursor2::get(const Feature2 **feature) NOTHROWS
{
    return this->impl->get(feature);
}
