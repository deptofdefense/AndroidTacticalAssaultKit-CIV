#include "feature/OGR_FeatureDataSource.h"

#include <cmath>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <inttypes.h>
#include <map>
#include <memory>
#include <stack>
#include <stdexcept>

#include "core/AtakMapView.h"
#include "feature/DefaultDriverDefinition.h"
#include "feature/DefaultDriverDefinition2.h"
#include "feature/DefaultSchemaDefinition.h"
#include "feature/Geometry.h"
#include "feature/OGRDriverDefinition2.h"
#include "feature/OGR_DriverDefinition.h"
#include "feature/OGR_SchemaDefinition.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "util/ConfigOptions.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging2.h"
#include "util/Memory.h"

#include <ogr_api.h>
#include <ogr_geometry.h>
#ifdef __ANDROID__
#include <ogr_core.h>
#endif
#include "FeatureDataSource.h"
#include "ogr_feature.h"
#include "ogr_spatialref.h"
#include "ogrsf_frmts.h"
#include "formats/ogr/OGRUtils.h"
#include "formats/ogr/OGR_Content.h"
#include "port/String.h"
#include "math/Utils.h"

#define MEM_FN( fn )    "atakmap::feature::OGR_FeatureDataSource::" fn ": "

using namespace TAK::Engine::Formats::OGR;
using namespace TAK::Engine::Util;

namespace atakmap {
    namespace feature {
        const char* const OGR_FeatureDataSource::DEFAULT_STROKE_COLOR_PROPERTY ("OGR_FEATURE_DATASOURCE_DEFAULT_STROKE_COLOR");
        const char* const OGR_FeatureDataSource::DEFAULT_STROKE_WIDTH_PROPERTY ("OGR_FEATURE_DATASOURCE_DEFAULT_STROKE_WIDTH");


        OGR_FeatureDataSource::OGR_FeatureDataSource () :
            areaThreshold (ComputeAreaThreshold(static_cast<unsigned int>(std::ceil(core::AtakMapView::DENSITY))))
        { }

        FeatureDataSource::Content* OGR_FeatureDataSource::parseFile (const char* cfilePath) const
        {
            if (!cfilePath) {
                throw std::invalid_argument (MEM_FN ("parseFile")
                                             "Received NULL filePath");
            }
            return new OGR_Content (cfilePath, areaThreshold);
        }

        std::size_t OGR_FeatureDataSource::ComputeAreaThreshold(unsigned DPI) {
            return OGRUtils_ComputeAreaThreshold(DPI);
        }

        std::size_t OGR_FeatureDataSource::ComputeLevelOfDetail(std::size_t threshold, OGREnvelope env) {
            return OGRUtils_ComputeLevelOfDetail(threshold, env);
        }
    }
}
