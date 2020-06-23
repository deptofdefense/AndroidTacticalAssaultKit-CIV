#ifndef TAK_ENGINE_UTIL_FILTER_H_INCLUDED
#define TAK_ENGINE_UTIL_FILTER_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            template<class T>
            class Filter
            {
            protected :
                virtual ~Filter() NOTHROWS = 0;
            public :
                virtual bool accept(const T arg) NOTHROWS = 0;
            };

            template<class T>
            inline Filter<T>::~Filter() NOTHROWS
            {}
        }
    }
}
#endif // TAK_ENGINE_UTIL_FILTER_H_INCLUDED
