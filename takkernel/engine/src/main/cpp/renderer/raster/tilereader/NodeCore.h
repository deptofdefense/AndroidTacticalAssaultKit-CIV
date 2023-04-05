#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_NODECORE_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_NODECORE_H_INCLUDED

#include <unordered_set>

#include "core/GeoPoint2.h"
#include "core/RenderContext.h"
#include "port/Platform.h"
#include "math/Point2.h"
#include "math/Rectangle2.h"
#include "raster/ImageInfo.h"
#include "raster/tilereader/TileReader2.h"
#include "raster/tilereader/TileReaderFactory2.h"
#include "renderer/Shader.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/raster/TileCacheControl.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"
#include "renderer/raster/tilereader/NodeContextResources.h"
#include "renderer/raster/tilereader/TileReadRequestPrioritizer.h"
#include "renderer/raster/tilereader/VertexResolver.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    struct RenderState
                    {
                        float r{ 1.f };
                        float g{ 1.f };
                        float b{ 1.f };
                        float a{ 1.f };
                        GLuint texId{ GL_NONE };
                        // attrib bindings
                        GLuint vertexCoords{ GL_NONE };
                        GLuint texCoords{ GL_NONE };
                        GLuint indices{ GL_NONE };
                    };

                    class NodeCore : public TAK::Engine::Renderer::Raster::TileCacheControl::OnTileUpdateListener
                    {
                    private :
                        struct TileIndexHash
                        {
                            std::size_t operator()(const Math::Point2<std::size_t> &xyz) const NOTHROWS
                            {
                                std::size_t hash = xyz.z;
                                hash = (hash << xyz.z) | xyz.x;
                                hash = (hash << xyz.z) | xyz.y;
                                return hash;
                            }
                        };
                    private:
                        NodeCore(TAK::Engine::Core::RenderContext& ctx,
                            const TAK::Engine::Raster::ImageInfo& info,
                            const GLQuadTileNode2::Initializer& init,
                            const Util::TAKErr code,
                            const std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2>& reader,
                            TAK::Engine::Raster::DatasetProjection2Ptr&& imprecise,
                            TAK::Engine::Raster::DatasetProjection2Ptr&& precise,
                            const std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO>& asyncio,
                            const GLQuadTileNode2::Options& opts) NOTHROWS;
                    public:
                        ~NodeCore() NOTHROWS;
                    public:
                        void onTileUpdated(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS override;
                    private:
                        void refreshUpdateList() NOTHROWS;
                        void prioritize(const TAK::Engine::Core::GeoPoint2 &camLLA) NOTHROWS;
                    private:
                        const GLQuadTileNode2::Initializer& init;
                        std::shared_ptr<const Shader2> shader;
                        RenderState renderState;
                        Util::TAKErr initResult;
                    private:
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2> tileReader;
                        /** Projects between the image coordinate space and WGS84. */
                        std::shared_ptr<TAK::Engine::Raster::DatasetProjection2> imprecise;
                        /** Projects between the image coordinate space and WGS84. */
                        std::shared_ptr<TAK::Engine::Raster::DatasetProjection2> precise;

                        TAK::Engine::Raster::ImageInfo info;
                        VertexResolverPtr vertexResolver;
                        TAK::Engine::Renderer::GLTextureCache2* textureCache;

                        bool textureBorrowEnabled;
                        bool textureCopyEnabled;

                        std::size_t nominalTileHeight;
                        std::size_t nominalTileWidth;

                        std::size_t numResolutionLevels;
                        bool isMultiResolution;


                        /** longitudinal unwrapping for datasets which cross the IDL */
                        double unwrap;

                        GLuint frameBufferHandle;
                        GLuint depthBufferHandle;

                        /** minification filter to be applied to texture */
                        GLenum minFilter;
                        /** magnification filter to be applied to texture */
                        GLenum magFilter;

                        // modulation color components
                        uint32_t color;
                        float colorR;
                        float colorG;
                        float colorB;
                        float colorA;

                        bool debugDrawEnabled;
                        GLQuadTileNode2::Options options;
                        bool loadingTextureEnabled;

                        bool disposed;

                        Math::Rectangle2<double> drawROI[2u];
                        int drawPumpHemi;
                        int drawPumpLevel;

                        bool progressiveLoading;

                        int64_t fadeTimerLimit;

                        bool versionCheckEnabled{ true };

                        int tilesThisFrame;

                        bool adaptiveLodCulling{ false };

                        TAK::Engine::Math::Matrix2 xproj;
                        TAK::Engine::Math::Matrix2 mvp;

                        std::shared_ptr<NodeContextResources> resources;

                        TileReadRequestPrioritizer requestPrioritizer;
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO> asyncio;

                        bool suspended;
                        int stateMask;

                        TAK::Engine::Core::RenderContext &context;

                        //GLDiagnostics diagnostics = new GLDiagnostics();
                        bool diagnosticsEnabled{ false };

                        TAK::Engine::Renderer::Core::Controls::SurfaceRendererControl *surfaceControl;

                        TAK::Engine::Renderer::Raster::TileCacheControl *cacheControl;
                        //TileClientControl clientControl;

                        struct {
                            Thread::Mutex mutex;
                            std::unordered_set<Math::Point2<std::size_t>, TileIndexHash> write;
                            std::unordered_set<Math::Point2<std::size_t>, TileIndexHash> value;
                        } updatedTiles;
                    private :
                        friend ENGINE_API Util::TAKErr NodeCore_create(std::unique_ptr<NodeCore, void(*)(const NodeCore*)> &, TAK::Engine::Core::RenderContext &, const TAK::Engine::Raster::ImageInfo &, const TAK::Engine::Raster::TileReader::TileReaderFactory2Options &, const GLQuadTileNode2::Options &, const bool, const GLQuadTileNode2::Initializer &) NOTHROWS;
                        friend class GLQuadTileNode2;
                        friend class GLQuadTileNode3;
                        friend class PreciseVertexResolver;
                    };

                    typedef std::unique_ptr<NodeCore, void(*)(const NodeCore*)> NodeCorePtr;

                    ENGINE_API Util::TAKErr NodeCore_create(NodeCorePtr& value, TAK::Engine::Core::RenderContext &ctx, const TAK::Engine::Raster::ImageInfo &info, const TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts, const GLQuadTileNode2::Options &opts, const bool throwOnReaderFailedInit, const GLQuadTileNode2::Initializer &init) NOTHROWS;
                }
            }
        }
    }
}
#endif
