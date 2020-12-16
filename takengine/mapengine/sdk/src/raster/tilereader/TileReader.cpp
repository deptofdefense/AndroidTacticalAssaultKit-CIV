#include "TileReader.h"

#include <math.h>

#include "math/Utils.h"
#include "thread/Lock.h"
#include "util/Logging.h"

using namespace atakmap::raster::tilereader;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;


TileReader::TileReader(const char *uri, const char *cache_uri, int min_cache_level, const std::shared_ptr<AsynchronousIO> &async_io)
: uri_(uri),
read_lock_(std::shared_ptr<util::SyncObject>(new util::SyncObject())),
asynchronous_io_(async_io),
async_request_id_(1),
valid_(true),
cache_uri_(cache_uri),
min_cache_level_(cache_uri ? min_cache_level : INT_MAX) {
    
    if (!asynchronous_io_.get()) {
        asynchronous_io_ = std::shared_ptr<AsynchronousIO>(new AsynchronousIO(read_lock_));
    }
    
}

TileReader::~TileReader() { }

const char *TileReader::getUri() const { return uri_; }

int64_t TileReader::getWidth(int level) const {
    return std::max(this->getWidth(), (int64_t)level);
}

int64_t TileReader::getHeight(int level) const {
    return std::max(this->getHeight() >> (int64_t)level, (int64_t)1);
}

int TileReader::getTileWidth(int level, int64_t tileColumn) const {
    int64_t maxX = this->getWidth(level);
    int tileWidth = this->getTileWidth();
    int retval = tileWidth;
    if ((retval * (tileColumn + 1)) > maxX)
        retval = static_cast<int>(maxX - (tileWidth * tileColumn));
    return retval;
}

int TileReader::getTileHeight(int level, int64_t tileRow) const {
    int64_t maxY = this->getHeight(level);
    int tileHeight = this->getTileHeight();
    int retval = tileHeight;
    if ((retval * (tileRow + 1)) > maxY)
        retval = static_cast<int>(maxY - (tileHeight * tileRow));
    return retval;
}

TileReader::ReadResult TileReader::read(int level, int64_t tileColumn, int64_t tileRow, void *data, size_t byteCount) {
    if (level < 0) {
        throw std::invalid_argument("level less than 0");
    }
    
    return this->read(this->getTileSourceX(level, tileColumn),
                      this->getTileSourceY(level, tileRow),
                      this->getTileSourceWidth(level, tileColumn),
                      this->getTileSourceHeight(level, tileRow),
                      this->getTileWidth(level, tileColumn),
                      this->getTileHeight(level, tileRow),
                      data,
                      byteCount);
}

int TileReader::asyncRead(int level, int64_t tileColumn, int64_t tileRow, AsynchronousReadRequestListener *callback) {
    if (level < 0) {
        throw std::invalid_argument("level");
    }
    
    util::SyncLock lock(*this->asynchronous_io_->sync_on_);
    
    int requestId = this->async_request_id_++;
    this->asyncRead(new ReadRequest(this, requestId, level, tileColumn, tileRow, callback));
    return requestId;
}

int TileReader::asyncRead(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW, int dstH, AsynchronousReadRequestListener *callback) {
    
    util::SyncLock lock(*this->asynchronous_io_->sync_on_);
    int requestId = this->async_request_id_++;
    this->asyncRead(new ReadRequest(this, requestId, srcX, srcY, srcW, srcH, dstW,
                                    dstH, callback));
    return requestId;
}

void TileReader::asyncRead(ReadRequest *retval) {
    retval->callback_->requestCreated(retval->id);
    this->asynchronous_io_->queueRequest(this, retval);
}

void TileReader::cancel() {
    
}

TileReader::ReadResult TileReader::fill(ReadRequest &request) {
    
    util::SyncLock sync_lock(*this->read_lock_);
    
    {
        // lock when we check canceled
        Lock lock(request.mutex_);

        if (!this->valid_) {
            request.canceled_ = true;
        }
            
        // if the request was asynchronously canceled or if the ROI is empty
        // ignore
        if (request.canceled_ ||
            request.srcW == 0 ||
            request.srcH == 0 ||
            request.dstW == 0 ||
            request.dstH == 0) {
            
            return request.canceled_ ? TileReader::Canceled : TileReader::Success;
        }
    }
    
    return this->fillImpl(request);
}

TileReader::ReadResult TileReader::fillImpl(ReadRequest &request) {
    return this->fillDirect(request);
}

TileReader::ReadResult TileReader::fillDirect(ReadRequest &request) {
    int size = this->getTransferSize(request.dstW, request.dstH);
    AsynchronousIO::ReadBuffer &readBuffer = this->asynchronous_io_->getReadBuffer(size);
    
    void *buf = readBuffer.size() > 0 ? &readBuffer[0] : nullptr;
    size_t bufSize = readBuffer.size();
    
    ReadResult retval = this->read(request.srcX,
                                     request.srcY,
                                     request.srcW,
                                     request.srcH,
                                     request.dstW,
                                     request.dstH,
                                     buf,
                                     bufSize);
    
    if (retval == TileReader::Success) {
        this->dispatchUpdate(request,
                             buf,
                             bufSize,
                             0, 0,
                             request.dstW, request.dstH);
    }
    
    return retval;
}

int64_t TileReader::getNumTilesX(int level) const {
    return (int) ceil((double) this->getWidth(level) / (double) this->getTileWidth());
}

int64_t TileReader::getNumTilesX() const { return this->getNumTilesX(0); }

int64_t TileReader::getNumTilesY(int level) const {
    return (int) ceil((double) this->getHeight(level) / (double) this->getTileHeight());
}

int64_t TileReader::getNumTilesY() const { return this->getNumTilesY(0); }

int64_t TileReader::getTileSourceX(int level, int64_t tileColumn) const {
    return tileColumn * ((int64_t)this->getTileWidth() << (int64_t)level);
}

int64_t TileReader::getTileSourceY(int level, int64_t tileRow) const {
    return tileRow * ((int64_t)this->getTileHeight() << (int64_t)level);
}

int64_t TileReader::getTileSourceWidth(int level, int64_t tileColumn) const {
    int64_t retval = ((int64_t)this->getTileWidth() << (int64_t)level);
    int64_t maxX = this->getWidth(0);
    if ((retval * (tileColumn + 1)) > maxX)
        retval = (maxX - (retval * tileColumn));
    return retval;
}

int64_t TileReader::getTileSourceHeight(int level, int64_t tileRow) const {
    int64_t retval = ((int64_t)this->getTileHeight() << (int64_t)level);
    int64_t maxY = this->getHeight(0);
    if ((retval * (tileRow + 1)) > maxY)
        retval = (maxY - (retval * tileRow));
    return retval;
}

int64_t TileReader::getTileColumn(int level, int64_t srcX) const {
    return (int64_t) ((double) srcX / (double) ((int64_t)this->getTileWidth() << (int64_t)level));
}

int64_t TileReader::getTileRow(int level, int64_t srcY) const {
    return (int64_t) ((double) srcY / (double) ((int64_t)this->getTileHeight() << (int64_t)level));
}

void TileReader::releaseAyncIOTask(TileReader *reader, ReadRequest *request) {
    // if we are the exclusive owner of the async I/O thread, release it
    if (reader->asynchronous_io_->sync_on_.get() == reader->read_lock_.get()) {
        reader->asynchronous_io_->release();
    }
    
    reader->disposeImpl();
    delete reader;
}

void TileReader::dispose() {
   
    util::SyncLock lock(*this->asynchronous_io_->sync_on_);
    
    // abort all of our pending requests
    this->asynchronous_io_->abortRequests(this);
    // perform any user requested clean-up
    
    //this->disposeImpl();
    
    // if we are the exclusive owner of the async I/O thread, release it
    //if (this->asynchronousIO->syncOn.get() == this->readLock.get())
        this->asynchronous_io_->runLater(*this, AsynchronousIO::Task(this, nullptr, releaseAyncIOTask));
    
    // XXX - move onto async IO thread ??? we could get stuck here for
    //       a while if there is a request being serviced that hasn't
    //       been canceled
    {
    //XXX--    util::SyncLock lock(*this->readLock);
    //    this->disposeImpl();
        this->valid_ = false;
    }
}

void TileReader::disposeImpl() {
    // mark as invalid to prevent final possible asynchronous read after
    // this method returns
    
    /*TODO--if(this->cache != null) {
        this->cache.close();
        this->cache = null;
    }*/
}

int TileReader::getMaxNumResolutionLevels() const {
    return getNumResolutionLevels(this->getWidth(),
                                  this->getHeight(),
                                  this->getTileWidth(),
                                  this->getTileHeight());
}

bool TileReader::isMultiResolution() const {
    return false;
}

bool TileReader::isCanceled(const ReadRequest &request) const {
    return request.canceled_;
}

void TileReader::dispatchUpdate(ReadRequest &request, const void *data, size_t dataSize, int dstX, int dstY, int dstW, int dstH) {
    Lock lock(request.mutex_);
    if (!request.canceled_) {
        request.callback_->requestUpdate(request.id, data, dataSize, dstX, dstY, dstW, dstH);
    }
}

void TileReader::dispatchError(ReadRequest &request, const char *what) {
    Lock lock(request.mutex_);
    if (!request.canceled_) {
        request.callback_->requestError(request.id, what);
    }
}

int TileReader::getTransferSize(int width, int height) const {
    return this->getPixelSize()*width*height;
}

int TileReader::getPixelSize() const {
    return getPixelSize(this->getFormat());
}

int TileReader::getMinCacheLevel() const {
    return this->min_cache_level_;
}

int TileReader::getNumResolutionLevels(int64_t width, int64_t height, int64_t tileWidth, int64_t tileHeight) {
    auto numTilesX = (int64_t)ceil((double)width / (double)tileWidth);
    auto numTilesY = (int64_t)ceil((double)height / (double)tileHeight);

    int retval = 1;
    while (numTilesX > 1 && numTilesY > 1) {
        width = math::max(width >> 1, INT64_C(1));
        height = math::max(height >> 1, INT64_C(1));
        numTilesX = (int64_t)ceil((double)width / (double)tileWidth);
        numTilesY = (int64_t)ceil((double)height / (double)tileHeight);
        retval++;
    }
    return retval;
}

int TileReader::getPixelSize(Format format) {
    switch (format) {
        case MONOCHROME:
            return 1;
        case MONOCHROME_ALPHA:
            return 2;
        case RGB:
            return 3;
        case ARGB:
        case RGBA:
            return 4;
        default:
            throw std::invalid_argument("format");
    }
}

void TileReader::cancelAsyncRead(int requestId) {
    asynchronous_io_->cancelRequest(requestId);
}

bool TileReader::AsynchronousIO::Task::valid() const { return action_ != nullptr; }
void TileReader::AsynchronousIO::Task::cancel() {
    request_->cancel();
    
}

void TileReader::AsynchronousIO::Task::run() {
    action_(reader_, request_);
}

TileReader::AsynchronousIO::AsynchronousIO()
: AsynchronousIO(std::shared_ptr<util::SyncObject>(new SyncObject())) { }

TileReader::AsynchronousIO::AsynchronousIO(const std::shared_ptr<SyncObject> &syncOn)
: sync_on_(syncOn),
dead_(true),
started_(false) {
    if (!syncOn.get()) {
        this->sync_on_ = std::shared_ptr<util::SyncObject>(new SyncObject());
    }
}

void TileReader::AsynchronousIO::release() {
    util::SyncLock lock(*this->sync_on_);
    this->abortRequests(nullptr);
    this->dead_ = true;
    this->sync_on_->notify();
}

void TileReader::AsynchronousIO::abortRequests(TileReader *reader) {
    
    util::SyncLock lock(*this->sync_on_);
    
    auto end = tasks_.end();
    for (auto it = tasks_.begin(); it != end;) {
        if (reader == nullptr || ((*it).request_ && (*it).request_->owner_ == reader)) {
            (*it).cancel();
            it = tasks_.erase(it);
        } else {
            ++it;
        }
    }
}

void TileReader::AsynchronousIO::cancelRequest(int id) {
    Task task;
    {
        util::SyncLock lock(*this->sync_on_);
        
        auto end = tasks_.end();
        for (auto it = tasks_.begin(); it != end; ++it) {
            if ((*it).request_ && (*it).request_->id == id) {
                task = *it;
                tasks_.erase(it);
                break;
            }
        }
        
        if (!task.valid() && current_task_.valid() &&
            current_task_.request_ != nullptr && current_task_.request_->id == id) {
            task = current_task_;
            current_task_ = Task();
        }
    }
    
    if (task.valid()) {
        task.cancel();
    } else {
        atakmap::util::Logger::log(Logger::Debug, "No task found to cancel for %d\n", id);
    }
}

void TileReader::AsynchronousIO::queueRequestAction(TileReader *reader, TileReader::ReadRequest *request) {
    request->servicing_ = true;
    request->service();
    request->servicing_ = false;
}

void TileReader::AsynchronousIO::queueRequest(TileReader *reader, ReadRequest *request) {
    this->runLater(*reader, Task(reader, request, queueRequestAction));
}



void TileReader::AsynchronousIO::runLater(TileReader &reader, Task task) {
    
    util::SyncLock lock(*this->sync_on_);
    
    this->tasks_.push_back(task);
    
    this->dead_ = false;
    if (!this->started_) {
        this->started_ = true;
        TAKErr code(TE_Ok);
        ThreadPtr t(nullptr, nullptr);
        code = Thread_start(t, threadRun, this);
        if (code == TE_Ok) {
            // XXX - want this started as detached???
            t->detach();

            thread_ = std::move(t);
        } else {
            Logger_log(TELL_Error, "TileReader: Failed to start thread");
            this->started_ = false;
        }
        if (thread_.get()) {
            //TODO--t.setPriority(Thread.NORM_PRIORITY);
            //TODO--t.setName("gdal-async-io-thread@" + Integer.toString(this->hashCode(), 16));*/
        }
    }
    
    this->sync_on_->notify();
}

void *TileReader::AsynchronousIO::threadRun(void *threadData) {
    static_cast<TileReader::AsynchronousIO *>(threadData)->runImpl();
    return nullptr;
}

void TileReader::AsynchronousIO::runImpl() {
    while (true) {
        Task task;
        {
            util::SyncLock lock(*this->sync_on_);
            
            if (this->dead_) {
                this->started_ = false;
                break;
            }
            
            if (this->tasks_.size() < 1) {
                current_task_ = Task();
                //this->readBuffer.clear();
                    this->sync_on_->wait();
                continue;
            }
            
            task = this->tasks_.front();
            this->tasks_.pop_front();
            current_task_ = task;
            
            if (task.request_ && task.request_->canceled_) {
                delete task.request_;
                continue;
            }
        }
        
        if (task.valid()) {
            task.run();
        }
        if (task.request_) {
            delete task.request_;
        }
    }
}

TileReader::ReadRequest::ReadRequest(TileReader *owner, int id, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW, int dstH,
            int level, int64_t tileColumn, int64_t tileRow, AsynchronousReadRequestListener *callback)
: owner_(owner),
id(id),
srcX(srcX),
srcY(srcY),
srcW(srcW),
srcH(srcH),
dstW(dstW),
dstH(dstH),
level(level),
tileColumn(tileColumn),
tileRow(tileRow),
callback_(callback),
canceled_(false),
servicing_(false) {
}

void TileReader::ReadRequest::cancel() {
    {
        Lock lock(mutex_);
        this->canceled_ = true;
        if (this->callback_) {
            this->callback_->requestCanceled(id);
            this->callback_ = nullptr;
        }
    }
    
    if (this->servicing_)
        owner_->cancel();
}

void TileReader::ReadRequest::service() {
    
    bool canceled = false;
    {
        Lock lock(mutex_);
        canceled = this->canceled_;
        if (!canceled) {
            this->callback_->requestStarted(id);
        }
    }
    
    // fill request unsynchronized
    ReadResult readResult = Canceled;
    if (!canceled) {
        readResult = this->owner_->fill(*this);
    }

    {
        Lock lock(mutex_);

 //       if (!this->canceled) {
        if (this->callback_) {
            switch(readResult) {
                case Success:
                    this->callback_->requestCompleted(id);
                    break;
                case Error:
                    this->callback_->requestError(id, nullptr);
                    break;
                case Canceled:
                    this->callback_->requestCanceled(id);
                    break;
                default :
                    throw std::runtime_error("illegal state");
            }
        }
        
        // done with callback
        this->callback_ = nullptr;
    }
}
