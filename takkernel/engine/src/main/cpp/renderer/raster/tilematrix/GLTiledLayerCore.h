#ifndef TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEDLAYERCORE_H_INCLUDED
#define TAK_ENGINE_RENDERER_TILEMATRIX_GLTILEDLAYERCORE_H_INCLUDED

#include "core/Projection2.h"
#include "raster/DatasetProjection.h"
#include "raster/tilematrix/TileMatrix.h"
#include "feature/Envelope2.h"
#include "renderer/GLTextureCache2.h"
#include "renderer/AsyncBitmapLoader2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            namespace Raster {
                namespace TileMatrix {

                    /**
                     * The core data structure for a Tiled Map Layer, containing properties about
                     * the layer that will be utilized through the renderer infrastructure.
                     * 
                     * @author Developer
                     *
                     */
                    class GLTiledLayerCore {

                      public:
                        /**
                         * The base URL of the layer.
                         */
                        const char * const clientSourceUri;

                        /**
                         * The {@link Projection} of the layer
                         */
                        TAK::Engine::Core::Projection2Ptr proj;
    
                        /**
                         * If <code>true</code>, renderers should perform additional drawing
                         */
                        bool debugDraw;
    
                        /**
                         * A cache that may be used by renderers to store texture data that has gone
                         * out of view.  May be <code>null</code> if no cache is available.
                         */
                        GLTextureCache2 *textureCache;

                        /**
                         * The asynchronous bitmap loader to be used to service tile download
                         * requests.
                         */
                        AsyncBitmapLoader2 *bitmapLoader;
    
                        /**
                         * The red color component that should be applied when rendering the layer.
                         */
                        float r;

                        /**
                         * The green color component that should be applied when rendering the layer.
                         */
                        float g;

                        /**
                         * The blue color component that should be applied when rendering the layer.
                         */
                        float b;

                        /**
                         * The alpha color component that should be applied when rendering the layer.
                         */
                        float a;
    
                        /**
                         * The minimum latitude of the full extent of the layer.
                         */
                        double fullExtentMinLat;
                        /**
                         * The minimum longitude of the full extent of the layer.
                         */
                        double fullExtentMinLng;

                        /**
                         * The maximum latitude of the full extent of the layer.
                         */
                        double fullExtentMaxLat;

                        /**
                         * The maximum longitude of the full extent of the layer.
                         */
                        double fullExtentMaxLng;
    
                        /**
                         * Converts between the projected coordinate space of the layer and
                         * latitude/longitude.
                         */
                        atakmap::raster::DatasetProjection * const proj2geo;
    
                        /**
                         * Tile access for the layer.
                         */
                        std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> matrix;
    
                        /**
                         * The bounds of the tile containing region, in the projected coordinate
                         * space of the layer.
                         */
                        Feature::Envelope2 fullExtent;
    
                        long refreshInterval;

                        int tileDrawVersion;

                       private:
                        int64_t lastRefresh;


                       public: 
                        /**
                         * Creates a new <code>GLTiledLayerCore</code>
                         * 
                         * @param service       The data structure describing the service
                         * @param serviceUrl    The base URL of the map server
                         */
                        GLTiledLayerCore(const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix, const char *uri);
                        ~GLTiledLayerCore();
    
                        void requestRefresh();

                        void drawPump();


                     };

                }
            }
        }
    }
}

#endif
