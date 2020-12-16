
#include "renderer/feature/GLBatchGeometry2.h"

#include "feature/Geometry.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::util;

GLBatchGeometry2::GLBatchGeometry2(RenderContext &surface_, int zOrder_) NOTHROWS:
    surface(surface_),
    zOrder(zOrder_),
    featureId(0LL),
    lod(0),
    subid(0),
    version(0LL),
    sharedQueueMutex(new Thread::Mutex(Thread::TEMT_Recursive))
{}

GLBatchGeometry2::~GLBatchGeometry2() NOTHROWS
{
    this->stop();
}

TAKErr GLBatchGeometry2::init(const int64_t feature_id, const char *name_val) NOTHROWS
{
    this->featureId = feature_id;
    this->name = name_val;
    return TE_Ok;
}

TAKErr GLBatchGeometry2::setName(const char *name_val) NOTHROWS
{
    if (this->surface.isRenderThread())
    {
        return this->setNameImpl(name_val);
    }
    else
    {
        TAKErr code(TE_Ok);
        Lock lock(*sharedQueueMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        std::unique_ptr<NameUpdater> updatePtr(new NameUpdater(*this, name_val));
        NameUpdater *update = updatePtr.get();
        this->updateQueue.insert(updatePtr.release());
        this->surface.queueEvent(setNameRunnable, std::unique_ptr<void, void(*)(const void *)>(update, Memory_leaker_const<void>));

        return TE_Ok;
    }
}

TAKErr GLBatchGeometry2::setNameImpl(const char *name_val) NOTHROWS
{
    this->name = name_val;
    return TE_Ok;
}

TAKErr GLBatchGeometry2::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    this->lod = lod_val;
    if (this->surface.isRenderThread())
    {
        return this->setGeometryImpl(std::move(blob), type);
    }
    else
    {
        TAKErr code(TE_Ok);
        Lock lock(*sharedQueueMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        std::unique_ptr<GeometryUpdater> updatePtr(new GeometryUpdater(*this, std::move(blob), type));
        GeometryUpdater *update = updatePtr.get();
        this->updateQueue.insert(updatePtr.release());
        this->surface.queueEvent(setGeometryBlobRunnable, std::unique_ptr<void, void(*)(const void *)>(update, Memory_leaker_const<void>));

        return TE_Ok;
    }
}

TAKErr GLBatchGeometry2::setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS
{
    if (this->surface.isRenderThread())
    {
        return this->setGeometryImpl(geom);
    }
    else
    {
        TAKErr code(TE_Ok);
        Lock lock(*sharedQueueMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        TAK::Engine::Feature::GeometryPtr_const geomPtr(geom.clone(), atakmap::feature::destructGeometry);
        std::unique_ptr<GeometryUpdater> updatePtr(new GeometryUpdater(*this, std::move(geomPtr)));
        GeometryUpdater *update = updatePtr.get();
        this->updateQueue.insert(updatePtr.release());
        this->surface.queueEvent(setGeometryGeomRunnable, std::unique_ptr<void, void(*)(const void *)>(update, Memory_leaker_const<void>));

        return TE_Ok;
    }
}

void GLBatchGeometry2::start()
{}

void GLBatchGeometry2::stop()
{
#if 1
    Lock lock(*sharedQueueMutex);
    std::set<Updater *>::iterator iter;
    for (iter = this->updateQueue.begin(); iter != this->updateQueue.end(); iter++)
    {
        (*iter)->cancel();
    }
    this->updateQueue.clear();
#else
    // capture all outstanding updates and clear the protected queue
    std::vector<Updater *> outstandingUpdates;
    {
        LockPtr lock(NULL, NULL);
        Lock_create(lock, *sharedQueueMutex);
        outstandingUpdates.reserve(this->updateQueue.size());
        std::set<Updater *>::iterator iter;
        for (iter = this->updateQueue.begin(); iter != this->updateQueue.end(); iter++)
        {
            outstandingUpdates.push_back(*iter);
        }
        this->updateQueue.clear();
    }

    // cancel all updates that had been queued
    {
        std::vector<Updater *>::iterator iter;
        for (iter = outstandingUpdates.begin(); iter != outstandingUpdates.end(); iter++)
        {
            (*iter)->cancel();
        }
    }
#endif
}

void GLBatchGeometry2::setNameRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<NameUpdater> update(static_cast<NameUpdater *>(opaque));
    Lock cancelLock(*update->sharedMutex);
    if (!update->canceled) {
        update->owner.setNameImpl(update->name);
        update->owner.updateQueue.erase(update.get());
    }
}

void GLBatchGeometry2::setGeometryBlobRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<GeometryUpdater> update(static_cast<GeometryUpdater *>(opaque));
    Lock cancelLock(*update->sharedMutex);
    if (!update->canceled) {
        update->owner.setGeometryImpl(std::move(update->blob), update->type);
        update->owner.updateQueue.erase(update.get());
    }
}

void GLBatchGeometry2::setGeometryGeomRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<GeometryUpdater> update(static_cast<GeometryUpdater *>(opaque));
    Lock cancelLock(*update->sharedMutex);
    if (!update->canceled) {
        if (update->geom.get())
            update->owner.setGeometryImpl(*(update->geom));

        update->owner.updateQueue.erase(update.get());
    }
}

GLBatchGeometry2::Updater::Updater(GLBatchGeometry2 &owner_) NOTHROWS :
    owner(owner_),
    canceled(false),
    sharedMutex(owner_.sharedQueueMutex)
{}

void GLBatchGeometry2::Updater::cancel() NOTHROWS
{
    Lock lock(*sharedMutex);
    canceled = true;
}

GLBatchGeometry2::NameUpdater::NameUpdater(GLBatchGeometry2 &owner_, const char *name_) NOTHROWS :
    Updater(owner_),
    name(name_)
{}

GLBatchGeometry2::GeometryUpdater::GeometryUpdater(GLBatchGeometry2 &owner_, BlobPtr &&blob_, const int type_) NOTHROWS :
    Updater(owner_),
    blob(std::move(blob_)),
    geom(nullptr, nullptr),
    type(type_)
{}

GLBatchGeometry2::GeometryUpdater::GeometryUpdater(GLBatchGeometry2 &owner_, TAK::Engine::Feature::GeometryPtr_const &&geom_) NOTHROWS :
    Updater(owner_),
    blob(nullptr, nullptr),
    geom(std::move(geom_)),
    type(-1)
{}
