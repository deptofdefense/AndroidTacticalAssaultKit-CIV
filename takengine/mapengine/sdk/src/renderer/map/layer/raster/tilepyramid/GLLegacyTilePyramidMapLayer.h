#ifndef ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEPYRAMID_GLLEGACYTILEPYRAMIDMAPLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEPYRAMID_GLLEGACYTILEPYRAMIDMAPLAYER_H_INCLUDED

#include "base/RefCount.hh"
#include "feature/Envelope.h"
#include "raster/tilepyramid/TilesetInfo.h"
#include "renderer/map/layer/raster/GLMapLayer.h"
#include "renderer/map/layer/raster/GLMapLayerSPI.h"

namespace atakmap {
    
    namespace raster {
        class ImageDatasetDescriptor;
        class DatasetProjection;
        
        namespace tilepyramid {
            class LegacyTilePyramidTileReader;
        }
    }
    
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {
                    
                    namespace tilereader {
                        class GLQuadTileNode;
                    }
                    
                    namespace tilepyramid {
                        
                        class GLLegacyTilePyramidMapLayer : public GLMapLayer {
                        public:
                            static const char * const SUPPORTED_TYPES[];
                            
                            class SPI;
                            
                        private:
                            atakmap::raster::tilepyramid::TilesetInfo *_tsInfo;
                            atakmap::renderer::map::layer::raster::tilereader::GLQuadTileNode *rootNode;
                            atakmap::raster::tilepyramid::LegacyTilePyramidTileReader *tileReader;
                            atakmap::feature::Envelope bounds;
                            atakmap::renderer::GLRenderContext *renderContext;
                            std::unique_ptr<atakmap::raster::DatasetProjection> datasetProj;
                            bool initialized;
                            
                        public:
                            GLLegacyTilePyramidMapLayer(atakmap::renderer::GLRenderContext *renderContext, atakmap::raster::tilepyramid::TilesetInfo *info);
                            virtual ~GLLegacyTilePyramidMapLayer();
                            
                        private:
                            void init(const GLMapView *view);
                            
                        public:
                            virtual void release();
                            virtual const char *getLayerUri() const;
                            virtual void draw(const GLMapView *view);
                            virtual const atakmap::raster::ImageDatasetDescriptor *getInfo() const;
                            virtual void start();
                            virtual void stop();
                        };
                        
                        class GLLegacyTilePyramidMapLayer::SPI : public GLMapLayerSPI {
                        public:
                            virtual ~SPI() throw();
                            virtual GLMapLayer *createLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info);
                        };
                        
                    }
                }
            }
        }
    }
}

#endif
