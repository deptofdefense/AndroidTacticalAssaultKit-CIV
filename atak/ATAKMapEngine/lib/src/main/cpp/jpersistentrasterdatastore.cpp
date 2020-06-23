#include "jpersistentrasterdatastore.h"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cerrno>

#include <dirent.h>
#include <sys/stat.h>

#include <android/log.h>

#include "common.h"

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
    bool getFileTreeData(const char *name, int64_t *numFiles, int64_t *totalSize, int64_t *lastModified, const int64_t limit)
    {
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
    }
}
