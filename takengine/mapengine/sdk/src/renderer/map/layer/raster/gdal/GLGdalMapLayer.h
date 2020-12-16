#ifndef ATAKMAP_RENDERER_RASTER_GDAL_GLGDALMAPLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_RASTER_GDAL_GLGDALMAPLAYER_H_INCLUDED

#include "thread/Mutex.h"
#include "thread/Thread.h"

#include "renderer/map/layer/raster/tilereader/GLTiledMapLayer.h"
#include "renderer/map/layer/raster/GLMapLayerSPI.h"

namespace atakmap {
    
    namespace core {
        class Projection;
    }
    
    namespace raster {
        class DatasetProjection;
        namespace tilereader {
            class TileReader;
        }
    }
    
    namespace renderer {
        namespace raster {
            
            
            
            class GLGdalMapLayer : public atakmap::renderer::raster::GLTiledMapLayer {
            public:
                static const char * const SUPPORTED_TYPES[];
                
                class SPI;
                
                
                /**************************************************************************/
                
            protected:
                GDALDataset *dataset;
                
                /**
                 * The projection for the dataset.
                 */
                atakmap::raster::DatasetProjection *proj;
                
            protected:
                atakmap::raster::tilereader::TileReader *reader;
                bool quadTreeInit;
                
            private:
                TAK::Engine::Thread::Mutex mutex;
                TAK::Engine::Thread::ThreadPtr initializer;
                
            public:
                GLGdalMapLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info);
                virtual ~GLGdalMapLayer();
                
                virtual void start();
                virtual void stop();
                
            protected:
                virtual void init();
                
            private:
                static void *initializerThreadFunc(void *);
                
                
            protected:
                atakmap::raster::DatasetProjection *createDatasetProjection() const;
                
            private:
                virtual void initImpl();
                
                static void initGLRunnable(void *);
                
            protected:
                virtual void releaseImpl();
                
            public:
                virtual void release();
                
            protected:
                atakmap::renderer::map::layer::raster::tilereader::GLQuadTileNode *createRoot();
                
                atakmap::raster::tilereader::TileReader *createTileReader();
            };
            
            class GLGdalMapLayer::SPI : public atakmap::renderer::map::layer::raster::GLMapLayerSPI {
            public:
                virtual ~SPI() throw();
                virtual GLMapLayer *createLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info);
            };
        }
    }
}

#endif
