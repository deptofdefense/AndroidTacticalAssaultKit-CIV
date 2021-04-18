
#include "renderer/core/GLContent.h"
#include "renderer/GLWorkers.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

//
// GLC3DTPendingContent
//

GLContentContext::GLC3DTPendingContent::~GLC3DTPendingContent() NOTHROWS {
    this->future_.cancel(); // does nothing if already detached
}

void GLContentContext::GLC3DTPendingContent::draw(const TAK::Engine::Renderer::Core::GLMapView2& view, const int renderPass) NOTHROWS {

}

void GLContentContext::GLC3DTPendingContent::release() NOTHROWS {
    // triggers a self delete
    GLContentContext::PendingNode_* node = PendingNode_::fromPendingContent(this);
    node->selfRemove();
    delete node;
}

int GLContentContext::GLC3DTPendingContent::getRenderPass() NOTHROWS {
    return 0;
}

void GLContentContext::GLC3DTPendingContent::start() NOTHROWS {

}

void GLContentContext::GLC3DTPendingContent::stop() NOTHROWS {

}

//
// GLC3DTContentContext
//

namespace {
    class FuncLoader : public GLContentContext::Loader {
    public:
        FuncLoader(TAKErr(*loadFunc)(GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const String&)) NOTHROWS
            : load_func_(loadFunc) {}
        ~FuncLoader() NOTHROWS {}
        virtual TAKErr load(GLMapRenderable2Ptr&result, TAK::Engine::Core::RenderContext& ctx, const String& URI) NOTHROWS {
            return load_func_(result, ctx, URI);
        }
        TAKErr(*load_func_)(GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const String&);
    };
}

GLContentContext::GLContentContext(
    TAK::Engine::Core::RenderContext& ctx,
    TAK::Engine::Util::SharedWorkerPtr loadWorker,
    TAKErr(*loadFunc)(GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const String&))
    : GLContentContext(ctx, loadWorker, std::make_shared<FuncLoader>(loadFunc))
{}

GLContentContext::GLContentContext(
    TAK::Engine::Core::RenderContext& ctx,
    TAK::Engine::Util::SharedWorkerPtr loadWorker,
    std::shared_ptr<Loader> loader)
    : ctx_(ctx),
    load_worker_(loadWorker),
    loader_(loader) {

    // avoid if checks by making it circular
    pending_list_.next = pending_list_.prev = &pending_list_;
}

Future<Core::GLMapRenderable2*> GLContentContext::loadImpl_(const std::shared_ptr<HolderImpl_>& holder,
    const char* URI) NOTHROWS {

    // we are in GLThread

    PendingNode_* node = new PendingNode_();
    holder->content_ = GLMapRenderable2Ptr(&node->pending_content_, Memory_leaker_const<GLMapRenderable2>);
    holder->state_.store(GLContentHolder::LOADING, std::memory_order_release);

    // add to list
    node->next = &pending_list_; // end
    node->prev = pending_list_.prev; // tail
    node->next->prev = node;
    node->prev->next = node;

    // schedule call on load_worker_ to loadTask_
    // if success pass result to updateTask_ on the GL thread
    node->pending_content_.future_ = Task_begin(load_worker_, loadTask_, loader_, &ctx_, String(URI))
        .thenOn(GLWorkers_glThread(), updateTask_, holder);

    return node->pending_content_.future_;
}

void GLContentContext::Node_::selfRemove() {
    // no need to check because of circular trick
    next->prev = prev;
    prev->next = next;
}

GLContentContext::PendingNode_* GLContentContext::PendingNode_::fromPendingContent(GLC3DTPendingContent* content) NOTHROWS {
    // we know address is inside a PendingNode_, so offsetof will work
    uint8_t* addr = reinterpret_cast<uint8_t*>(content);
    return reinterpret_cast<PendingNode_*>(addr - offsetof(PendingNode_, pending_content_));
}

TAKErr GLContentContext::loadTask_(
    TAK::Engine::Renderer::Core::GLMapRenderable2Ptr& content, 
    const std::shared_ptr<Loader>& loader,
    TAK::Engine::Core::RenderContext* ctx,
    const TAK::Engine::Port::String& URI) NOTHROWS {
    return loader->load(content, *ctx, URI);
}

TAKErr GLContentContext::updateTask_(Core::GLMapRenderable2*& output, GLMapRenderable2Ptr& input, const std::shared_ptr<HolderImpl_>& holder) NOTHROWS {

    // in the GLThread

    GLC3DTPendingContent* pending = static_cast<GLC3DTPendingContent*>(holder->content_.release());

    // prevent cancel for doing anything when PendingContent releases
    pending->future_.detach();

    // remove node
    PendingNode_* node = PendingNode_::fromPendingContent(pending);
    node->selfRemove();
    delete node;

    // update the holder
    holder->content_ = std::move(input);
    holder->state_.store(GLContentHolder::LOADED, std::memory_order_release);

    output = holder->content_.get();

    return TE_Ok;
}

void GLContentContext::cancelAll() NOTHROWS {
    Node_* node = pending_list_.next; //  head
    while (node != &pending_list_) { // end
        PendingNode_* pending = static_cast<PendingNode_*>(node);

        // hold on to next...
        Node_* next = node->next;

        // ...because this will delete node
        pending->pending_content_.release();

        node = next;
    }
}

//
// GLC3DTContentHolder
//

GLContentHolder::GLContentHolder()
    : impl_(std::make_shared<GLContentContext::HolderImpl_>())
{}

GLMapRenderable2* GLContentHolder::getMapRenderable() const NOTHROWS {
    if (impl_)
        return impl_->content_.get(); // content_ is never nullptr with shared_
    return nullptr;
}

Future<GLMapRenderable2*> GLContentHolder::load(GLContentContext& context, const char* URI) {
    // causes release of pending or old
    return context.loadImpl_(impl_, URI);
}

void GLContentHolder::unload() {
    impl_->content_.release();
    impl_->state_.store(EMPTY, std::memory_order_release);
}

GLContentHolder::LoadState GLContentHolder::getLoadState() const NOTHROWS {
    return (LoadState)impl_->state_.load(std::memory_order_acquire);
}