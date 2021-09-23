
#ifndef TAK_ENGINE_MODEL_HITTESTCONTROL_H_INCLUDED
#define TAK_ENGINE_MODEL_HITTESTCONTROL_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/MapSceneModel2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                /**
                 * Hit-test interface to register as a Control
                 */
                class ENGINE_API HitTestControl {
                public:
                    virtual ~HitTestControl() NOTHROWS;

                    /**
                     * Performs a hit-test
                     *
                     * @param x             The screen X location
                     * @param y             The screen Y location
                     * @param result        The hit location, if succesful
                     */
                    virtual TAK::Engine::Util::TAKErr hitTest(TAK::Engine::Core::GeoPoint2 *result, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float screenX, const float screenY) NOTHROWS = 0;
                };

                ENGINE_API const char *HitTestControl_getType() NOTHROWS;
            }
        }
    }
}

#endif