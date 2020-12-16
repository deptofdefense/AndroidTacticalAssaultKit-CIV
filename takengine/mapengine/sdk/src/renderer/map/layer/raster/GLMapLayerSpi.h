#ifndef ATAKMAP_RENDERER_GLMAPLAYERSPI_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPLAYERSPI_H_INCLUDED

namespace atakmap {
    
    namespace raster {
        class DatasetDescriptor;
    }
    
    namespace renderer {
        
        class GLRenderContext;
        
        namespace map {
            
            namespace layer {
                namespace raster {

                    class GLMapLayer;
                    
                    class GLMapLayerSPI {
                    public:
                        virtual ~GLMapLayerSPI() throw();
                        virtual GLMapLayer *createLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info) = 0;
                        
                    protected:
                        static bool checkSupportedTypes(const char * const supportedTypes[], const char *type);
                    };

                }
            }
        }
    }
}


#endif
