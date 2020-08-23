#ifndef TAK_ENGINE_FORMATS_DRG_DRG_H_INCLUDED
#define TAK_ENGINE_FORMATS_DRG_DRG_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace DRG {
                enum DRGClass
                {
                    TEDC_Topographic,
                    TEDC_Orthopohoto,
                    TEDC_Planimetric,
                };
                enum DRGSeries
                {
                    TEDS_7_5min,
                    TEDS_7_5minx15min,
                    TEDS_Alaska,
                    TEDS_30min_60min,
                    TEDS_1degx2deg
                };
                struct ENGINE_API DRGCategory
                {
                    DRGSeries series;
                    std::size_t scale_denom;
                    DRGClass drg_class;
                };
                struct ENGINE_API DRGInfo
                {
                    DRGCategory category;
                    double secondaryCellLatitude;
                    double secondaryCellLongitude;
                    std::size_t mapIndexNumber;
                    bool haveCorners;
                    double minLat;
                    double minLng;
                    double maxLat;
                    double maxLng;
                };

                /**
                 * Returns `true` if the given filename represents a valid DRG DSN.
                 * @param filename
                 * @return
                 */
                ENGINE_API Util::TAKErr DRG_isDRG(bool *value, const char *filename) NOTHROWS;
                /**
                 * If the filename represents a valid DRG DSN, parses the location information.
                 * @param value
                 * @param filename
                 * @return
                 */
                ENGINE_API Util::TAKErr DRG_parseName(DRGInfo *value, const char *filename) NOTHROWS;
            }
        }
    }
}
#endif // TAK_ENGINE_FORMATS_DRG_DRG_H_INCLUDED
