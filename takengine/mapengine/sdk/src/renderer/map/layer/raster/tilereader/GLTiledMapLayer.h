
#ifndef ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILEDMAPLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILEDMAPLAYER_H_INCLUDED

#include "math/Rectangle.h"
#include "feature/Envelope.h"
#include "renderer/map/layer/raster/GLMapLayer.h"

class GDALDataset;

namespace atakmap {
    
    namespace raster {
        class DatasetDescriptor;
        class DatasetProjection;
    }
    
    namespace renderer {
        
        class GLRenderContext;
        class GLTexture;
        
        namespace map {
            namespace layer {
                namespace raster {
                    namespace tilereader {
                        class GLQuadTileNode;
                    }
                }
            }
        }
        
        namespace raster {
            
            class GLTiledMapLayer : public atakmap::renderer::map::layer::raster::GLMapLayer {
            private:
                static GLTexture *loadingTexture;
                
                /**************************************************************************/
                
            protected:
                GLRenderContext * const context;
                const atakmap::raster::DatasetDescriptor * const info;
                atakmap::renderer::map::layer::raster::tilereader::GLQuadTileNode *quadTree;
                
            private:
                const atakmap::feature::Envelope minimumBoundingBox;
                bool initialized;
                
            protected:
                GLTiledMapLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info);
                
                virtual ~GLTiledMapLayer();
                
                /**
                 * Initializes {@link #quadTree}.
                 */
                virtual void init() = 0;
                
                virtual void draw(const atakmap::renderer::map::GLMapView *view);
                
                /**
                 * Cleans up any resources allocated as a result of {@link #init()}; always
                 * invoked AFTER {@link #quadTree} is released.
                 *
                 * <P>The default implementation returns immediately.
                 */
                virtual void releaseImpl();
                
            public:
                virtual void release();
                
                virtual const char *getLayerUri() const;
                
                virtual const atakmap::raster::DatasetDescriptor *getInfo() const;
                
                /**************************************************************************/
                // MapDataAccess
                
                /*TODO--MapData getMapData(GeoPoint p, double resolution) {
                    if (p.getLatitude() > this.minimumBoundingBox.maxY || p.getLatitude() < this.minimumBoundingBox.minY)
                        return null;
                    else if (p.getLongitude() > this.minimumBoundingBox.maxX || p.getLongitude() < this.minimumBoundingBox.minX)
                        return null;
                    // XXX - should check resolution???
                    return this.quadTree;
                }*/
                
                /**************************************************************************/
                
                static GLTexture *getLoadingTexture();
                
                static atakmap::math::Rectangle<float> getRasterROI(const atakmap::renderer::map::GLMapView *view,
                                                                    GDALDataset *dataset,
                                                                    const atakmap::raster::DatasetProjection *proj);
                
                static atakmap::math::Rectangle<float> getRasterROI(const atakmap::renderer::map::GLMapView *view,
                                                                    int rasterWidth, int rasterHeight,
                                                                    const atakmap::raster::DatasetProjection *proj);
                
                static atakmap::math::Rectangle<float> getRasterROI(const atakmap::renderer::map::GLMapView *view,
                                                                    long rasterWidth, long rasterHeight,
                                                                    const atakmap::raster::DatasetProjection *proj,
                                                                    const atakmap::core::GeoPoint &ulG_R, const atakmap::core::GeoPoint &urG_R,
                                                                    const atakmap::core::GeoPoint &lrG_R, const atakmap::core::GeoPoint &llG_R);
            };
            
        }
    }
    
}

#endif