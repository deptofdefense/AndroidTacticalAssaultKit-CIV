#ifndef TAK_ENGINE_RENDERER_TILEMATRIX_GLZOOMLEVEL_H_INCLUDED
#define TAK_ENGINE_RENDERER_TILEMATRIX_GLZOOMLEVEL_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"
#include "renderer/raster/tilematrix/GLTiledLayerCore.h"
#include "renderer/raster/tilematrix/GLTile.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLMapBatchable2.h"



namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileMatrix {

                    class GLTilePatch;

                    /**
                     * Renderer for a given zoom level of a Tiled Map Layer. The zoom level renderer
                     * is responsible for subdividing the layer into one or more
                     * <I>tile patches</I>, managing the lifetime of those patches and determing
                     * which patches are to be drawn during a given render pump.
                     * 
                     * @author Developer
                     *
                     */
                    class ENGINE_API GLZoomLevel : public Core::GLMapRenderable2, public Core::GLMapBatchable2 {
                      private:

                        GLTiledLayerCore *core;
                        std::map<int, GLTilePatch *> patches;

                        int patchRows;
                        int patchCols;
                        int patchesGridOffsetX;
                        int patchesGridOffsetY;
                        int numPatchesX;
                        int numPatchesY;

                        int lastPumpDrawn;
    
                       public:
                        const TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel info;

                         /**
                         * The previous zoom level.
                         */
                        GLZoomLevel *previous;
    
                        /**
                         * The number of mesh subdivisions, per tile, that should be targeted for
                         * tiles at this zoom level.
                         */
                        int tileMeshSubdivisions;
    


                        GLZoomLevel(GLZoomLevel *prev, GLTiledLayerCore *core,
                                    const TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel &lod);
                        ~GLZoomLevel();

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

                        /**
                         * Obtains all patches that intersect the specified region that either have
                         * live tiles intersecting the region or have tiles whose textures are
                         * cached intersecting the region.
                         * 
                         * @param patches
                         * @param minX
                         * @param minY
                         * @param maxX
                         * @param maxY
                         */
                        void getTiles(std::set<std::shared_ptr<GLTile>> *tiles, double minX, double minY, double maxX, double maxY);
                        bool release(bool unusedOnly, int renderPump);

                    };
                }
            }
        }
    }
}


#endif
