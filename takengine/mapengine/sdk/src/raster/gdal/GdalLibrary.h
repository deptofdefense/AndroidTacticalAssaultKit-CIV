#ifndef ATAKMAP_RASTER_GDAL_GDALLIBRARY_H_INCLUDED
#define ATAKMAP_RASTER_GDAL_GDALLIBRARY_H_INCLUDED

#include "gdal_priv.h"
#include "ogr_spatialref.h"
#include "thread/Mutex.h"
#include "raster/tilereader/TileReader.h"

namespace atakmap {
    namespace raster {
        namespace gdal {
            
            class GdalLibrary
            {
            private:
                static TAK::Engine::Thread::Mutex mutex;
                static bool initialized;
                static bool initSuccess;

            public:
                static OGRSpatialReference *EPSG_4326;
                static bool init(const char *gdalDataDir);

                static bool isInitialized();

                static int getSpatialReferenceID(OGRSpatialReference *srs);
                
                static std::shared_ptr<atakmap::raster::tilereader::TileReader::AsynchronousIO> getMasterIOThread();
            };

        }
    }
}

#endif
