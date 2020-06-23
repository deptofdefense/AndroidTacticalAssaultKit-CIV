#ifndef TAK_ENGINE_UTIL_PROCESSINGCALLBACK_H_INCLUDED
#define TAK_ENGINE_UTIL_PROCESSINGCALLBACK_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            struct ENGINE_API ProcessingCallback
            {
            public :
                ProcessingCallback() NOTHROWS;

                bool *cancelToken;
                void *opaque;
                /**
                 * @param current   Current progress, less than zero for indeterminate
                 * @param max       The maximum progress value, ignored if `current` is less than zero
                 */
                TAKErr(*progress)(void *opaque, const int current, const int max) NOTHROWS;
                TAKErr(*error)(void *opaque, const char *msg) NOTHROWS;
            };

            bool ProcessingCallback_isCanceled(ProcessingCallback *callback) NOTHROWS;
        }
    }
}

#endif

