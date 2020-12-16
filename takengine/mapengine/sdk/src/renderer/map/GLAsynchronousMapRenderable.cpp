#include "renderer/map/GLAsynchronousMapRenderable.h"

#include "port/Iterator.h"
#include "thread/Lock.h"
#include "util/Logging.h"

using namespace TAK::Engine::Thread;

using namespace atakmap::core;
using namespace atakmap::renderer::map;
using namespace atakmap::port;

GLAsynchronousMapRenderable::GLAsynchronousMapRenderable() :
    preparedState(nullptr),
    targetState(nullptr),
    thread(nullptr, nullptr),
    backgroundWorker(nullptr),
    initialized(false),
    servicingRequest(false),
    invalid(false),
    mutex()
{}

GLAsynchronousMapRenderable::~GLAsynchronousMapRenderable()
{
    release();
}


GLAsynchronousMapRenderable::ViewState  *GLAsynchronousMapRenderable::newViewStateInstance()
{
    return new ViewState();
}

void GLAsynchronousMapRenderable::releaseViewStateInstance(ViewState *vs)
{
    delete vs;
}

int GLAsynchronousMapRenderable::getBackgroundThreadPriority()
{
    return 0;
}

void GLAsynchronousMapRenderable::setBackgroundThreadName(WorkerThread *worker)
{
    std::ostringstream ss;
    ss << "GLAsyncMapRenderableThread-";
    ss << (intptr_t)this;

    worker->setName(ss.str().c_str());
}

bool GLAsynchronousMapRenderable::checkState()
{
    return invalid ||
        (preparedState->drawVersion != targetState->drawVersion);
}

void GLAsynchronousMapRenderable::initImpl(const GLMapView *view)
{}

void GLAsynchronousMapRenderable::invalidateNoSync()
{
    invalid = true;
}

void GLAsynchronousMapRenderable::invalidate()
{
    Lock lock(mutex);
    invalidateNoSync();
}

void GLAsynchronousMapRenderable::draw(const GLMapView *view)
{
    Lock lock(mutex);
    if (!initialized) {
        preparedState = newViewStateInstance();
        targetState = newViewStateInstance();

        backgroundWorker = new WorkerThread(this);
        this->setBackgroundThreadName(backgroundWorker);

        ThreadCreateParams tparams;
        tparams.name = backgroundWorker->name_;
        // XXX - conversion from int to priority
        //tparams.priority = this->getBackgroundThreadPriority();

        Thread_start(thread, WorkerThread::asyncRun, backgroundWorker, tparams);

        initImpl(view);

        initialized = true;
    }

    targetState->set(view);
    if (!servicingRequest && checkState())
        cv.signal(lock);

    Iterator<GLMapRenderable *> *iter = this->getRenderablesIterator();
    while(iter->hasNext())
        iter->next()->draw(view);
    this->releaseRenderablesIterator(iter);
}

void GLAsynchronousMapRenderable::release()
{
    WorkerThread *deadWorker = backgroundWorker;
    {
        Lock lock(mutex);
        if (!initialized)
            return;

        backgroundWorker = nullptr;
        cv.signal(lock);
        //                    notify();
    }

    thread->join();
    thread.reset();
    delete deadWorker;
    deadWorker = nullptr;

    {
        Lock lock(mutex);

        if (preparedState) {
            releaseViewStateInstance(preparedState);
            preparedState = nullptr;
        }
        if (targetState) {
            releaseViewStateInstance(targetState);
            targetState = nullptr;
        }

        releaseImpl();
        initialized = false;
    }
}

/*****************************************************************************/
// View State

GLAsynchronousMapRenderable::ViewState::ViewState() :
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

GLAsynchronousMapRenderable::ViewState::~ViewState()
{}


void GLAsynchronousMapRenderable::ViewState::set(const GLMapView *view)
{
    drawMapScale = view->drawMapScale;
    drawMapResolution = view->getView()->getMapResolution(drawMapScale);
    drawLat = view->drawLat;
    drawLng = view->drawLng;
    drawRotation = view->drawRotation;
    animationFactor = view->animationFactor;
    drawVersion = view->drawVersion;
    drawSrid = view->drawSrid;
    westBound = view->westBound;
    southBound = view->southBound;
    northBound = view->northBound;
    eastBound = view->eastBound;
    upperLeft.set(view->upperLeft.latitude, view->upperLeft.longitude);
    upperRight.set(view->upperRight.latitude, view->upperRight.longitude);
    lowerRight.set(view->lowerRight.latitude, view->lowerRight.longitude);
    lowerLeft.set(view->lowerLeft.latitude, view->lowerLeft.longitude);
    _left = view->left;
    _right = view->right;
    _top = view->top;
    _bottom = view->bottom;
    focusx = view->focusx;
    focusy = view->focusy;
    settled = view->settled;
}

void GLAsynchronousMapRenderable::ViewState::copy(const ViewState *view)
{
    drawMapScale = view->drawMapScale;
    drawMapResolution = view->drawMapResolution;
    drawLat = view->drawLat;
    drawLng = view->drawLng;
    drawRotation = view->drawRotation;
    animationFactor = view->animationFactor;
    drawVersion = view->drawVersion;
    drawSrid = view->drawSrid;
    westBound = view->westBound;
    southBound = view->southBound;
    northBound = view->northBound;
    eastBound = view->eastBound;
    upperLeft.set(view->upperLeft.latitude, view->upperLeft.longitude);
    upperRight.set(view->upperRight.latitude, view->upperRight.longitude);
    lowerRight.set(view->lowerRight.latitude, view->lowerRight.longitude);
    lowerLeft.set(view->lowerLeft.latitude, view->lowerLeft.longitude);
    _left = view->_left;
    _right = view->_right;
    _top = view->_top;
    _bottom = view->_bottom;
    focusx = view->focusx;
    focusy = view->focusy;
    settled = view->settled;
}

/*****************************************************************************/
// Worker Thread

GLAsynchronousMapRenderable::WorkerThread::WorkerThread(GLAsynchronousMapRenderable *owner) :
    owner_(owner),
    pending_data_(nullptr),
    name_(nullptr)
{}

void GLAsynchronousMapRenderable::WorkerThread::setName(const char *n)
{
    if (name_)
        delete[] name_;
    if (n) {
        size_t len = strlen(n)+1;
        char *copy = new char[len];
        memcpy(copy, n, len);
        name_ = copy;
    }
}

void* GLAsynchronousMapRenderable::WorkerThread::asyncRun(void* self)
{
    if (self)
    {
        auto* worker(static_cast<WorkerThread*> (self));

        worker->asyncRunImpl();
    }
    return nullptr;
}

void GLAsynchronousMapRenderable::WorkerThread::asyncRunImpl() {
    QueryContext *pendingData(nullptr);
    bool releasePending = false;
    try {
        pendingData = owner_->createQueryContext();
        releasePending = true;
        ViewState *queryState = owner_->newViewStateInstance();
        while (true) {
            {
                Lock lock(owner_->mutex);
                if (owner_->backgroundWorker != this) {
                    break;
                }

                // update release/renderable collections
                if (owner_->servicingRequest &&
                    owner_->updateRenderableLists(pendingData))
                    owner_->preparedState->copy(queryState);
                owner_->resetQueryContext(pendingData);
                owner_->servicingRequest = false;

                // check the state and wait if appropriate
                if (!owner_->checkState()) {
                    owner_->cv.wait(lock); // XXX - exception previously getting ignored, handle code???
                    continue;
                }

                // copy the target state to query outside of the
                // synchronized block
                queryState->copy(owner_->targetState);
                owner_->invalid = false;
                owner_->servicingRequest = true;
            }
            owner_->query(queryState, pendingData);
        }
    }
    catch (...) {
    }
    if (releasePending)
        owner_->releaseQueryContext(pendingData);
    owner_->servicingRequest = false;
}

GLAsynchronousMapRenderable::QueryContext::~QueryContext() { }

