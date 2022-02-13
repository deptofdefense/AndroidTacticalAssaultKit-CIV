#include <cmath>
#include <memory>

#include "core/AtakMapView.h"
#include "feature/DefaultDriverDefinition2.h"

#ifdef __ANDROID__
#include <ogr_core.h>
#endif
#include "OGR_FeatureDataSource2.h"

#include "feature/LegacyAdapters.h"
#include "OGRUtils.h"
#include "OGR_Content.h"
#include "util/Memory.h"

using namespace atakmap;
using namespace TAK::Engine::Util;

#define MEM_FN( fn )    "atakmap::feature::OGR_FeatureDataSource2::" fn ": "

typedef std::unique_ptr<feature::FeatureDataSource::Content, void(*)(const feature::FeatureDataSource::Content *)> LegacyContentPtr;

namespace TAK {
namespace Engine {
namespace Formats {
namespace OGR {

const char* const OGR_FeatureDataSource2::DEFAULT_STROKE_COLOR_PROPERTY("OGR_FEATURE_DATASOURCE_DEFAULT_STROKE_COLOR");
const char* const OGR_FeatureDataSource2::DEFAULT_STROKE_WIDTH_PROPERTY("OGR_FEATURE_DATASOURCE_DEFAULT_STROKE_WIDTH");


OGR_FeatureDataSource2::OGR_FeatureDataSource2()
    : areaThreshold(OGRUtils_ComputeAreaThreshold(static_cast<unsigned int>(std::ceil(core::AtakMapView::DENSITY)))) {
}

TAKErr OGR_FeatureDataSource2::parse(ContentPtr& content, const char* filePath) NOTHROWS {
    if (!filePath) {
        return TE_InvalidArg;
    }

    return OGR_Content2_create(content, filePath, nullptr, areaThreshold);
}

}
}
}
}
