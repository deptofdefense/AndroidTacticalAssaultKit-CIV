#ifndef TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEPATCH_H_INCLUDED
#define TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEPATCH_H_INCLUDED

#include "renderer/raster/tilematrix/GLTiledLayerCore.h"
#include "renderer/raster/tilematrix/GLZoomLevel.h"
#include "raster/tilematrix/TileMatrix.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/core/GLMapBatchable2.h"
#include "renderer/core/GLMapRenderable2.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileMatrix {
                    class GLTile;

                    /**
                     * A collection of some subset of tile renderers for a contiguous region of a
                     * Tiled Map Layer at a given zoom level.
                     * 
                     * <P>The patch is responsible for managing the lifetime of the tile renderers
                     * for the tile region that it covers and selecting which tiles for the region
                     * are to be rendered during the draw pump.
                     *   
                     * @author Developer
                     *
                     */
                    class ENGINE_API GLTilePatch : public Core::GLMapRenderable2, public Core::GLMapBatchable2 {
                      private:
                        std::shared_ptr<GLTile> *tiles_;
                        int num_tiles_;
                        GLTiledLayerCore *core_;
                        GLZoomLevel *parent_;
   
                        double patchMinLat;
                        double patchMinLng;
                        double patchMaxLat;
                        double patchMaxLng;

                        int lastPumpDrawn;
    

                      public:
                        /**
                         * The column grid offset, in tiles, to the first tile of the patch. 
                         */
                        int gridOffsetX;
                        /**
                         * The row grid offset, in tiles, to the first tile of the patch. 
                         */
                        int gridOffsetY;
                        /**
                         * The number of tile columns contained by the patch.
                         */
                        int gridColumns;
                        /**
                         * The number of tile rows contained by the patch.
                         */
                        int gridRows;
    

                        /**
                         * Creates a new tile patch.
                         * 
                         * @param core          The core data structure for the tiled layer that the
                         *                      tile belongs to
                         * @param parent        The zoom level that the patch belongs to
                         * @param gridOffsetX   The column grid offset, in tiles, to the first tile
                         *                      of the patch.
                         * @param gridOffsetY   The row grid offset, in tiles, to the first tile of
                         *                      the patch.
                         * @param gridColumns   The number of tile columns contained by the patch.
                         * @param gridRows      The number of tile rows contained by the patch.
                         */
                        GLTilePatch(GLTiledLayerCore *core, GLZoomLevel *parent, int gridOffsetX, int gridOffsetY, int gridColumns, int gridRows);
                        ~GLTilePatch();


                        /*********************************************************************/
                        // GLMapBatchable2
                        Util::TAKErr batch(const Renderer::Core::GLGlobeBase &view, const int renderPass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS override;

                        /*********************************************************************/
                        // GLMapRenderable2
                        void draw(const Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS override;
                        void release() NOTHROWS override;
                        int getRenderPass() NOTHROWS override;
                        void start() NOTHROWS override;
                        void stop() NOTHROWS override;

                        /*********************************************************************/
                        // Our own public methods

                        void debugDraw(const Renderer::Core::GLGlobeBase &view);
                        bool release(bool unusedOnly, int renderPump);

                        void getTiles(std::set<std::shared_ptr<GLTile>> *tiles, double minX, double minY, double maxX, double maxY);
                        const GLZoomLevel *getParent();
                    };
                }
            }
        }
    }
}

#endif
