#ifndef TAK_ENGINE_FEATURE_HITTESTSERVICE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_HITTESTSERVICE2_H_INCLUDED

#include "core/GeoPoint.h"
#include "core/Service.h"
#include "feature/Feature2.h"
#include "port/Collection.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class HitTestService2 : public atakmap::core::Service
            {
            protected :
                virtual ~HitTestService2() NOTHROWS = 0;
            public:
                /**
                 * Returns the FIDs for the features selected by a hit test at the specified location.
                 *
                 * @param fids          Returns the FIDs for selected features. Items will be inserted
                 *                      by z-order, with top-most items first.
                 * @param screenX       The x screen location in pixels (UL origin)
                 * @param screenY       The y screen location in pixels (UL origin)
                 * @param touch         The geodetic touch location
                 * @param resolution    The map resolution, in meters per pixel
                 * @param radius        The touch radius, in pixels
                 * @param limit         The maximum number of results to return, zero indicates
                 *                      no limit
                 *
                 * @return  TE_Ok on success
                 */
                virtual Util::TAKErr hitTest(Port::Collection<int64_t> &fids, const float screenX, const float screenY, const atakmap::core::GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS = 0;
            public: // Service
                virtual const char *getType() const NOTHROWS;
            public:
                static const char * const SERVICE_TYPE;
            };
        }
    }
}
#endif
