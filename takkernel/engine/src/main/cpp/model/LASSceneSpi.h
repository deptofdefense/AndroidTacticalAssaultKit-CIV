#ifndef TAK_ENGINE_MODEL_LASSCENESPI_H_INCLUDED
#define TAK_ENGINE_MODEL_LASSCENESPI_H_INCLUDED

#include "model/Scene.h"
#include "model/SceneInfo.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            Util::TAKErr LASSceneSPI_computeZColor(double z, float *r, float *g, float *b);
            class ENGINE_API LASSceneSPI : public Model::SceneSpi {
            public:
                const char *getType() const NOTHROWS override;
                int getPriority() const NOTHROWS override;
                Util::TAKErr create(Model::ScenePtr &scene, const char *URI,
                                    Util::ProcessingCallback *callbacks,
                                    const TAK::Engine::Port::Collection<Model::ResourceAlias> *resourceAliases) const NOTHROWS override;
            };
        }
    }
}

#endif //TAK_ENGINE_MODEL_LASSCENESPI_H_INCLUDED
