#ifndef TAK_ENGINE_RASTER_TILEREADER_TILEREADER2_H_INCLUDED
#define TAK_ENGINE_RASTER_TILEREADER_TILEREADER2_H_INCLUDED

#include <cstdint>
#include <list>
#include <map>
#include <string>
#include <vector>

#include "thread/Cond.h"
#include "thread/Mutex.h"
#include "thread/ThreadPool.h"

#include "port/Platform.h"
#include "port/Collection.h"
#include "port/String.h"
#include "renderer/Bitmap2.h"
#include "core/Control.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileReader {

                /**
                 * Provides tile-based access to raster data. Synchronous and asynchronous data
                 * access methods are provided.
                 *
                 * <P>Data returned is intended for visualization purposes and the allowed pixel
                 * formats represent this.
                 *
                 * @author Developer
                 */
                class ENGINE_API TileReader2 {
                   public:
                    class ReadRequest;
                    class AsynchronousReadRequestListener;
                    class AsynchronousIO;
                    class ReadRequestPrioritizer;
                    /**************************************************************************/
                   protected:
                    /**
                     * Defers initialization of asynchronousio, installAsynchronousIO() must be invoked before any other methods are usable.
                     */
                    TileReader2(const char *uri) NOTHROWS;
                    virtual ~TileReader2();
                   public:
                    /**
                     * Obtains the URI for the data.
                     *
                     * @param value Reference to be initialized with the data URI
                     */
                    virtual Util::TAKErr getUri(Port::String &value) NOTHROWS;

                    /**
                     * Obtains the width of the image at its native resolution.
                     *
                     * @param value output - width of the image at its native resolution.
                     */
                    virtual Util::TAKErr getWidth(int64_t *value) NOTHROWS = 0;

                    /**
                     * Obtains the width of the image at the specified level.
                     *
                     * @param value output - width of the image at the specified level.
                     * @param level The level
                     */
                    virtual Util::TAKErr getWidth(int64_t *value, const size_t level) NOTHROWS;

                    /**
                     * Obtains the height of the image at its native resolution.
                     *
                     * @param value output - height of the image at its native resolution.
                     */
                    virtual Util::TAKErr getHeight(int64_t *value) NOTHROWS = 0;

                    /**
                     * Obtains the height of the image at the specified level.
                     *
                     * @param value output - the height of the image at the specified level.
                     * @param the level
                     */
                    virtual Util::TAKErr getHeight(int64_t *value, const size_t level) NOTHROWS;

                    /**
                     * Obtains the nominal width of a tile.
                     *
                     * @param value output - nominal width of a tile.
                     */
                    virtual Util::TAKErr getTileWidth(size_t *value) NOTHROWS = 0;

                    /**
                     * Obtains the width of the tile in the specified column at the specified
                     * level.
                     *
                     * @param value output - width of the tile in the specified column at the specified
                     *          level.
                     * @param level the level
                     * @param tileColumn the column
                     */
                    Util::TAKErr getTileWidth(size_t *value, const size_t level, const int64_t tileColumn) NOTHROWS;

                    /**
                     * Returns the nominal height of a tile.
                     *
                     * @param value output - The nominal height of a tile.
                     */
                    virtual Util::TAKErr getTileHeight(size_t *value) NOTHROWS = 0;

                    /**
                     * Obtains the height of the tile in the specified row at the specified
                     * level.
                     *
                     * @param value output - height of the tile in the specified row at the specified
                     *          level.
                     */
                    Util::TAKErr getTileHeight(size_t *value, const size_t level, const int64_t tileRow) NOTHROWS;

                    /**
                     * Reads the specified tile at the specified level and stores the data in
                     * the specified array. The returned data will have dimensions consistent
                     * with getTileWidth() and getTileHeight()
                     * for the specified level, tile column and tile row.
                     *
                     * @param value         the result of the read
                     * @param level         The resolution level
                     * @param tileColumn    The tile column
                     * @param tileRow       The tile row
                     * @param data          Output buffer for the data of the specified tile
                     *
                     * @return TE_Ok if read was successful, various codes on failure
                     */
                    virtual Util::TAKErr read(uint8_t *data, const size_t level, const int64_t tileColumn, const int64_t tileRow) NOTHROWS;
                    /**
                     * Reads an arbitrary region of the image at an arbitrary scale. The source
                     * data will be scaled to meet dstW and dstH.
                     *
                     * @param value the result of the read
                     * @param srcX  The source (unscaled) x-coordinate of the region
                     * @param srcY  The source (unscaled) y-coordinate of the region
                     * @param srcW  The source (unscaled) width of the region
                     * @param srcH  The source (unscaled) height of the region
                     * @param dstW  The output width
                     * @param dstH  The output size
                     * @param buf   Output buffer for the data of the specified tile
                     *
                     * @return TE_Ok if read was successful, various codes on failure
                     */
                    virtual Util::TAKErr read(uint8_t *buf, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH,
                                              const size_t dstW, const size_t dstH) NOTHROWS = 0;

                   private:
                    int nextRequestId() NOTHROWS;

                   protected:
                    /**
                     * Implements asynchronous cancel. This method is invoked by the actively
                     * servicing {@link ReadRequest} and should never be invoked externally.
                     *
                     * <P>The default implementation returns immediately.
                     */
                    virtual Util::TAKErr cancel() NOTHROWS;
                    /**
                     * Fills the specified asynchronous ReadRequest.
                     *
                     * <P>This method will return immediately with TE_Canceled if the <code>TileReader</code> has
                     * been previously disposed or if the source or destination regions for the
                     * request are empty.
                     *
                     * @param request   The request to be filled.
                     * @return TE_Ok on success, various codes on failure
                     */
                    Util::TAKErr fill(uint8_t *buffer, ReadRequest &request) NOTHROWS;

                   public:
                    /**
                     * Obtains the number of tile columns at native resolution.
                     *
                     * @param value output - number of tile columns at native resolution.
                     */
                    Util::TAKErr getNumTilesX(int64_t *value) NOTHROWS;

                    /**
                     * Obtains the number of tile columns at the specified resolution level.
                     *
                     *
                     * @param value output - number of tile columns at the specified resolution level.
                     * @param level The resolution level
                     */
                    Util::TAKErr getNumTilesX(int64_t *value, const size_t level) NOTHROWS;

                    /**
                     * Obtains the number of tile rows at native resolution.
                     *
                     * @param value output - number of tile rows at native resolution.
                     */
                    Util::TAKErr getNumTilesY(int64_t *value) NOTHROWS;

                    /**
                     * Obtains the number of tile rows at the specified resolution level.
                     *
                     * @param value output - number of tile rows at the specified resolution level.
                     * @param level The resolution level
                     */
                    Util::TAKErr getNumTilesY(int64_t *value, const size_t level) NOTHROWS;

                    /**
                     * Obtains the x-coordinate, at native resolution, of the specified tile.
                     *
                     * @param value output - x-coordinate, at native resolution, of the specified tile.
                     * @param level         The resolution level
                     * @param tileColumn    The tile column.
                     */
                    Util::TAKErr getTileSourceX(int64_t *value, const size_t level, const int64_t tileColumn) NOTHROWS;

                    /**
                     * Obtains the y-coordinate, at native resolution, of the specified tile.
                     *
                     * @param value output - y-coordinate, at native resolution, of the specified tile.
                     * @param level     The resolution level
                     * @param tileRow   The tile row.
                     */
                    Util::TAKErr getTileSourceY(int64_t *value, const size_t level, const int64_t tileRow) NOTHROWS;

                    /**
                     * Returns the width, at native resolution, of the specified tile.
                     *
                     * @param value output - width, at native resolution, of the specified tile.
                     * @param level         The resolution level
                     * @param tileColumn    The tile column.
                     */
                    Util::TAKErr getTileSourceWidth(int64_t *value, const size_t level, const int64_t tileColumn) NOTHROWS;

                    /**
                     * Returns the height, at native resolution, of the specified tile.
                     *
                     * @param value output - height, at native resolution, of the specified tile.
                     * @param level     The resolution level
                     * @param tileRow   The tile row.
                     */
                    Util::TAKErr getTileSourceHeight(int64_t *value, const size_t level, const int64_t tileRow) NOTHROWS;

                    Util::TAKErr getTileColumn(int64_t *value, const size_t level, const int64_t srcX) NOTHROWS;

                    Util::TAKErr getTileRow(int64_t *value, const size_t level, const int64_t srcY) NOTHROWS;

                    /**
                     * Returns the pixel format of the data as it will be returned from the
                     * read and asyncRead methods.
                     *
                     * @param format output - pixel format of the data
                     */
                    virtual Util::TAKErr getFormat(Renderer::Bitmap2::Format *format) NOTHROWS = 0;
                   public:
                    /**
                     * Returns the maximum number of resolution levels supported by this tile
                     * source.
                     *
                     * @param value output - maximum number of resolution levels supported by this tile
                     *          source.
                     */
                    virtual Util::TAKErr getMaximumNumResolutionLevels(size_t *value) NOTHROWS;

                    /**
                     * Obtains a flag indicating whether or not the source is multi-resolution.
                     * If <code>true</code> the source can decode the image at resolutions other
                     * than the native resolution without subsampling. If <code>false</code> all
                     * tiles at a resolution other than the native resolution must be
                     * subsampled.
                     *
                     * <P>Use of a
                     * TileCache does not make a <code>TileReader</code> multi-resolution.
                     *
                     * @param value output - set to true if the source contains tiles at multiple
                     *          resolutions, false otherwise.
                     */
                    virtual Util::TAKErr isMultiResolution(bool *value) NOTHROWS;

                   protected:
                    /**
                     * Obtains a flag indicating whether or not the specified request has been
                     * canceled.
                     *
                     * @param value output - true if the request is canceled, false otherwise
                     * @param request   An asynchronous read request
                     */
                    virtual Util::TAKErr isCanceled(bool *value, const ReadRequest &request) NOTHROWS;

                    /**
                     * Returns the transfer size, in bytes, for a buffer of <code>width</code>
                     * by <code>height</code> pixels.
                     *
                     * <P>Defaults to getPixelSize()*width*height.
                     *
                     * @param value output - set to the number of bytes required for an output buffer capable of
                     *          holding <code>width</code> by <code>height</code> pixels.
                     * @param width     The width of the output buffer
                     * @param height    The height of the output buffer
                     *
                     */
                    virtual Util::TAKErr getTransferSize(size_t *value, const size_t width, const size_t height) NOTHROWS;

                   public:
                    /**
                     * Obtains the size of a pixel, in bytes.
                     *
                     * @param value output - set to the size of a pixel, in bytes.
                     */
                    virtual Util::TAKErr getPixelSize(size_t *value) NOTHROWS;

                    /**
                     * Instructs the <code>TileReader</code> that more than one tile may be
                     * consecutively requested.
                     *
                     * <P>This merely serves as a hint to the reader; tiles may be requested
                     * regardless of whether <code>start()</code> had previously been invoked.
                     */
                    virtual Util::TAKErr start() NOTHROWS;

                    /**
                     * Instructs the <code>TileReader</code> that the bulk requests indicated
                     * by <code>start()</code> will cease.
                     *
                     * <P>This merely serves as a hint to the reader; tiles may continue to be
                     * requested even after <code>stop()</code> has been invoked.
                     */
                    virtual Util::TAKErr stop() NOTHROWS;

                    /**
                     * Returns the version for the specified tile. This value may change over
                     * the duration of the runtime, indicating that tiles previously read with
                     * a different version are not the most up-to-date and should be reloaded.
                     * Version numbers should be monotonically increasing.
                     *
                     * @param value output - set to the current tiles version. The default implementation always
                     *          returns <code>0</code>
                     * @param level         The level
                     * @param tileColumn    The tile column
                     * @param tileRow       The tile row
                     */
                    virtual Util::TAKErr getTileVersion(int64_t *value, const size_t level, const int64_t tileColumn, const int64_t tileRow) NOTHROWS;

                   protected:
                    /**
                     * Registers the specified control; may only be invoked before the constructor returns.
                     */
                    Util::TAKErr registerControl(Core::Control *control) NOTHROWS;

                   public:
                    Util::TAKErr getControl(Core::Control **control, const char *type) NOTHROWS;

                    Util::TAKErr getControls(Port::Collection<Core::Control *> &ctrls) NOTHROWS;

                   protected:
                    typedef void (*AsyncRunnable)(TileReader2 *, void *, TileReader2::AsynchronousIO &);

                    /**************************************************************************/
                   private:
                    static void ReadRequest_run(uint8_t *readBuffer, ReadRequest &request) NOTHROWS;
                    static void ReadRequest_abort(ReadRequest &request) NOTHROWS;

                   protected:
                    const Port::String uri;
                    int asyncRequestId;

                    /**
                     * The object used for synchronization of reads.
                     */
                    Thread::Mutex readLock;

                    /**
                     * The object used for synchronization of reads.
                     */
                    Thread::Mutex idLock;

                    /**
                     * Flag indicating whether or not this instance is valid. Remains
                     * <code>true</code> until dispose() is invoked. Reads should not be
                     * permitted if <code>false</code>.
                     */
                    bool valid;

                   private:

                    std::map<std::string, Core::Control *> controls;
                };  // TileReader

                typedef std::unique_ptr<TileReader2, void (*)(const TileReader2 *)> TileReader2Ptr;

                /**
                 * An asynchronous read request. Defines the region or tile to be read and
                 * provides a mechanism to cancel the request asynchronously.
                 *
                 * @author Developer
                 */
                class TileReader2::ReadRequest
                {
                   public:
                    ReadRequest(const std::shared_ptr<TileReader2> &owner, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH,
                                const size_t dstW, const size_t dstH, const int level, const int64_t tileColumn, const int64_t tileRow,
                                AsynchronousReadRequestListener *callback) NOTHROWS;
                    ReadRequest(const std::shared_ptr<TileReader2> &owner, const int level, const int64_t tileColumn, const int64_t tileRow,
                                AsynchronousReadRequestListener *callback) NOTHROWS;
                   private:
                    ReadRequest(const std::shared_ptr<TileReader2> &owner, const int id, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH,
                                const size_t dstW, const size_t dstH, const int level, const int64_t tileColumn, const int64_t tileRow,
                                AsynchronousReadRequestListener *callback, bool b) NOTHROWS;

                   public:
                    /**
                     * Cancels the read request. If the request is currently be serviced, an
                     * attempt is made to abort the in-progress operation.
                     *
                     * <P>This method may be invoked from any thread.
                     */
                    void cancel() NOTHROWS;

                   private:
                    std::shared_ptr<TileReader2> owner;

                   public:
                    /**
                     * The ID for the request. IDs are monotonically increasing per
                     * <code>TileReader</code>.
                     */
                    const int id;

                    bool canceled;
                    bool servicing;

                    /** The source (unscaled) x-coordinate of the region to be read. */
                    const int64_t srcX;
                    /** The source (unscaled) y-coordinate of the region to be read. */
                    const int64_t srcY;
                    /** The source (unscaled) width of the region to be read. */
                    const int64_t srcW;
                    /** The source (unscaled) height of the region to be read. */
                    const int64_t srcH;
                    /** The output width of the region. */
                    const size_t dstW;
                    /** The output height of the region. */
                    const size_t dstH;

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

                    Thread::Mutex lock;
                    AsynchronousReadRequestListener *callback;

                private :
                    friend class TileReader2;
                    friend class TileReader2::AsynchronousIO;
                };

                /**
                 * Callback interface for asynchronous reads.
                 *
                 * @author Developer
                 */
                class TileReader2::AsynchronousReadRequestListener {
                   protected:
                    virtual ~AsynchronousReadRequestListener() NOTHROWS = 0;

                   public:
                    /**
                     * This method is invoked when the read request is created.
                     *
                     * @param request   The request that was created
                     */
                    virtual void requestCreated(const int id) NOTHROWS = 0;

                    /**
                     * This method is invoked immediately before a read request begins being
                     * serviced.
                     *
                     * @param id    The ID of the read request about to be serviced.
                     */
                    virtual void requestStarted(const int id) NOTHROWS = 0;

                    /**
                     * This method is invoked while the read request is being serviced to
                     * deliver the data that is being read. Coordinates specified are always
                     * relative to the output buffer. This method may be invoked multiple
                     * times while the request is being serviced.
                     *
                     * @param id    The ID of the read request currently being serviced
                     * @param data  The data for the update
                     */
                    virtual void requestUpdate(const int id, const Renderer::Bitmap2 &data) NOTHROWS = 0;

                    /**
                     * This method is invoked when the request is successfully completed.
                     * This method will only be invoked for a read request that the
                     * {@link #requestStarted(int)} method had been previously invoked for.
                     *
                     * @param id    The ID of the read request that completed
                     */
                    virtual void requestCompleted(const int id) NOTHROWS = 0;

                    /**
                     * This method is invoked if the read request was canceled while being
                     * serviced. This method will only be invoked for a read request that
                     * the {@link #requestStarted(int)} method had been previously invoked
                     * for.
                     *
                     * @param id    The ID of the read request that was canceled
                     */
                    virtual void requestCanceled(const int id) NOTHROWS = 0;

                    /**
                     * This method is invoked if an error occurs while servicing the read
                     * request. This method will only be invoked for a read request that
                     * the {@link #requestStarted(int)} method had been previously invoked
                     * for.
                     *
                     * @param id    The ID of the read request that was canceled
                     * @param error The error that occurred.
                     */
                    virtual void requestError(const int id, const Util::TAKErr code, const char *msg) NOTHROWS = 0;
                };  // AsynchronousReadRequestListener

                class TileReader2::ReadRequestPrioritizer
                {
                public :
                    virtual ~ReadRequestPrioritizer() NOTHROWS = 0;
                public :
                    /**
                     * @return  `true` if `a` is higher priority than `b`, `false` otherwise
                     */
                    virtual bool compare(const TileReader2::ReadRequest& a, const TileReader2::ReadRequest& b) NOTHROWS = 0;
                };

                typedef std::unique_ptr<TileReader2::ReadRequestPrioritizer, void(*)(const TileReader2::ReadRequestPrioritizer *)> ReadRequestPrioritizerPtr;

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
                class TileReader2::AsynchronousIO {
                   public:
                    typedef bool (*AsyncCancelCheck)(TileReader2 *reader, AsyncRunnable requestAction, void *requestOpaque,
                                                     void *cancelOpaque);

                    AsynchronousIO() NOTHROWS;
                    AsynchronousIO(const int64_t maxIdle) NOTHROWS;
                    ~AsynchronousIO() NOTHROWS;

                   public:
                    /**
                     * Aborts all unserviced tasks and kills the thread. If a task is
                     * currently being serviced, it will complete before the thread exits.
                     * The thread may be restarted by queueing a new task.
                     */
                    Util::TAKErr release() NOTHROWS;

                    /**
                     * Aborts all unserviced tasks made by the specified reader. If
                     * <code>null</code> tasks for all readers are aborted.
                     *
                     * @param reader
                     */
                    Util::TAKErr abortRequests(TileReader2 *reader) NOTHROWS;

                    /**
                     * Queues an asynchronous read.
                     *
                     * @param request   The read request
                     * 
                     * @return  TE_Ok on success, others on failure
                     */
                    Util::TAKErr asyncRead(const std::shared_ptr<ReadRequest> &request) NOTHROWS;

                    void setReadRequestPrioritizer(const TileReader2& reader, ReadRequestPrioritizerPtr&& prioritizer) NOTHROWS;
                   private:

                    Util::TAKErr runImpl() NOTHROWS;

                    static void *threadRun(void *threadData);

                   private:
                    std::map<const TileReader2 *, ReadRequestPrioritizerPtr> requestPrioritizers;
                    std::map<const TileReader2 *, std::vector<std::shared_ptr<TileReader2::ReadRequest>>> tasks;
                    // Valid only when holding lock
                    std::shared_ptr<TileReader2::ReadRequest> currentTask;
                    Thread::Mutex syncOn;
                    Thread::CondVar cv;
                    Thread::ThreadPoolPtr thread;
                    bool dead;
                    bool started;
                    const int64_t maxIdle;

                    friend class TileReader2;
                };  // AsynchronousIO

                // Globals

                /**
                 * Obtains the number of resolution levels possible given the specified
                 * image and tile dimensions.
                 *
                 * @param value output - the number of resolution levels possible given the specified
                 *          image and tile dimensions.
                 * @param width         The image width
                 * @param height        The image height
                 * @param tileWidth     The tile width
                 * @param tileHeight    The tile height
                 */
                ENGINE_API Util::TAKErr TileReader2_getNumResolutionLevels(size_t *value, const int64_t width, const int64_t height, const int64_t tileWidth, const int64_t tileHeight) NOTHROWS;
                /**
                 * Obtains the pixel size of the specified format, in bytes.
                 *
                 * @param value output - pixel size of the specified format, in bytes.
                 * @param format    The pixel format
                 */
                ENGINE_API Util::TAKErr TileReader2_getPixelSize(size_t *value, const Renderer::Bitmap2::Format format) NOTHROWS;
                ENGINE_API TileReader2::AsynchronousIO *TileReader2_getMasterIOThread() NOTHROWS;
            }  // namespace TileReader
        }  // namespace Raster
    }  // namespace Engine
}  // namespace TAK
#endif
