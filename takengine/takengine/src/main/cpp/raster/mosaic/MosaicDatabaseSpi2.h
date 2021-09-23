#pragma once

#include "raster/mosaic/MosaicDatabase2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace Mosaic {
                class ENGINE_API MosaicDatabaseSpi2
                {
                   public:
                    MosaicDatabaseSpi2() NOTHROWS;
                    virtual ~MosaicDatabaseSpi2() NOTHROWS = 0;
                    virtual const char *getName() const NOTHROWS = 0;
                    virtual Util::TAKErr createInstance(MosaicDatabase2Ptr &db) const NOTHROWS = 0;
                };
                typedef std::unique_ptr<MosaicDatabaseSpi2, void (*)(const MosaicDatabaseSpi2 *)> MosaicDatabaseSpi2Ptr;
            }
        }
    }
}