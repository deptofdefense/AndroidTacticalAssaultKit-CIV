
#include "thread/Lock.h"

#include "util/IO.h"
#include "db/Cursor.h"
#include "db/SpatiaLiteDBURILoaderSpi.h"

using namespace atakmap::db;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

class SpatiaLiteDBUriLoaderDataInput : public atakmap::util::DataInput {
public:
    SpatiaLiteDBUriLoaderDataInput(const uint8_t *begin, const uint8_t *end)
    : _begin(nullptr), _end(nullptr), _pos(nullptr) {
        size_t size = end - begin;
        if (size) {
            uint8_t *copy = new uint8_t[size];
            memcpy(copy, begin, size);
            _begin = copy;
            _end = copy + size;
            _pos = copy;
        }
    }
    
    virtual ~SpatiaLiteDBUriLoaderDataInput() throw() {
        this->close();
    }
    
    virtual void close() throw (atakmap::util::IO_Error) {
        
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw atakmap::util::IO_Error("Failed to acquire mutex");
        
        delete [] _begin;
        _begin = nullptr;
        _end = nullptr;
        _pos = nullptr;
    }
    
    virtual size_t read(uint8_t *buf, size_t len) throw (atakmap::util::IO_Error) {
        
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw atakmap::util::IO_Error("Failed to acquire mutex");
        
        size_t avail = _end - _pos;
        if (avail == 0 && len != 0) {
            return EndOfStream;
        }
        
        if (avail < len) {
            len = avail;
        }
        
        memcpy(buf, _pos, len);
        _pos += len;
        return len;
    }
    
    virtual uint8_t readByte() throw (atakmap::util::IO_Error) {
        uint8_t r = 0;
        if (EndOfStream == read(&r, 1)) {
            throw atakmap::util::IO_Error("end of stream");
        }
        return r;
    }

    
private:
    Mutex mutex;
    const uint8_t *_begin;
    const uint8_t *_end;
    const uint8_t *_pos;
};

SpatiaLiteDBURILoaderSpi::~SpatiaLiteDBURILoaderSpi() {
    
}

std::string urlDecode(const std::string &src) {
    std::string ret;
    char ch;
    int i, ii;
    for (i=0; i<src.length(); i++) {
        if (int(src[i])==37) {
            sscanf(src.substr(i+1,2).c_str(), "%x", &ii);
            ch=static_cast<char>(ii);
            ret+=ch;
            i=i+2;
        } else {
            ret+=src[i];
        }
    }
    return (ret);
}

std::shared_ptr<atakmap::db::SpatiaLiteDB> SpatiaLiteDBURILoaderSpi::ensureCachedDb(std::string &filePath) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, _mutex);
    if (code != TE_Ok)
        throw std::runtime_error("Failed to acquire mutex");

    auto it = _dbCache.find(filePath);
    if (it != _dbCache.end()) {
        return it->second;
    }
    it = _dbCache.insert(_dbCache.end(), std::make_pair(filePath, std::make_shared<SpatiaLiteDB>(filePath.c_str())));
    return it->second;
}

void SpatiaLiteDBURILoaderSpi::flushDbCache() {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, _mutex);
    if (code != TE_Ok)
        throw std::runtime_error("Failed to acquire mutex");
    _dbCache.clear();
}

atakmap::util::DataInput *SpatiaLiteDBURILoaderSpi::openURI(const char *uri, const atakmap::util::URILoaderArgs *optionalArgs) {
    
    if (strstr(uri, "sqlite://") == uri) {
    
        std::string decodedURI = urlDecode(uri + 9);
        uri = decodedURI.c_str();
        
        const char *query = strstr(uri, "?query=");
        if (!query) {
            return nullptr;
        }
        
        std::string filePath(uri, query);
        query += 7;
        
        std::shared_ptr<atakmap::db::SpatiaLiteDB> database = ensureCachedDb(filePath);
        std::unique_ptr<Cursor> cursor(database->query(query));
        
        if (cursor && cursor->moveToNext()) {
            Cursor::Blob blob = cursor->getBlob(0);
            return new SpatiaLiteDBUriLoaderDataInput(blob.first, blob.second);
        }
    }
    
    return nullptr;
}
