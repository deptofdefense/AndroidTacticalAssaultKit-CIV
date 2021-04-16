#ifndef ATAKMAP_RASTER_PFPSMAPTYPEFRAME_H_INCLUDED
#define ATAKMAP_RASTER_PFPSMAPTYPEFRAME_H_INCLUDED

#include "core/GeoPoint.h"
#include "port/Platform.h"
#include <string>

namespace atakmap {
    namespace raster {
        namespace pfps {
            
            /*
            * See method comments for MIL references.
            */
            class ENGINE_API PfpsMapTypeFrame
            {

            public:
                static bool getRpfPrettyName(std::string *result, const char *filename);

                static bool coverageFromFilepath(const char *filepath, core::GeoPoint &ul, core::GeoPoint &ur,
                                                 core::GeoPoint &lr, core::GeoPoint &ll);

                static bool coverageFromFilename(const char *buffer, core::GeoPoint &ul,
                                                 core::GeoPoint &ur, core::GeoPoint& lr, core::GeoPoint &ll);



            };


        }
    }
}
#endif

