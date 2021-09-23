
#ifndef TAK_ENGINE_MODEL_CESIUM3DTILESCENESPI_H_INCLUDED
#define TAK_ENGINE_MODEL_CESIUM3DTILESCENESPI_H_INCLUDED

#include "model/Scene.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API Cesium3DTilesSceneSpi : public SceneSpi {
            public:
                Cesium3DTilesSceneSpi() NOTHROWS;
                virtual ~Cesium3DTilesSceneSpi() NOTHROWS;

                virtual const char* getType() const NOTHROWS;

                virtual int getPriority() const NOTHROWS;

                virtual TAK::Engine::Util::TAKErr create(ScenePtr& scene,
                    const char* URI,
                    Util::ProcessingCallback* callbacks,
                    const TAK::Engine::Port::Collection<ResourceAlias>* resourceAliases) const NOTHROWS;
            };
        }
    }
}

#endif