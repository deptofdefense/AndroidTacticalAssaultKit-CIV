
#ifndef ATAKMAP_UTIL_URILOADER_H_INCLUDED
#define ATAKMAP_UTIL_URILOADER_H_INCLUDED

#include <list>

#include "thread/Mutex.h"
#include "thread/Lock.h"

#include "util/IO.h"

namespace atakmap {
    
    namespace util {
    
        struct URILoaderArgs {
            //XXX- for future implementations
        };
        
        class URILoaderSpi {
        public:
            virtual ~URILoaderSpi();
            virtual atakmap::util::DataInput *openURI(const char *uri, const URILoaderArgs *optionalArgs) = 0;
        };
        
        class URILoader {
        public:
            static atakmap::util::DataInput *openURI(const char *uri, const URILoaderArgs *optonalArgs);
            
            static void registerSpi(URILoaderSpi *spi, int priority);
            
            static void unregisterSpi(URILoaderSpi *spi);
            
        private:
            static TAK::Engine::Thread::Mutex mutex;
            static std::list<URILoaderSpi *> spis;
        };
    }
}

#endif