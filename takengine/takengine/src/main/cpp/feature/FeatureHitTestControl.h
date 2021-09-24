#ifndef TAK_ENGINE_FEATURE_FEATUREHITTESTCONTROL_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATUREHITTESTCONTROL_H_INCLUDED

#include "port/Collection.h"
#include "core/GeoPoint2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {

            class ENGINE_API FeatureHitTestControl {
            public:
                virtual ~FeatureHitTestControl() NOTHROWS;

                /**
                 * Performs a hit-test at the specified location. Returns the features at
                 * the specified location, inserted in top-most to bottom-most order.
                 *
                 * @param fids          The FIDs for features at the specified location
                 *                      Insertion order is assumed to be top-most Z first.
                 * @param screenX       The x-coordinate of the location, in screen pixels
                 * @param screenY       The y-coordinate of the location, in screen pixels
                 * @param point         The coordinate associated with the location
                 * @param resolution    The resolution of the map at the time of the
                 *                      selection
                 * @param radius        A radius, in pixels, around the specified point that
                 *                      may be considered valid for the hit-test
                 * @param limit         If non-zero, specifies the maximum number of results
                 *                      that may be added to the return value.
                 */
                virtual TAK::Engine::Util::TAKErr hitTest(Port::Collection<int64_t> &fids, float screenX, float screenY, const TAK::Engine::Core::GeoPoint2 &point, double resolution, float radius, int limit) NOTHROWS = 0;
            };

            ENGINE_API const char *FeatureHitTestControl_getType() NOTHROWS;


        }
    }
}

#endif