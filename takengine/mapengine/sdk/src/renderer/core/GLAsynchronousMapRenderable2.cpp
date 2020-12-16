#include "renderer/core/GLAsynchronousMapRenderable2.h"

#include "port/StringBuilder.h"
#include "thread/Lock.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::renderer::map;


GLAsynchronousMapRenderable2::GLAsynchronousMapRenderable2() NOTHROWS :
    preparedState(nullptr, nullptr),
    targetState(nullptr, nullptr),
    thread(nullptr, nullptr),
    initialized(false),
    servicingRequest(false),
    invalid(false),
    mutex()
{}

GLAsynchronousMapRenderable2::~GLAsynchronousMapRenderable2() NOTHROWS
{
    release();
}


TAKErr GLAsynchronousMapRenderable2::newViewStateInstance(ViewStatePtr &result) NOTHROWS
{
    result = ViewStatePtr(new ViewState(), Memory_deleter_const<ViewState>);
    return TE_Ok;
}

int GLAsynchronousMapRenderable2::getBackgroundThreadPriority() const NOTHROWS
{
    return 0;
}

TAKErr GLAsynchronousMapRenderable2::setBackgroundThreadName(WorkerThread &worker) NOTHROWS
{
    StringBuilder ss;
    ss << "GLAsyncMapRenderableThread-";
    ss << (uintptr_t)this;

    worker.setName(ss.c_str());
    return TE_Ok;
}

bool GLAsynchronousMapRenderable2::shouldQuery() NOTHROWS
{
    return invalid ||
        (preparedState->drawVersion != targetState->drawVersion);
}

void GLAsynchronousMapRenderable2::initImpl(const GLMapView *view) NOTHROWS
{}

void GLAsynchronousMapRenderable2::invalidateNoSync() NOTHROWS
{
    invalid = true;
}

void GLAsynchronousMapRenderable2::invalidate() NOTHROWS
{
    Lock lock(mutex);
    invalidateNoSync();
}

void GLAsynchronousMapRenderable2::draw(const GLMapView *view)
{
    TAKErr code;

    Lock lock(mutex);
    code = lock.status;

    if (!initialized) {
        // XXX - check error codes !!!
        code = newViewStateInstance(preparedState);
        code = newViewStateInstance(targetState);

        backgroundWorker.reset(new WorkerThread(*this));
        code = this->setBackgroundThreadName(*backgroundWorker);

        ThreadCreateParams tparams;
        tparams.name = backgroundWorker->name_;
        // XXX - conversion from int to priority
        //tparams.priority = this->getBackgroundThreadPriority();

        Thread_start(thread, WorkerThread::asyncRun, backgroundWorker.get(), tparams);

        initImpl(view);

        initialized = true;
    }

    targetState->set(*view);
    if (!servicingRequest && shouldQuery())
        cv.signal(lock);

    Collection<GLMapRenderable *>::IteratorPtr iter(nullptr, nullptr);
    code = this->getRenderables(iter);
    if (code == TE_Done)
        return;

    do {
        GLMapRenderable *renderable;
        code = iter->get(renderable);
        TE_CHECKBREAK_CODE(code);
        renderable->draw(view);
        code = iter->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    iter.reset();
}

void GLAsynchronousMapRenderable2::release()
{
    std::unique_ptr<WorkerThread> deadWorker(backgroundWorker.release());
    {
        Lock lock(mutex);
        if (!initialized)
            return;

        backgroundWorker.reset();
        cv.signal(lock);
        //                    notify();
    }

    thread->join();
    thread.reset();
    deadWorker.reset();

    {
        Lock lock(mutex);

        preparedState.reset();
        targetState.reset();

        releaseImpl();
        initialized = false;
    }
}

TAKErr GLAsynchronousMapRenderable2::releaseImpl() NOTHROWS
{
    return TE_Ok;
}

/*****************************************************************************/
// View State

GLAsynchronousMapRenderable2::ViewState::ViewState() :
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
    settled(false)
{}

GLAsynchronousMapRenderable2::ViewState::~ViewState()
{}


void GLAsynchronousMapRenderable2::ViewState::set(const GLMapView &view)
{
    drawMapScale = view.drawMapScale;
    drawMapResolution = view.getView()->getMapResolution(drawMapScale);
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
    upperLeft.set(view.upperLeft.latitude, view.upperLeft.longitude);
    upperRight.set(view.upperRight.latitude, view.upperRight.longitude);
    lowerRight.set(view.lowerRight.latitude, view.lowerRight.longitude);
    lowerLeft.set(view.lowerLeft.latitude, view.lowerLeft.longitude);
    _left = view.left;
    _right = view.right;
    _top = view.top;
    _bottom = view.bottom;
    focusx = view.focusx;
    focusy = view.focusy;
    settled = view.settled;
}

void GLAsynchronousMapRenderable2::ViewState::copy(const ViewState &view)
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
    upperLeft.set(view.upperLeft.latitude, view.upperLeft.longitude);
    upperRight.set(view.upperRight.latitude, view.upperRight.longitude);
    lowerRight.set(view.lowerRight.latitude, view.lowerRight.longitude);
    lowerLeft.set(view.lowerLeft.latitude, view.lowerLeft.longitude);
    _left = view._left;
    _right = view._right;
    _top = view._top;
    _bottom = view._bottom;
    focusx = view.focusx;
    focusy = view.focusy;
    settled = view.settled;
}

/*****************************************************************************/
// Worker Thread

GLAsynchronousMapRenderable2::WorkerThread::WorkerThread(GLAsynchronousMapRenderable2 &owner) :
    owner_(owner),
    pending_data_(nullptr),
    name_(nullptr)
{}

void GLAsynchronousMapRenderable2::WorkerThread::setName(const char *n)
{
    name_ = n;
}

void* GLAsynchronousMapRenderable2::WorkerThread::asyncRun(void* self)
{
    if (self)
    {
        auto* worker(static_cast<WorkerThread*> (self));

        worker->asyncRunImpl();
    }
    return nullptr;
}

void GLAsynchronousMapRenderable2::WorkerThread::asyncRunImpl() {
    TAKErr code;
    QueryContextPtr pendingData(nullptr, nullptr);
    try {
        code = owner_.createQueryContext(pendingData);
        ViewStatePtr queryState(nullptr, nullptr);
        code = owner_.newViewStateInstance(queryState);
        while (true) {
            {
                Lock lock(owner_.mutex);
                if (owner_.backgroundWorker.get() != this) {
                    break;
                }

                // update release/renderable collections
                if (owner_.servicingRequest && owner_.updateRenderableLists(*pendingData) == TE_Ok) {
                    owner_.preparedState->copy(*queryState);
                }
                owner_.resetQueryContext(*pendingData);
                owner_.servicingRequest = false;

                // check the state and wait if appropriate
                if (!owner_.shouldQuery()) {
                    owner_.cv.wait(lock); // XXX - exceptions previously ignored, need to handle any codes???
                    continue;
                }

                // copy the target state to query outside of the
                // synchronized block
                queryState->copy(*owner_.targetState);
                owner_.invalid = false;
                owner_.servicingRequest = true;
            }

            owner_.query(*pendingData, *queryState);
        }
    }
    catch (...) { }
    owner_.servicingRequest = false;
}

GLAsynchronousMapRenderable2::QueryContext::~QueryContext() { }

