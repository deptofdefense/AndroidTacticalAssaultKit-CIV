#ifndef TAK_ENGINE_RENDERER_MODEL_SCENELAYERCONTROL2_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_SCENELAYERCONTROL2_H_INCLUDED

#include "renderer/model/SceneObjectControl.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API SceneLayerControl2
                {
                public :
                    virtual ~SceneLayerControl2() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr getSceneControl(void **ctrl, const char *type, const int64_t sid) NOTHROWS = 0;
                };
                
                ENGINE_API const char *SceneLayerControl2_getType() NOTHROWS;
            }
        }
    }
}
#endif
