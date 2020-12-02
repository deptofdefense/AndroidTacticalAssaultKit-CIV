#include "jpersistentrasterdatastore.h"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cerrno>

#include <sys/stat.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include <util/IO2.h>

#include "common.h"

using namespace TAK::Engine::Util;

namespace
{
    bool getFileTreeData(const char *name, int64_t *numFiles, int64_t *totalSize, int64_t *lastModified, const int64_t limit);
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_raster_PersistentRasterDataStore_getFileTreeData
  (JNIEnv *env, jclass clazz, jstring jfile, jlong numFilesPtr, jlong totalSizePtr, jlong lastModifiedPtr, jlong limit)
{
    const char *file = env->GetStringUTFChars(jfile, NULL);
    int64_t *numFiles = JLONG_TO_INTPTR(int64_t, numFilesPtr);
    int64_t *totalSize = JLONG_TO_INTPTR(int64_t, totalSizePtr);
    int64_t *lastModified = JLONG_TO_INTPTR(int64_t, lastModifiedPtr);

    if(numFiles)        *numFiles = 0LL;
    if(totalSize)       *totalSize = 0LL;
    if(lastModified)    *lastModified = 0LL;

    const bool retval = getFileTreeData(file,
                                        numFiles,
                                        totalSize,
                                        lastModified,
                                        limit);
    env->ReleaseStringUTFChars(jfile, file);
    return retval;
}


namespace
{
    struct Stats
    {
        int64_t totalSize;
        int64_t numFiles;
        int64_t lastModified;
        int64_t limit;
    };

    TAKErr fileTreeVisitor(void *opaque, const char *path)
    {
        Stats &stats = *static_cast<Stats *>(opaque);
        int64_t fl = 0LL;
        TAK::Engine::Util::IO_length(&fl, path);
        stats.totalSize += 0LL;
        stats.numFiles++;
        int64_t lastModified(-1LL);
        IO_getLastModified(&lastModified, path);
        if (lastModified > stats.lastModified)
            stats.lastModified = lastModified;
        if (stats.numFiles == stats.limit)
            return TE_Done;
        else
            return TE_Ok;
    }

    bool getFileTreeData(const char *name, int64_t *numFiles, int64_t *totalSize, int64_t *lastModified, const int64_t limit)
    {
        Stats stats;
        stats.lastModified = -1LL;
        stats.limit = limit;
        stats.numFiles = 0LL;
        stats.totalSize = 0LL;
        IO_visitFiles(fileTreeVisitor, &stats, name, TELFM_RecursiveFiles);

        *numFiles = stats.numFiles;
        *totalSize = stats.totalSize;
        *lastModified = stats.lastModified;

        return ((*numFiles)<limit);
    }
}
