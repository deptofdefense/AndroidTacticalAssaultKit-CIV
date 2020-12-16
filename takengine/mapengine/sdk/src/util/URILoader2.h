
#ifndef TAK_ENGINE_UTIL_URILOADER2_H_INCLUDED
#define TAK_ENGINE_UTIL_URILOADER2_H_INCLUDED

#include "util/DataInput2.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            
            struct URILoader2Args {
                
                URILoader2Args() :
                continueOnError(false) { }
                
                bool continueOnError;
            };
            
            class URILoader2Spi {
            public:
                virtual ~URILoader2Spi() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr openURI(TAK::Engine::Util::DataInput2Ptr &input, const char *uri, const URILoader2Args *optionalArgs) NOTHROWS = 0;
            };
           
            TAK::Engine::Util::TAKErr URILoader2_openURI(TAK::Engine::Util::DataInput2Ptr &input, const char *uri, const URILoader2Args *optionalArgs);
            TAK::Engine::Util::TAKErr URILoader2_registerSpi(const std::shared_ptr<URILoader2Spi> &spi, int priority);
            TAK::Engine::Util::TAKErr URILoader2_unregisterSpi(const URILoader2Spi *spi);
        }
    }
}

#endif