#ifndef TAK_ENGINE_UTIL_IO2_H_INCLUDED
#define TAK_ENGINE_UTIL_IO2_H_INCLUDED

#include <cstdio>
#include <cstdlib>

#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/Filesystem.h"
#include "util/NonCopyable.h"
#include "util/NonHeapAllocatable.h"

#if TE_SHOULD_ADAPT_STORAGE_PATHS
#   define TE_GET_STORAGE_PATH(p) TAK::Engine::Util::File_getStoragePath(p)
#   define TE_GET_RUNTIME_PATH(p) TAK::Engine::Util::File_getRuntimePath(p)

// for exception based code
#   define TE_GET_STORAGE_PATH_THROW(p, np, t) \
    TAK::Engine::Port::String np = p; \
    if (TAK::Engine::Util::File_getStoragePath(np) != TAK::Engine::Util::TE_Ok) \
        throw t;

#   define TE_GET_RUNTIME_PATH_THROW(p, np, t) \
    TAK::Engine::Port::String np = p; \
    if (TAK::Engine::Util::File_getRuntimePath(np) != TAK::Engine::Util::TE_Ok) \
        throw t;

// for takerr based code
#   define TE_GET_STORAGE_PATH_CODE(p, np, c) \
    TAK::Engine::Port::String np = p; \
    c = TAK::Engine::Util::File_getStoragePath(np);

#   define TE_GET_RUNTIME_PATH_CODE(p, np, c) \
    TAK::Engine::Port::String np = p; \
    c = TAK::Engine::Util::File_getRuntimePath(np);


#else
#   define TE_GET_STORAGE_PATH(p) TAK::Engine::Util::TE_Ok
#   define TE_GET_RUNTIME_PATH(p) TAK::Engine::Util::TE_Ok
#   define TE_GET_STORAGE_PATH_THROW(p, np, message) const char *np = p;
#   define TE_GET_STORAGE_PATH_THROW(p, np, message) const char *np = p;
#   define TE_GET_STORAGE_PATH_CODE(p, np, c) const char *np = p; c = TAK::Engine::Util::TE_Ok;
#   define TE_GET_RUNTIME_PATH_CODE(p, np, c) const char *np = p; c = TAK::Engine::Util::TE_Ok;
#endif

namespace TAK {
    namespace Engine {
        namespace Util {
            class ENGINE_API DataInput2;
            class ENGINE_API DataOutput2;

            enum TAKEndian
            {
                TE_BigEndian,
                TE_LittleEndian,
            };

#if __BIG_ENDIAN__ || __ARMEB__ || __THUMBEB__ \
  || defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
            const TAKEndian TE_PlatformEndian = TE_BigEndian;
#else
            const TAKEndian TE_PlatformEndian = TE_LittleEndian;
#endif

            class ENGINE_API File : NonHeapAllocatable,
                         NonCopyable
            {
            public:
                File(const char *path, const char *mode);
            public :
                ~File();
				File(const File &) = delete;
            public:
                operator FILE * ();
				void *operator new(std::size_t) = delete;
				void *operator new[](std::size_t) = delete;
				void operator delete(void *) = delete;
				void operator delete[](void *) = delete;
            private:
                FILE *fd;
            };

            ENGINE_API TAKErr IO_copy(const char *dst, const char *src) NOTHROWS;
	    ENGINE_API TAKErr IO_copy(DataOutput2 &dst, DataInput2 &src) NOTHROWS;
	    ENGINE_API TAKErr IO_createTempFile(Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS;
	    ENGINE_API TAKErr IO_createTempDirectory(Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS;
            ENGINE_API TAKErr IO_getFileCount(std::size_t *value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_getFileCount(std::size_t *value, const char *path, const std::size_t limit) NOTHROWS;

	    ENGINE_API TAKErr IO_getParentFile(Port::String &value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_getName(Port::String &value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_getAbsolutePath(Port::String &value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_getRelativePath(Port::String &value, const char *basePath, const char *filePath) NOTHROWS;
	    ENGINE_API TAKErr IO_listFiles(Port::Collection<Port::String> &value, const char *path, bool(*filter)(const char *file) = nullptr) NOTHROWS;
            ENGINE_API TAKErr IO_listFiles(Port::Collection<Port::String> &value, const char *path, ListFilesMethod method, bool(*filter)(const char *file) = nullptr) NOTHROWS;
	    ENGINE_API TAKErr IO_getDirectoryInfo(int64_t *fileCount, int64_t *size, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_length(int64_t *value, const char* path) NOTHROWS;
	    ENGINE_API TAKErr IO_getLastModified(int64_t *value, const char* path) NOTHROWS;
	    ENGINE_API TAKErr IO_isDirectory(bool *value, const char* path) NOTHROWS;
            ENGINE_API TAKErr IO_isNetworkDirectory(bool *value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_isFile(bool *value, const char* path) NOTHROWS;
	    ENGINE_API TAKErr IO_exists(bool *value, const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_delete(const char *path) NOTHROWS;
	    ENGINE_API TAKErr IO_mkdirs(const char* dirPath) NOTHROWS;
            ENGINE_API TAKErr IO_openFile(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *path) NOTHROWS;

            /**
             * Iterates over the files under the specified path
             * @param visitor   The visitor callback, accepts 'opaque' and the current file path. Return TE_Done to signal early return
             * @param opaque    The opaque argument to the visitor
             * @param vpath     The base path
             * @param method    The file listing method
             */
            ENGINE_API TAKErr IO_visitFiles(TAKErr (*visitor)(void *, const char *), void *opaque, const char *vpath, ListFilesMethod method) NOTHROWS;

            ENGINE_API TAKErr IO_readZipEntry(std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> &value, std::size_t *len, const char *zipPath, const char *entry) NOTHROWS;

            /**
             * Lists all zip entries (full depth-first paths)
             */
            ENGINE_API TAKErr IO_listZipEntries(Port::Collection<Port::String> &value, const char *path, bool(*filter)(const char *file) = nullptr) NOTHROWS;
            ENGINE_API TAKErr IO_openZipEntry(std::unique_ptr<DataInput2, void (*)(const DataInput2 *)> &dataPtr, const char *zipPath, const char *entry) NOTHROWS;

            ENGINE_API TAKErr IO_registerZipExt(const char *ext) NOTHROWS;

            /**
             * Open a stream to a file with a virtual path (i.e. a path that may be within an archive)
             */
            ENGINE_API TAKErr IO_openFileV(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *vpath);

            /**
             * Test for existence of a virtual path (i.e. a path that may be within an archive)
             */
            ENGINE_API TAKErr IO_existsV(bool *value, const char *vpath) NOTHROWS;

            ENGINE_API TAKErr IO_isFileV(bool *value, const char *vpath) NOTHROWS;
            ENGINE_API TAKErr IO_isDirectoryV(bool *value, const char *vpath) NOTHROWS;

            /**
             * Get the file size of a file with a virtual path (i.e. a path that may be insize an archive).
             * The path must be a file and not a directory.
             */
            ENGINE_API TAKErr IO_getFileSizeV(int64_t *size, const char *vpath) NOTHROWS;

            ENGINE_API TAKErr IO_listFilesV(Port::Collection<Port::String> &value, const char *vpath, ListFilesMethod method, bool(*filter)(const char *file) = nullptr, size_t limit = SIZE_MAX) NOTHROWS;

            /**
             * Iterates over the files under the specified path
             * @param visitor   The visitor callback, accepts 'opaque' and the current file path. Return TE_Done to signal early return
             * @param opaque    The opaque argument to the visitor
             * @param vpath     The base path
             * @param method    The file listing method
             */
            ENGINE_API TAKErr IO_visitFilesV(TAKErr (*visitor)(void *, const char *), void *opaque, const char *vpath, ListFilesMethod method) NOTHROWS;
            

            /**
             * Modifies the supplied path such that it is appropriate for storage (persisting) purposes.
             */
			ENGINE_API TAKErr File_getStoragePath(Port::String &path) NOTHROWS;

            /**
             * Modifies the supplied path such that it is appropriate for runtime purposes.
             */
			ENGINE_API TAKErr File_getRuntimePath(Port::String &path) NOTHROWS;

            /**
             * Find the first file extension in path. This can be in the middle of a path
             * directory (i.e. virtual paths), so to ensure its the ext of the final file
             * get the file name first or repeat calls.
             */
            ENGINE_API TAKErr IO_getExt(Port::String &ext, const char **extPos, const char *path) NOTHROWS;

            ENGINE_API TAKErr IO_correctPathSeps(Port::String &result, const char *path) NOTHROWS;

            ENGINE_API void IO_setFilesystem(const std::shared_ptr<Filesystem> &filesystem) NOTHROWS;
        }
    }
}

#endif
