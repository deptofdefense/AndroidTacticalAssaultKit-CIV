#ifndef ATAKMAP_RASTER_MOSAIC_MOSAICUTILS_H_INCLUDED
#define ATAKMAP_RASTER_MOSAIC_MOSAICUTILS_H_INCLUDED

#include <limits>
#include <cstdint>
#include "raster/mosaic/ATAKMosaicDatabase.h"

namespace atakmap {
    namespace raster {
        namespace mosaic {

            struct BuildMosaicCallback {
            public:
                virtual void onProgressUpdate(int itemsProcessed) = 0;
            };



            class MosaicUtils {
            public:
                static const int DEFAULT_NUM_LEVELS = 3;


                static bool isMosaicDir(const char *f, int limit = INT_MAX);
                static void buildMosaicDatabase(const char *mosaicDir, const char *databaseFile,
                                                BuildMosaicCallback *callback = nullptr);


            private:
                static void buildGenericSubDatabase(ATAKMosaicDatabase *database, const char *subdir,
                                                    const char *relativeTo, int *count,
                                                    BuildMosaicCallback *callback);
                static void buildMrsidSubDatabase(ATAKMosaicDatabase *database, const char *subdir,
                                                  const char *relativeTo, int *count,
                                                  BuildMosaicCallback *callback);


            };
        }
    }
}

#endif
