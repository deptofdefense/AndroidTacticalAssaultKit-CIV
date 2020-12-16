
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "renderer/IconCacheDbURILoader2Spi.h"
#include "db/Cursor.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    TAKErr openIconCacheDb(std::unique_ptr<atakmap::db::SpatiaLiteDB> &ptr, const char *fileName) NOTHROWS;
    void deleteDataInput2(const TAK::Engine::Util::DataInput2 *input);
}

IconCacheDbURILoader2Spi::IconCacheDbURILoader2Spi(const char *filePath) NOTHROWS
: iconCacheFile(filePath) { }

IconCacheDbURILoader2Spi::~IconCacheDbURILoader2Spi() NOTHROWS {
    
}

TAKErr IconCacheDbURILoader2Spi::openURI(TAK::Engine::Util::DataInput2Ptr &result, const char *uri, const TAK::Engine::Util::URILoader2Args *optionalArgs) NOTHROWS {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    {
        if (!this->iconCacheDb) {
            code = openIconCacheDb(this->iconCacheDb, this->iconCacheFile);
            TE_CHECKRETURN_CODE(code);
        }
    }
    
    std::vector<const char *> args {
        uri
    };
    
    code = Util::TE_IO;
    try {
        std::unique_ptr<atakmap::db::Cursor> cursor(this->iconCacheDb->query("SELECT bitmap FROM cache WHERE url = ?", args));
        if (cursor->moveToNext()) {
            atakmap::util::BlobImpl blob = cursor->getBlob(0);
            size_t len = blob.second - blob.first;
            std::unique_ptr<TAK::Engine::Util::MemoryInput2> input(new TAK::Engine::Util::MemoryInput2());
            code = input->open(blob.takeData(), len);
            TE_CHECKRETURN_CODE(code);
            result = DataInput2Ptr(input.release(), ::deleteDataInput2);
        } else {
            //XXX-- correct for not found?
            code = Util::TE_Unsupported;
        }
        
    } catch (const std::exception &ex) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed lookup for '%s' because '%s'", uri, ex.what());
    } catch (...) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed lookup '%s'", uri);
    }
    
    return code;
}

namespace {
    TAKErr openIconCacheDb(std::unique_ptr<atakmap::db::SpatiaLiteDB> &ptr, const char *fileName) NOTHROWS {
        
        std::unique_ptr<atakmap::db::SpatiaLiteDB> result;
        try {
            result = std::unique_ptr<atakmap::db::SpatiaLiteDB>(new atakmap::db::SpatiaLiteDB(fileName));
        } catch (std::exception &e) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed to open icon cache '%s' because '%s'", fileName, e.what());
            return TE_IO;
        } catch (...) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed to open icon cache '%s' for unknown reason", fileName);
            return TE_IO;
        }
        
        ptr = std::move(result);
        return TE_Ok;
    }
    
    void deleteDataInput2(const TAK::Engine::Util::DataInput2 *input) {
        // XXX-- DataInput2 destructor protected?
        delete static_cast<const TAK::Engine::Util::MemoryInput2 *>(input);
    }
}
