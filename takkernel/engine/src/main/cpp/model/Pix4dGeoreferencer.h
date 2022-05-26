
#ifndef TAK_ENGINE_MODEL_PIX4DGEOREFERENCER_H_INCLUDED
#define TAK_ENGINE_MODEL_PIX4DGEOREFERENCER_H_INCLUDED

#include "model/SceneInfo.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API Pix4dGeoreferencer : public Georeferencer {
            public:
                Pix4dGeoreferencer() NOTHROWS;
                virtual ~Pix4dGeoreferencer() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr locate(SceneInfo &sceneInfo) NOTHROWS;
            };
        }
    }
}

#endif