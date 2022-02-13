#include "raster/tilereader/TileReader2.h"

#include <algorithm>
#include <chrono>

#include "thread/Lock.h"

#include "util/Memory.h"

using namespace TAK::Engine::Raster::TileReader;

using namespace TAK::Engine;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

#define TAG "TileReader2"

namespace {

    void releaseIOAsyncRunnable(TileReader2 *reader, void *opaque)
    {
        auto *io = (TileReader2::AsynchronousIO *)opaque;
        io->release();
    }
}


TileReader2::TileReader2(const char *uri_) NOTHROWS
    : uri(uri_),
      asyncRequestId(1),
      readLock(Thread::TEMT_Recursive),
      valid(true),
      controls()
{}

TileReader2::~TileReader2()
{}

TAKErr TileReader2::getUri(Port::String &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = this->uri;
    return code;
}
TAKErr TileReader2::getWidth(int64_t *value, const size_t level) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t sourceWidth;
    code = this->getWidth(&sourceWidth);
    TE_CHECKRETURN_CODE(code);

    *value = std::max(sourceWidth >> (int64_t)level, (int64_t)1);
    return code;
}

TAKErr TileReader2::getHeight(int64_t *value, const size_t level) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t sourceHeight;
    code = this->getHeight(&sourceHeight);
    TE_CHECKRETURN_CODE(code);

    *value = std::max(sourceHeight >> (int64_t)level, (int64_t)1);
    return code;
}

TAKErr TileReader2::getTileWidth(size_t *value, const size_t level, const int64_t tileColumn) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t maxX;
    code = this->getWidth(&maxX, level);
    TE_CHECKRETURN_CODE(code);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    int64_t retval = tileWidth;
    if ((retval * (tileColumn + 1)) > maxX)
        retval = maxX - ((int64_t)tileWidth * tileColumn);
    *value = (size_t)retval;
    return code;
}

TAKErr TileReader2::getTileHeight(size_t *value, const size_t level, const int64_t tileRow) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t maxY;
    code = this->getHeight(&maxY, level);
    TE_CHECKRETURN_CODE(code);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    int64_t retval = tileHeight;
    if ((retval * (tileRow + 1)) > maxY)
        retval = maxY - ((int64_t)tileHeight * tileRow);
    *value = (size_t)retval;

    return code;
}

TAKErr TileReader2::read(uint8_t *data, const size_t level, const int64_t tileColumn, const int64_t tileRow) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t tileSourceX;
    code = this->getTileSourceX(&tileSourceX, level, tileColumn);
    TE_CHECKRETURN_CODE(code);

    int64_t tileSourceY;
    code = this->getTileSourceY(&tileSourceY, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    int64_t tileSourceWidth;
    code = this->getTileSourceWidth(&tileSourceWidth, level, tileColumn);
    TE_CHECKRETURN_CODE(code);

    int64_t tileSourceHeight;
    code = this->getTileSourceHeight(&tileSourceHeight, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth, level, tileColumn);
    TE_CHECKRETURN_CODE(code);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    code = this->read(data, tileSourceX, tileSourceY, tileSourceWidth, tileSourceHeight, tileWidth, tileHeight);
    TE_CHECKRETURN_CODE(code);

    return code;
}

int TileReader2::nextRequestId() NOTHROWS
{
    TAK::Engine::Thread::Lock lock(this->idLock);
    return asyncRequestId++;
}

TAKErr TileReader2::cancel() NOTHROWS
{
    TAKErr code(TE_Ok);
    return code;
}

TAKErr TileReader2::fill(uint8_t *buffer, ReadRequest &request) NOTHROWS
{
    TAKErr code(TE_Ok);

    {
        Thread::Lock lock(readLock);
        if (!this->valid)
            request.canceled = true;

        // if the request was asynchronously canceled or if the ROI is empty
        // ignore
        if (request.canceled || request.srcW == 0 || request.srcH == 0 || request.dstW == 0 || request.dstH == 0) {
            return request.canceled ? TE_Canceled : code;
        }

        code = this->read(buffer, request.srcX, request.srcY, request.srcW, request.srcH, request.dstW, request.dstH);
        // minimize debugging
        if (code == TE_Ok) {
            Bitmap2::DataPtr data(buffer, Memory_leaker_const<uint8_t>);
            Bitmap2::Format format;
            getFormat(&format);
            {
                TAK::Engine::Thread::Lock cb_lock(request.lock);
                if(request.callback)
                    request.callback->requestUpdate(request.id, Bitmap2(std::move(data), request.dstW, request.dstH, format));
            }
        }
    }

    return code;
}

TAKErr TileReader2::getNumTilesX(int64_t *value) NOTHROWS
{
    return this->getNumTilesX(value, 0);
}

TAKErr TileReader2::getNumTilesX(int64_t *value, const size_t level) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t levelWidth;
    code = this->getWidth(&levelWidth, level);
    TE_CHECKRETURN_CODE(code);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    *value = (int64_t)ceil((double)levelWidth / (double)tileWidth);
    return code;
}

TAKErr TileReader2::getNumTilesY(int64_t *value) NOTHROWS
{
    return this->getNumTilesY(value, 0);
}

TAKErr TileReader2::getNumTilesY(int64_t *value, const size_t level) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t levelHeight;
    code = this->getHeight(&levelHeight, level);
    TE_CHECKRETURN_CODE(code);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    *value = (int64_t)ceil((double)levelHeight / (double)tileHeight);
    return code;
}

TAKErr TileReader2::getTileSourceX(int64_t *value, const size_t level, const int64_t tileColumn) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    *value = tileColumn * ((int64_t)tileWidth << (int64_t)level);
    return code;
}

TAKErr TileReader2::getTileSourceY(int64_t *value, const size_t level, const int64_t tileRow) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    *value = tileRow * ((int64_t)tileHeight << (int64_t)level);
    return code;
}

TAKErr TileReader2::getTileSourceWidth(int64_t *value, const size_t level, const int64_t tileColumn) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    *value = ((int64_t)tileWidth << (int64_t)level);

    int64_t maxX;
    code = this->getWidth(&maxX, 0);
    TE_CHECKRETURN_CODE(code);

    if (((*value) * (tileColumn + 1)) > maxX)
        *value = (maxX - ((*value) * tileColumn));
    return code;
}

TAKErr TileReader2::getTileSourceHeight(int64_t *value, const size_t level, const int64_t tileRow) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    *value = ((int64_t)tileHeight << (int64_t)level);

    int64_t maxY;
    code = this->getHeight(&maxY, 0);
    TE_CHECKRETURN_CODE(code);

    if (((*value) * (tileRow + 1)) > maxY)
        *value = (maxY - ((*value) * tileRow));
    return code;
}

TAKErr TileReader2::getTileColumn(int64_t *value, const size_t level, const int64_t srcX) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    *value = (int64_t)((double)srcX / (double)((int64_t)tileWidth << (int64_t)level));
    return code;
}

TAKErr TileReader2::getTileRow(int64_t *value, const size_t level, const int64_t srcY) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    *value = (int64_t)((double)srcY / (double)((int64_t)tileHeight << (int64_t)level));
    return code;
}

TAKErr TileReader2::getMaximumNumResolutionLevels(size_t *value) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t width;
    code = this->getWidth(&width);
    TE_CHECKRETURN_CODE(code);

    int64_t height;
    code = this->getHeight(&height);
    TE_CHECKRETURN_CODE(code);

    size_t tileWidth;
    code = this->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);

    size_t tileHeight;
    code = this->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    code = TileReader2_getNumResolutionLevels(value, width, height, tileWidth, tileHeight);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TileReader2::isMultiResolution(bool *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    *value = false;
    return code;
}

TAKErr TileReader2::isCanceled(bool *value, const ReadRequest &request) NOTHROWS
{
    TAKErr code(TE_Ok);
    *value = request.canceled;
    return code;
}

TAKErr TileReader2::getTransferSize(size_t *value, const size_t width, const size_t height) NOTHROWS
{
    TAKErr code(TE_Ok);

    size_t pixelSize;
    code = this->getPixelSize(&pixelSize);
    TE_CHECKRETURN_CODE(code);

    *value = pixelSize * width * height;
    return code;
}

TAKErr TileReader2::getPixelSize(size_t *value) NOTHROWS
{
    TAKErr code(TE_Ok);

    ::Renderer::Bitmap2::Format format;
    code = this->getFormat(&format);
    TE_CHECKRETURN_CODE(code);

    code = ::Renderer::Bitmap2_formatPixelSize(value, format);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TileReader2::start() NOTHROWS
{
    TAKErr code(TE_Ok);
    return code;
}

TAKErr TileReader2::stop() NOTHROWS
{
    TAKErr code(TE_Ok);
    return code;
}

TAKErr TileReader2::getTileVersion(int64_t *value, const size_t level, const int64_t tileColum, const int64_t tileRow) NOTHROWS
{
    TAKErr code(TE_Ok);
    *value = 0;
    return code;
}

TAKErr TileReader2::registerControl(Core::Control *control) NOTHROWS
{
    TAKErr code(TE_Ok);
    this->controls[control->type] = control;
    return code;
}

TAKErr TileReader2::getControl(Core::Control **value, const char *type) NOTHROWS
{
    TAKErr code(TE_InvalidArg);

    auto c = controls.find(type);
    if (c != controls.end()) {
        *value = c->second;
        code = TE_Ok;
    }

    return code;
}

TAKErr TileReader2::getControls(Port::Collection<Core::Control *> &ctrls) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::map<std::string, Core::Control *>::iterator c;
    for (c = this->controls.begin(); c != this->controls.end(); c++) {
        code = ctrls.add(c->second);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Raster::TileReader::TileReader2_getNumResolutionLevels(size_t *value, const int64_t width_, const int64_t height_, const int64_t tileWidth,
                                           const int64_t tileHeight) NOTHROWS
{
    TAKErr code(TE_Ok);

    int64_t width = width_;
    int64_t height = height_;
    auto numTilesX = (int64_t)ceil((double)width / (double)tileWidth);
    auto numTilesY = (int64_t)ceil((double)height / (double)tileHeight);

    *value = 1;
    while (numTilesX > 1 && numTilesY > 1) {
        width = std::max(width >> 1LL, 1LL);
        height = std::max(height >> 1LL, 1LL);
        numTilesX = (int64_t)ceil((double)width / (double)tileWidth);
        numTilesY = (int64_t)ceil((double)height / (double)tileHeight);
        (*value)++;
    }
    return code;
}

TAKErr TAK::Engine::Raster::TileReader::TileReader2_getPixelSize(size_t *value, const ::Renderer::Bitmap2::Format format) NOTHROWS
{
    return ::Renderer::Bitmap2_formatPixelSize(value, format);
}


TileReader2::AsynchronousIO *TAK::Engine::Raster::TileReader::TileReader2_getMasterIOThread() NOTHROWS
{
    static TileReader2::AsynchronousIO masterIOThread;
    return &masterIOThread;
}

void TileReader2::ReadRequest_run(uint8_t *readBuffer, ReadRequest &request) NOTHROWS
{
    TAKErr code(TE_Ok);

    {
        Thread::Lock lock(request.lock);
        if (!request.callback)
            // Aborted. Just return
            return;

        request.servicing = true;
        request.callback->requestStarted(request.id);
    }

    // int64_t s = SystemClock.elapsedRealtime();
    // result = TileReader.this->fill(ReadRequest.this);
    code = request.owner->fill(readBuffer, request);
    // int64_t e = SystemClock.elapsedRealtime();
    // if(ReadRequest.this->tileColumn != -1)
    // Log.d(TAG, "read request (" + level + "," + tileColumn + "," +
    // tileRow + ") in " + (e-s) + "ms");

    {
        Thread::Lock lock(request.lock);
        if (!request.callback)
            // Aborted. Just return
            return;
        AsynchronousReadRequestListener* const cb = request.callback;
        // request is at terminal state; no further notifications
        request.callback = nullptr;

        switch (code) {
        case TE_Canceled:
            cb->requestCanceled(request.id);
            break;
        case TE_Ok:
            cb->requestCompleted(request.id);
            break;
        default:
            cb->requestError(request.id, code, "ReadRequest_run error");
            break;
        }

        request.servicing = false;
    }
}

void TileReader2::ReadRequest_abort(ReadRequest &request) NOTHROWS
{
    Thread::Lock lock(request.lock);
    if (!request.callback)
        // Already aborted
        return;

    request.cancel();
    AsynchronousReadRequestListener* const cb = request.callback;
    // request is canceled; no further notifications
    request.callback = nullptr;

    cb->requestCanceled(request.id);
}

/**************************************************************************/
// ReadRequest

// XXX -
namespace {
    std::size_t getTileWidth(TileReader2& reader) NOTHROWS
    {
        std::size_t v;
        if (reader.getTileWidth(&v) != TE_Ok)
            return 0;
        return v;
    }
    std::size_t getTileHeight(TileReader2& reader) NOTHROWS
    {
        std::size_t v;
        if (reader.getTileHeight(&v) != TE_Ok)
            return 0;
        return v;
    }
    std::size_t getTileWidth(TileReader2& reader, const std::size_t level, int64_t tileCol) NOTHROWS
    {
        std::size_t v;
        if (reader.getTileWidth(&v, level, tileCol) != TE_Ok)
            return 0;
        return v;
    }
    std::size_t getTileHeight(TileReader2& reader, const std::size_t level, int64_t tileRow) NOTHROWS
    {
        std::size_t v;
        if (reader.getTileHeight(&v, level, tileRow) != TE_Ok)
            return 0;
        return v;
    }
    int64_t getTileSourceX(TileReader2& reader, const std::size_t level, int64_t tileCol) NOTHROWS
    {
        int64_t v;
        if (reader.getTileSourceX(&v, level, tileCol) != TE_Ok)
            return 0;
        return v;
    }
    int64_t getTileSourceY(TileReader2& reader, const std::size_t level, int64_t tileRow) NOTHROWS
    {
        int64_t v;
        if (reader.getTileSourceY(&v, level, tileRow) != TE_Ok)
            return 0;
        return v;
    }
    int64_t getTileSourceWidth(TileReader2& reader, const std::size_t level, int64_t tileCol) NOTHROWS
    {
        int64_t v;
        if (reader.getTileSourceWidth(&v, level, tileCol) != TE_Ok)
            return 0;
        return v;
    }
    int64_t getTileSourceHeight(TileReader2& reader, const std::size_t level, int64_t tileRow) NOTHROWS
    {
        int64_t v;
        if (reader.getTileSourceHeight(&v, level, tileRow) != TE_Ok)
            return 0;
        return v;
    }
}

TileReader2::ReadRequest::ReadRequest(const std::shared_ptr<TileReader2> &owner, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH,
            const size_t dstW, const size_t dstH, const int level, const int64_t tileColumn, const int64_t tileRow,
            AsynchronousReadRequestListener *callback) NOTHROWS :
    ReadRequest(owner, owner->nextRequestId(), srcX, srcY, srcW, srcH, dstW, dstH, level, tileColumn, tileRow, callback, true)
{}
TileReader2::ReadRequest::ReadRequest(const std::shared_ptr<TileReader2> &owner, const int level, const int64_t tileColumn, const int64_t tileRow,
            AsynchronousReadRequestListener *callback) NOTHROWS :
    ReadRequest(owner,
                ::getTileSourceX(*owner, level, tileColumn), ::getTileSourceY(*owner, level, tileRow),
                ::getTileSourceWidth(*owner, level, tileColumn), ::getTileSourceHeight(*owner, level, tileRow),
                ::getTileWidth(*owner, level, tileColumn), ::getTileHeight(*owner, level, tileRow),
                level,
                tileColumn,
                tileRow,
                callback)
{}
TileReader2::ReadRequest::ReadRequest(const std::shared_ptr<TileReader2> &owner_, const int id_, const int64_t srcX_, const int64_t srcY_, const int64_t srcW_,
                                      const int64_t srcH_, const size_t dstW_, const size_t dstH_, const int level_,
                                      const int64_t tileColumn_, const int64_t tileRow_,
                                      AsynchronousReadRequestListener *callback_, bool) NOTHROWS : owner(owner_),
                                                                                             id(id_),
                                                                                             canceled(false),
                                                                                             servicing(false),
                                                                                             srcX(srcX_),
                                                                                             srcY(srcY_),
                                                                                             srcW(srcW_),
                                                                                             srcH(srcH_),
                                                                                             dstW(dstW_),
                                                                                             dstH(dstH_),
                                                                                             tileRow(tileRow_),
                                                                                             tileColumn(tileColumn_),
                                                                                             level(level_),
                                                                                             lock(Thread::TEMT_Recursive),
                                                                                             callback(callback_)
{}

void TileReader2::ReadRequest::cancel() NOTHROWS
{
    TAK::Engine::Thread::Lock l(lock);
    if (this->canceled)
        return;
    this->canceled = true;
    AsynchronousReadRequestListener* const cb = this->callback;
    // request is canceled; no further notifications
    this->callback = nullptr;
    if (cb)
        cb->requestCanceled(this->id);
    if (this->servicing)
        this->owner->cancel();
}

/*****************************************************************************/
// AsynchronousReadRequestListener

TileReader2::AsynchronousReadRequestListener::~AsynchronousReadRequestListener() NOTHROWS {}

TileReader2::ReadRequestPrioritizer::~ReadRequestPrioritizer() NOTHROWS {}

/*****************************************************************************/
// AsynchronousIO

TileReader2::AsynchronousIO::AsynchronousIO() NOTHROWS : 
    AsynchronousIO(0LL)
{}

TileReader2::AsynchronousIO::AsynchronousIO(const int64_t maxIdle_) NOTHROWS : tasks(),
                                                                               currentTask(nullptr),
                                                                               syncOn(Thread::TEMT_Recursive),
                                                                               cv(),
                                                                               thread(nullptr, nullptr),
                                                                               dead(true),
                                                                               started(false),
                                                                               maxIdle(maxIdle_)
{}

TileReader2::AsynchronousIO::~AsynchronousIO() NOTHROWS
{
    release();
}

TAKErr TileReader2::AsynchronousIO::release() NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, syncOn);
        code = this->abortRequests(nullptr);
        this->dead = true;
        this->cv.broadcast(*lock.get());
    }
    return code;
}

TAKErr TileReader2::AsynchronousIO::abortRequests(TileReader2 *reader) NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::Lock lock(syncOn);

        auto requests = this->tasks.find(reader);
        if (requests != this->tasks.end()) {
            requests->second.clear();
            for (auto request : requests->second)
                ReadRequest_abort(*request);
            this->tasks.erase(requests);
        }

        if (currentTask && (!reader || currentTask->owner.get() == reader)) {
            // leave currentTask alone; it belongs to the io thread and that will clean it up
            ReadRequest_abort(*currentTask);
            code = TE_Ok;
        }
    }
    return code;
}

TAKErr TileReader2::AsynchronousIO::asyncRead(const std::shared_ptr<ReadRequest> &rr) NOTHROWS
{
    TAKErr code(TE_Ok);

    rr->callback->requestCreated(rr->id);
    {
        Thread::Lock lock(syncOn);

        this->dead = false;
        if (!this->started) {
            // std::string threadName("tilereader-async-io-thread@" + std::to_string(this->hashCode(), 16));
            Thread::ThreadCreateParams param;
            param.name = "tilereader-async-io-thread";
            param.priority = Thread::TETP_Lowest;

            code = TAK::Engine::Thread::ThreadPool_create(this->thread, 5, threadRun, this);

            if (code == TE_Ok) {
                this->started = true;
            } else {
                this->started = false;
            }
        }
        if (this->started) {
            std::vector<std::shared_ptr<ReadRequest>> &requests = this->tasks[rr->owner.get()];
            requests.push_back(rr);
            struct Cmp
            {
                ReadRequestPrioritizer* cmp{ nullptr };
                bool operator()(const std::shared_ptr<ReadRequest>& a, const std::shared_ptr<ReadRequest>& b) NOTHROWS
                {
                    // REMEMBER: requests are pulled of the _tail_ of the queue

                    // canceled requests bubble to the top
                    if (a->canceled && b->canceled)
                        return a->id > b->id;
                    else if (a->canceled)
                        return false;
                    else if (b->canceled)
                        return true;

                    // if a prioritizer is defined, use it
                    if (cmp) {
                        const bool a_b = cmp->compare(*a, *b);
                        const bool b_a = cmp->compare(*b, *a);
                        // if not equal, return priorization
                        if (a_b != b_a)
                            return a_b;
                    }

                    // prioritize high resolution tiles over low resolution tiles
                    if(a->level < b->level)
                        return false;
                    else if(a->level > b->level)
                        return true;
                    else
                        return a->id<b->id; // LIFO on read requests
                }
            };
            Cmp cmp;
            auto prioritizer = this->requestPrioritizers.find(rr->owner.get());
            if (prioritizer != this->requestPrioritizers.end())
                cmp.cmp = prioritizer->second.get();
            std::sort(requests.begin(), requests.end(), cmp);

            this->cv.signal(lock);
        }
    }
    return code;
}

void TileReader2::AsynchronousIO::setReadRequestPrioritizer(const TileReader2& reader, ReadRequestPrioritizerPtr&& prioritizer) NOTHROWS
{
    Thread::Lock lock(syncOn);
    if (!prioritizer) {
        this->requestPrioritizers.erase(&reader);
    } else {
        this->requestPrioritizers.insert(std::make_pair(&reader, std::move(prioritizer)));
    }
}

TAKErr TileReader2::AsynchronousIO::runImpl() NOTHROWS
{
    TAKErr code(TE_Ok);
    struct {
        array_ptr<uint8_t> data;
        std::size_t length{ 0u };
    } readBuffer;

    while (true) {
        std::shared_ptr<ReadRequest> task;
        // synchronized (this->syncOn)
        {
            Thread::Lock lock(syncOn);

            // Clear executing reference
            currentTask.reset();

            if (this->dead) {
                this->started = false;
                break;
            }

            if (this->tasks.size() < 1) {
                auto start = std::chrono::high_resolution_clock::now();

                code = this->cv.wait(lock, this->maxIdle);
                TE_CHECKBREAK_CODE(code);

                auto end = std::chrono::high_resolution_clock::now();
                auto timSpan = end - start;
                // check if the thread has idle'd out
                if (this->maxIdle > 0L && (timSpan.count()) > this->maxIdle)
                    this->dead = true;
                // wake up and re-run the sync block
                continue;
            }

            std::vector<std::shared_ptr<ReadRequest>>* requests = &this->tasks.begin()->second;
            for (auto it = this->tasks.begin(); it != this->tasks.end(); it++) {
                if (it->second.back()->id < requests->back()->id)
                    requests = &it->second;
            }

            task = requests->back();
            requests->pop_back();
            if (requests->empty())
                this->tasks.erase(task->owner.get());
            currentTask = task;
        }

        do {
            std::size_t req;
            if (task->owner->getTransferSize(&req, task->dstW, task->dstH) != TE_Ok)
                break;
            if (readBuffer.length < req) {
                readBuffer.length = 0u;
                readBuffer.data.reset(new(std::nothrow) uint8_t[req]);
                if (!readBuffer.data.get())
                    break;
                readBuffer.length = req;
            }
            ReadRequest_run(readBuffer.data.get(), *task);
        } while (false);
    }
    return code;
}

void *TileReader2::AsynchronousIO::threadRun(void *threadData)
{
    static_cast<TileReader2::AsynchronousIO *>(threadData)->runImpl();
    return nullptr;
}
