#ifndef ATAKMAP_RASTER_TILEREADER_TILEREADER_H_INCLUDED
#define ATAKMAP_RASTER_TILEREADER_TILEREADER_H_INCLUDED

#include <stdint.h>
#include <stdexcept>
#include <list>
#include <vector>

#include "thread/Mutex.h"
#include "thread/Thread.h"
#include "util/SyncObject.h"
#include "util/NonCopyable.h"

namespace atakmap {
    namespace raster {
        namespace tilereader {
            
            class TileReader : TAK::Engine::Util::NonCopyable {
            private:
                class ReadRequest;
                
            public:
                class AsynchronousReadRequestListener;
                
                class AsynchronousIO;
                
            public:
                
                enum Format {
                    MONOCHROME,
                    MONOCHROME_ALPHA,
                    RGB,
                    RGBA,
                    ARGB,
                };
                
                enum Interleave {
                    /** Band Sequential */
                    BSQ,
                    /** Pixel Interleave */
                    BIP,
                    /** Line Interleave */
                    BIL,
                };

                enum ReadResult {
                    Success,
                    Error,
                    //Again,
                    Canceled,
                };

            protected:
                const TAK::Engine::Port::String uri_;
                std::shared_ptr<AsynchronousIO> asynchronous_io_;
                int async_request_id_;
                
                std::shared_ptr<util::SyncObject> read_lock_;
                bool valid_;
                
            private:
                TAK::Engine::Port::String cache_uri_;
                
                int min_cache_level_;
                
            protected:
                TileReader(const char *uri, const char *cache_uri, int min_cache_level, const std::shared_ptr<AsynchronousIO> &async_io);
                
            public:
                virtual ~TileReader();
                
                /**
                 * Returns the URI for the data.
                 *
                 * @return  The URI for the data.
                 */
                const char *getUri() const;
                
                /**
                 * Returns the width of the image at its native resolution.
                 *
                 * @return The width of the image at its native resolution.
                 */
                virtual int64_t getWidth() const = 0;
                
                /**
                 * Returns the width of the image at the specified level.
                 *
                 * @param level The level
                 * @return The width of the image at the specified level.
                 */
                int64_t getWidth(int level) const;
                
                /**
                 * Returns the height of the image at its native resolution.
                 *
                 * @return The height of the image at its native resolution.
                 */
                virtual int64_t getHeight() const = 0;
                
                /**
                 * Returns the height of the image at the specified level.
                 *
                 * @return The height of the image at the specified level.
                 */
                int64_t getHeight(int level) const;
                
                /**
                 * Returns the nominal width of a tile.
                 *
                 * @return The nominal width of a tile.
                 */
                virtual int getTileWidth() const = 0;
                
                /**
                 * Returns the width of the tile in the specified column at the specified
                 * level.
                 *
                 * @return  The width of the tile in the specified column at the specified
                 *          level.
                 */
                int getTileWidth(int level, int64_t tileColumn) const;
                
                /**
                 * Returns the nominal height of a tile.
                 *
                 * @return The nominal height of a tile.
                 */
                virtual int getTileHeight() const = 0;
                
                /**
                 * Returns the height of the tile in the specified row at the specified
                 * level.
                 * 
                 * @return  The height of the tile in the specified row at the specified
                 *          level.
                 */
                int getTileHeight(int level, int64_t tileRow) const;
                
                virtual void cancelAsyncRead(int requestId);

                /**
                 * Reads the specified tile at the specified level and stores the data in
                 * the specified array. The returned data will have dimensions consistent
                 * with {@link #getTileWidth(int, int)} and {@link #getTileHeight(int, int)}
                 * for the specified level, tile column and tile row.
                 *
                 * @param level         The resolution level
                 * @param tileColumn    The tile column
                 * @param tileRow       The tile row
                 * @param data          Output buffer for the data of the specified tile
                 *
                 * @return  <code>true</code> if the read completed successfuly,
                 *          <code>false</code> if the operation was canceled asynchronously
                 */
                ReadResult read(int level, int64_t tileColumn, int64_t tileRow, void *data, size_t byteCount);
                
                /**
                 * Reads an arbitrary region of the image at an arbitrary scale. The source
                 * data will be scaled to meet <code>dstW</code> and <code>dstH</code>.
                 *
                 * @param srcX  The source (unscaled) x-coordinate of the region
                 * @param srcY  The source (unscaled) y-coordinate of the region
                 * @param srcW  The source (unscaled) width of the region
                 * @param srcH  The source (unscaled) height of the region
                 * @param dstW  The output width
                 * @param dstH  The output size
                 * @param buf   Output buffer for the data of the specified tile
                 *
                 * @return  <code>true</code> if the read completed successfuly,
                 *          <code>false</code> if the operation was canceled asynchronously
                 *
                 * @see #read(int, int64_t, int64_t, byte[])
                 */
                virtual ReadResult read(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW,
                                        int dstH, void *buf, size_t byteCount) = 0;
                
                /**
                 * Performs an asynchronous read of a single tile from the dataset at the
                 * specified resolution level.
                 *
                 * @param level         The resolution level
                 * @param tileColumn    The tile column at the specified resolution level
                 * @param tileRow       The tile column at the specified resolution level
                 * @param callback      The callback listener that the data will be
                 *                      delivered to; may not be <code>null</code>.
                 *
                 * @return              requestId
                 */
                int asyncRead(int level, int64_t tileColumn, int64_t tileRow, class AsynchronousReadRequestListener *callback);
                
                /**
                 * Performs an asynchronous read of an arbitrary region of the image at an
                 * arbitrary scale. The source data will be scaled to meet <code>dstW</code>
                 * and <code>dstH</code>.
                 *
                 * @param srcX      The source (unscaled) x-coordinate of the region
                 * @param srcY      The source (unscaled) y-coordinate of the region
                 * @param srcW      The source (unscaled) width of the region
                 * @param srcH      The source (unscaled) height of the region
                 * @param dstW      The output width
                 * @param dstH      The output size
                 * @param callback  The callback listener that the data will be delivered
                 *                  to; may not be <code>null</code>.
                 *
                 * @return              requestId
                 *
                 * @see #asyncRead(int, int64_t, int64_t, AsynchronousReadRequestListener)
                 */
                int asyncRead(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW, int dstH, AsynchronousReadRequestListener *callback);
                
            private:
                void asyncRead(ReadRequest *retval);
                
            protected:
                /**
                 * Implements asynchronous cancel. This method is invoked by the actively
                 * servicing {@link ReadRequest} and should never be invoked externally.
                 *
                 * <P>The default implementation returns immediately.
                 */
                virtual void cancel();
                
                /**
                 * Fills the specified asynchronous {@link ReadRequest}.
                 *
                 * <P>This method will return immediately if the <code>TileReader</code> has
                 * been previously disposed or if the source or destination regions for the
                 * request are empty.
                 *
                 * @param request   The request to be filled.
                 */
                ReadResult fill(ReadRequest &request);
                
            private:
                /**
                 * Fills the asynchronous read request. This method should issue the
                 * {@link AsynchronousReadRequestListener#requestUpdate(int, byte[], int, int, int, int) AsynchronousReadRequest.requestUpdate}
                 * and
                 * {@link AsynchronousReadRequestListener#requestError(int, Throwable) AsynchronousReadRequest.requestError}
                 * callbacks as appropriate before this method returns. The
                 * {@link AsynchronousReadRequestListener#requestStarted(int) AsynchronousReadRequest.requestStarted},
                 * {@link AsynchronousReadRequestListener#requestCanceled(int) AsynchronousReadRequest.requestCanceled},
                 * and
                 * {@link AsynchronousReadRequestListener#requestCompleted(int) AsynchronousReadRequest.requestCompleted}
                 * will be issued as appropriate externally.
                 *
                 * <P>This method should always be invoked after being externally
                 * synchronized on <code>this->readLock</code>.
                 *
                 * @param request
                 */
                ReadResult fillImpl(ReadRequest &request);
                
                ReadResult fillDirect(ReadRequest &request);
                
            public:
                /**
                 * Returns the number of tile columns at native resolution.
                 *
                 * @return  The number of tile columns at native resolution.
                 */
                int64_t getNumTilesX() const;
                
                /**
                 * Returns the number of tile columns at the specified resolution level.
                 *
                 * @param level The resolution level
                 *
                 * @return  The number of tile columns at the specified resolution level.
                 */
                int64_t getNumTilesX(int level) const;
                
                /**
                 * Returns the number of tile rows at native resolution.
                 *
                 * @return  The number of tile rows at native resolution.
                 */
                int64_t getNumTilesY() const;
                
                /**
                 * Returns the number of tile rows at the specified resolution level.
                 *
                 * @param level The resolution level
                 *
                 * @return  The number of tile rows at the specified resolution level.
                 */
                int64_t getNumTilesY(int level) const;
                
                /**
                 * Returns the x-coordinate, at native resolution, of the specified tile.
                 *
                 * @param level         The resolution level
                 * @param tileColumn    The tile column.
                 *
                 * @return  The x-coordinate, at native resolution, of the specified tile.
                 */
                int64_t getTileSourceX(int level, int64_t tileColumn) const;
                
                /**
                 * Returns the y-coordinate, at native resolution, of the specified tile.
                 *
                 * @param level     The resolution level
                 * @param tileRow   The tile row.
                 *
                 * @return  The y-coordinate, at native resolution, of the specified tile.
                 */
                int64_t getTileSourceY(int level, int64_t tileRow) const;
                
                /**
                 * Returns the width, at native resolution, of the specified tile.
                 *
                 * @param level         The resolution level
                 * @param tileColumn    The tile column.
                 *
                 * @return  The width, at native resolution, of the specified tile.
                 */
                int64_t getTileSourceWidth(int level, int64_t tileColumn) const;
                
                /**
                 * Returns the height, at native resolution, of the specified tile.
                 *
                 * @param level     The resolution level
                 * @param tileRow   The tile row.
                 *
                 * @return  The height, at native resolution, of the specified tile.
                 */
                int64_t getTileSourceHeight(int level, int64_t tileRow) const;
                
                int64_t getTileColumn(int level, int64_t srcX) const;
                
                int64_t getTileRow(int level, int64_t srcY) const;
                
                /**
                 * Returns the pixel format of the data as it will be returned from the
                 * <code>read</code> and <code>asyncRead</code> methods.
                 *
                 * @return  The pixel format of the data
                 */
                virtual Format getFormat() const = 0;
                
                /**
                 * Returns the interleave mode of the data as it will be returned from the
                 * <code>read</code> and <code>asyncRead</code> methods.
                 *
                 * @return  The interleave mode of the data
                 */
                virtual Interleave getInterleave() const = 0;
                
                /**
                 * Disposes the <code>TileReader</code> and releases any allocated
                 * resources. Any pending asynchronous read requests will be aborted. The
                 * user should always cancel any in-progress asynchronous read request
                 * before invoking this method to avoid having to wait for the request to
                 * complete before this method can return.
                 */
                void dispose();
                
                /**
                 * Implementation specific disposal. Implementations should always invoke
                 * <code>super.disposeImpl()</code>.
                 *
                 * <P>The default implementation marks {@link #valid} as <code>false</code>.
                 *
                 * <P>This method will always be externally synchronized on
                 * <code>this->readLock</code>.
                 */
            protected:
                virtual void disposeImpl();
                
            public:
                /**
                 * Returns the maximum number of resolution levels supported by this tile
                 * source.
                 *
                 * @return  The maximum number of resolution levels supported by this tile
                 *          source.
                 */
                int getMaxNumResolutionLevels() const;
                
                /**
                 * Returns a flag indicating whether or not the source is multi-resolution.
                 * If <code>true</code> the source can decode the image at resolutions other
                 * than the native resolution without subsampling. If <code>false</code> all
                 * tiles at a resolution other than the native resolution must be
                 * subsampled.
                 *
                 * <P>Use of a
                 * {@link com.atakmap.android.maps.tilesets.cache.TileCache TileCache}
                 * does not make a <code>TileReader</code> multi-resolution.
                 *
                 * @return  <code>true</code> if the source contains tiles at multiple
                 *          resolutions, <code>false</code> otherwise.
                 */
                bool isMultiResolution() const;
                
            protected:
                /**
                 * Returns a flag indicating whether or not the specified request has been
                 * canceled.
                 *
                 * @param request   An asynchronous read request
                 *
                 * @return  <code>true</code> if the request has been canceled,
                 *          <code>false</code> otherwise.
                 */
                bool isCanceled(const ReadRequest &request) const;
                
                /**
                 * Dispatches an update for the specified asynchronous read request.
                 *
                 * @param request   The request
                 * @param data      The update data; contains <code>dstW*dstH</code> pixels
                 * @param dstX      The x-coordinate for the update data in the destination
                 *                  pixel buffer
                 * @param dstY      The y-coordinate for the update data in the destination
                 *                  pixel buffer
                 * @param dstW      The width of the update data
                 * @param dstH      The height of the udpdate data
                 */
                void dispatchUpdate(ReadRequest &request, const void *data, size_t dataSize, int dstX, int dstY, int dstW, int dstH);
                
                /**
                 * Dispatches an error for the specified asynchronous read request.
                 *
                 * @param request   The request
                 * @param error     The error that occurred.
                 */
                void dispatchError(ReadRequest &request, const char *what);
                
                /**
                 * Returns the transfer size, in bytes, for a buffer of <code>width</code>
                 * by <code>height</code> pixels.
                 *
                 * <P>Defaults to <code>this->getPixelSize()*width*height</code>.
                 *
                 * @param width     The width of the output buffer
                 * @param height    The height of the output buffer
                 *
                 * @return  The number of bytes required for an output buffer capable of
                 *          holding <code>width</code> by <code>height</code> pixels.
                 */
                int getTransferSize(int width, int height) const;
            public:
                /**
                 * Returns the size of a pixel, in bytes.
                 *
                 * @return  The size of a pixel, in bytes.
                 */
                int getPixelSize() const;
                
                // cache support
                
                /*TODO--TileCacheData.Allocator getTileCacheDataAllocator() {
                    return null;
                }
                
                public TileCacheData.Compositor getTileCacheDataCompositor() {
                    return null;
                }
                
                public TileCacheData.Serializer getTileCacheDataSerializer() {
                    return null;
                }*/
                
                /**
                 * Returns the minimum RSET level where the tile cache will be employed. Any
                 * requested level less than the returned value will be read and subsampled
                 * directly from the dataset every time.
                 *
                 * @return
                 */
                int getMinCacheLevel() const;
                
            protected:
                /*TODO--TileCache.ReadCallback asReadCallback(final ReadRequest request) {
                    return new TileCache.ReadCallback() {
                        public boolean canceled() {
                            return TileReader.this->isCanceled(request);
                        }
                        
                        public void update(int dstX, int dstY, int dstW, int dstH, byte[] data, int off,
                                           int len) {
                            
                            TileReader.this->dispatchUpdate(request,
                                                           data,
                                                           dstX, dstY,
                                                           dstW, dstH);
                        }
                    };
                }*/
                
                /**************************************************************************/
            public :
                /**
                 * Returns the number of resolution levels possible given the specified
                 * image and tile dimensions.
                 *
                 * @param width         The image width
                 * @param height        The image height
                 * @param tileWidth     The tile width
                 * @param tileHeight    The tile height
                 *
                 * @return  The number of resolution levels possible given the specified
                 *          image and tile dimensions.
                 */
                static int getNumResolutionLevels(int64_t width, int64_t height, int64_t tileWidth, int64_t tileHeight);
            protected :
                /**
                 * Returns the pixel size of the specified format, in bytes.
                 * 
                 * @param format    The pixel format
                 * 
                 * @return  The pixel size of the specified format, in bytes.
                 */
                static int getPixelSize(Format format);
                
                
                /**************************************************************************/
                
            private:
                static void releaseAyncIOTask(TileReader *reader, ReadRequest *request);
            };
            
            /**
             * An asynchronous read request. Defines the region or tile to be read and
             * provides a mechanism to cancel the request asynchronously.
             *
             * @author Developer
             */
            class TileReader::ReadRequest {
                
                friend TileReader;
                
                /**
                 * The ID for the request. IDs are monotonically increasing per
                 * <code>TileReader</code>.
                 */
            public:
                const int id;
                
            private:
                TAK::Engine::Thread::Mutex mutex_;
                
                TileReader *owner_;
                
                bool canceled_;
                bool servicing_ = false;
                
                /**
                 * The source (unscaled) x-coordinate of the region to be read.
                 */
            public:
                const int64_t srcX;
                /**
                 * The source (unscaled) y-coordinate of the region to be read.
                 */
                const int64_t srcY;
                /**
                 * The source (unscaled) width of the region to be read.
                 */
                const int64_t srcW;
                /**
                 * The source (unscaled) height of the region to be read.
                 */
                const int64_t srcH;
                /**
                 * The output width of the region.
                 */
                const int dstW;
                /**
                 * The output height of the region.
                 */
                const int dstH;
                
                /**
                 * The tile row to be read. Will be <code>-1</code> if the read request
                 * was made over an arbitrary region.
                 */
                const int64_t tileRow;
                /**
                 * The tile column to be read. Will be <code>-1</code> if the read
                 * request was made over an arbitrary region.
                 */
                const int64_t tileColumn;
                /**
                 * The tile level to be read. Will be <code>-1</code> if the read
                 * request is over an arbitrary region.
                 */
                const int level;
                
            private:
                AsynchronousReadRequestListener *callback_;
                
                ReadRequest(TileReader *owner, int id, int level, int64_t tileColumn, int64_t tileRow,
                            AsynchronousReadRequestListener *callback)
                : ReadRequest(owner,
                            id,
                            owner->getTileSourceX(level, tileColumn),
                            owner->getTileSourceY(level, tileRow),
                            owner->getTileSourceWidth(level, tileColumn),
                            owner->getTileSourceHeight(level, tileRow),
                            owner->getTileWidth(level, tileColumn),
                            owner->getTileHeight(level, tileRow),
                            level,
                            tileColumn,
                            tileRow,
                            callback) { }
                
                
                ReadRequest(TileReader *owner, int id, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW, int dstH, AsynchronousReadRequestListener *callback)
                : ReadRequest(owner, id, srcX, srcY, srcW, srcH, dstW, dstH, -1, -1, -1, callback) { }
                
                ReadRequest(TileReader *owner, int id, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW, int dstH,
                            int level, int64_t tileColumn, int64_t tileRow, AsynchronousReadRequestListener *callback);
                
            public:
                /**
                 * Cancels the read request. If the request is currently be serviced, an
                 * attempt is made to abort the in-progress operation.
                 *
                 * <P>This method may be invoked from any thread.
                 */
                void cancel();
            
                void service();
            };
            
            /**
             * Callback interface for asynchronous reads.
             *
             * @author Developer
             */
            class TileReader::AsynchronousReadRequestListener {
            public:
                /**
                 * This method is invoked when the read request is created.
                 *
                 * @param request   The request that was created
                 */

                virtual void requestCreated(int id) = 0;

                /**
                 * This method is invoked immediately before a read request begins being
                 * serviced.
                 *
                 * @param id    The ID of the read request about to be serviced.
                 */
                virtual void requestStarted(int id) = 0;
                
                /**
                 * This method is invoked while the read request is being serviced to
                 * deliver the data that is being read. Coordinates specified are always
                 * relative to the output buffer. This method may be invoked multiple
                 * times while the request is being serviced.
                 *
                 * @param id    The ID of the read request currently being serviced
                 * @param data  The data for the update
                 * @param dstX  The x-coordinate in the full output buffer of the update
                 *              data
                 * @param dstY  The y-coordinate in the full output buffer of the update
                 *              data
                 * @param dstW  The width in the full output buffer of the update data
                 * @param dstH  The height in the full output buffer of the update data
                 */
                virtual void requestUpdate(int id, const void *data, size_t dataSize, int dstX, int dstY, int dstW, int dstH) = 0;
                
                /**
                 * This method is invoked when the request is successfully completed.
                 * This method will only be invoked for a read request that the
                 * {@link #requestStarted(int)} method had been previously invoked for.
                 *
                 * @param id    The ID of the read request that completed
                 */
                virtual void requestCompleted(int id) = 0;
                
                /**
                 * This method is invoked if the read request was canceled while being
                 * serviced. This method will only be invoked for a read request that
                 * the {@link #requestStarted(int)} method had been previously invoked
                 * for.
                 *
                 * @param id    The ID of the read request that was canceled
                 */
                virtual void requestCanceled(int id) = 0;
                
                /**
                 * This method is invoked if an error occurs while servicing the read
                 * request. This method will only be invoked for a read request that
                 * the {@link #requestStarted(int)} method had been previously invoked
                 * for.
                 *
                 * @param id    The ID of the read request that was canceled
                 * @param error The error that occurred.
                 */
                virtual void requestError(int id, const char *what) = 0;
            }; // AsynchronousReadRequestListener
            
            /**
             * The asynchronous I/O thread for use by one or more
             * <code>TileReader</code> instances. The same instance can be utilized by
             * multiple <code>TileReader</code> objects to ensure data reading and
             * delivery in series rather than in parallel. Forcing request servicing
             * into series rather than in parallel can be advantageous in memory
             * constrained environments when trying to render virtual mosaics from
             * multiple files/tile readers onto a single canvas.
             *
             * @author Developer
             */
            class TileReader::AsynchronousIO : TAK::Engine::Util::NonCopyable {
                
                friend TileReader;
                
            public:
                typedef std::vector<uint8_t> ReadBuffer;
                
            private:
                class Task {
                    friend AsynchronousIO;
                    
                public:
                    Task()
                    : reader_(NULL),
                    request_(NULL),
                    action_(NULL) { }
                    
                    Task(TileReader *reader, ReadRequest *request, void (*action)(TileReader *, ReadRequest *))
                    : reader_(reader),
                    request_(request),
                    action_(action) { }
                    
                    Task(const Task &other)
                    : reader_(other.reader_),
                    request_(other.request_),
                    action_(other.action_) { }
                    
                    const Task &operator=(const Task &rhs) {
                        reader_ = rhs.reader_;
                        request_ = rhs.request_;
                        action_ = rhs.action_;
                        return *this;
                    }
                    
                    bool valid() const;
                    void run();
                    void cancel();
                    
                private:
                    TileReader *reader_;
                    ReadRequest *request_;
                    void (*action_)(TileReader *, ReadRequest *);
                };
                
                std::shared_ptr<TAK::Engine::Thread::Thread> thread_;
                
                typedef std::list<Task> TaskList;
                
                TaskList tasks_;
                std::shared_ptr<util::SyncObject> sync_on_;
                
                Task current_task_;
                
                bool dead_;
                bool started_;
                
                ReadBuffer read_buffer_;
                
            public:
                AsynchronousIO();
                
            public:
                AsynchronousIO(const std::shared_ptr<util::SyncObject> &syncOn);
                
            private:
                ReadBuffer &getReadBuffer(int size) {
                    if (this->read_buffer_.size() < static_cast<std::size_t>(size)) {
                        this->read_buffer_.resize(static_cast<std::size_t>(size));
                    }
                    return this->read_buffer_;
                }
                
                /**
                 * Aborts all unserviced tasks and kills the thread. If a task is
                 * currently being serviced, it will complete before the thread exits.
                 * The thread may be restarted by queueing a new task.
                 */
            public:
                void release();
                
                /**
                 * Aborts all unserviced tasks made by the specified reader. If
                 * <code>null</code> tasks for all readers are aborted.
                 *
                 * @param reader
                 */
                void abortRequests(TileReader *reader);
                
                void cancelRequest(int id);
                
            private:
                static void queueRequestAction(TileReader *reader, TileReader::ReadRequest *request);
                
                void queueRequest(TileReader *reader, TileReader::ReadRequest *request);
                
                void runLater(TileReader &reader, Task task);
                
                static void *threadRun(void *threadData);
                void runImpl();
                
            }; // AsynchronousIO
        }
    }
}

#endif
