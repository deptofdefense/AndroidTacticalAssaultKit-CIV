#include "formats/quantizedmesh/impl/TerrainDataCache.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "port/String.h"
#include "util/Logging2.h"

#include <map>
#include <list>
#include <string>


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;

namespace {
    const int MAX_SIZE = 16000000;
    std::map<std::string, std::shared_ptr<TerrainData>> cache;
    int64_t totalSize = 0;
    Thread::Mutex cacheMutex(Thread::TEMT_Recursive);
}


void TAK::Engine::Formats::QuantizedMesh::Impl::TerrainDataCache_clear()
{
    Thread::Lock lock(cacheMutex);
    cache.clear();
    totalSize = 0;
}

void TAK::Engine::Formats::QuantizedMesh::Impl::TerrainDataCache_dispose(const char *dir)
{
    char pathSep = TAK::Engine::Port::Platform_pathSep();
    std::string pathSepStr(&pathSep, 1);

    std::string path(dir);
    if (!Port::String_endsWith(dir, pathSepStr.c_str()))
        path.append(pathSepStr);
    {
        Thread::Lock lock(cacheMutex);
        auto iter = cache.begin();
        while (iter != cache.end()) {
            auto cur = iter;
            iter++;
            const std::string &f = cur->first;
            if (f.compare(0, path.length(), path) == 0) {
                totalSize -= cur->second->getTotalSize();
                cache.erase(cur);
            }
        }
    }
}

std::shared_ptr<TerrainData> TAK::Engine::Formats::QuantizedMesh::Impl::TerrainDataCache_getData(const char *file, int level)
{
    {
        Thread::Lock lock(cacheMutex);

        // Check if file is already cached
        auto iter = cache.find(std::string(file));
        if (iter != cache.end())
            return iter->second;

        // About to load a new file - check if we've already exceeded the
        // max cache size
        if (totalSize > MAX_SIZE) {
            // Clear the entire cache so we can start over
            Util::Logger_log(Util::LogLevel::TELL_Debug, "TerrainDataCache exceeded max size of %d - clearing...", MAX_SIZE);
            TerrainDataCache_clear();
        }
    }

    // Check if file actually exists first
    bool exists = false;
    Util::TAKErr code =  Util::IO_exists(&exists, file);
    if (code != Util::TE_Ok || !exists)
        return nullptr;

    // Remote sources sometimes give 0 length tiles;  ignore these, we know they
    // will fail to read.
    int64_t size = 0;
    code = Util::IO_getFileSizeV(&size, file);
    if (code != Util::TE_Ok || size == 0)
        return nullptr;

    // Load data
    std::unique_ptr<TerrainData> data;
    code = TerrainData_deserialize(data, file, level);
    if (code != Util::TE_Ok)
        return nullptr;

    {
        Thread::Lock lock(cacheMutex);

        auto iter = cache.find(file);
        if (iter != cache.end()) {
            // Since the terrain data read is done asynchronously,
            // we may have loaded it already on another thread
            return iter->second;
        }
        std::shared_ptr<TerrainData> ret = std::move(data);
        cache[file] = ret;
        totalSize += ret->getTotalSize();
        return ret;
    }
}

std::shared_ptr<TerrainData> TAK::Engine::Formats::QuantizedMesh::Impl::TerrainDataCache_getData(const QMESourceLayer &layer, const TileCoordImpl &tile)
{
    Port::String file;
    Util::TAKErr code = layer.getTileFilename(&file, tile.x, tile.y, tile.z);
    if (code != Util::TE_Ok)
        return nullptr;
    return TerrainDataCache_getData(file.get(), tile.z);
}
