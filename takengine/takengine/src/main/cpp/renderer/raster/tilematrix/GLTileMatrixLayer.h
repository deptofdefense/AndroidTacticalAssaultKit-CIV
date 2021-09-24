#ifndef TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEMATRIXLAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEMATRIXLAYER_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"
#include "renderer/raster/tilematrix/GLTiledLayerCore.h"
#include "renderer/raster/tilematrix/GLZoomLevel.h"

#include "renderer/raster/GLMapLayer2.h"
#include "renderer/core/GLMapView2.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileMatrix {

                    /**
                     * 
                     * @author Developer
                     *
                     */
                    class ENGINE_API GLTileMatrixLayer : public GLMapLayer2 {
                      private:
                        TAK::Engine::Core::MapRenderer *renderer;
                        atakmap::raster::DatasetDescriptor *info;
                        GLTiledLayerCore core;
                        GLZoomLevel **zoomLevels;
                        std::size_t numZoomLevels;
                        
                        // Controls
                        class TileClientControlImpl;
                        std::map<std::string, void *> controls;
                        std::unique_ptr<TileClientControlImpl> tileClientControl;
    
                      public:
                        /**
                         * Creates a new <code>GLArcGISTiledMapService</code>
                         * 
                         * @param renderer  The renderer context
                         * @param info      The dataset descriptor
                         * @param baseUrl   The base URL of the map server
                         * @param service   The data structure for the map service
                         */
                       GLTileMatrixLayer(TAK::Engine::Core::MapRenderer *renderer, atakmap::raster::DatasetDescriptor *info,
                                         const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix);
                       /**
                        * Creates a GLTileMatrixLayer against a context. Some Controls which require the Renderer
                        * will be disabled when constructed in this way.
                        */
                       GLTileMatrixLayer(TAK::Engine::Core::RenderContext *context, atakmap::raster::DatasetDescriptor *info,
                                         const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix);
                       ~GLTileMatrixLayer();

                      private:
                        void init();
                        void commonInit(TAK::Engine::Core::RenderContext *context);



                      public:
                        /*************************************************************/
                        // GLMapLayer2

                        const char *getLayerUri() const NOTHROWS;

                        const atakmap::raster::DatasetDescriptor *getInfo() const NOTHROWS;
                        Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS;


                        /*************************************************************/
                        // GLMapRenderable2 (via GLMapLayer2)

                        void draw(const Core::GLGlobeBase &view, const int renderPass) NOTHROWS;
                        void release() NOTHROWS;
                        int getRenderPass() NOTHROWS;
                        void start() NOTHROWS;
                        void stop() NOTHROWS;

                        /*************************************************************/
                        // Misc public
                        void debugDraw(const Core::GLGlobeBase &view);

                    };
                }
            }
        }
    }
}


#endif
