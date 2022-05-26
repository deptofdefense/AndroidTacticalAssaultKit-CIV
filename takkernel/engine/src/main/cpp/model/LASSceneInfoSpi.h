
#ifndef TAK_ENGINE_MODEL_LASSceneInfoSpi_H_INCLUDED
#define TAK_ENGINE_MODEL_LASSceneInfoSpi_H_INCLUDED

#include "model/SceneInfo.h"

namespace TAK {
    namespace Engine {
        namespace Model {

            TAK::Engine::Util::TAKErr LASSceneInfo_create(TAK::Engine::Port::Collection<SceneInfoPtr>& scenes, const char* path) NOTHROWS;

            class ENGINE_API LASSceneInfoSpi : public SceneInfoSpi {
            public:
                static const char *getStaticName() NOTHROWS;
                LASSceneInfoSpi() NOTHROWS;
                virtual ~LASSceneInfoSpi() NOTHROWS;
                virtual int getPriority() const NOTHROWS;
                virtual const char *getName() const NOTHROWS;
                virtual bool isSupported(const char *path) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS;
            };
        }
    }
}

#endif