#ifdef MSVC
#include "renderer/raster/tilereader/PreciseVertexResolver.h"

#include "renderer/raster/tilereader/NodeCore.h"

#include <algorithm>
#include <iterator>
#include <sstream>

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

PreciseVertexResolver::PreciseVertexResolver(GLQuadTileNode3 &owner) NOTHROWS
    : VertexResolver(*owner.core->imprecise),
      owner(owner),
      monitor(Thread::TEMT_Recursive),
      thread(nullptr, nullptr),
      activeID(),
      threadCounter(0),
      pending(pointSort),
      unresolvable(pointSort),
      precise(pointSort),
      currentNode(nullptr),
      currentRequest(pointSort),
      scratchImg(0.0, 0.0),
      needsResolved(0),
      requested(0),
      numNodesPending(0),
      initialized(false)
{
    renderRunnables.mutex.reset(new Mutex(TEMT_Recursive));
    do {
        TAKErr code(TE_Ok);

        // fill the four corners -- we can assume that these are precisely
        // defined for the dataset projection
        const int64_t minx = 0;
        const int64_t miny = 0;
        int64_t maxx, maxy;
        code = owner.core->tileReader->getWidth(&maxx);
        TE_CHECKBREAK_CODE(code);
        code = owner.core->tileReader->getHeight(&maxy);
        TE_CHECKBREAK_CODE(code);

        GeoPoint2 ul, ur, lr, ll;
        code = owner.core->imprecise->imageToGround(&ul, Math::Point2<double>(static_cast<double>(minx), static_cast<double>(miny)));
        TE_CHECKBREAK_CODE(code);
        this->precise[Math::Point2<int64_t>(minx, miny)] = ul;

        code = owner.core->imprecise->imageToGround(&ur, Math::Point2<double>(static_cast<double>(maxx), static_cast<double>(miny)));
        TE_CHECKBREAK_CODE(code);
        this->precise[Math::Point2<int64_t>(maxx, miny)] = ur;

        code = owner.core->imprecise->imageToGround(&lr, Math::Point2<double>(static_cast<double>(maxx), static_cast<double>(maxy)));
        TE_CHECKBREAK_CODE(code);
        this->precise[Math::Point2<int64_t>(maxx, maxy)] = lr;

        code = owner.core->imprecise->imageToGround(&ll, Math::Point2<double>(static_cast<double>(minx), static_cast<double>(maxy)));
        TE_CHECKBREAK_CODE(code);
        this->precise[Math::Point2<int64_t>(minx, maxy)] = ll;
    }  while (false);
}
PreciseVertexResolver::~PreciseVertexResolver()
{
    {
        Monitor::Lock lock(this->monitor);
        // Clear the thread id as a signal to our thread to exit
        this->activeID = Thread::ThreadID();
        this->queue.clear();
        this->pending.clear();
        lock.signal();
    }
    // Join our thread
    thread.reset();

    // Veto queued events
    {
        Thread::Lock lock(*this->renderRunnables.mutex);
        for (auto iter : this->renderRunnables.queued)
            iter->invalid = true;
        this->renderRunnables.queued.clear();
    }

    if (owner.core->textureCache && (this->precise.size() > 4 || this->unresolvable.size() > 0)) {
        TAKErr code(TE_Ok);
        do {
            // XXX - need to restrict how many vertices we are storing
            // in the cache
            MemBuffer2Ptr buf(nullptr, nullptr);
            code = serialize(buf, this->precise, this->unresolvable);
            TE_CHECKBREAK_CODE(code);
            std::string key;
            code = this->getCacheKey(&key);
            TE_CHECKBREAK_CODE(code);
            GLTextureCache2::EntryPtr entry(
                new GLTextureCache2::Entry(
                    GLTexture2Ptr(new GLTexture2(1, buf->size(), GL_LUMINANCE, GL_UNSIGNED_BYTE), Memory_deleter_const<GLTexture2>),
                    std::unique_ptr<float, void (*)(const float *)>(nullptr, nullptr),
                    std::unique_ptr<float, void (*)(const float *)>(nullptr, nullptr), 0, 0,
                    GLTextureCache2::Entry::OpaquePtr(buf.get(), Memory_leaker_const<void>)),
                Memory_deleter_const<GLTextureCache2::Entry>);

            code = owner.core->textureCache->put(key.c_str(), std::move(entry));
            TE_CHECKBREAK_CODE(code);
        } while (false);

        TE_CHECKRETURN(code);
    }

    this->precise.clear();
    this->unresolvable.clear();
    this->initialized = false;
}


void PreciseVertexResolver::beginDraw(const GLGlobeBase &view) NOTHROWS
{
    this->currentRequest.clear();
    this->numNodesPending = 0;

    this->targeting = view.targeting;
}
void PreciseVertexResolver::endDraw(const GLGlobeBase &view) NOTHROWS
{
    if (!view.targeting && this->numNodesPending == 0) {
        // all node grids are full resolved, go ahead and expand the
        // grids
        std::size_t minGridWidth = 16u;
        for (auto &entry : this->requestNodes) {
            std::shared_ptr<GLQuadTileNode3> node(entry.lock());
            if (node && node->glTexGridWidth < minGridWidth)
                minGridWidth = node->glTexGridWidth;
        }

        for (auto &entry : this->requestNodes) {
            std::shared_ptr<GLQuadTileNode3> node(entry.lock());
            if (!node)
                continue;
            if (node->glTexGridWidth > minGridWidth)
                continue;

            const std::size_t targetGridWidth = static_cast<std::size_t>((uint64_t)1u << (4u - std::min(node->tileIndex.z << 1u, (size_t)4)));
            if (node->glTexGridWidth < targetGridWidth) {
                queueGLCallback(new RenderRunnableOpaque(*this, node, static_cast<int>(targetGridWidth)));
            }
        }
    }
    this->requestNodes.clear();

    {
        Monitor::Lock lock(this->monitor);

        for (auto iter = this->queue.begin(); iter != this->queue.end();) {
            if (this->currentRequest.find(*iter) == this->currentRequest.end()) {
                iter = this->queue.erase(iter);
            } else {
                iter++;
            }
        }
        SortedPointSet tmp(pointSort);
        std::set_intersection(this->pending.begin(), this->pending.end(), this->currentRequest.begin(), this->currentRequest.end(),
                              std::inserter(tmp, tmp.begin()), pointSort);
        this->pending = tmp;
        this->currentRequest.clear();
    }
}
void PreciseVertexResolver::beginNode(const GLQuadTileNode3 &node) NOTHROWS
{
    VertexResolver::beginNode(node);

    this->currentNode = &node;
    this->needsResolved = 0;
    this->requested = 0;
    this->requestNodes.push_back(node.selfRef);
}
void PreciseVertexResolver::endNode(const GLQuadTileNode3 &node) NOTHROWS
{
    this->currentNode = nullptr;
    // update our pending count if the node needs one or more vertices
    // resolved
    if (this->requested > 0 && this->needsResolved > 0)
        this->numNodesPending++;

    VertexResolver::endNode(node);
}
TAKErr PreciseVertexResolver::project(GeoPoint2 *value, bool *resolved, const int64_t imgSrcX, const int64_t imgSrcY) NOTHROWS
{
    GeoPoint2 geo;
    bool geoValid = false;

    if (!this->targeting) {
        this->requested++;

        this->query.x = imgSrcX;
        this->query.y = imgSrcY;

        {
            Monitor::Lock lock(this->monitor);

            auto miter = this->precise.find(this->query);
            if (miter != this->precise.end()) {
                geo = miter->second;
                geoValid = true;
            }

            if (!geoValid && !this->initialized) {
                TAKErr code(TE_Ok);
                do {
                    if (owner.core->textureCache != nullptr) {
                        GLTextureCache2::EntryPtr entry(nullptr, nullptr);
                        std::string key;
                        code = getCacheKey(&key);
                        TE_CHECKBREAK_CODE(code);
                        code = owner.core->textureCache->remove(entry, key.c_str());
                        if (code == TE_Ok) {
                            void *data = entry->opaque.get();
                            MemBuffer2 buf(static_cast<const uint8_t *>(data), entry->opaqueSize);
                            code = deserialize(buf, this->precise, this->unresolvable);
                            TE_CHECKBREAK_CODE(code);

                            this->queue.remove_if(PointMapPredicate(this->precise));
                            this->queue.remove_if(PointSetPredicate(this->unresolvable));
                            for (auto kvp : this->precise)
                                this->pending.erase(kvp.first);
                            for (auto kvp : this->unresolvable)
                                this->pending.erase(kvp);
                        }
                        code = TE_Ok;
                        miter = this->precise.find(this->query);
                        if (miter != this->precise.end()) {
                            geo = miter->second;
                            geoValid = true;
                        }
                    }
                } while (false);
                this->initialized = true;
                TE_CHECKRETURN_CODE(code);
            }

            if (!geoValid) {
                TAKErr code = this->resolve();
                TE_CHECKRETURN_CODE(code);

                // try to obtain the next and previous points, if
                // present we can interpolate this point

                if (this->currentNode != nullptr) {
                    const int64_t texGridIncrementX = (this->currentNode->tileSrcWidth / this->currentNode->glTexGridWidth);
                    const int64_t texGridIncrementY = (this->currentNode->tileSrcHeight / this->currentNode->glTexGridHeight);

                    const int64_t prevImgSrcX = imgSrcX - texGridIncrementX;
                    const int64_t prevImgSrcY = imgSrcY - texGridIncrementY;
                    const int64_t nextImgSrcX = imgSrcX + texGridIncrementX;
                    const int64_t nextImgSrcY = imgSrcY + texGridIncrementY;

                    SortedPointMap::iterator interpolate0;
                    SortedPointMap::iterator interpolate1;

                    // check horizontal interpolation
                    this->query.y = imgSrcY;

                    this->query.x = prevImgSrcX;
                    interpolate0 = this->precise.find(this->query);
                    this->query.x = nextImgSrcX;
                    interpolate1 = this->precise.find(this->query);
                    if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                        geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                        (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                        geoValid = true;
                    }

                    // check vertical interpolation
                    if (!geoValid) {
                        this->query.x = imgSrcX;

                        this->query.y = prevImgSrcY;
                        interpolate0 = this->precise.find(this->query);
                        this->query.y = nextImgSrcY;
                        interpolate1 = this->precise.find(this->query);
                        if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                            geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                            (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                            geoValid = true;
                        }
                    }

                    // check cross interpolation
                    if (!geoValid) {
                        // XXX - just doing this quickly along one
                        // diagonal, but should really be doing a
                        // bilinear interpolation
                        this->query.x = prevImgSrcX;
                        this->query.y = prevImgSrcY;
                        interpolate0 = this->precise.find(this->query);
                        this->query.x = nextImgSrcX;
                        this->query.y = nextImgSrcY;
                        interpolate1 = this->precise.find(this->query);
                        if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                            geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                            (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                            geoValid = true;
                        }
                    }
                }
            }
        }
    }
    if (!geoValid) {
        return VertexResolver::project(value, nullptr, imgSrcX, imgSrcY);
    } else {
        *value = geo;
        if (resolved)
            *resolved = true;
        return TE_Ok;
    }
}
TAKErr PreciseVertexResolver::preciseImageToGround(GeoPoint2 *ground, const Point2<double> &image)
{
    bool isPrecise = false;
    if (owner.imageToGround(ground, &isPrecise, image) != TE_Ok || !isPrecise)
        return TE_Err;

    return TE_Ok;
}
TAKErr PreciseVertexResolver::resolve()
{
    Monitor::Lock lock(this->monitor);

    if (this->unresolvable.find(this->query) != this->unresolvable.end())
        return TE_Ok;
    this->needsResolved++;
    Math::Point2<int64_t> p(this->query);
    this->currentRequest.insert(p);
    if (this->pending.find(p) != this->pending.end()) {
        lock.signal();
        return TE_Ok;
    }
    this->queue.push_back(p);
    this->pending.insert(p);

    if (!this->thread) {
        std::stringstream sstr("Precise Vertex Resolver-");
        sstr << threadCounter++;
        std::string s = sstr.str();

        Thread::ThreadCreateParams param;
        param.name = s.c_str();
        param.priority = Thread::ThreadPriority::TETP_Normal;

        TAKErr code = Thread_start(this->thread, threadRun, this, param);
        activeID = thread->getID();
        TE_CHECKRETURN_CODE(code)
    }
    lock.signal();
    return TE_Ok;
}

void PreciseVertexResolver::renderThreadRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<RenderRunnableOpaque> runnableInfo(static_cast<RenderRunnableOpaque *>(opaque));
    Thread::Lock lock(*runnableInfo->mutex);
    if (runnableInfo->invalid)
        return;

    auto& resolver = runnableInfo->owner;
    std::shared_ptr<GLQuadTileNode3> node(runnableInfo->node.lock());
    bool markDirty = false;
    switch (runnableInfo->eventType) {
        case RenderRunnableOpaque::END_DRAW:
            if (node && node->glTexGridWidth < static_cast<std::size_t>(runnableInfo->targetGridWidth)) {
                node->expandTexGrid();
                resolver.owner.verticesInvalid = true;
                markDirty = true;
            }
            break;
        case RenderRunnableOpaque::RUN_RESULT:
            resolver.owner.verticesInvalid = true;
            markDirty = true;
            break;
    }
    if(resolver.owner.core->surfaceControl && markDirty) {
        resolver.owner.core->surfaceControl->markDirty();
    }

    for (auto iter = resolver.renderRunnables.queued.begin(); iter != resolver.renderRunnables.queued.end(); ++iter) {
        if (*iter == runnableInfo.get()) {
            resolver.renderRunnables.queued.erase(iter);
            break;
        }
    }
}

void PreciseVertexResolver::queueGLCallback(RenderRunnableOpaque *runnableInfo)
{
    Lock lock(*this->renderRunnables.mutex);
    this->renderRunnables.queued.push_back(runnableInfo);
    this->owner.core->context.queueEvent(renderThreadRunnable, std::unique_ptr<void, void(*)(const void *)>(runnableInfo, Memory_leaker_const<void>));
}

void *PreciseVertexResolver::threadRun(void *opaque)
{
    auto *p = (PreciseVertexResolver *)opaque;
    p->threadRunImpl();
    return nullptr;
}

void PreciseVertexResolver::threadRunImpl()
{
    Point2<int64_t> xy;
    bool havePoint = false;
    GeoPoint2 result;
    bool haveResult = false;
    while (true) {
        {
            Monitor::Lock lock(this->monitor);

            if (haveResult)
                this->precise.insert(SortedPointMap::value_type(xy, result));
            else if (havePoint)
                this->unresolvable.insert(xy);

            if (havePoint) {
                this->pending.erase(xy);

                // Convert to renderthread
                queueGLCallback(new RenderRunnableOpaque(*this));
            }

            havePoint = haveResult = false;
            if (this->activeID != Thread::Thread_currentThreadID())
                break;
            if (this->queue.empty()) {
                lock.wait();
                continue;
            }
            xy = this->queue.front();
            this->queue.pop_front();
            havePoint = true;
        }

        TAKErr code = this->preciseImageToGround(&result, Point2<double>((double)xy.x, (double)xy.y));
        if (code == TE_Ok && !TE_ISNAN(result.latitude) && !TE_ISNAN(result.longitude))
            haveResult = true;
    }

    {
        Monitor::Lock lock(this->monitor);

        if (this->activeID == Thread::Thread_currentThreadID()) {
            this->activeID = Thread::ThreadID();
        }
    }
}

TAKErr PreciseVertexResolver::getCacheKey(std::string *value)
{
    TAKErr code(TE_Ok);
    Port::String s;

    code = owner.getUri(s);
    TE_CHECKRETURN_CODE(code);
    std::string ret = s.get();
    ret += ",coords";
    *value = ret;
    return code;
}

TAKErr PreciseVertexResolver::serialize(MemBuffer2Ptr &buf, const SortedPointMap &precise,
                                                         const SortedPointSet &unresolvable)
{
    TAKErr code(TE_Ok);
    buf = MemBuffer2Ptr(new MemBuffer2((4 + (32 * precise.size())) + (4 + (16 * unresolvable.size()))), Memory_deleter_const<MemBuffer2>);

    code = buf->put(static_cast<uint32_t>(precise.size()));
    TE_CHECKRETURN_CODE(code);
    for (auto e : precise) {
        // 2 int64 + 2 double
        code = buf->put(e.first.x);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.first.y);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.second.latitude);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.second.longitude);
        TE_CHECKRETURN_CODE(code);
    }

    code = buf->put(static_cast<uint32_t>(unresolvable.size()));
    TE_CHECKRETURN_CODE(code);
    for (auto p : unresolvable) {
        // 2 int64
        code = buf->put(p.x);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(p.y);
        TE_CHECKRETURN_CODE(code);
    }

    buf->flip();
    return code;
}

TAKErr PreciseVertexResolver::deserialize(MemBuffer2 &buf, SortedPointMap &precise, SortedPointSet &unresolvable)
{
    TAKErr code(TE_Ok);
    uint32_t size;

    code = buf.get(&size);
    TE_CHECKRETURN_CODE(code);
    for (uint32_t i = 0; i < size; i++) {
        Point2<int64_t> point;
        GeoPoint2 geo;
        code = buf.get(&point.x);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&point.y);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&geo.latitude);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&geo.longitude);
        TE_CHECKRETURN_CODE(code);
        precise.insert(SortedPointMap::value_type(point, geo));
    }

    code = buf.get(&size);
    TE_CHECKRETURN_CODE(code);
    for (uint32_t i = 0; i < size; i++) {
        Point2<int64_t> point;
        code = buf.get(&point.x);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&point.y);
        TE_CHECKRETURN_CODE(code);
        unresolvable.insert(point);
    }
    return code;
}

PreciseVertexResolver::RenderRunnableOpaque::RenderRunnableOpaque(PreciseVertexResolver &owner) NOTHROWS
    : owner(owner), eventType(RUN_RESULT), targetGridWidth(0), invalid(false), mutex(owner.renderRunnables.mutex)
{}

PreciseVertexResolver::RenderRunnableOpaque::RenderRunnableOpaque(PreciseVertexResolver &owner,
                                                                                   const std::shared_ptr<GLQuadTileNode3> &node, const std::size_t targetGridWidth) NOTHROWS
    : owner(owner), eventType(END_DRAW), node(node), targetGridWidth(targetGridWidth), invalid(false), mutex(owner.renderRunnables.mutex)
{}
#endif