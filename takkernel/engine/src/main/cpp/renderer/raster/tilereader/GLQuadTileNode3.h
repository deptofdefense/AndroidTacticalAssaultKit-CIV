#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_GLQUADTILENODE3_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_GLQUADTILENODE3_H_INCLUDED

#include <list>

#include "core/GeoPoint2.h"
#include "core/RenderContext.h"
#include "feature/Envelope2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "raster/DatasetProjection2.h"
#include "raster/ImageInfo.h"
#include "raster/RasterDataAccess2.h"
#include "raster/tilereader/TileReaderFactory2.h"
#include "renderer/GLTextureCache2.h"
#include "renderer/core/GLResolvableMapRenderable2.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    class NodeCore;
#if 0
                    struct ENGINE_API GLQuadTileNodeOptions
                    {
                        bool textureCopyEnabled{ true };
                        bool childTextureCopyResolvesParent{ true };
                        TAK::Engine::Renderer::GLTextureCache2* textureCache{ nullptr };
                        bool progressiveLoad{ false };
                        double levelTransitionAdjustment{ 1.0 };
                        bool textureBorrowEnabled{ true };
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO> asyncio;
                    };
#endif
                    class ENGINE_API GLQuadTileNode3 :
                            public TAK::Engine::Renderer::Core::GLResolvableMapRenderable2,
                            public TAK::Engine::Raster::TileReader::TileReader2::AsynchronousReadRequestListener,
                            public TAK::Engine::Raster::RasterDataAccess2
                    {
                    private :
                        struct IOCallbackOpaque;
                    public :
                        /**
                         * Creates a new root node.
                         *
                         * @param info          The image information
                         * @param readerOpts    The {@link TileReaderFactory.Options} to be used
                         *                      when creating the {@link TileReader} associated with
                         *                      this node. May be <code>null</code>
                         * @param opts          The render configuration {@link GLQuadTileNode2.Options}. May be
                         *                      <code>null</code>.
                         * @param init          The initializer for this node.
                         */
                        GLQuadTileNode3(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Raster::ImageInfo& info, const TAK::Engine::Raster::TileReader::TileReaderFactory2Options& readerOpts, const GLQuadTileNode2::Options &opts, const GLQuadTileNode2::Initializer &init) NOTHROWS;
                    private :
                        /**
                         * Creates a new child node.
                         *
                         * @param parent The parent node
                         * @param idx The index; <code>0</code> for upper-left, <code>1</code> for upper-right,
                         *            <code>2</code> for lower-left and <code>3</code> for lower-right.
                         */
                        GLQuadTileNode3(GLQuadTileNode3 *parent, const std::size_t idx) NOTHROWS;
                        GLQuadTileNode3(GLQuadTileNode3 *parent, const std::size_t idx, const std::shared_ptr<NodeCore>& core) NOTHROWS;
                    public :
                        ~GLQuadTileNode3();
                    public :
                        void setColor(const int color) NOTHROWS;
                        TAK::Engine::Raster::DatasetProjection2& getDatasetProjection() const NOTHROWS;
                        TAK::Engine::Raster::TileReader::TileReader2& getReader() const NOTHROWS;
                    private :
                        Util::TAKErr computeBounds(TAK::Engine::Feature::Envelope2 &value, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH) const NOTHROWS;

                        /**
                         * <P>
                         * <B>IMPORTANT:</B> Must be invoked on the render thread.
                         *
                         * @param tileColumn
                         * @param tileRow
                         * @param level
                         */
                        Util::TAKErr set(const std::size_t tileColumn, const std::size_t tileRow, const std::size_t level) NOTHROWS;
                        void releaseTexture() NOTHROWS;
                    public :
                        virtual void release() NOTHROWS override;
                    private :
                        void validateTexture() NOTHROWS;
                        void validateTexCoordIndices() NOTHROWS;
                        void validateTexVerts() NOTHROWS;
                        void validateVertexCoords(const TAK::Engine::Renderer::Core::GLGlobeBase& view) NOTHROWS;
                        void cull(const int pump) NOTHROWS;
                    public :
                        virtual int getRenderPass() NOTHROWS override;
                        virtual void draw(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const int renderPass) NOTHROWS override;
                        virtual void start() NOTHROWS override;
                        virtual void stop() NOTHROWS override;
                    private :
                        bool shouldResolve() const NOTHROWS;
                        void resolveTexture(const bool fetch) NOTHROWS;
                        void resetFadeTimer() NOTHROWS;
                        bool needsRefresh() const NOTHROWS;

                        /**
                         * @param view The view
                         * @param level The resolution level. The scale factor is equivalent to
                         *            <code>1.0d / (double)(1<<level)</code>
                         * @return <code>true</code> if the ancestor should draw
                         */
                        bool drawImpl(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const int level) NOTHROWS;
                        void drawTexture(const TAK::Engine::Renderer::Core::GLGlobeBase& view, TAK::Engine::Renderer::GLTexture2 &tex, const int texCoords) NOTHROWS;
                        void setLCS(const TAK::Engine::Renderer::Core::GLGlobeBase& view) NOTHROWS;
                        void drawTextureImpl(const TAK::Engine::Renderer::Core::GLGlobeBase& view, TAK::Engine::Renderer::GLTexture2& tex, const int texCoords) NOTHROWS;
                        void expandTexGrid() NOTHROWS;

                        /**
                         * Invokes {@link #super_draw(GLMapView, boolean)}.
                         *
                         * @param view The view
                         */
                        void drawImpl(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const bool resolve) NOTHROWS;
                        void super_draw(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const bool resolve) NOTHROWS;
                        TAK::Engine::Renderer::GLTexture2 *tryBorrow() NOTHROWS;
                        void debugDraw(const TAK::Engine::Renderer::Core::GLGlobeBase& view) NOTHROWS;

                        /**
                         * Abandons all of the children.
                         */
                        void abandon() NOTHROWS;
                    public : // GLResolvable
                        // XXX - implementation for getState
                        virtual TAK::Engine::Renderer::Core::GLResolvableMapRenderable2::State getState() override;
                        virtual void suspend() override;
                        virtual void resume() override;
                    public : // RasterDataAccess2
                        virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                        virtual Util::TAKErr imageToGround(TAK::Engine::Core::GeoPoint2 *ground, bool *isPrecise, const Math::Point2<double> &image) NOTHROWS override;
                        virtual Util::TAKErr groundToImage(Math::Point2<double> *image, bool *isPrecise, const TAK::Engine::Core::GeoPoint2 &ground) NOTHROWS override;
                        virtual Util::TAKErr getType(Port::String &value) NOTHROWS override;
                        virtual Util::TAKErr getSpatialReferenceId(int *value) NOTHROWS override;
                        virtual Util::TAKErr hasPreciseCoordinates(bool *value) NOTHROWS override;
                        virtual Util::TAKErr getWidth(int *value) NOTHROWS override;
                        virtual Util::TAKErr getHeight(int *value) NOTHROWS override;
                        virtual Util::TAKErr getImageInfo(TAK::Engine::Raster::ImageInfoPtr_const &info) NOTHROWS override;
                        Util::TAKErr getImageInfo(TAK::Engine::Raster::ImageInfo *info) NOTHROWS;
                    private : // Asynchronous Read Request Listener
                        void rendererIORunnableImpl(IOCallbackOpaque* iocb) NOTHROWS;
                        void queueGLCallback(IOCallbackOpaque* iocb);
                        /**
                         * <P>
                         * <B>IMPORTANT:</B> Must be invoked on the render thread.
                         *
                         * @param id
                         * @return
                         */
                        bool checkRequest(const int id) const NOTHROWS;
                        void requestCreated(const int id) NOTHROWS;
                        void requestStarted(const int id) NOTHROWS;
                        void requestUpdate(const int id, const Renderer::Bitmap2 &data) NOTHROWS;
                        void requestCompleted(const int id) NOTHROWS;
                        void requestCanceled(const int id) NOTHROWS;
                        void requestError(const int id, const Util::TAKErr code, const char *msg) NOTHROWS;
                    private :
                        static Util::TAKErr create(Core::GLMapRenderable2Ptr &value,
                                                   TAK::Engine::Core::RenderContext& ctx,
                                                   const TAK::Engine::Raster::ImageInfo& info,
                                                   const TAK::Engine::Raster::TileReader::TileReaderFactory2Options& readerOpts,
                                                   const GLQuadTileNode2::Options& opts,
                                                   const GLQuadTileNode2::Initializer& init) NOTHROWS;
                        static void rendererIORunnable(void* opaque) NOTHROWS;
                    private :
                        std::shared_ptr<NodeCore> core;
                        std::weak_ptr<GLQuadTileNode3> selfRef;

                        /**
                         * The read request currently being serviced. Valid access to the request is only guaranteed on
                         * the render thread.
                         */
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::ReadRequest> currentRequest;

                        TAK::Engine::Renderer::GLTexture2Ptr texture;

                        /*private*/ const TAK::Engine::Renderer::Core::GLGlobeBase *renderer;

                        Math::Point2<std::size_t> tileIndex;

                        TAK::Engine::Renderer::Core::GLResolvable::State state;

                        // source coordinate space (unscaled)
                        int64_t tileSrcX;
                        int64_t tileSrcY;
                        int64_t tileSrcWidth;
                        int64_t tileSrcHeight;
                        int64_t tileSrcMidX;
                        int64_t tileSrcMidY;

                        std::size_t tileWidth;
                        std::size_t tileHeight;

                        /**
                         * The texture coordinate for the texture in the order lower-left,
                         * lower-right, upper-right, upper-left. Using this order relieves us of the
                         * need to do a vertical flip of the raster data.
                         */
                        GLuint textureCoordinates{ GL_NONE };
                        bool texCoordsShared;

                        GLuint borrowTextureCoordinates{ GL_NONE };

                        /**
                         * The texture coordinate for the texture in the order upper-left,
                         * upper-right, lower-right, lower-left. Using this order relieves us of the
                         * need to do a vertical flip of the raster data.
                         */
                        GLuint vertexCoordinates2{ GL_NONE };

                        GLuint glTexCoordIndices2{ GL_NONE };
                        bool indicesShared;

                        bool textureCoordsValid;
                        bool receivedUpdate;

                        GLenum glTexType{ GL_NONE };
                        GLenum glTexFormat{ GL_NONE };

                        std::size_t glTexGridWidth;
                        std::size_t glTexGridHeight;
                        std::size_t glTexGridVertCount;
                        std::size_t glTexGridIdxCount;

                        struct GridVertex {
                            TAK::Engine::Core::GeoPoint2 lla;
                            Math::Point2<double> xyz;
                            bool resolved{ false };
                        };
                        struct {
                            int srid{ -1 };
                            bool valid{ false };
                            std::vector<GridVertex> value;
                        } gridVertices;
                        TAK::Engine::Core::GeoPoint2 centroid;
                        TAK::Engine::Core::GeoPoint2 centroidHemi2;
                        int primaryHemi;
                        Math::Point2<double> centroidProj;
                        Math::Point2<double> centroidProjHemi2;

                        double minLat;
                        double minLng;
                        double maxLat;
                        double maxLng;
                        struct {
                            TAK::Engine::Feature::Envelope2 value;
                            bool valid{ false };
                        } childMbb[4u];

                        bool touched;

                        int64_t tileVersion;

                        /**
                         * This node's children, in the order: upper-left, upper-right, lower-left, lower-right.
                         */
                        std::shared_ptr<GLQuadTileNode3> children[4u];

                        std::size_t halfTileWidth;
                        std::size_t halfTileHeight;

                        GLQuadTileNode3 *parent;
                        GLQuadTileNode3 &root;

                        GLQuadTileNode3 *borrowingFrom;

                        int lastTouch;

                        /* OWNED BY ROOT */

                        bool verticesInvalid;

                        bool derivedUnresolvableData;

                        int64_t fadeTimer;

                        int64_t readRequestStart;
                        int64_t readRequestComplete;
                        int64_t readRequestElapsed;

                        Util::MemBuffer2Ptr debugDrawIndices;

                        bool descendantsRequestDraw;

                        std::list<std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::ReadRequest>> readRequests;
                        Thread::Mutex queuedCallbacksMutex;
                        std::vector<IOCallbackOpaque *> queuedCallbacks;

                    private :
                        friend class GLTiledMapLayer2;
                        friend class PreciseVertexResolver;
                        friend ENGINE_API Util::TAKErr GLQuadTileNode3_create(Core::GLMapRenderable2Ptr&, TAK::Engine::Core::RenderContext&, const TAK::Engine::Raster::ImageInfo&, const TAK::Engine::Raster::TileReader::TileReaderFactory2Options&, const GLQuadTileNode2::Options&, const GLQuadTileNode2::Initializer&) NOTHROWS;
                    };

                    typedef std::unique_ptr<GLQuadTileNode3, void(*)(const GLQuadTileNode3*)> GLQuadTileNode3Ptr;

                    ENGINE_API Util::TAKErr GLQuadTileNode3_create(Core::GLMapRenderable2Ptr &value,
                                                                   TAK::Engine::Core::RenderContext& ctx,
                                                                   const TAK::Engine::Raster::ImageInfo& info,
                                                                   const TAK::Engine::Raster::TileReader::TileReaderFactory2Options& readerOpts,
                                                                   const GLQuadTileNode2::Options& opts,
                                                                   const GLQuadTileNode2::Initializer& init) NOTHROWS;
                }
            }
        }
    }
}

#endif
