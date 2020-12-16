#ifndef TAK_ENGINE_RENDERER_MODEL_MATERIALMANAGER_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_MATERIALMANAGER_H_INCLUDED

#include <map>
#include <set>

#include "core/MapRenderer.h"
#include "model/Material.h"
#include "port/Platform.h"
#include "renderer/model/GLMaterial.h"
#include "thread/Mutex.h"
#include "util/Error.h"
#include "util/Tasking.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API MaterialManager
                {
                public:
                    class TextureLoader;
                    typedef std::unique_ptr<TextureLoader, void(*)(const TextureLoader *)> TextureLoaderPtr;
                public:
                    MaterialManager(TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
                    MaterialManager(TAK::Engine::Core::RenderContext &ctx, TextureLoaderPtr &&loader) NOTHROWS;
                    MaterialManager(TAK::Engine::Core::RenderContext& ctx, TextureLoaderPtr&& loader, MaterialManager& parent) NOTHROWS;
                public:
                    Util::TAKErr load(GLMaterial **value, const TAK::Engine::Model::Material &m, const unsigned int hints = 0u) NOTHROWS;
                    void unload(const GLMaterial *m);
                private:
                    TAK::Engine::Core::RenderContext &ctx;
                    MaterialManager* parent;
                    std::map<std::string, std::pair<std::size_t, std::unique_ptr<GLMaterial>>> textures;
                    TextureLoaderPtr loader;
                    Thread::Mutex mutex;
                };

                typedef std::unique_ptr<MaterialManager, void(*)(const MaterialManager *)> MaterialManagerPtr;

                class MaterialManager::TextureLoader
                {
                public :
                    virtual ~TextureLoader() NOTHROWS;
                public :
                    virtual Util::TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>>& value, const char* uri) NOTHROWS = 0;
                };
            }
        }
    }
}

#endif
