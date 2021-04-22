#include "renderer/core/GLAsynchronousMapRenderable3.h"

#include <sstream>

#include "feature/SpatialCalculator2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

GLAsynchronousMapRenderable3::GLAsynchronousMapRenderable3() NOTHROWS :
    thread_(nullptr, nullptr),
    initialized_(false),
    servicing_request_(false),
    invalid_(false),
    cancelled_(false),
    monitor_(TEMT_Recursive),
    surface_ctrl_(nullptr)
{
    prepared_state_.drawVersion = -1;
    target_state_.drawVersion = -1;
}

GLAsynchronousMapRenderable3::~GLAsynchronousMapRenderable3() NOTHROWS
{
    release();
}

int GLAsynchronousMapRenderable3::getBackgroundThreadPriority() const NOTHROWS
{
    return 0;
}

TAKErr GLAsynchronousMapRenderable3::getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS
{
    std::string s = "GLAsyncMapRenderableThread-";
    s += std::to_string((uintptr_t)this);

    value = s.c_str();
    return TE_Ok;
}

bool GLAsynchronousMapRenderable3::shouldQuery() NOTHROWS
{
    return invalid_ ||
        (prepared_state_.drawVersion != target_state_.drawVersion);
}

bool GLAsynchronousMapRenderable3::shouldCancel() NOTHROWS
{
    return false;
}

void GLAsynchronousMapRenderable3::initImpl(const GLGlobeBase &view) NOTHROWS
{}

void GLAsynchronousMapRenderable3::invalidateNoSync() NOTHROWS
{
    invalid_ = true;
    if (surface_ctrl_) {
        surface_ctrl_->markDirty();
    }
}

void GLAsynchronousMapRenderable3::invalidate() NOTHROWS
{
    Monitor::Lock lock(monitor_);
    invalidateNoSync();
}

void GLAsynchronousMapRenderable3::draw(const GLGlobeBase &view, int renderPass) NOTHROWS
{
    if (!(renderPass&getRenderPass()))
        return;

    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor_);
    code = lock.status;

    if (!initialized_) {
        surface_ctrl_ = view.getSurfaceRendererControl();

        // force refresh
        prepared_state_.drawVersion = ~view.renderPasses[0u].drawVersion;
        target_state_.drawVersion = ~view.renderPasses[0u].drawVersion;
        invalid_ = true;

        background_worker_.reset(new WorkerThread(*this));

        ThreadCreateParams tparams;
        this->getBackgroundThreadName(tparams.name);
        // XXX - conversion from int to priority
        //tparams.priority = this->getBackgroundThreadPriority();

        Thread_start(thread_, WorkerThread::asyncRun, background_worker_.get(), tparams);

        initImpl(view);

        initialized_ = true;
        cancelled_ = false;
    }

    const bool hasNonSurface = !!(getRenderPass()&~(GLGlobeBase::Surface|GLGlobeBase::Surface2));
    const int passTest = hasNonSurface ? ~(GLGlobeBase::Surface|GLGlobeBase::Surface2) : getRenderPass();
        
    // if the target state has not already been computed for the pump and
    // it is a sprite pass if there is any sprite content or there is a pass
    // match and there is not any sprite content, update the target state
    if ((target_state_.drawVersion != view.renderPasses[0u].drawVersion) && (passTest & renderPass) != 0)
        target_state_ = view.renderPasses[0u];
    if (shouldQuery()) {
        if (!servicing_request_) {
            lock.signal();
        } else if (shouldCancel()){
            cancelled_ = true;
            lock.signal();
        }
    }

    // if surface only pass, ignore outside of current AOI
    if ((renderPass & ~(GLGlobeBase::Surface | GLGlobeBase::Surface2)) == 0) {
        Envelope2 aois[2];
        std::size_t numAois = 0u;
        if (prepared_state_.crossesIDL) {
            aois[numAois++] = Envelope2(prepared_state_.westBound, prepared_state_.southBound, 0.0, 180.0, prepared_state_.northBound, 0.0);
            aois[numAois++] = Envelope2(-180.0, prepared_state_.southBound, 0.0, prepared_state_.eastBound, prepared_state_.northBound, 0.0);
        } else {
            aois[numAois++] = Envelope2(prepared_state_.westBound, prepared_state_.southBound, 0.0, prepared_state_.eastBound, prepared_state_.northBound, 0.0);
        }
        bool isect = false;
        for (std::size_t i = 0; i < numAois; i++)
            isect |= SpatialCalculator_intersects(Envelope2(view.renderPass->westBound, view.renderPass->southBound, 0.0, view.renderPass->eastBound, view.renderPass->northBound, 0.0), aois[i]);
        if (!isect)
            return;
    }

    // lock the renderables for read
    ReadLock rlock(renderables_mutex_);
    code = lock.status;
    if (code != TE_Ok)
        return;

    Collection<GLMapRenderable2 *>::IteratorPtr iter(nullptr, nullptr);
    code = this->getRenderables(iter);
    if (code == TE_Done)
        return;

    do {
        GLMapRenderable2 *renderable;
        code = iter->get(renderable);
        TE_CHECKBREAK_CODE(code);
        renderable->draw(view, renderPass);
        code = iter->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    iter.reset();
}

void GLAsynchronousMapRenderable3::release() NOTHROWS
{
    std::unique_ptr<WorkerThread> deadWorker(background_worker_.release());
    // acquire lock
    {
        Monitor::Lock lock(monitor_);
        if (!initialized_)
            return;

        background_worker_.reset();
        lock.signal();
        // release lock
    }


    thread_->join();
    thread_.reset();
    deadWorker.reset();

    // acquire lock
    {
        Monitor::Lock lock(monitor_);

        {
            WriteLock wlock(renderables_mutex_);
            releaseImpl();
        }

        initialized_ = false;
        // release lock
    }

    if (surface_ctrl_) {
        surface_ctrl_->markDirty();
    }
}

TAKErr GLAsynchronousMapRenderable3::releaseImpl() NOTHROWS
{
    return TE_Ok;
}

/*****************************************************************************/
// Worker Thread

GLAsynchronousMapRenderable3::WorkerThread::WorkerThread(GLAsynchronousMapRenderable3 &owner) :
    owner_(owner),
    pending_data_(nullptr)
{}

void* GLAsynchronousMapRenderable3::WorkerThread::asyncRun(void* self)
{
    if (self)
    {
        auto* worker(static_cast<WorkerThread*> (self));

        worker->asyncRunImpl();
    }
    return nullptr;
}

void GLAsynchronousMapRenderable3::WorkerThread::asyncRunImpl() {
    TAKErr code;
    QueryContextPtr pendingData(nullptr, nullptr);
    try {
        code = owner_.createQueryContext(pendingData);
        GLGlobeBase::State queryState;
        while (true) {
            {
                Monitor::Lock lock(owner_.monitor_);
                if (owner_.background_worker_.get() != this) {
                    break;
                }

                // update release/renderable collections
                {
                    WriteLock wlock(owner_.renderables_mutex_);
                    code = wlock.status;
                    TE_CHECKBREAK_CODE(code);
                    if (owner_.servicing_request_ && owner_.updateRenderableLists(*pendingData) == TE_Ok) {
                        owner_.prepared_state_ = queryState;
                        if (owner_.surface_ctrl_ && (owner_.getRenderPass()&(GLGlobeBase::Surface|GLGlobeBase::Surface2))) {
                            if (owner_.prepared_state_.crossesIDL) {
                                owner_.surface_ctrl_->markDirty(Envelope2(owner_.prepared_state_.westBound, owner_.prepared_state_.southBound, 0.0, 180.0, owner_.prepared_state_.northBound, 0.0), false);
                                owner_.surface_ctrl_->markDirty(Envelope2(-180.0, owner_.prepared_state_.southBound, 0.0, owner_.prepared_state_.eastBound, owner_.prepared_state_.northBound, 0.0), false);
                            } else {
                                owner_.surface_ctrl_->markDirty(Envelope2(owner_.prepared_state_.westBound, owner_.prepared_state_.southBound, 0.0, owner_.prepared_state_.eastBound, owner_.prepared_state_.northBound, 0.0), false);
                    }
                }
                    }
                }

                owner_.resetQueryContext(*pendingData);
                owner_.servicing_request_ = false;

                // check the state and wait if appropriate
                if (!owner_.shouldQuery()) {
                    lock.wait(); // XXX - exceptions previously ignored, need to handle any codes???
                    continue;
                }

                // copy the target state to query outside of the
                // synchronized block
                queryState = owner_.target_state_;
                owner_.invalid_ = false;
                owner_.servicing_request_ = true;
                owner_.cancelled_ = false;
            }

            code = owner_.query(*pendingData, queryState);
        }
    }
    catch (...) { }
    owner_.servicing_request_ = false;
}

GLAsynchronousMapRenderable3::QueryContext::~QueryContext() { }

