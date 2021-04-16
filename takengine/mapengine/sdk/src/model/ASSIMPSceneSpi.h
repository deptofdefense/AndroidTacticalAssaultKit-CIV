
#ifndef TAK_ENGINE_MODEL_ASSIMPSCENESPI_H_INCLUDED
#define TAK_ENGINE_MODEL_ASSIMPSCENESPI_H_INCLUDED

#include "model/Scene.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            class ENGINE_API ASSIMPSceneSpi : public SceneSpi {
            public:
                virtual ~ASSIMPSceneSpi() NOTHROWS;

                virtual const char *getType() const NOTHROWS;
                virtual int getPriority() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr create(ScenePtr &scene, const char *URI, Util::ProcessingCallback *callbacks, const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS;
            };
        }
    }
}

#endif