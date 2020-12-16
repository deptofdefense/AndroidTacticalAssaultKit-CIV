#include "renderer/model/MaterialManager.h"

#include "port/StringBuilder.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "thread/Lock.h"
#include "util/Memory.h"
#include "model/Mesh.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

// XXX - default material loader

namespace
{
    class DefaultTextureLoader : public MaterialManager::TextureLoader
    {
    public :
        DefaultTextureLoader(RenderContext &ctx) NOTHROWS;
    public :
        TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> &value, const char *uri) NOTHROWS override;
    private :
        RenderContext &ctx;
        AsyncBitmapLoader2 *loader;
    };

    void glReleaseGLMaterialTexture(void *opaque) NOTHROWS
    {
        auto *arg = static_cast<GLMaterial *>(opaque);
        if (arg->isLoading())
            return; // texture was never loaded, return

        GLTexture2 *tex = arg->getTexture();
        if (tex)
            tex->release();
    }
}

MaterialManager::TextureLoader::~TextureLoader() NOTHROWS 
{ }

MaterialManager::MaterialManager(RenderContext &ctx_) NOTHROWS :
    ctx(ctx_),
    parent(nullptr),
    loader(new DefaultTextureLoader(ctx_), Memory_deleter_const<MaterialManager::TextureLoader, DefaultTextureLoader>) 
{ }

MaterialManager::MaterialManager(TAK::Engine::Core::RenderContext &ctx_, TextureLoaderPtr &&loader_) NOTHROWS :
    ctx(ctx_),
    parent(nullptr),
    loader(std::move(loader_)) 
{ }

MaterialManager::MaterialManager(TAK::Engine::Core::RenderContext& ctx_, TextureLoaderPtr&& loader_, MaterialManager &parent) NOTHROWS :
    ctx(ctx_),
    parent(&parent),
    loader(std::move(loader_))
{ }
                
TAKErr MaterialManager::load(GLMaterial **value, const TAK::Engine::Model::Material &m, const unsigned int hints) NOTHROWS
{
    const char *textureUri = m.textureUri;
    if (!textureUri) {
        *value = new GLMaterial(m);
        return TE_Ok;
    }

    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<std::string, std::pair<std::size_t, std::unique_ptr<GLMaterial>>>::iterator entry;
    entry = textures.find(textureUri);
    if (entry == textures.end()) {
        TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> pendingTex;
        code = loader->load(pendingTex, textureUri);

        // unsuppoted, try parent
        if (code == TE_Unsupported && this->parent)
            return this->parent->load(value, m, hints);

        TE_CHECKRETURN_CODE(code);
        textures.insert(std::make_pair<std::string, std::pair<std::size_t, std::unique_ptr<GLMaterial>>>(textureUri, std::make_pair<std::size_t, std::unique_ptr<GLMaterial>>(0u, std::unique_ptr<GLMaterial>(new GLMaterial(m, pendingTex, hints)))));
        entry = textures.find(textureUri);
    }
    // bump the reference count
    entry->second.first++;

    *value = entry->second.second.get();
    return code;
}
void MaterialManager::unload(const GLMaterial *m)
{
    const char *textureUri = m->getSubject().textureUri;
    if (!textureUri) {
        delete m;
        return;
    }

    std::unique_ptr<GLMaterial> toDestruct;

    do {
        TAKErr code(TE_Ok);
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKBREAK_CODE(code);

        std::map<std::string, std::pair<std::size_t, std::unique_ptr<GLMaterial>>>::iterator entry;
        entry = textures.find(textureUri);
        if (entry == textures.end()) {
            Logger_log(TELL_Warning, "Invalid GLMaterial for this MaterialManager");
            break;
        }

        // decrement the reference count
        entry->second.first--;

        // all references are unloaded, destruct
        if (!entry->second.first) {
            toDestruct = std::move(entry->second.second);
            textures.erase(entry);
        }
    } while (false);

    if (!toDestruct.get())
        return;

    if (ctx.isRenderThread())
        glReleaseGLMaterialTexture(toDestruct.get());
    else
        ctx.queueEvent(glReleaseGLMaterialTexture, std::unique_ptr<void, void(*)(const void*)>(toDestruct.release(), Memory_void_deleter_const<GLMaterial>));
}

namespace
{
    class AsyncBitmapLoader2AsyncResult : public AsyncResult<std::shared_ptr<Bitmap2>> {
    public:
        ~AsyncBitmapLoader2AsyncResult() NOTHROWS override;
        AsyncBitmapLoader2::Task task;
    protected:
        TAKErr onSignalWork(MonitorLockPtr& lockPtr) NOTHROWS override;
    };

    AsyncBitmapLoader2AsyncResult::~AsyncBitmapLoader2AsyncResult() NOTHROWS
    { }

    TAKErr AsyncBitmapLoader2AsyncResult::onSignalWork(MonitorLockPtr& lockPtr) NOTHROWS {

        TAKErr code = TE_Ok;
        try {
            code = this->setValue(lockPtr, this->task->getFuture().get());
        }
        catch (...) {
            code = TE_Err;
        }

        TE_CHECKRETURN_CODE(code);
        this->task = nullptr;

        return code;
    }

    TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> wrapBitmapLoadTask(AsyncBitmapLoader2::Task task) {
        auto wrapper = std::make_shared<AsyncBitmapLoader2AsyncResult>();
        wrapper->task = task;
        SharedWorkerPtr worker = GeneralWorkers_single();
        worker->scheduleWork(wrapper);
        return TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>>(wrapper, worker);
    }

    DefaultTextureLoader::DefaultTextureLoader(RenderContext &ctx_) NOTHROWS :
        ctx(ctx_),
        loader(nullptr)
    {
        GLMapRenderGlobals_getBitmapLoader(&loader, ctx);
    }

        
    TAKErr DefaultTextureLoader::load(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> &value, const char *uri) NOTHROWS
    {
        
        
        if (!loader)
            return TE_IllegalState;

        std::string urix;
        if((strstr(uri, ".zip") || strstr(uri, ".kmz")) && strncmp(uri, "zip://", 6)) {
            const std::size_t len = strlen(uri);

            StringBuilder sb;
            sb << "zip://";
            for (std::size_t i = 0u; i < len; i++) {
                sb << uri[i];
                if (i > 3u && uri[i - 3] == '.' && uri[i - 2] == 'z' && uri[i - 1] == 'i' && uri[i] == 'p') {
                    sb << '!';
                }
                else
                if (i > 3u && uri[i - 3] == '.' && uri[i - 2] == 'k' && uri[i - 1] == 'm' && uri[i] == 'z') {
                    sb << '!';
                }
            }

            urix = std::string(sb.c_str());
            uri = urix.c_str();
        } else if(!strstr(uri, ":/")) {
            StringBuilder sb;
            sb << "file://" << uri;

            urix = std::string(sb.c_str());
            uri = urix.c_str();
        }

        AsyncBitmapLoader2::Task task;
        TAKErr code = this->loader->loadBitmapUri(task, uri);
        TE_CHECKRETURN_CODE(code);

        value = wrapBitmapLoadTask(task);
        return code;
    }
}
