
#include "util/URIOfflineCache.h"
#include "util/URI.h"
#include "util/DataOutput2.h"
#include "port/StringBuilder.h"
#include "util/Tasking.h"
#include "thread/Mutex.h"
#include "port/Platform.h"
#include "db/Database2.h"
#include "port/STLVectorAdapter.h"
#include "db/Query.h"
#include <curl/curl.h>
#include <unordered_map>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::DB;

namespace {
    const char* CACHE_DB_NAME = "__cachedb__";
    bool notCacheDbFilter(const char* file);
    TAKErr URIToSubpath(String& result, const char *URI) NOTHROWS;
    TAKErr maintainTask(uint64_t& size, String& path, uint64_t sizeLimit) NOTHROWS;
    TAKErr ensureCacheDb(DatabasePtr& db, const char* basePath) NOTHROWS;
    TAKErr kickCacheFile(int64_t& knownSize, const DatabasePtr& db) NOTHROWS;
    TAKErr addFileToDb(Database2* db, const char* URI, int64_t size, int64_t mtime) NOTHROWS;
    int64_t getKnownSizeFromDb(DatabasePtr& db) NOTHROWS;
    TAKErr getCacheFileLRM(String& URI, int64_t& size, const DatabasePtr& db) NOTHROWS;

    SharedWorkerPtr maintenanceWorker() NOTHROWS {
        return GeneralWorkers_single();
    }

    struct CacheExchange {
        enum State {
            UNKNOWN,
            STALE,
            REMOVED,
            FILLED,
        };

        State dest;
        Future<State> result;
    };
}

struct URIOfflineCache::Impl {

    Impl(const char* path, size_t sizeLimit)
    : db(nullptr, nullptr) {
        
        String fixedPath;
        IO_correctPathSeps(fixedPath, path);
        const char sep[] = { Platform_pathSep(), 0 };

        if (!String_endsWith(fixedPath, sep)) {
            StringBuilder sb;
            StringBuilder_combine(sb, path, Platform_pathSep());
            fixedPath = sb.c_str();
        }

        this->base_path = fixedPath;
        this->size_limit = sizeLimit;
        this->last_known_size = 0;
    }

    TAK::Engine::Thread::Mutex mutex;
    std::unordered_map<std::string, CacheExchange> pending_exchanges;

    DatabasePtr db;
    
    int64_t size_limit;
    int64_t last_known_size;
    String base_path;
};

URIOfflineCache::URIOfflineCache(const char* path, uint64_t sizeLimit) NOTHROWS
: impl_(std::make_shared<Impl>(path, sizeLimit)) 
{}

TAKErr URIOfflineCache::open(DataInput2Ptr& result, const char* URI, int64_t renewSeconds, bool forceRenew) NOTHROWS {

    if (!URI)
        return TE_InvalidArg;

    String subpath;
    TAKErr code = URIToSubpath(subpath, URI);
    if (code != TE_Ok)
        return code;

    // don't let it open own db
    if (String_strcasecmp(subpath, CACHE_DB_NAME) == 0)
        return URI_open(result, URI);

    StringBuilder fullPath;
    StringBuilder_combine(fullPath, impl_->base_path, subpath);

    CacheExchange filledExchange;
    Promise<CacheExchange::State> promise;
    bool fulfillPromise = false;

    // serialize all exchanges
    {
        TAK::Engine::Thread::Lock lock(impl_->mutex);
        if (lock.status != TE_Ok)
            return lock.status;

        auto it = impl_->pending_exchanges.find(subpath.get());
        if (it != impl_->pending_exchanges.end()) {
            if (it->second.dest != CacheExchange::FILLED) {
                // need a fulfill after this exchange
                fulfillPromise = true;
                it->second = CacheExchange{
                    CacheExchange::FILLED,
                    promise.getFuture()
                };
            }
            filledExchange = it->second;
        } else {
            fulfillPromise = true;
            filledExchange = impl_->pending_exchanges
                .insert(std::pair<std::string, CacheExchange>(
                    subpath.get(), 
                    CacheExchange {
                        CacheExchange::FILLED,
                        promise.getFuture()
                    })).first->second;
        }
    }

    if (fulfillPromise) {

        int64_t lastModified = 0; // stays 0 when path does not exist
        IO_getLastModified(&lastModified, fullPath.c_str());
        int64_t sysMilis = Platform_systime_millis();
        
        CacheExchange::State state = CacheExchange::UNKNOWN;
        code = IO_mkdirs(impl_->base_path);
        
        // non-exists OR stale
        int64_t pastModify = (sysMilis - lastModified) / 1000;
        if (code == TE_Ok && pastModify >= renewSeconds || forceRenew) {
            DataInput2Ptr input(nullptr, nullptr);
            code = URI_open(input, URI);
            
            FileOutput2 output;
            if (code == TE_Ok) {
                code = output.open(fullPath.c_str());
            }

            if (code == TE_Ok) {
                code = IO_copy(output, *input);
                state = CacheExchange::FILLED;
            }

            output.close();
            if (input)
                input->close();

            // update maintenance catalog
            int64_t mtime = 0;
            int64_t size = 0;
            IO_getLastModified(&mtime, fullPath.c_str());
            IO_getFileSizeV(&size, fullPath.c_str());

            Task_begin(maintenanceWorker(), dbAddTask_, impl_, subpath, size, mtime);
        } else if (code == TE_Ok && lastModified != 0) {
            state = CacheExchange::FILLED;
        }

        promise = state;
    }

    CacheExchange::State state;
    TAKErr exchCode = TE_Err;
    filledExchange.result.await(state, exchCode);

    if (exchCode == TE_Ok && state == CacheExchange::FILLED) {
        {
            TAK::Engine::Thread::Lock lock(impl_->mutex);
            if (lock.status != TE_Ok)
                return lock.status;

            // clear from pendings
            auto it = impl_->pending_exchanges.find(subpath.get());
            if (it != impl_->pending_exchanges.end())
                impl_->pending_exchanges.erase(it);
        }

        code = IO_openFile(result, fullPath.c_str());
    }

    // All else fails-- just read directly
    if (code != TE_Ok)
        code = URI_open(result, URI);

    return code;
}

const char* URIOfflineCache::getPath() const NOTHROWS {
    return impl_->base_path.get();
}

TAKErr URIOfflineCache::dbAddTask_(bool&, const std::shared_ptr<Impl>& impl, const TAK::Engine::Port::String& subpath, int64_t size, int64_t mtime) NOTHROWS {
    TAKErr code = TE_Ok;

    if (!impl->db) {
        code = ensureCacheDb(impl->db, impl->base_path);
        if (code != TE_Ok)
            return code;

        // get the sum total
        impl->last_known_size = getKnownSizeFromDb(impl->db);
    }

    // upsert entry
    code = addFileToDb(impl->db.get(), subpath, size, mtime);

    QueryPtr query(nullptr, nullptr);
    impl->db->query(query, "SELECT uri,size,mtime FROM cache_files ORDER BY mtime ASC;");

    // kick until known to be below size
    while (impl->last_known_size > impl->size_limit && query->moveToNext() == TE_Ok) {

        const char* lrmSubpath = "";
        int64_t lrmSize = 0;

        query->getString(&lrmSubpath, 0);
        query->getLong(&lrmSize, 1);

        // skip what was added
        if (String_equal(subpath, lrmSubpath))
            continue;

        {
            TAK::Engine::Thread::Lock lock(impl->mutex);
            if (lock.status != TE_Ok)
                return lock.status;

            // skip what is pending
            auto it = impl->pending_exchanges.find(subpath.get());
            if (it != impl->pending_exchanges.end())
                continue;   
        }

        StringBuilder fullPath;
        StringBuilder_combine(fullPath, impl->base_path, lrmSubpath);

        TAKErr delCode = IO_delete(fullPath.c_str());
        if (delCode == TE_Ok)
            impl->last_known_size -= lrmSize;
    }

    return code;
}

namespace {
    TAKErr URIToSubpath(String& result, const char* URI) NOTHROWS {

        char* encoded = curl_escape(URI, (int)strlen(URI));
        if (!encoded)
            return TE_Err;

        result = encoded;
        curl_free(encoded);

        return TE_Ok;
    }

    TAKErr buildCacheVisitor(void* opaque, const char* filePath) NOTHROWS {

        Database2* db = static_cast<Database2*>(opaque);

        String fileName;
        IO_getName(fileName, filePath);

        if (String_strcasecmp(fileName, CACHE_DB_NAME) == 0)
            return TE_Ok;

        int64_t size = 0;
        int64_t mtime = 0;

        IO_getLastModified(&mtime, filePath);
        IO_getFileSizeV(&size, filePath);
        addFileToDb(db, fileName, size, mtime);

        return TE_Ok;
    }

    TAKErr ensureCacheDb(DatabasePtr& db, const char* basePath) NOTHROWS {

        StringBuilder dbPath;
        StringBuilder_combine(dbPath, basePath, CACHE_DB_NAME);

        IO_mkdirs(basePath);

        TAKErr code = Databases_openDatabase(db, dbPath.c_str(), false);
        if (code != TE_Ok) {
        
            // try a delete and open again
            IO_delete(dbPath.c_str());
            code = Databases_openDatabase(db, dbPath.c_str(), false);
            if (code != TE_Ok)
                return code;
        }

        code = db->execute("CREATE TABLE IF NOT EXISTS cache_files (uri TEXT PRIMARY KEY, size INT, mtime INT)", nullptr, 0);
        if (code != TE_Ok)
            return code;

        return IO_visitFiles(buildCacheVisitor, db.get(), basePath, TELFM_ImmediateFiles);
    }

    TAKErr addFileToDb(Database2* db, const char* URI, int64_t size, int64_t mtime) NOTHROWS {

        StringBuilder sizeStr;
        StringBuilder mtimeStr;

        sizeStr.append(size);
        mtimeStr.append(mtime);

        const char* args[] = {
            URI,
            sizeStr.c_str(),
            mtimeStr.c_str()
        };

        
        TAKErr code = db->execute("INSERT OR REPLACE INTO cache_files(uri,size,mtime) VALUES(?,?,?);",
            args, 3);
        if (code != TE_Ok)
            return code;

        return TE_Ok;
    }

    bool notCacheDbFilter(const char* file) {
        return String_strcasecmp(file, CACHE_DB_NAME) != 0;
    }

    TAKErr getCacheFileLRM(String& URI, int64_t& size, const DatabasePtr& db) NOTHROWS {

        TAK::Engine::DB::QueryPtr query(nullptr, nullptr);
        TAKErr code = db->query(query, "SELECT uri,size FROM cache_files WHERE mtime = (SELECT min(mtime) FROM cache_files)");
        if (code != TE_Ok)
            return code;

        if (query->moveToNext() != TE_Ok)
            return TE_Done;

        const char *uriStr = "";
        int64_t sizeVal = 0;

        query->getString(&uriStr, 0);
        query->getLong(&sizeVal, 1);

        URI = uriStr;
        size = sizeVal;

        return TE_Ok;
    }

    int64_t getKnownSizeFromDb(DatabasePtr& db) NOTHROWS {

        TAK::Engine::DB::QueryPtr query(nullptr, nullptr);
        TAKErr code = db->query(query, "SELECT sum(size) FROM cache_files");
        if (code != TE_Ok)
            return -1;

        if (query->moveToNext() != TE_Ok)
            return -1;

        int64_t sizeVal = 0;
        query->getLong(&sizeVal, 0);
        return sizeVal;
    }
}
