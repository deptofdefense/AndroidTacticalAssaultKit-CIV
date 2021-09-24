#include "feature/FilterFeatureDataStore2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

FilterFeatureDataStore2::FilterFeatureDataStore2(FeatureDataStore2Ptr&& impl_) NOTHROWS :
    impl(std::move(impl_))
{}
FilterFeatureDataStore2::~FilterFeatureDataStore2() NOTHROWS
{}

TAKErr FilterFeatureDataStore2::addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener* l) NOTHROWS
{
    return impl->addOnDataStoreContentChangedListener(l);
}
TAKErr FilterFeatureDataStore2::removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    return impl->removeOnDataStoreContentChangedListener(l);
}
TAKErr FilterFeatureDataStore2::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    return impl->getFeature(feature, fid);
}
TAKErr FilterFeatureDataStore2::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    return impl->queryFeatures(cursor);
}
TAKErr FilterFeatureDataStore2::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS
{
    return impl->queryFeatures(cursor, params);
}
TAKErr FilterFeatureDataStore2::queryFeaturesCount(int *value) NOTHROWS
{
    return impl->queryFeaturesCount(value);
}
TAKErr FilterFeatureDataStore2::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    return impl->queryFeaturesCount(value, params);
}
TAKErr FilterFeatureDataStore2::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS
{
    return impl->getFeatureSet(featureSet, featureSetId);
}
TAKErr FilterFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    return impl->queryFeatureSets(cursor);
}
TAKErr FilterFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    return impl->queryFeatureSets(cursor, params);
}
TAKErr FilterFeatureDataStore2::queryFeatureSetsCount(int *value) NOTHROWS
{
    return impl->queryFeatureSetsCount(value);
}
TAKErr FilterFeatureDataStore2::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    return impl->queryFeatureSetsCount(value, params);
}
TAKErr FilterFeatureDataStore2::getModificationFlags(int *value) NOTHROWS
{
    return impl->getVisibilitySettingsFlags(value);
}
TAKErr FilterFeatureDataStore2::beginBulkModification() NOTHROWS
{
    return impl->beginBulkModification();
}
TAKErr FilterFeatureDataStore2::endBulkModification(const bool successful) NOTHROWS
{
    return impl->endBulkModification(successful);
}
TAKErr FilterFeatureDataStore2::isInBulkModification(bool *value) NOTHROWS
{
    return impl->isInBulkModification(value);
}
TAKErr FilterFeatureDataStore2::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return impl->insertFeatureSet(featureSet, provider, type, name, minResolution, maxResolution);
}
TAKErr FilterFeatureDataStore2::updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS
{
    return impl->updateFeatureSet(fsid, name);
}
TAKErr FilterFeatureDataStore2::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return impl->updateFeatureSet(fsid, minResolution, maxResolution);
}
TAKErr FilterFeatureDataStore2::updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return impl->updateFeatureSet(fsid, name, minResolution, maxResolution);
}
TAKErr FilterFeatureDataStore2::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    return impl->deleteFeatureSet(fsid);
}
TAKErr FilterFeatureDataStore2::deleteAllFeatureSets() NOTHROWS
{
    return impl->deleteAllFeatureSets();
}
TAKErr FilterFeatureDataStore2::insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return impl->insertFeature(feature, fsid, name, geom, altitudeMode, extrude, style, attributes);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const char *name) NOTHROWS
{
    return impl->updateFeature(fid, name);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    return impl->updateFeature(fid, geom);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return impl->updateFeature(fid, geom, altitudeMode, extrude);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return impl->updateFeature(fid, altitudeMode, extrude);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    return impl->updateFeature(fid, style);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return impl->updateFeature(fid, attributes);
}
TAKErr FilterFeatureDataStore2::updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return impl->updateFeature(fid, name, geom, style, attributes);
}
TAKErr FilterFeatureDataStore2::deleteFeature(const int64_t fid) NOTHROWS
{
    return impl->deleteFeature(fid);
}
TAKErr FilterFeatureDataStore2::deleteAllFeatures(const int64_t fsid) NOTHROWS
{
    return impl->deleteAllFeatures(fsid);
}
TAKErr FilterFeatureDataStore2::getVisibilitySettingsFlags(int *value) NOTHROWS
{
    return impl->getVisibilitySettingsFlags(value);
}
TAKErr FilterFeatureDataStore2::setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS
{
    return impl->setFeatureVisible(fid, visible);
}
TAKErr FilterFeatureDataStore2::setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    return impl->setFeaturesVisible(params, visible);
}
TAKErr FilterFeatureDataStore2::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    return impl->isFeatureVisible(value, fid);
}
TAKErr FilterFeatureDataStore2::setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS
{
    return impl->setFeatureSetVisible(setId, visible);
}
TAKErr FilterFeatureDataStore2::setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    return impl->setFeatureSetsVisible(params, visible);
}
TAKErr FilterFeatureDataStore2::setFeatureSetsReadOnly(const FeatureSetQueryParameters &paramsRef, const bool readOnly) NOTHROWS
{
    return impl->setFeatureSetsReadOnly(paramsRef, readOnly);
}
TAKErr FilterFeatureDataStore2::isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS
{
    return impl->isFeatureSetVisible(value, setId);
}
TAKErr FilterFeatureDataStore2::setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS
{
    return impl->setFeatureSetReadOnly(fsid, readOnly);
}
TAKErr FilterFeatureDataStore2::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS
{
    return impl->isFeatureSetReadOnly(value, fsid);
}
TAKErr FilterFeatureDataStore2::isFeatureReadOnly(bool *value, const int64_t fsid) NOTHROWS
{
    return impl->isFeatureReadOnly(value, fsid);
}
TAKErr FilterFeatureDataStore2::isAvailable(bool *value) NOTHROWS
{
    return impl->isAvailable(value);
}
TAKErr FilterFeatureDataStore2::refresh() NOTHROWS
{
    return impl->refresh();
}
TAKErr FilterFeatureDataStore2::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    return impl->getUri(value);
}
TAKErr FilterFeatureDataStore2::close() NOTHROWS
{
    return impl->close();
}
