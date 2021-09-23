#ifndef TAK_ENGINE_FEATURE_LEGACYADAPTERS_H_INCLUDED
#define TAK_ENGINE_FEATURE_LEGACYADAPTERS_H_INCLUDED

#include "feature/Feature.h"
#include "feature/FeatureDataSource.h"
#include "feature/FeatureDataSource2.h"
#include "feature/FeatureDefinition2.h"
#include "feature/Geometry2.h"
#include "feature/Style.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            typedef std::unique_ptr<const std::pair<const uint8_t *, const uint8_t *>, void(*)(const std::pair<const uint8_t *, const uint8_t *> *)> BlobPtr;

			ENGINE_API Util::TAKErr LegacyAdapters_create(FeaturePtr_const &value, const atakmap::feature::Feature &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(FeatureDefinitionPtr &value, const atakmap::feature::FeatureDataSource::FeatureDefinition &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(FeatureDataSourcePtr &value, const atakmap::feature::FeatureDataSource &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(FeatureDataSource2::ContentPtr &value, atakmap::feature::FeatureDataSource::Content &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(FeatureDataSource2::ContentPtr &value, std::unique_ptr<atakmap::feature::FeatureDataSource::Content, void(*)(const atakmap::feature::FeatureDataSource::Content *)> &&legacy) NOTHROWS;

			ENGINE_API Util::TAKErr LegacyAdapters_adapt(Geometry2Ptr &value, const atakmap::feature::Geometry &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(Geometry2Ptr_const &value, const atakmap::feature::Geometry &legacy) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(GeometryPtr &value, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(GeometryPtr_const &value, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS;

			ENGINE_API Util::TAKErr LegacyAdapters_toWkb(BlobPtr &wkb, const atakmap::feature::Geometry &geom) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_toBlob(BlobPtr &blob, const atakmap::feature::Geometry &geom) NOTHROWS;

            
        }
    }
}

#endif
