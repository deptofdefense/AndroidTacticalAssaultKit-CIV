#ifndef TAK_ENGINE_RENDERER_MODEL_SCENELAYERCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_SCENELAYERCONTROL_H_INCLUDED

#include "renderer/model/SceneObjectControl.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API SceneLayerControl
                {
                public :
                    virtual ~SceneLayerControl() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr getSceneObjectControl(SceneObjectControl **ctrl, const int64_t sid) NOTHROWS = 0;
                };
                
                ENGINE_API const char *SceneLayerControl_getType() NOTHROWS;
            }
        }
    }
}
#endif
