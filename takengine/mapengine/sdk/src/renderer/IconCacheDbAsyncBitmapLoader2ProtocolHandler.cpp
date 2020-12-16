
#include "renderer/IconCacheDbAsyncBitmapLoader2ProtocolHandler.h"

#include "db/Cursor.h"

#include "util/Logging.h"
#include "util/IO2.h"

#include "thread/Lock.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    Util::TAKErr openIconCacheDb(std::unique_ptr<atakmap::db::SpatiaLiteDB> &ptr, const char *fileName) NOTHROWS;
    void deleteDataInput2(const TAK::Engine::Util::DataInput2 *input);
}

IconCacheDbAsyncBitmapLoader2ProtocolHandler::IconCacheDbAsyncBitmapLoader2ProtocolHandler(const char *iconCacheDbFile) NOTHROWS
: iconCacheFile(iconCacheDbFile)
{ }

IconCacheDbAsyncBitmapLoader2ProtocolHandler::~IconCacheDbAsyncBitmapLoader2ProtocolHandler()
{ }

Util::TAKErr IconCacheDbAsyncBitmapLoader2ProtocolHandler::handleURI(Util::DataInput2Ptr &ctx, const char * uri) NOTHROWS {

    Util::TAKErr code;
    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, this->mutex);
        TE_CHECKRETURN_CODE(code);
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
            ctx = Util::DataInput2Ptr(input.release(), ::deleteDataInput2);
        } else {
            //XXX-- correct for not found?
            code = Util::TE_BadIndex;
        }
        
    } catch (const std::exception &ex) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed lookup for '%s' because '%s'", uri, ex.what());
    } catch (...) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed lookup '%s'", uri);
    }
    
    return code;
}

namespace {
    Util::TAKErr openIconCacheDb(std::unique_ptr<atakmap::db::SpatiaLiteDB> &ptr, const char *fileName) NOTHROWS {
        
        std::unique_ptr<atakmap::db::SpatiaLiteDB> result;
        try {
            result = std::unique_ptr<atakmap::db::SpatiaLiteDB>(new atakmap::db::SpatiaLiteDB(fileName));
        } catch (std::exception &e) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed to open icon cache '%s' because '%s'", fileName, e.what());
            return Util::TE_IO;
        } catch (...) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "IconCacheDbAsyncBitmapLoader2ProtocolHandler: failed to open icon cache '%s' for unknown reason", fileName);
            return Util::TE_IO;
        }
        
        ptr = std::move(result);
        return Util::TE_Ok;
    }
    
    void deleteDataInput2(const TAK::Engine::Util::DataInput2 *input) {
        // XXX-- DataInput2 destructor protected?
        delete static_cast<const TAK::Engine::Util::MemoryInput2 *>(input);
    }
}