#ifndef TAK_ENGINE_FORMATS_OSR_OSRPROJECTIONSPI_H_INCLUDED
#define TAK_ENGINE_FORMATS_OSR_OSRPROJECTIONSPI_H_INCLUDED

#include "core/ProjectionSpi3.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace OSR {
                ENGINE_API Core::ProjectionSpi3 &OSRProjectionSpi_get() NOTHROWS;
            }
        }
    }
}

#endif
