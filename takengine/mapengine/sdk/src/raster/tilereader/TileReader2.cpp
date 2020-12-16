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
      asynchronousIO(nullptr),
      asyncRequestId(1),
      readLock(Thread::TEMT_Recursive),
      valid(true),
      controls()
{}

TileReader2::~TileReader2()
{
    do {
        TAKErr code(TE_Ok);
        Thread::Lock lock(readLock);

        if (this->asynchronousIO != nullptr) {
            // abort all of our pending requests
            code = this->asynchronousIO->abortRequests(this);
            TE_CHECKBREAK_CODE(code);
        }

        // XXX - move onto async IO thread ??? we could get stuck here for
        //       a while if there is a request being serviced that hasn't
        //       been canceled
        // mark as invalid to prevent final possible asynchronous read after
        // this method returns
        this->valid = false;

        TE_CHECKBREAK_CODE(code);
    } while (false);

    asynchronousIO.reset();
}

void TileReader2::installAsynchronousIO(std::shared_ptr<AsynchronousIO> io)
{
    if (this->asynchronousIO != nullptr)
        return;

    this->asynchronousIO = io;
    if (!this->asynchronousIO) {
        this->asynchronousIO = std::make_shared<AsynchronousIO>();
    }
}

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

TAKErr TileReader2::asyncRead(const int level, const int64_t tileColumn, const int64_t tileRow,
                              AsynchronousReadRequestListener *callback) NOTHROWS
{
    TAKErr code(TE_Ok);

    {
        int64_t srcX;
        code = this->getTileSourceX(&srcX, level, tileColumn);
        TE_CHECKRETURN_CODE(code);

        int64_t srcY;
        code = this->getTileSourceY(&srcY, level, tileRow);
        TE_CHECKRETURN_CODE(code);

        int64_t srcW;
        code = this->getTileSourceWidth(&srcW, level, tileColumn);
        TE_CHECKRETURN_CODE(code);

        int64_t srcH;
        code = this->getTileSourceHeight(&srcH, level, tileRow);
        TE_CHECKRETURN_CODE(code);

        size_t dstW;
        code = this->getTileWidth(&dstW, level, tileColumn);
        TE_CHECKRETURN_CODE(code);

        size_t dstH;
        code = this->getTileHeight(&dstH, level, tileRow);
        TE_CHECKRETURN_CODE(code);

        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, readLock);
        code = this->asyncRead(
            new ReadRequest(*this, this->asyncRequestId++, srcX, srcY, srcW, srcH, dstW, dstH, level, tileColumn, tileRow, callback));
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr TileReader2::asyncRead(const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH, const size_t dstW,
                              const size_t dstH, AsynchronousReadRequestListener *callback) NOTHROWS
{
    TAKErr code(TE_Ok);

    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, readLock);
        code = this->asyncRead(new ReadRequest(*this, this->asyncRequestId++, srcX, srcY, srcW, srcH, dstW, dstH, -1, -1, -1, callback));
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr TileReader2::asyncRead(ReadRequest *rr) NOTHROWS
{
    TAKErr code(TE_Ok);

    rr->callback->requestCreated(rr->id);
    code = this->asynchronousIO->runLater(this, rr, ReadRequest_run, ReadRequest_abort, ReadRequest_cleanup);
    if (code != TE_Ok)
        delete rr;
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TileReader2::asyncCancel(int id) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->asynchronousIO->cancelRequests(this, ReadRequest_cancelcheck, &id);
    return code;
}

TAKErr TileReader2::asyncAbort(AsynchronousReadRequestListener &callback) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->asynchronousIO->abortRequests(this, ReadRequest_abortcheck, &callback);
    return code;
}

TAKErr TileReader2::cancel() NOTHROWS
{
    TAKErr code(TE_Ok);
    return code;
}

TAKErr TileReader2::fill(ReadRequest &request) NOTHROWS
{
    TAKErr code(TE_Ok);

    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, readLock);
        if (!this->valid)
            request.canceled = true;

        // if the request was asynchronously canceled or if the ROI is empty
        // ignore
        if (request.canceled || request.srcW == 0 || request.srcH == 0 || request.dstW == 0 || request.dstH == 0) {
            return request.canceled ? TE_Canceled : code;
        }

        code = this->fillImpl(request);
    }

    return code;
}

TAKErr TileReader2::fillImpl(ReadRequest &request) NOTHROWS
{
    return this->fillDirect(request);
}

TAKErr TileReader2::fillDirect(ReadRequest &request) NOTHROWS
{
    TAKErr code;

    size_t size;
    code = this->getTransferSize(&size, request.dstW, request.dstH);
    TE_CHECKRETURN_CODE(code);

    uint8_t *readBuffer = nullptr;
    code = asynchronousIO->getReadBuffer(&readBuffer, size);
    TE_CHECKRETURN_CODE(code);

    code = this->read(readBuffer, request.srcX, request.srcY, request.srcW, request.srcH, request.dstW, request.dstH);
    if (code == TE_Canceled) // Note: minimizing debugging on cancel
        return code;
    TE_CHECKRETURN_CODE(code);

    if (code == TE_Ok) {
        Bitmap2::DataPtr data(readBuffer, Memory_leaker_const<uint8_t>);
        Bitmap2::Format format;
        getFormat(&format);
        this->dispatchUpdate(request, Bitmap2(std::move(data), request.dstW, request.dstH, format));
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

TAKErr TileReader2::dispatchUpdate(ReadRequest &request, const Bitmap2 &data) NOTHROWS
{
    TAKErr code(TE_Ok);
    request.callback->requestUpdate(request.id, data);
    return code;
}

TAKErr TileReader2::dispatchError(ReadRequest &request, const TAKErr code, const char *msg) NOTHROWS
{
    // TAKErr code(TE_Ok);
    request.callback->requestError(request.id, code, msg);
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

TAKErr TileReader2::asyncRun(void *opaque, AsyncRunnable runnable, AsyncRunnable cancel, AsyncRunnable cleanup) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->asynchronousIO->runLater(this, opaque, runnable, cancel, cleanup);
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

void TileReader2::ReadRequest_run(TileReader2 *reader, void *opaque)
{
    TAKErr code(TE_Ok);
    auto *request = static_cast<ReadRequest *>(opaque);

    Thread::LockPtr lock(nullptr, nullptr);
    TAK::Engine::Thread::Lock_create(lock, request->lock);
    if (!request->callback)
        // Aborted. Just return
        return;

    request->servicing = true;
    request->callback->requestStarted(request->id);
 
    // int64_t s = SystemClock.elapsedRealtime();
    // result = TileReader.this->fill(ReadRequest.this);
    code = reader->fill(*request);
    // int64_t e = SystemClock.elapsedRealtime();
    // if(ReadRequest.this->tileColumn != -1)
    // Log.d(TAG, "read request (" + level + "," + tileColumn + "," +
    // tileRow + ") in " + (e-s) + "ms");

    switch (code) {
        case TE_Canceled:
            request->callback->requestCanceled(request->id);
            break;
        case TE_Ok:
            request->callback->requestCompleted(request->id);
            break;
        default:
            request->callback->requestError(request->id, code, "ReadRequest_run error");
            break;
    }

    request->servicing = false;
}

void TileReader2::ReadRequest_abort(TileReader2 *reader, void *opaque)
{
    auto *request = static_cast<ReadRequest *>(opaque);
    Thread::LockPtr lock(nullptr, nullptr);
    TAK::Engine::Thread::Lock_create(lock, request->lock);
    if (!request->callback)
        // Already aborted
        return;

    request->cancel();
    request->callback->requestCanceled(request->id);

    request->callback = nullptr;
}

void TileReader2::ReadRequest_cleanup(TileReader2 *reader, void *opaque)
{
    auto *request = static_cast<ReadRequest *>(opaque);
    delete request;
}

bool TileReader2::ReadRequest_cancelcheck(TileReader2 *reader, AsyncRunnable action, void *requestOpaque, void *cancelOpaque)
{
    if (action == ReadRequest_run) {
        auto *r = (ReadRequest *)requestOpaque;
        int *checkId = (int *)cancelOpaque;
        if (r->id == *checkId) {
            r->cancel();
            return true;
        }
    }
    return false;
}

bool TileReader2::ReadRequest_abortcheck(TileReader2 *reader, AsyncRunnable action, void *requestOpaque, void *abortOpaque)
{
    if (action == ReadRequest_run) {
        auto *r = (ReadRequest *)requestOpaque;
        auto *checkcb = (AsynchronousReadRequestListener *)abortOpaque;
        if (r->callback == checkcb) {
            r->cancel();
            return true;
        }
    }
    return false;
}

/**************************************************************************/
// ReadRequest

TileReader2::ReadRequest::ReadRequest(TileReader2 &owner_, const int id_, const int64_t srcX_, const int64_t srcY_, const int64_t srcW_,
                                      const int64_t srcH_, const size_t dstW_, const size_t dstH_, const int level_,
                                      const int64_t tileColumn_, const int64_t tileRow_,
                                      AsynchronousReadRequestListener *callback_) NOTHROWS : owner(owner_),
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
    this->canceled = true;
    if (this->servicing)
        this->owner.cancel();
}

/*****************************************************************************/
// AsynchronousReadRequestListener

TileReader2::AsynchronousReadRequestListener::~AsynchronousReadRequestListener() NOTHROWS {}

/*****************************************************************************/
// AsynchronousIO

TileReader2::AsynchronousIO::AsynchronousIO() NOTHROWS : tasks(),
                                                         currentTask(nullptr),
                                                         syncOn(Thread::TEMT_Recursive),
                                                         cv(),
                                                         thread(nullptr, nullptr),
                                                         dead(true),
                                                         started(false),
                                                         maxIdle(0LL),
                                                         readBuffer(nullptr),
                                                         readBufferLength(0)
{}

TileReader2::AsynchronousIO::AsynchronousIO(const int64_t maxIdle_) NOTHROWS : tasks(),
                                                                               currentTask(nullptr),
                                                                               syncOn(Thread::TEMT_Recursive),
                                                                               cv(),
                                                                               thread(nullptr, nullptr),
                                                                               dead(true),
                                                                               started(false),
                                                                               maxIdle(maxIdle_),
                                                                               readBuffer(nullptr),
                                                                               readBufferLength(0)
{}

TileReader2::AsynchronousIO::~AsynchronousIO() NOTHROWS
{
    delete[] readBuffer;
}

TAKErr TileReader2::AsynchronousIO::getReadBuffer(uint8_t **value, const size_t size) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (this->readBufferLength < size) {
        if (this->readBuffer)
            delete[] this->readBuffer;
        this->readBuffer = new uint8_t[size];
        this->readBufferLength = size;
    }
    *value = this->readBuffer;
    return code;
}

TAKErr TileReader2::AsynchronousIO::release() NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, syncOn);
        code = this->abortRequests(nullptr);
        this->dead = true;
        this->cv.signal(*lock.get());
    }
    return code;
}

TAKErr TileReader2::AsynchronousIO::abortRequests(TileReader2 *reader) NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, syncOn);

        auto iter = this->tasks.begin();
        Task t;
        while (iter != this->tasks.end()) {
            t = *iter;
            if (reader == nullptr || t.reader == reader) {
                if (t.abort)
                    t.abort(t.reader, t.opaque);
                if (t.cleanup)
                    t.cleanup(t.reader, t.opaque);
                iter = this->tasks.erase(iter);
            } else {
                iter++;
            }
        }

        if (currentTask && (!reader || currentTask->reader == reader)) {
            // leave currentTask alone; it belongs to the io thread and that will clean it up
            if (currentTask->abort)
                currentTask->abort(currentTask->reader, currentTask->opaque);
            code = TE_Ok;
        }
    }
    return code;
}

TAKErr TileReader2::AsynchronousIO::abortRequests(TileReader2 *reader, AsyncCancelCheck check, void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, syncOn);

        auto iter = this->tasks.begin();
        Task t;
        while (iter != this->tasks.end()) {
            t = *iter;
            if ((reader == nullptr || t.reader == reader) && check(t.reader, t.action, t.opaque, opaque)) {
                if (t.abort)
                    t.abort(t.reader, t.opaque);
                if (t.cleanup)
                    t.cleanup(t.reader, t.opaque);
                iter = this->tasks.erase(iter);
                code = TE_Ok;
            } else {
                iter++;
            }
        }

        if (currentTask && (!reader || currentTask->reader == reader) &&
            check(currentTask->reader, currentTask->action, currentTask->opaque, opaque)) {
            // leave currentTask alone; it belongs to the io thread and that will clean it up
            if (currentTask->abort)
                currentTask->abort(currentTask->reader, currentTask->opaque);
            code = TE_Ok;
        }
    }
    return code;
}

Util::TAKErr TileReader2::AsynchronousIO::cancelRequests(TileReader2 *reader, AsyncCancelCheck check, void *opaque) NOTHROWS
{
    Thread::LockPtr lock(nullptr, nullptr);
    TAK::Engine::Thread::Lock_create(lock, syncOn);
    TAKErr code(TE_InvalidArg);

    auto iter = this->tasks.begin();
    Task t;
    while (iter != this->tasks.end()) {
        t = *iter;
        if (reader == nullptr || t.reader == reader) {
            if (check(t.reader, t.action, t.opaque, opaque)) {
                code = TE_Ok;
            }
        }
        iter++;
    }
    if (currentTask && (!reader || currentTask->reader == reader) &&
        check(currentTask->reader, currentTask->action, currentTask->opaque, opaque))
        code = TE_Ok;

    return code;
}

TAKErr TileReader2::AsynchronousIO::runLater(TileReader2 *reader, void *opaque, AsyncRunnable action, AsyncRunnable abort,
                                             AsyncRunnable cleanup) NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Thread::LockPtr lock(nullptr, nullptr);
        TAK::Engine::Thread::Lock_create(lock, syncOn);

        this->dead = false;
        if (!this->started) {
            // std::string threadName("tilereader-async-io-thread@" + std::to_string(this->hashCode(), 16));
            Thread::ThreadCreateParams param;
            param.name = "tilereader-async-io-thread";
            param.priority = Thread::TETP_Lowest;

            code = Thread_start(this->thread, threadRun, this, param);

            if (code == TE_Ok) {
                this->started = true;
            } else {
                this->started = false;
            }
        }
        if (this->started) {
            this->tasks.push_back(Task(reader, opaque, action, abort, cleanup));
            this->cv.signal(*lock.get());
        }
    }
    return code;
}

TAKErr TileReader2::AsynchronousIO::runImpl() NOTHROWS
{
    TAKErr code(TE_Ok);
    Task task;
    while (true) {
        // synchronized (this->syncOn)
        {
            Thread::LockPtr lock(nullptr, nullptr);
            TAK::Engine::Thread::Lock_create(lock, syncOn);

            // Clear executing reference
            currentTask = nullptr;
            if (task.cleanup) {
                task.cleanup(task.reader, task.opaque);
                task = Task();
            }

            if (this->dead) {
                this->started = false;
                break;
            }

            if (this->tasks.size() < 1) {
                auto start = std::chrono::high_resolution_clock::now();
                if (this->readBuffer)
                    delete[] this->readBuffer;
                this->readBuffer = nullptr;
                this->readBufferLength = 0;

                code = this->cv.wait(*lock.get(), this->maxIdle);
                TE_CHECKBREAK_CODE(code);

                auto end = std::chrono::high_resolution_clock::now();
                auto timSpan = end - start;
                // check if the thread has idle'd out
                if (this->maxIdle > 0L && (timSpan.count()) > this->maxIdle)
                    this->dead = true;
                // wake up and re-run the sync block
                continue;
            }

            task = this->tasks.front();
            this->tasks.pop_front();
            currentTask = &task;
        }

        task.action(task.reader, task.opaque);
    }
    return code;
}

void *TileReader2::AsynchronousIO::threadRun(void *threadData)
{
    static_cast<TileReader2::AsynchronousIO *>(threadData)->runImpl();
    return nullptr;
}
