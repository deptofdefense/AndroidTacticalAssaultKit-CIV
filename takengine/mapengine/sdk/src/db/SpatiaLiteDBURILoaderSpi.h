
#ifndef SpatialiteDBURILoader_hpp
#define SpatialiteDBURILoader_hpp

#include <unordered_map>

#include "thread/Mutex.h"

#include "util/URILoader.h"
#include "db/SpatiaLiteDB.h"

namespace atakmap {
    namespace db {
        
        class SpatiaLiteDBURILoaderSpi : public atakmap::util::URILoaderSpi {
        public:
            virtual ~SpatiaLiteDBURILoaderSpi();
            virtual atakmap::util::DataInput *openURI(const char *uri, const atakmap::util::URILoaderArgs *optionalArgs);
        
        private:
            std::shared_ptr<atakmap::db::SpatiaLiteDB> ensureCachedDb(std::string &filePath);
            void flushDbCache();
            
        private:
            TAK::Engine::Thread::Mutex _mutex;
            std::unordered_map<std::string, std::shared_ptr<atakmap::db::SpatiaLiteDB>> _dbCache;
        };
        
    }
}

#endif /* SpatialiteDBURILoader_hpp */
