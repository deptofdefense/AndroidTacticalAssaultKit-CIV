#ifndef ATAKMAP_RENDERER_FEATURE_STYLE_GLSTYLESPI_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_STYLE_GLSTYLESPI_H_INCLUDED

#include <list>
#include "thread/Mutex.h"

namespace atakmap {
    
    namespace feature {
        class Style;
        class Geometry;
    }

    namespace renderer {
        namespace feature {
            class GLStyle;

            class GLStyleSpi;
            
            struct GLStyleSpiArg {
                const atakmap::feature::Style *style;
                const atakmap::feature::Geometry *geometry;
            };
            

            class GLStyleSpi {
            public:
                virtual ~GLStyleSpi();
                virtual GLStyle *create(const GLStyleSpiArg &style) = 0;
            };
            
            class GLStyleFactory {
            public:
                static void registerSpi(GLStyleSpi *spi);
                static void unregisterSpi(GLStyleSpi *spi);
                static GLStyle *create(const GLStyleSpiArg &style);
                
            private:
                static std::list<GLStyleSpi *> spis;
                static TAK::Engine::Thread::Mutex mutex;
            };
        }
    }
}

#endif
