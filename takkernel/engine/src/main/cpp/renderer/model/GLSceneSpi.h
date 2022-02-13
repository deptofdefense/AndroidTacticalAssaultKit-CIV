#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENESPI_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENESPI_H_INCLUDED

#include "core/RenderContext.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/model/MaterialManager.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API GLSceneSpi
                {
                public :
                    struct Options
                    {
                        Options() NOTHROWS;

                        Port::String cacheDir;
                        std::shared_ptr<MaterialManager> materialManager;
                        bool showIndicator;
                    };
                public:
                    virtual ~GLSceneSpi() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr create(Core::GLMapRenderable2Ptr &value, TAK::Engine::Core::RenderContext &ctx, const TAK::Engine::Model::SceneInfo &info, const Options &opts) NOTHROWS = 0;
                };
            }
        }
    }
}
#endif

