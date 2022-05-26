#ifndef TAK_ENGINE_RENDERER_MODEL_GLSCENEFACTORY_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLSCENEFACTORY_H_INCLUDED

#include "core/RenderContext.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/model/GLSceneSpi.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                ENGINE_API Util::TAKErr GLSceneFactory_create(Core::GLMapRenderable2Ptr &value, TAK::Engine::Core::RenderContext &ctx, const TAK::Engine::Model::SceneInfo &info, const GLSceneSpi::Options &opts) NOTHROWS;
                ENGINE_API Util::TAKErr GLSceneFactory_registerSpi(const std::shared_ptr<GLSceneSpi> &spi, const int priority) NOTHROWS;
                ENGINE_API Util::TAKErr GLSceneFactory_registerSpi(std::unique_ptr<GLSceneSpi> &&spi, const int priority) NOTHROWS;
                ENGINE_API Util::TAKErr GLSceneFactory_unregisterSpi(const GLSceneSpi &spi) NOTHROWS;
            }
        }
    }
}
#endif
