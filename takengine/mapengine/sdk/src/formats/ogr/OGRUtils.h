#pragma once

#include <ogr_geometry.h>
#ifdef __ANDROID__
#include <ogr_core.h>
#endif
#include "feature/LegacyAdapters.h"
#include "ogr_feature.h"
#include "ogr_spatialref.h"
#include "ogrsf_frmts.h"
#include <cstddef>
#include <memory>


#include "ogr_core.h"
        
namespace TAK {
namespace Engine {
namespace Formats {
namespace OGR {

        std::size_t OGRUtils_ComputeAreaThreshold(unsigned int DPI);
        std::size_t OGRUtils_ComputeLevelOfDetail(std::size_t threshold, OGREnvelope env);

    };
}
}
}

