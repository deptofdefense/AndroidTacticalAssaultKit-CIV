#ifndef TAK_ENGINE_MODEL_GEOREFERENCER_H_INCLUDED
#define TAK_ENGINE_MODEL_GEOREFERENCER_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            struct SceneInfo;
            class ENGINE_API Georeferencer {
                    public:
                    virtual ~Georeferencer() NOTHROWS = 0;
                    virtual TAK::Engine::Util::TAKErr locate(SceneInfo &sceneInfo) NOTHROWS = 0;
            };
        }
    }
}

#endif
