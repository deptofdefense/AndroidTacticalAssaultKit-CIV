#pragma once

#include "raster/mosaic/MosaicDatabaseSpi2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace Mosaic {
                ENGINE_API Util::TAKErr MosaicDatabaseFactory2_register(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS;
                ENGINE_API Util::TAKErr MosaicDatabaseFactory2_unregister(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS;
                ENGINE_API Util::TAKErr MosaicDatabaseFactory2_create(MosaicDatabase2Ptr &db, const char *provider) NOTHROWS;
                ENGINE_API Util::TAKErr MosaicDatabaseFactory2_canCreate(const char *provider) NOTHROWS;
            }
        }
    }
}