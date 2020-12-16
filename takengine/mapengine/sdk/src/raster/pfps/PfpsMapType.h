#ifndef ATAKMAP_RASTER_PFPSMAPTYPE_H_INCLUDED
#define ATAKMAP_RASTER_PFPSMAPTYPE_H_INCLUDED

#include "core/GeoPoint.h"
#include <string>
#include <stdexcept>

namespace atakmap {
    namespace raster {
        namespace pfps {

            struct PfpsMapType
            {
                const static int SCALE_UNIT_SCALE = 0;
                const static int SCALE_UNIT_METER = 4;

                const char *shortName;
                const double scale;
                const int scaleUnits;
                const char *folderName;
                const char *productName;
                const char *seriesName;
                const char *category;

                /** Creates a new instance of MapType */
                PfpsMapType(const char *shortName, double scale, int scaleUnits,
                                   const char *folderName, const char *productName, const char *seriesName,
                                   const char *category) throw (std::invalid_argument) : shortName(shortName), scale(scale), scaleUnits(scaleUnits),
                                   folderName(folderName), productName(productName), seriesName(seriesName), category(category) {
                    switch (scaleUnits) {
                    case SCALE_UNIT_SCALE:
                    case SCALE_UNIT_METER:
                        break;
                    default:
                        throw std::invalid_argument("Invalid unit");
                    }
                }


            };
        }
    }
}


#endif