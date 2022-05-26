#include "jpersistentrasterdatastore.h"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cerrno>

#include <sys/stat.h>
#ifdef __ANDROID__
#include <dirent.h>
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
#ifdef __ANDROID__
        struct stat st_buf;
        int err;

        err = stat (name, &st_buf);
        // XXX - not sure what the appropriate error handling is here --
        //         raise java exception???
        if (err)
            return true; // XXX - log issue

        if (S_ISREG (st_buf.st_mode)) {
            if(totalSize)
                (*totalSize) += st_buf.st_size;
            (*numFiles)++;
            if(lastModified) {
                if(st_buf.st_mtime > *lastModified)
                    (*lastModified) = st_buf.st_mtime;
            }
        } else if (S_ISDIR (st_buf.st_mode)) {
            DIR *dp;
            struct dirent *entry;
            char dirname[1024];

            dp  = opendir(name);
            if(dp) {
                while (true) {
                    // get next entry
                    entry = readdir(dp);
                    // if no more entries; break
                    if(!entry)
                        break;

                    if(strncmp(entry->d_name, "..", 2) != 0 && strncmp(entry->d_name, ".", 1) != 0) {
                        //__android_log_print(ANDROID_LOG_DEBUG, "PersistentRasterDataStore", "process child: %s/%s\n", name, entry->d_name);

                        // compose the child name and recurse
                        err = snprintf(dirname, 1024, "%s/%s", name, entry->d_name);
                        if(err >= 0 && err < 1024) {
                            if(!getFileTreeData(dirname, numFiles, totalSize, lastModified, limit))
                                break;
                        } else {
                            // XXX -
                            (*numFiles)++;
                        }
                    }
                }
            }
            closedir(dp);
        }

        return ((*numFiles)<limit);
#else
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
#endif
    }
}
