#ifndef TAK_ENGINE_RENDERER_TILEMATRIX_GLTILE_H_INCLUDED
#define TAK_ENGINE_RENDERER_TILEMATRIX_GLTILE_H_INCLUDED

#include "renderer/GLTexture2.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLMapBatchable2.h"
#include "renderer/core/GLResolvable.h"
#include "renderer/Bitmap2.h"
#include "renderer/raster/tilematrix/GLTiledLayerCore.h"

#include "util/Disposable.h"
#include "math/Matrix2.h"
#include "renderer/raster/tilereader/GLTileMesh.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileMatrix {

                    // Circular - just forward
                    class GLTilePatch;


                    /**
                     * Renderer for a single tile for a Tiled Map Layer. The tile renderer is
                     * responsible for requesting the loading of the tile, uploading the tile data
                     * to a texture, managing the lifetime of the tile texture and rendering the
                     * tile data to the screen during the draw pump.
                     * 
                     * @author Developer
                     *
                     */
                    class ENGINE_API GLTile : public Core::GLMapRenderable2, public Core::GLMapBatchable2, public Core::GLResolvable
                    {

                    private:
                        struct BitmapLoadContext {
                            bool vetoed;
                            bool refreshOnComplete;
                            std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> matrix;
                            const Core::GLGlobeBase &view;
                            int tileX;
                            int tileY;
                            int tileZ;
                            int tileDrawVersion;
                            BitmapLoadContext(bool refreshOnComplete, std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix,
                                              const Core::GLGlobeBase &view, int tileX, int tileY, int tileZ, int tileDrawVersion);

                            void veto();
                            static std::shared_ptr<Bitmap2> load(void *opaque);
                        };

                        struct BorrowRecord 
                        {
                             /** the GLTile we belong to */
                             GLTile *const owner;
                             /** the texture being borrowed from */
                             std::shared_ptr<GLTile> from;
                             /** the minimum x-coordinate of the borrowed region */
                             const double projMinX;
                             /** the minimum y-coordinate of the borrowed region */
                             const double projMinY;
                             /** the maximum x-coordinate of the borrowed region */
                             const double projMaxX;
                             /** the maximum y-coordinate of the borrowed region */
                             const double projMaxY;
                             /** the texture mesh */
                             GLTileMesh *mesh;

                            BorrowRecord(GLTile *owner, const std::shared_ptr<GLTile> &from, double borrowMinX, double borrowMinY, double borrowMaxX, double borrowMaxY);
                            ~BorrowRecord();

                        };

                        GLTexture2Ptr texturePtr;
                        Math::Matrix2 proj2uv;
                        double projMinX;
                        double projMinY;
                        double projMaxX;
                        double projMaxY;
                        State state;
    
                        std::shared_ptr<BitmapLoadContext> pendingTextureContext;
                        AsyncBitmapLoader2::Task pendingTextureTask;
    
                        std::size_t vertexCount;
                        std::unique_ptr<float, void (*)(const float *)> textureCoordsPtr;
                        std::unique_ptr<float, void (*)(const float *)> vertexCoordsPtr;

                        GLTiledLayerCore *core;
                        GLTilePatch *patch;
        
                        GLTileMesh *mesh;

                        const int tileX;
                        const int tileY;
                        const int tileZ;
    
                        std::string textureKey;
    
                        std::set<GLTile *> borrowers;
                        std::set<BorrowRecord *> borrowRecords;
                        int tileVersion;

                        int lastPumpDrawn;

                    public:
                        /**
                         * Creates a new tile renderer.
                         * 
                         * @param core      The core data structure for the tiled layer that the
                         *                  tile belongs to
                         * @param patch     The patch that the tile is a child of
                         * @param url       The URL to the tile
                         * @param projMinX  The minimum x-coordinate of the tile, in the projected
                         *                  coordinate space
                         * @param projMinY  The minimum y-coordinate of the tile, in the projected
                         *                  coordinate space
                         * @param projMaxX  The maximum x-coordinate of the tile, in the projected
                         *                  coordinate space
                         * @param projMaxY  The maximum y-coordinate of the tile, in the projected
                         *                  coordinate space
                         */
                        GLTile(GLTiledLayerCore *core, GLTilePatch *patch, int tileX, int tileY);
                        virtual ~GLTile() NOTHROWS;


                        /**
                         * Returns <code>true</code> if any tiles are borrowing this tile's
                         * texture.
                         *  
                         * @return  <code>true</code> if any tiles are borrowing this tile's
                         *          texture, <code>false</code> otherwise.
                         */
                        bool hasBorrowers();

                        /**
                         * Returns true if this tile has a non-null texture
                         */
                        bool hasTexture();


                        /*********************************************************************/
                        // GLMapBatchable2
                        Util::TAKErr batch(const Renderer::Core::GLGlobeBase &view, const int renderPass,
                                           TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS override;

                        /*********************************************************************/
                        // GLMapRenderable2
                        void draw(const Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS override;
                        void release() NOTHROWS override;
                        int getRenderPass() NOTHROWS override;
                        void start() NOTHROWS override;
                        void stop() NOTHROWS override;


                        /*********************************************************************/
                        // GLResolvable
                        State getState() override;
                        void suspend() override;
                        void resume() override;

                        static std::string getTileTextureKey(const GLTiledLayerCore &core, int zoom, int tileX, int tileY);

                    private:

                        /**
                         * Tries to borrow textures from other tiles.
                         */
                        void tryBorrow();

                        /**
                         * Stops borrow textures from all other tiles.
                         */
                        void unborrow();
    
                        bool checkForCachedTexture();
                        bool renderCommon(const Renderer::Core::GLGlobeBase &view, int renderPass);

                        void debugDraw(const Renderer::Core::GLGlobeBase &view);

                        friend class GLTilePatch;

                    };
                }
            }
        }
    }
}




#endif
