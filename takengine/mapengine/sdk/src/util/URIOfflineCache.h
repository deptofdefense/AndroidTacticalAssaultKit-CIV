#ifndef TAK_ENGINE_UTIL_URIOFFLINECACHE_H_INCLUDED
#define TAK_ENGINE_UTIL_URIOFFLINECACHE_H_INCLUDED

#include "util/Error.h"
#include "util/DataInput2.h"
#include "port/String.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            /**
             *
             */
            class ENGINE_API URIOfflineCache {
            public:
                /**
                 *
                 */
                URIOfflineCache(const char* path, uint64_t sizeLimit) NOTHROWS;

                /**
                 *
                 */
                TAKErr open(DataInput2Ptr& result, const char* URI, int64_t renewSeconds, bool forceRenew = false) NOTHROWS;

                /**
                 *
                 */
                const char *getPath() const NOTHROWS;

            private:
                struct Impl;
                static TAKErr dbAddTask_(bool&, const std::shared_ptr<Impl>& impl, const TAK::Engine::Port::String& subpath, int64_t size, int64_t mtime) NOTHROWS;

            private:
                std::shared_ptr<Impl> impl_;
            };

        }
    }
}

#endif