#include "renderer/core/GLAsynchronousMapRenderable3.h"

#include <sstream>

#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

GLAsynchronousMapRenderable3::GLAsynchronousMapRenderable3() NOTHROWS :
    prepared_state_(nullptr, nullptr),
    target_state_(nullptr, nullptr),
    thread_(nullptr, nullptr),
    initialized_(false),
    servicing_request_(false),
    invalid_(false),
    cancelled_(false),
    monitor_(TEMT_Recursive)
{}

GLAsynchronousMapRenderable3::~GLAsynchronousMapRenderable3() NOTHROWS
{
    release();
}


TAKErr GLAsynchronousMapRenderable3::newViewStateInstance(ViewStatePtr &result) NOTHROWS
{
    result = ViewStatePtr(new ViewState(), Memory_deleter_const<ViewState>);
    return TE_Ok;
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
        (prepared_state_->drawVersion != target_state_->drawVersion);
}

bool GLAsynchronousMapRenderable3::shouldCancel() NOTHROWS
{
    return false;
}

void GLAsynchronousMapRenderable3::initImpl(const GLMapView2 &view) NOTHROWS
{}

void GLAsynchronousMapRenderable3::invalidateNoSync() NOTHROWS
{
    invalid_ = true;
}

void GLAsynchronousMapRenderable3::invalidate() NOTHROWS
{
    Monitor::Lock lock(monitor_);
    invalidateNoSync();
}

void GLAsynchronousMapRenderable3::draw(const GLMapView2 &view, int renderPass) NOTHROWS
{
    if (!(renderPass&getRenderPass()))
        return;

    TAKErr code(TE_Ok);

    Monitor::Lock lock(monitor_);
    code = lock.status;

    if (!initialized_) {
        // XXX - check error codes !!!
        code = newViewStateInstance(prepared_state_);
        code = newViewStateInstance(target_state_);

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

    if(target_state_->drawVersion != view.drawVersion)
        target_state_->set(view);
    if (shouldQuery()) {
        if (!servicing_request_) {
            lock.signal();
        } else if (shouldCancel()){
            cancelled_ = true;
            lock.signal();
        }
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

        prepared_state_.reset();
        target_state_.reset();

        {
            WriteLock wlock(renderables_mutex_);
            releaseImpl();
        }

        initialized_ = false;
        // release lock
    }
}

TAKErr GLAsynchronousMapRenderable3::releaseImpl() NOTHROWS
{
    return TE_Ok;
}

/*****************************************************************************/
// View State

GLAsynchronousMapRenderable3::ViewState::ViewState() :
    drawMapScale(NAN),
    drawMapResolution(NAN),
    drawLat(NAN),
    drawLng(NAN),
    drawRotation(NAN),
    animationFactor(NAN),
    drawVersion(-1),
    drawSrid(-1),
    westBound(NAN),
    southBound(NAN),
    northBound(NAN),
    eastBound(NAN),
    upperLeft(NAN, NAN),
    upperRight(NAN, NAN),
    lowerRight(NAN, NAN),
    lowerLeft(NAN, NAN),
    targetMapScale(NAN),
    targetLat(NAN),
    targetLng(NAN),
    targetRotation(NAN),
    _left(-1),
    _right(-1),
    _top(-1),
    _bottom(-1),
    focusx(NAN),
    focusy(NAN),
    settled(false),
    crossesIDL(false)
{}

GLAsynchronousMapRenderable3::ViewState::~ViewState()
{}


void GLAsynchronousMapRenderable3::ViewState::set(const GLMapView2 &view)
{
    drawMapScale = view.drawMapScale;
    drawMapResolution = view.drawMapResolution;
    drawLat = view.drawLat;
    drawLng = view.drawLng;
    drawRotation = view.drawRotation;
    animationFactor = view.animationFactor;
    drawVersion = view.drawVersion;
    drawSrid = view.drawSrid;
    westBound = view.westBound;
    southBound = view.southBound;
    northBound = view.northBound;
    eastBound = view.eastBound;
    upperLeft = view.upperLeft;
    upperRight = view.upperRight;
    lowerRight = view.lowerRight;
    lowerLeft = view.lowerLeft;
    _left = view.left;
    _right = view.right;
    _top = view.top;
    _bottom = view.bottom;
    focusx = view.focusx;
    focusy = view.focusy;
    settled = view.settled;
    crossesIDL = view.crossesIDL;
}

void GLAsynchronousMapRenderable3::ViewState::copy(const ViewState &view)
{
    drawMapScale = view.drawMapScale;
    drawMapResolution = view.drawMapResolution;
    drawLat = view.drawLat;
    drawLng = view.drawLng;
    drawRotation = view.drawRotation;
    animationFactor = view.animationFactor;
    drawVersion = view.drawVersion;
    drawSrid = view.drawSrid;
    westBound = view.westBound;
    southBound = view.southBound;
    northBound = view.northBound;
    eastBound = view.eastBound;
    upperLeft = view.upperLeft;
    upperRight = view.upperRight;
    lowerRight = view.lowerRight;
    lowerLeft = view.lowerLeft;
    _left = view._left;
    _right = view._right;
    _top = view._top;
    _bottom = view._bottom;
    focusx = view.focusx;
    focusy = view.focusy;
    settled = view.settled;
    crossesIDL = view.crossesIDL;
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
        ViewStatePtr queryState(nullptr, nullptr);
        code = owner_.newViewStateInstance(queryState);
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
                        owner_.prepared_state_->copy(*queryState);
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
                queryState->copy(*owner_.target_state_);
                owner_.invalid_ = false;
                owner_.servicing_request_ = true;
                owner_.cancelled_ = false;
            }

            code = owner_.query(*pendingData, *queryState);
        }
    }
    catch (...) { }
    owner_.servicing_request_ = false;
}

GLAsynchronousMapRenderable3::QueryContext::~QueryContext() { }

