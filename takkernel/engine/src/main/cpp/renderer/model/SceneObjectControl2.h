#ifndef TAK_ENGINE_RENDERER_MODEL_SCENEOBJECTCONTROL2_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_SCENEOBJECTCONTROL2_H_INCLUDED

#include "core/Control.h"
#include "core/GeoPoint2.h"
#include "feature/AltitudeMode.h"
#include "feature/Envelope2.h"
#include "math/Matrix2.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "renderer/model/SceneObjectControl.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API SceneObjectControl2 : public SceneObjectControl
                {
                public :
                    virtual ~SceneObjectControl2() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr setXrayColor(const unsigned int color) NOTHROWS = 0;
                    virtual Util::TAKErr getXrayColor(unsigned int *color) NOTHROWS = 0;
                };

                ENGINE_API const char *SceneObjectControl2_getType() NOTHROWS;
            }
        }
    }
}
#endif
