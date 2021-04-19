
#ifndef TAK_ENGINE_RENDERER_CORE_GLCONTENT_H
#define TAK_ENGINE_RENDERER_CORE_GLCONTENT_H

#include "port/Platform.h"
#include "renderer/core/GLMapRenderable2.h"
#include "util/Tasking.h"
#include "core/RenderContext.h"
#include <atomic>

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class GLContentHolder;

                /**
                 * Context in which content may be loading. All loading content is tracked and automatically
                 * canceled during destruction of GLContentContext. As content finishes loading, ownership is
                 * transfered to the GLContentHolder that initiated the load with the context.
                 */
                class GLContentContext {
                public:
                    // no copying or moving
                    GLContentContext(const GLContentContext&) = delete;
                    GLContentContext(GLContentContext&&) = delete;
                    void operator=(const GLContentContext&) = delete;
                    void operator=(GLContentContext&&) = delete;

                    /**
                     * Create a content context designated with a worker (in which to load) and a
                     * function pointer to the loading task.
                     *
                     * @param ctx the render context
                     * @param loadWorker the worker to delegate loading task function to
                     * @param loadFunc the loading task function
                     */
                    explicit GLContentContext(
                        TAK::Engine::Core::RenderContext& ctx,
                        TAK::Engine::Util::SharedWorkerPtr loadWorker,
                        TAK::Engine::Util::TAKErr(*loadFunc)(
                            TAK::Engine::Renderer::Core::GLMapRenderable2Ptr&, 
                            TAK::Engine::Core::RenderContext&,
                            const TAK::Engine::Port::String&));

                    class Loader {
                    public:
                        virtual ~Loader() NOTHROWS {}
                        virtual TAK::Engine::Util::TAKErr load(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr&,
                            TAK::Engine::Core::RenderContext&,
                            const TAK::Engine::Port::String&) = 0;
                    };

                    explicit GLContentContext(
                        TAK::Engine::Core::RenderContext& ctx,
                        TAK::Engine::Util::SharedWorkerPtr loadWorker,
                        std::shared_ptr<Loader> loader);

                    /**
                     * Cancel all loading content in the context.
                     */
                    void cancelAll() NOTHROWS;

                    inline TAK::Engine::Core::RenderContext& getRenderContext() const NOTHROWS {
                        return ctx_;
                    }

                    inline std::shared_ptr<Loader> getLoader() const NOTHROWS {
                        return loader_;
                    }

                private:
                    friend class GLContentHolder;

                    class GLC3DTPendingContent : public TAK::Engine::Renderer::Core::GLMapRenderable2 {
                    public:
                        virtual ~GLC3DTPendingContent() NOTHROWS;

                        virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2& view, const int renderPass) NOTHROWS;
                        virtual void release() NOTHROWS;
                        virtual int getRenderPass() NOTHROWS;
                        virtual void start() NOTHROWS;
                        virtual void stop() NOTHROWS;
                        TAK::Engine::Util::Future<TAK::Engine::Renderer::Core::GLMapRenderable2*> future_;
                    };

                    struct HolderImpl_ {
                        HolderImpl_() : content_(nullptr, nullptr), state_(0) {}
                        TAK::Engine::Renderer::Core::GLMapRenderable2Ptr content_;
                        std::atomic_int state_;
                    };

                    struct LoadTaskArgs_ {
                        TAK::Engine::Core::RenderContext* ctx;

                        TAK::Engine::Util::TAKErr(*load_func)(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr&, 
                            TAK::Engine::Core::RenderContext& ctx, const 
                            TAK::Engine::Port::String&);

                        TAK::Engine::Port::String URI;
                    };

                    static Util::TAKErr loadTask_(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& content, 
                        const std::shared_ptr<Loader>& loader, 
                        TAK::Engine::Core::RenderContext* ctx,
                        const TAK::Engine::Port::String& URI) NOTHROWS;

                    TAK::Engine::Util::Future<TAK::Engine::Renderer::Core::GLMapRenderable2*> loadImpl_(const std::shared_ptr<GLContentContext::HolderImpl_>& holder, 
                        const char* URI) NOTHROWS;

                    static Util::TAKErr updateTask_(TAK::Engine::Renderer::Core::GLMapRenderable2*&, TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& content, const std::shared_ptr<HolderImpl_>& holder) NOTHROWS;

                    struct Node_ {
                        Node_* next;
                        Node_* prev;
                        void selfRemove();
                    };

                    struct PendingNode_ : Node_ {
                        PendingNode_() : pending_content_() {}
                        GLC3DTPendingContent pending_content_;
                        static PendingNode_* fromPendingContent(GLC3DTPendingContent* content) NOTHROWS;
                    };

                private:
                    TAK::Engine::Core::RenderContext& ctx_;
                    TAK::Engine::Util::SharedWorkerPtr load_worker_;
                    std::shared_ptr<Loader> loader_;
                    Node_ pending_list_;
                };

                /**
                 * Holder of loaded content. Loading content ownership lies with GLContentContext, but is then
                 * transfered to a GLContentHolder once loaded. A content holder may be copied or moved.
                 */
                class GLContentHolder {
                public:
                    enum LoadState {
                        EMPTY,
                        LOADING,
                        LOADED,
                        ERROR
                    };

                    /**
                     *
                     */
                    GLContentHolder();

                    /**
                     * Load content in a given context to be transfered to this holder when loaded.
                     * 
                     * NOTE: Only call on the GLThread
                     *
                     * @param context the context that owns the loading content
                     * @param URI the uri of the content
                     * 
                     * @return a Future that may be canceled OR used to schedule a callback operation
                     *         when the load finishes or errors. When GeneralWorkers_immediate() is
                     *         used to schedule a callback, callback invokation will be the GLThread 
                     *         immediately after the GLContentHolder has updated.
                     */
                    TAK::Engine::Util::Future<TAK::Engine::Renderer::Core::GLMapRenderable2*> load(GLContentContext& context, const char* URI);

                    /**
                     * Unload any current loaded content.
                     * 
                     * NOTE: Only call on the GLThread
                     */
                    void unload();

                    /**
                     * Get the current renderable that represents the content. This may be proxy content, the actual loaded
                     * content or nullptr (if empty).
                     * 
                     * NOTE: Only call on the GLThread
                     */
                    TAK::Engine::Renderer::Core::GLMapRenderable2* getMapRenderable() const NOTHROWS;

                    /**
                     * Get the state of the content
                     * 
                     * NOTE: Safe to call on any thread
                     */
                    LoadState getLoadState() const NOTHROWS;
                private:
                    // only the GL thread will access HolderImpl_::content_, or set HolderImpl_::state_, but will be 
                    // passed around to workers and if the holder goes away before the worker, data will be safely deleted
                    std::shared_ptr<GLContentContext::HolderImpl_> impl_;
                };
            }
        }
    }
}

#endif