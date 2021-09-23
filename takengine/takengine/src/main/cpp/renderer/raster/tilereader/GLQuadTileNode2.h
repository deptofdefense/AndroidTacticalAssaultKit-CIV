#pragma once

#include "math/Rectangle.h"
#include "port/Platform.h"
#include "raster/DatasetProjection2.h"
#include "raster/ImageInfo.h"
#include "raster/RasterDataAccess2.h"
#include "raster/tilereader/TileReader2.h"
#include "raster/tilereader/TileReaderFactory2.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLTextureCache2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/core/GLResolvable.h"
#include "renderer/core/GLResolvableMapRenderable2.h"
#include "thread/Mutex.h"
#include <list>
#include <vector>

namespace TAK {
namespace Engine {
namespace Renderer {
namespace Raster {
namespace TileReader {

class GLQuadTileNode2;
typedef std::unique_ptr<GLQuadTileNode2, void (*)(const GLQuadTileNode2 *)> GLQuadTileNode2Ptr;

class TileReadRequestPrioritizer;

class ENGINE_API GLQuadTileNode2 : public Core::GLResolvableMapRenderable2,
                                   public TAK::Engine::Raster::TileReader::TileReader2::AsynchronousReadRequestListener,
                                   public TAK::Engine::Raster::RasterDataAccess2
{
   public:
    struct Initializer
    {
        virtual ~Initializer() = 0;
        /**
         * Perform initialization of the TileReader and projection pointers
         * based on information in provided ImageInfo and Factory options. reader and imprecise
         * must be populated; precise is optional. Return TE_Ok for success,
         * any other value for error.
         */
        virtual Util::TAKErr init(std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2> &reader,
                                  TAK::Engine::Raster::DatasetProjection2Ptr &imprecise,
                                  TAK::Engine::Raster::DatasetProjection2Ptr &precise, const TAK::Engine::Raster::ImageInfo *info,
                                  TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts) const = 0;
    };

    struct Options
    {
        bool textureCopyEnabled;
        bool childTextureCopyResolvesParent;
        GLTextureCache2 *textureCache;
        bool progressiveLoad;
        double levelTransitionAdjustment;
        bool textureBorrowEnabled;
        Options();
    };

   protected:
    // Forward declared impl-private interface
    class VertexResolver;
    class DefaultVertexResolver;
    class PreciseVertexResolver;

    struct GridVertex
    {
        TAK::Engine::Core::GeoPoint2 value;
        bool resolved;
        TAK::Engine::Math::Point2<double> projected;
        int projectedSrid;

        GridVertex();
    };

    class NodeCore
    {
       public:
        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2> tileReader;
        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO> asyncio;
        /** Projects between the image coordinate space and WGS84. */
        TAK::Engine::Raster::DatasetProjection2Ptr imprecise;
        /** Projects between the image coordinate space and WGS84. */
        TAK::Engine::Raster::DatasetProjection2Ptr precise;

        const TAK::Engine::Raster::ImageInfo imageInfo;
        Options options;
        
        
        
        TAK::Engine::Core::RenderContext *context;
        std::string uri;

        bool textureBorrowEnabled;
        bool textureCopyEnabled;

        /** local GSD for dataset */
        double gsd;

        std::unique_ptr<VertexResolver> vertexResolver;
        GLTextureCache2 *textureCache;

        /* corner coordinates of dataset NOT tile */
        /** upper-left corner for dataset */
        TAK::Engine::Core::GeoPoint2 upperLeft;
        /** upper-right corner for dataset */
        TAK::Engine::Core::GeoPoint2 upperRight;
        /** lower-right corner for dataset */
        TAK::Engine::Core::GeoPoint2 lowerRight;
        /** lower-left corner for dataset */
        TAK::Engine::Core::GeoPoint2 lowerLeft;

        GLuint frameBufferHandle;
        GLuint depthBufferHandle;

        /** minification filter to be applied to texture */
        int minFilter;
        /** magnification filter to be applied to texture */
        int magFilter;

        // modulation color components
        int color;
        float colorR;
        float colorG;
        float colorB;
        float colorA;

        bool debugDrawEnabled;
        bool enableTextureTargetFBO;
        bool loadingTextureEnabled;

        atakmap::math::Rectangle<double> drawROI[2];
        int drawPumpHemi;
        size_t drawPumpLevel;
        bool progressiveLoading;

        int64_t fadeTimerLimit;
        bool versionCheckEnabled;
        int tilesThisFrame;

        Thread::Mutex infoLock;

        std::unique_ptr<TileReadRequestPrioritizer> readRequestPrioritizer;
        Util::TAKErr status;

        TAK::Engine::Renderer::Core::Controls::SurfaceRendererControl *surfaceRenderer;
       private:
        NodeCore(TAK::Engine::Core::RenderContext *context, const TAK::Engine::Raster::ImageInfo &info, const Initializer &init, const std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2> &reader,
                 const std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO> &asyncio,
                 TAK::Engine::Raster::DatasetProjection2Ptr &imprecise, TAK::Engine::Raster::DatasetProjection2Ptr &precise,
                 const Options &opts) NOTHROWS;
       public :
        ~NodeCore() NOTHROWS;
       public:
        static Util::TAKErr create(NodeCore **v, TAK::Engine::Core::RenderContext *context, const TAK::Engine::Raster::ImageInfo *info,
                                   TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts, const Options &opts,
                                   bool failOnReaderFailedInit, const Initializer &init);
    };

   public:
    //
    static Util::TAKErr create(GLQuadTileNode2Ptr &value,
                               TAK::Engine::Core::RenderContext *context, const TAK::Engine::Raster::ImageInfo *info,
                               TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts, const Options &opts,
                               const Initializer &init);
    virtual ~GLQuadTileNode2();

   protected:
    static Util::TAKErr create(GLQuadTileNode2Ptr &value, GLQuadTileNode2 *parent, int idx);

   private:
    GLQuadTileNode2();
    Util::TAKErr init(GLQuadTileNode2 *parent, int idx, NodeCore *core);

   public:
    void setColor(int color);
    TAK::Engine::Raster::DatasetProjection2 *getDatasetProjection();
    TAK::Engine::Raster::TileReader::TileReader2 *getReader();
    void setLoadingTextureEnabled(bool en);

   protected:
    Util::TAKErr createDefaultVertexResolver(std::unique_ptr<VertexResolver> &resolver);
    /**
     * <B>IMPORTANT:</B> Must be invoked on the render thread.
     */
    Util::TAKErr set(int64_t tileColumn, int64_t tileRow, size_t level);
    void releaseTexture();
    Util::TAKErr borrowTexture(GLTexture2 **tex, GLQuadTileNode2 *ref, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH,
                               float *texCoords, size_t texGridWidth, size_t texGridHeight);
    void unborrowTexture(GLQuadTileNode2 *ref);
    Util::TAKErr getLoadingTexture(GLTexture2 **ret, float *texCoords, std::size_t texGridWidth, std::size_t texGridHeight);
    Util::TAKErr validateTexture();
    Util::TAKErr validateTexVerts();
    Util::TAKErr validateVertexCoords(const Core::GLGlobeBase &view);
    bool shouldResolve();
    bool useCachedTexture();
    void resolveTexture();
    void drawTexture(const Core::GLGlobeBase &view, GLTexture2 *tex, float *texCoords);
    const char *getTextureKey();
    void invalidateVertexCoords();
    void expandTexGrid();
    void drawImpl(const Core::GLGlobeBase &view);

   private:
    Util::TAKErr super_set(int64_t tileColumn, int64_t tileRow, size_t level);
    void super_release();
    Util::TAKErr super_getLoadingTexture(GLTexture2 **ret, float *texCoords, size_t texGridWidth, size_t texGridHeight);
    bool super_shouldResolve();
    void resetFadeTimer();
    Util::TAKErr super_resolveTexture();
    void draw(const Core::GLGlobeBase &view, const size_t level, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int64_t poiX, int64_t poiY);
    void setLCS(const Core::GLGlobeBase &view);
    void super_drawTexture(const Core::GLGlobeBase &view, GLTexture2 *tex, float *texCoords);
    void super_invalidateVertexCoords();
    void super_draw(const Core::GLGlobeBase &view);
    void debugDraw(const Core::GLGlobeBase &view);

    State getStateImpl();
    int recurseState();
    State super_getState();
    void super_suspend();
    void super_resume();
    bool checkRequest(int id);
    std::string toString(bool l);

    void cull(const int drawPump) NOTHROWS;

    // IO Callback support - render thread runnables
    struct IOCallbackOpaque;
    static void rendererIORunnable(void *opaque) NOTHROWS;
    void rendererIORunnableImpl(IOCallbackOpaque *iocb);
    void queueGLCallback(IOCallbackOpaque *iocb);

   public:
    // GLMapRenderable2
    virtual void draw(const Core::GLGlobeBase &view, const int renderPass) NOTHROWS;
    virtual void release() NOTHROWS;
    virtual int getRenderPass() NOTHROWS;
    virtual void start() NOTHROWS;
    virtual void stop() NOTHROWS;

   public:
    // GLMapResolvable
    virtual State getState();
    virtual void suspend();
    virtual void resume();

   public:
    // TileReader2::AsynchronousReadRequestListener
    virtual void requestCreated(const int id) NOTHROWS;
    virtual void requestStarted(const int id) NOTHROWS;
    virtual void requestUpdate(const int id, const TAK::Engine::Renderer::Bitmap2 &data) NOTHROWS;
    virtual void requestCompleted(const int id) NOTHROWS;
    virtual void requestCanceled(const int id) NOTHROWS;
    virtual void requestError(const int id, const Util::TAKErr code, const char *msg) NOTHROWS;

   public:
    // RasterDataAccess2
    virtual Util::TAKErr getUri(Port::String &value) NOTHROWS;
    virtual Util::TAKErr imageToGround(TAK::Engine::Core::GeoPoint2 *ground, bool *isPrecise, const Math::Point2<double> &image) NOTHROWS;
    virtual Util::TAKErr groundToImage(Math::Point2<double> *image, bool *isPrecise, const TAK::Engine::Core::GeoPoint2 &ground) NOTHROWS;
    virtual Util::TAKErr getType(Port::String &value) NOTHROWS;
    virtual Util::TAKErr getSpatialReferenceId(int *value) NOTHROWS;
    virtual Util::TAKErr hasPreciseCoordinates(bool *value) NOTHROWS;
    virtual Util::TAKErr getWidth(int *value) NOTHROWS;
    virtual Util::TAKErr getHeight(int *value) NOTHROWS;
    virtual Util::TAKErr getImageInfo(TAK::Engine::Raster::ImageInfoPtr_const &info) NOTHROWS;

   protected:
    NodeCore *core_;

    /**
     * The id of the async read request currently outstanding. Valid access to the request is only guaranteed on
     * the render thread and only when currentRequestIdValid
     */
    std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::ReadRequest> current_request_;
    GLTexture2Ptr texture_;
    int64_t tile_row_;
    int64_t tile_column_;
    size_t level_;
    State state_;

    // source coordinate space (unscaled)
    int64_t tile_src_x_;
    int64_t tile_src_y_;
    int64_t tile_src_width_;
    int64_t tile_src_height_;

    size_t tile_width_;
    size_t tile_height_;

    /**
     * The texture coordinate for the texture in the order lower-left,
     * lower-right, upper-right, upper-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     * Size is textureCoordinatesCapacity
     */
    std::unique_ptr<float, void (*)(const float *)> texture_coordinates_;
    size_t texture_coordinates_capacity_;

    /**
     * The texture coordinate for the texture in the order upper-left,
     * upper-right, lower-right, lower-left. Using this order relieves us of the
     * need to do a vertical flip of the raster data.
     */
    std::unique_ptr<float, void (*)(const float *)> vertex_coordinates_;
    size_t vertex_coordinates_capacity_;
    std::unique_ptr<uint16_t, void (*)(const uint16_t *)> gl_tex_coord_indices_;
    size_t gl_tex_coord_indices_capacity_;
    std::unique_ptr<float, void (*)(const float *)> loading_texture_coordinates_;
    size_t loading_texture_coordinates_capacity_;
    bool texture_coords_valid_;
    bool received_update_;

    int gl_tex_type_;
    int gl_tex_format_;
    size_t gl_tex_grid_width_;
    size_t gl_tex_grid_height_;
    size_t gl_tex_grid_vert_count_;
    size_t gl_tex_grid_idx_count_;

    int vertex_coord_srid_;
    int verts_draw_version_;
    bool vertex_coords_valid_;
    std::vector<GridVertex> grid_vertices_;

   private:
    Thread::Mutex queued_callbacks_mutex_;
    std::vector<IOCallbackOpaque *> queued_callbacks_;
    TAK::Engine::Core::GeoPoint2 centroid_;
    TAK::Engine::Math::Point2<double> centroid_proj_;
    std::list<std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::ReadRequest>> read_requests_;

    double min_lat_;
    double min_lng_;
    double max_lat_;
    double max_lng_;

   protected:
    bool touched_;
    int64_t tile_version_;

    /**
     * This node's children, in the order: upper-left, upper-right, lower-left, lower-right.
     */
    GLQuadTileNode2Ptr children_[4];

    size_t half_tile_width_;
    size_t half_tile_height_;

    GLQuadTileNode2 *parent_;
    GLQuadTileNode2 *root_;
    GLQuadTileNode2 *borrowing_from_;
    std::set<GLQuadTileNode2 *> borrowers_;

    /* OWNED BY ROOT */
    bool vertices_invalid_;
    size_t loading_tex_coords_vert_count_;
    bool derived_unresolvable_data_;
    bool should_borrow_texture_;

   private:
    int64_t last_touch2_;
    int64_t fade_timer_;
    int64_t read_request_start_;
    int64_t read_request_complete_;
    int64_t read_request_elapsed_;

    std::unique_ptr<uint16_t, void (*)(const uint16_t *)> debug_draw_indices_;
    // Size of above
    size_t debug_draw_indices_capacity_;
    // Num elements actually meaningful
    size_t debug_draw_indices_count_;

   protected:
    /**
     * This is the lazy loaded key for the texture to be used. It needs to be empty string when the
     * value has become invalid
     */
    std::string texture_key_;
};

}  // namespace TileReader
}  // namespace Raster
}  // namespace Renderer
}  // namespace Engine
}  // namespace TAK
