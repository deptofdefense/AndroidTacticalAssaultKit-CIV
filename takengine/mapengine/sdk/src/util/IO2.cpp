#include "util/IO2.h"

#include <cstdio>
#include <unistd.h>
#ifdef MSVC
#include <sys/timeb.h>
#endif

#include <cpl_vsi.h>

#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/ConfigOptions.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/IO.h"
#include "util/Logging.h"
#include "util/ZipFile.h"
#include "util/CopyOnWrite.h"
#include "port/STLVectorAdapter.h"
#include "port/String.h"
#include "port/StringBuilder.h"

#include <platformstl/filesystem/directory_functions.hpp>
#include <platformstl/filesystem/filesystem_traits.hpp>
#include <platformstl/filesystem/readdir_sequence.hpp>
#include <platformstl/filesystem/path.hpp>

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Thread;

namespace
{
#ifdef MSVC
    char *mktemp(char *path)
    {
        errno_t err;

        size_t templateOffset = strlen(path) - 6;

        struct _timeb t;
        uint16_t milliMix;
        uint64_t val;
        do {
            _ftime(&t);
            milliMix = (uint16_t)(((float)rand() / (float)RAND_MAX) * 1000.0f);

            val = (t.time * 1000LL + (t.millitm ^ milliMix));
            sprintf(path + templateOffset, "%06X", (uint32_t)((val & 0xFFFFFF) ^ ((val >> 24) & 0xFFFFFF)));
            if (!platformstl::filesystem_traits<char>::file_exists(path)) {
                TAK::Engine::Util::File f(path, "wb");
                if (f)
                    return path;
            }
        } while (true);

        return NULL;
    }

    TAK::Engine::Port::String getTempDirImpl()
    {
        char tmpDir[FILENAME_MAX+1];
        std::size_t nameLen = GetTempPathA(FILENAME_MAX+1, tmpDir);
        if (nameLen)
        {
            if (tmpDir[nameLen-1] == '\\' || tmpDir[nameLen-1] == '/')
                tmpDir[nameLen-1] = '\0';
        }
        return tmpDir;
    }

    TAK::Engine::Port::String getTempDir()
    {
        static TAK::Engine::Port::String tempDir(getTempDirImpl());
        return tempDir;
    }

#endif

    TAKErr getFileCountImpl(std::size_t *result, const char *path, const std::size_t limit) NOTHROWS;

    class ZipExtRegistry {
    public:
        ZipExtRegistry();
        TAKErr registerExt(const char *ext);
        bool hasExt(const char *ext) const NOTHROWS;

    private:
        std::vector<std::string> exts;
    };

    CopyOnWrite<ZipExtRegistry> &globalZipExtRegistry();
}



File::File(const char *path, const char *mode) :
    fd(fopen(path, mode))
{}

File::~File()
{
    if (fd)
        fclose(fd);
}

File::operator FILE * ()
{
    return fd;
}

TAKErr TAK::Engine::Util::IO_copy(const char *dstPath, const char *srcPath) NOTHROWS
{
    using namespace atakmap::util;

    if (!pathExists(srcPath))
        return TE_InvalidArg;

    if (isDirectory(srcPath)) {
        if (!createDir(dstPath)) {
            return TE_IO;
        }

        std::vector<std::string> contents = atakmap::util::getDirContents(srcPath);
        const std::size_t contentsSize = contents.size();
        for (std::size_t i = 0; i < contentsSize; i++) {
            std::ostringstream dstChildPath;
            dstChildPath << dstPath;
#ifdef MSVC
            dstChildPath << "\\";
#else
            dstChildPath << "/";
#endif
            dstChildPath << getFileName(contents[i].c_str());

            IO_copy(dstChildPath.str().c_str(), contents[i].c_str());
        }
    } else {
        File src(srcPath, "rb");
        File dst(dstPath, "wb");

        if (!src) {
            Logger::log(Logger::Error, "IO2: Failed to open input %s", srcPath);
            return TE_IO;
        }
        if (!dst) {
            Logger::log(Logger::Error, "IO2: Failed to open output %s", dstPath);
            return TE_IO;
        }

        uint8_t buf[8192];

        do {
            std::size_t numRead = fread(buf, 1u, 8192, src);
            if (!numRead) {
                if (ferror(src))
                    return TE_IO;
                break;
            }
            fwrite(buf, 1u, numRead, dst);
            if (ferror(dst))
                return TE_IO;
        } while (true);

    }

    return TE_Ok;
}

TAKErr TAK::Engine::Util::IO_copy(DataOutput2 &dst, DataInput2 &src) NOTHROWS
{
    TAKErr code(TE_Ok);

    uint8_t buf[8192];

    do {
        std::size_t numRead;
        code = src.read(buf, &numRead, 8192);
        if (code == TE_EOF)
            break;
        TE_CHECKBREAK_CODE(code);
        if (numRead) {
            code = dst.write(buf, numRead);
            TE_CHECKBREAK_CODE(code);
        }
    } while (true);
    if (code == TE_EOF)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Util::IO_createTempFile(TAK::Engine::Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS
{
    using namespace atakmap::util;

    static Mutex mtx;

    TAKErr code(TE_Ok);
    Port::String tmpDir(dir);
    if (!tmpDir)
    {
#ifdef MSVC
        tmpDir = getTempDir();
#else
        tmpDir = P_tmpdir;
#endif
    }
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mtx);
    TE_CHECKRETURN_CODE(code);

    for (int i = 0; i < 20; i++) {
        StringBuilder tmpStrm;
        tmpStrm << tmpDir << '/'
            << (prefix ? prefix : "") << "XXXXXX";
        Port::String tmpPath(tmpStrm.c_str());

        if (!mktemp(tmpPath.get()))
        {
            Logger::log(Logger::Error, "IO_createTempDir: Call to mkdtemp failed");
            return TE_IO;
        }

        if (suffix) {
            StringBuilder suffixPath;
            suffixPath << tmpPath << suffix;

            deletePath(tmpPath);
            if (platformstl::filesystem_traits<char>::file_exists(suffixPath.c_str()))
                continue;
            {
                // create the file
                File f(suffixPath.c_str(), "wb");
            }
            value = suffixPath.c_str();
        } else {
            value = tmpPath;
        }

        return TE_Ok;
    }
    return TE_Err;
}

TAKErr TAK::Engine::Util::IO_getFileCount(std::size_t *result, const char *path, const std::size_t limit) NOTHROWS
{
    *result = 0;
    return getFileCountImpl(result, path, limit);
}

TAKErr TAK::Engine::Util::IO_getParentFile(Port::String &value, const char *path) NOTHROWS
{
    try {
        std::string retval = atakmap::util::getDirectoryForFile(path);
        value = retval.c_str();
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_getName(Port::String &value, const char *path) NOTHROWS
{
    try {
        std::string retval = atakmap::util::getFileName(path);
        value = retval.c_str();
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}


TAKErr TAK::Engine::Util::IO_getAbsolutePath(Port::String &value, const char *path) NOTHROWS
{
    try {
        std::string retval = atakmap::util::getFileAsAbsolute(path);
        value = retval.c_str();
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_getRelativePath(Port::String &value, const char *basePath, const char *filePath) NOTHROWS
{
    try {
        std::string retval = atakmap::util::computeRelativePath(basePath, filePath);
        value = retval.c_str();
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_listFiles(Port::Collection<Port::String> &value, const char *path, bool(*filter)(const char *file)) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool b;

    code = IO_exists(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    code = IO_isDirectory(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    try {
        std::vector<std::string> contents = atakmap::util::getDirContents(path);
        std::vector<std::string>::iterator it;
        for (it = contents.begin(); it != contents.end(); it++) {
            if (!filter || filter((*it).c_str()))
                value.add((*it).c_str());
        }
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_listFiles(Port::Collection<Port::String> &value, const char *path, ListFilesMethod method, bool(*filter)(const char *file)) NOTHROWS {

    if (method == TELFM_Immediate)
        return IO_listFiles(value, path, filter);

    std::vector<Port::String> files;
    Port::STLVectorAdapter<Port::String> vectorAdapter(files);

    TAKErr code = IO_listFiles(vectorAdapter, path);
    TE_CHECKRETURN_CODE(code);

    std::vector<Port::String>::iterator it = files.begin();
    std::vector<Port::String>::iterator end = files.end();
    while (it != end) {
        bool isFile = false;
        bool isDir = false;
        IO_isFile(&isFile, *it);
        IO_isDirectory(&isDir, *it);
        if (isFile && (!filter || filter(*it))) {
            value.add(*it);
        } else if (isDir && method != TELFM_ImmediateFiles) {

            if ((method == TELFM_Immediate || method == TELFM_Recursive) && (!filter || filter(*it))) {
                value.add(*it);
            }

            if (method == TELFM_Recursive || method == TELFM_RecursiveFiles) {
                code = IO_listFiles(value, *it, method, filter);
                TE_CHECKRETURN_CODE(code);
            }
        }
        ++it;
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Util::IO_getDirectoryInfo(int64_t *fileCount, int64_t *size, const char *path) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool b;

    code = IO_exists(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    code = IO_isDirectory(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    try {
        // XXX - reimplement to handle >4GB / >2^32 file count
        *fileCount = atakmap::util::getFileCount(path);
        *size = atakmap::util::getFileSize(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_length(int64_t *value, const char* path) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool b;

    code = IO_exists(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    code = IO_isDirectory(&b, path);
    TE_CHECKRETURN_CODE(code);
    if (b)
        return TE_InvalidArg;

    try {
        // XXX - reimplement to handle >4GB
        *value = atakmap::util::getFileSize(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_getLastModified(int64_t *value, const char* path) NOTHROWS
{
    try {
        *value = atakmap::util::getLastModified(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_isDirectory(bool *value, const char* path) NOTHROWS
{
    try {
        *value = atakmap::util::isDirectory(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_isFile(bool *value, const char* path) NOTHROWS
{
    try {
        *value = atakmap::util::isFile(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_exists(bool *value, const char *path) NOTHROWS
{
    try {
        *value = atakmap::util::pathExists(path);
        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_delete(const char *path) NOTHROWS
{
    try {
        if (atakmap::util::deletePath(path))
            return TE_Ok;
        else
            return TE_Err;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_mkdirs(const char* dirPath) NOTHROWS
{
    try {
        if (atakmap::util::createDir(dirPath))
            return TE_Ok;
        else
            return TE_Err;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Util::IO_openFile(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *path) NOTHROWS
{
    if (!path)
        return TE_InvalidArg;

    std::unique_ptr<FileInput2> result(new (std::nothrow) FileInput2());

    if (!result)
        return TE_OutOfMemory;

    TAKErr code = result->open(path);
    TE_CHECKRETURN_CODE(code);

    dataPtr = DataInput2Ptr(result.release(), Memory_deleter_const<DataInput2, FileInput2>);

    return code;
}

TAKErr TAK::Engine::Util::IO_visitFiles(TAKErr(*visitor)(void *, const char *), void *opaque, const char *path, ListFilesMethod method) NOTHROWS
{
    std::vector<Port::String> files;
    Port::STLVectorAdapter<Port::String> vectorAdapter(files);

    TAKErr code = IO_listFiles(vectorAdapter, path);
    TE_CHECKRETURN_CODE(code);

    std::vector<Port::String>::iterator it = files.begin();
    std::vector<Port::String>::iterator end = files.end();
    while (it != end) {
        bool isFile = false;
        bool isDir = false;
        IO_isFile(&isFile, *it);
        IO_isDirectory(&isDir, *it);
        if (isFile) {
            code = visitor(opaque, *it);
            TE_CHECKBREAK_CODE(code);
        } else if (isDir && method != TELFM_ImmediateFiles) {
            // if listing directories, visit
            if (method == TELFM_Immediate || method == TELFM_Recursive) {
                code = visitor(opaque, *it);
                TE_CHECKBREAK_CODE(code);
            }

            // recurse into the directory
            if (method == TELFM_Recursive || method == TELFM_RecursiveFiles) {
                code = IO_visitFiles(visitor, opaque, *it, method);
                TE_CHECKRETURN_CODE(code);
            }
        }
        ++it;
    }
    if (code == TE_Done)
        code = TE_Ok;

    return TE_Ok;
}

TAKErr TAK::Engine::Util::IO_readZipEntry(std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> &value, std::size_t *len, const char *zipPath, const char *entry) NOTHROWS
{
    TAKErr code(TE_Ok);
#if 1
    std::ostringstream vsipath;
    vsipath << "/vsizip/{" << zipPath << "}/" << entry;
    std::unique_ptr<VSILFILE, int(*)(VSILFILE *)> handle(VSIFOpenL(vsipath.str().c_str(), "rb"), VSIFCloseL);
    if(!handle.get())
        return TE_InvalidArg;
    DynamicOutput data;
    code = data.open(102400u);
    TE_CHECKRETURN_CODE(code);

    uint8_t buf[8192];
    do {
        std::size_t n = VSIFReadL(buf, 1u, 8192u, handle.get());
        if(!n)
            break;
        code = data.write(buf, n);
        TE_CHECKBREAK_CODE(code);
    } while(true);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *retval;
    code = data.get(&retval, len);
    TE_CHECKRETURN_CODE(code);

    array_ptr<uint8_t> content(new uint8_t[*len]);
    memcpy(content.get(), retval, *len);
    value = std::unique_ptr<const uint8_t, void(*)(const uint8_t *)>(content.release(), Memory_array_deleter_const<uint8_t>);
    return code;
#else
    return TE_Unsupported;
#endif
}

TAKErr TAK::Engine::Util::IO_listZipEntries(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, const char *path, bool(*filter)(const char *file)) NOTHROWS
{
    ZipFilePtr zipPtr(nullptr, nullptr);
    TAKErr code = ZipFile::open(zipPtr, path);
    TE_CHECKRETURN_CODE(code);

    code = zipPtr->gotoFirstEntry();
    TE_CHECKRETURN_CODE(code);

    do {
        TAK::Engine::Port::String entryPath;
        code = zipPtr->getCurrentEntryPath(entryPath);
        TE_CHECKBREAK_CODE(code);
        if (!filter || filter(entryPath.get()))
            value.add(entryPath);
    } while ((code = zipPtr->gotoNextEntry()) == TE_Ok);

    return code == TE_Done ? TE_Ok : code;
}

TAKErr TAK::Engine::Util::IO_openZipEntry(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *zipPath, const char *entry) NOTHROWS
{
    return ZipFileDataInput2::open(dataPtr, zipPath, entry);
}

TAKErr TAK::Engine::Util::IO_registerZipExt(const char *ext) NOTHROWS {
    return globalZipExtRegistry().invokeWrite(&ZipExtRegistry::registerExt, ext);
}

static bool isArchiveExt(const char *ext) {
    return globalZipExtRegistry().read()->hasExt(ext);
}

static std::pair<std::string, std::string> splitVPath(const char *vpath) NOTHROWS {

    if (!vpath)
        return std::make_pair("", "");

    std::string pathStr = vpath;

    TAK::Engine::Port::String ext;
    const char *extPos = nullptr;
    TAKErr code = IO_getExt(ext, &extPos, vpath);
    while(code != TE_Done) {
        if (ext.get() && isArchiveExt(ext)) {
            size_t pathSep = pathStr.find_first_of("\\/", static_cast<size_t>(extPos - vpath));
            if (pathSep != std::string::npos) {
                std::string zipPath = pathStr.substr(0, pathSep);
                std::string entryPath = pathStr.substr(pathSep + 1);
                return std::make_pair(zipPath, entryPath);
            } else {
                return std::make_pair(pathStr, ".");
            }
        }
        code = IO_getExt(ext, &extPos, extPos + 1);
    }
    return std::make_pair(pathStr, "");
}

TAKErr TAK::Engine::Util::IO_openFileV(std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> &dataPtr, const char *vpath)
{
    std::pair<std::string, std::string> split = splitVPath(vpath);
    if (split.second.length() > 0) {
        return IO_openZipEntry(dataPtr, split.first.c_str(), split.second.c_str());
    }
    return IO_openFile(dataPtr, vpath);
}

TAKErr TAK::Engine::Util::IO_isFileV(bool *value, const char *vpath) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    std::pair<std::string, std::string> split = splitVPath(vpath);
    if (split.second.length() > 0) {
        ZipFilePtr zipPtr(nullptr, nullptr);
        TAKErr code = ZipFile::open(zipPtr, split.first.c_str());
        TE_CHECKRETURN_CODE(code);

        if (split.second == ".") {
            *value = false;
            return TE_Ok;
        }

        code = zipPtr->gotoEntry(split.second.c_str());
        *value = code == TE_Ok;

        return code == TE_Done ? TE_Ok : code;
    }
    return IO_isFile(value, vpath);
}

TAKErr TAK::Engine::Util::IO_isDirectoryV(bool *value, const char *vpath) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    std::pair<std::string, std::string> split = splitVPath(vpath);
    if (split.second.length() > 0) {
        ZipFilePtr zipPtr(nullptr, nullptr);
        TAKErr code = ZipFile::open(zipPtr, split.first.c_str());
        TE_CHECKRETURN_CODE(code);

        if (split.second != ".") {

            if (split.second.back() != '/')
                split.second.push_back('/');

            code = zipPtr->gotoEntry(split.second.c_str());
        }

        *value = code == TE_Ok;
        return code;
    }
    return IO_isDirectory(value, vpath);
}

TAKErr TAK::Engine::Util::IO_existsV(bool *value, const char *vpath) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    std::pair<std::string, std::string> split = splitVPath(vpath);
    if (split.second.length() > 0) {
        ZipFilePtr zipPtr(nullptr, nullptr);
        TAKErr code = ZipFile::open(zipPtr, split.first.c_str());
        TE_CHECKRETURN_CODE(code);

        if (split.second != ".")
            code = zipPtr->gotoEntry(split.second.c_str());

        if (value)
            *value = (code == TE_Ok);

        return TE_Ok;
    }
    return IO_exists(value, vpath);
}

TAKErr TAK::Engine::Util::IO_getFileSizeV(int64_t *size, const char *vpath) NOTHROWS
{
    if (!size)
        return TE_InvalidArg;

    std::pair<std::string, std::string> split = splitVPath(vpath);
    if (split.second.length() > 0 && split.second != ".") {
        ZipFilePtr zipPtr(nullptr, nullptr);
        TAKErr code = ZipFile::open(zipPtr, split.first.c_str());
        TE_CHECKRETURN_CODE(code);

        code = zipPtr->gotoEntry(split.second.c_str());
        TE_CHECKRETURN_CODE(code);

        int64_t result = 0;
        code = zipPtr->getCurrentEntryUncompressedSize(result);
        TE_CHECKRETURN_CODE(code);

        *size = result;
        return TE_Ok;
    }

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP() {
        *size = atakmap::util::getFileSize(vpath);
    } TE_END_TRAP(code);

    return code;
}

TAKErr TAK::Engine::Util::IO_listFilesV(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, const char *vpath, ListFilesMethod method, bool(*filter)(const char *file), size_t limit) NOTHROWS
{
    struct ListFiles
    {
        TAK::Engine::Port::Collection<TAK::Engine::Port::String> &result;
        std::size_t limit;
        bool(*filter)(const char *file);

        ListFiles(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &result_, bool(*filter_)(const char *file), std::size_t limit_) :
            result(result_),
            filter(filter_),
            limit(limit_)
        {}

        static TAKErr process(void *opaque, const char *path)
        {
            ListFiles *ctx = static_cast<ListFiles *>(opaque);
            if (!ctx->filter || ctx->filter(path)) {
                ctx->result.add(path);
                if (ctx->result.size() == ctx->limit)
                    return TE_Done;
            }
            return TE_Ok;
        }
    };

    ListFiles ctx(value, filter, limit);
    return IO_visitFilesV(ListFiles::process, &ctx, vpath, method);
}

TAKErr TAK::Engine::Util::IO_visitFilesV(TAKErr(*visitor)(void *, const char *), void *opaque, const char *vpath, ListFilesMethod method) NOTHROWS
{
    if (!visitor)
        return TE_InvalidArg;

    std::pair<std::string, std::string> split = splitVPath(vpath);
    bool recursively = (method == TELFM_Recursive || method == TELFM_RecursiveFiles);
    bool onlyFiles = (method == TELFM_ImmediateFiles || method == TELFM_RecursiveFiles);

    if (split.second.length() > 0) {

        std::string prefix = split.second;
        if (prefix == ".")
            prefix = "";
        else if (prefix.back() != '/')
            prefix.push_back('/');

        ZipFilePtr zipPtr(nullptr, nullptr);
        TAKErr code = ZipFile::open(zipPtr, split.first.c_str());
        TE_CHECKRETURN_CODE(code);

#define __TE_ZIP_SUPPORTS_DIRECTORY_LISTING 0

        if (prefix.length() > 0) {
#if __TE_ZIP_SUPPORTS_DIRECTORY_LISTING
            // XXX - observing this failing -- no directory entries are returned
            code = zipPtr->gotoEntry(prefix.c_str());
            TE_CHECKRETURN_CODE(code);

            code = zipPtr->gotoNextEntry();
            TE_CHECKRETURN_CODE(code);
#else
            // scan to the first entry starting with the prefix
            do {
                TAK::Engine::Port::String entry;
                code = zipPtr->getCurrentEntryPath(entry);
                TE_CHECKBREAK_CODE(code);

                std::string entryStr = entry.get();
                if (entryStr.compare(0, prefix.size(), prefix) == 0)
                    break;
            } while (true);
#endif
        }

        do {
            TAK::Engine::Port::String entry;
            code = zipPtr->getCurrentEntryPath(entry);
            TE_CHECKRETURN_CODE(code);

            std::string entryStr = entry.get();

            // beyond directory?
            if (entryStr.compare(0, prefix.size(), prefix) != 0) {
                break;
            }

            // skip self
            if (entryStr == prefix) {
                continue;
            }

            bool isDir = false;
            if (entryStr.back() == '/') {
                entryStr.pop_back();
                isDir = true;
            }

            std::string withoutPrefix = entryStr.substr(prefix.length());

            // skip unwanted
            if ((!recursively && withoutPrefix.find_first_of('/') != std::string::npos) ||
                (isDir && onlyFiles))
                continue;

            std::ostringstream fullVPath;
            fullVPath << split.first << "/" << entryStr;
            std::string correctedFullVPath = fullVPath.str();
#if _WIN32
            for (size_t i = 0; i < correctedFullVPath.size(); ++i) {
                if (correctedFullVPath[i] == '/')
                    correctedFullVPath[i] = '\\';
            }
#endif
            code = visitor(opaque, correctedFullVPath.c_str());
            TE_CHECKBREAK_CODE(code);

        } while ((code = zipPtr->gotoNextEntry()) == TE_Ok);

        return code == TE_Done ? TE_Ok : code;
    }

    return IO_visitFiles(visitor, opaque, vpath, method);
}

#if _MSC_VER
TAKErr TAK::Engine::Util::File_getStoragePath(TAK::Engine::Port::String &path) NOTHROWS {
    return TE_Ok;
}

TAKErr TAK::Engine::Util::File_getRuntimePath(TAK::Engine::Port::String &path) NOTHROWS {
    return TE_Ok;
}
#endif

TAKErr TAK::Engine::Util::IO_getExt(Port::String &ext, const char **extPos, const char *path) NOTHROWS {

    std::string pathStr = path;
    size_t extDot = std::string::npos;
    size_t extEnd = pathStr.length();
    size_t delim = 0;

    // find the next path sep
    do {
        delim = pathStr.find_first_of(".\\/\t:", delim);

        if (delim != std::string::npos) {
            switch (pathStr[delim]) {
            case '.':
                extDot = delim;
                ++delim;
                break;
            default:
                extEnd = delim;
                if (extDot != std::string::npos)
                    delim = std::string::npos;
                else {
                    extDot = std::string::npos;
                    ++delim;
                }
                break;
            }
        }

    } while (delim < pathStr.length());

    if (extDot != std::string::npos) {
        ext = pathStr.substr(extDot, extEnd - extDot).c_str();
        if (extPos)
            *extPos = path + extDot;
        return TE_Ok;
    }

    if (extPos)
        *extPos = nullptr;

    return TE_Done;
}

TAKErr TAK::Engine::Util::IO_correctPathSeps(Port::String &result, const char *path) NOTHROWS {

    TAKErr code(TE_Ok);

    TE_BEGIN_TRAP() {

        std::string pathStr = path;
        char platSep = TAK::Engine::Port::Platform_pathSep();

        for (size_t i = 0; i < pathStr.length(); ++i) {
            if (pathStr[i] == '\\' || pathStr[i] == '/')
                pathStr[i] = platSep;
        }

        result = pathStr.c_str();

    } TE_END_TRAP(code);

    return code;
}

namespace
{
    TAKErr getFileCountImpl(std::size_t *result, const char *path, const std::size_t limit) NOTHROWS
    {
        typedef platformstl::filesystem_traits<char>        FS_Traits;
        typedef platformstl::readdir_sequence               DirSequence;

        FS_Traits::stat_data_type statData;

        TAKErr code;

        code = TE_Ok;
        if (path && FS_Traits::stat(path, &statData))
        {
            if (FS_Traits::is_file(&statData))
            {
                (*result)++;
            }
            else if (FS_Traits::is_directory(&statData))
            {
                DirSequence dirSeq(path,
                    DirSequence::files
                    | DirSequence::directories
                    | DirSequence::fullPath
                    | DirSequence::absolutePath);

                for (DirSequence::const_iterator dIter(dirSeq.begin());
                    dIter != dirSeq.end();
                    ++dIter)
                {
                    code = getFileCountImpl(result, *dIter, limit);
                    TE_CHECKBREAK_CODE(code);
                    if (*result >= limit)
                        break;
                }
                TE_CHECKRETURN_CODE(code);
            }
        } else {
            return TE_InvalidArg;
        }

        return code;
    }

    ZipExtRegistry::ZipExtRegistry() {
        exts.push_back(".zip");
        exts.push_back(".kmz");
    }

    TAKErr ZipExtRegistry::registerExt(const char *ext) {

        if (!ext || *ext != '.')
            return TE_InvalidArg;

        TAKErr code(TE_Ok);

        if (!hasExt(ext)) {
            TE_BEGIN_TRAP() {
                exts.push_back(ext);
            } TE_END_TRAP(code);
        }

        return code;
    }

    bool ZipExtRegistry::hasExt(const char *ext) const NOTHROWS {
        auto it = exts.begin();
        auto end = exts.end();
        while (it != end) {
            int cmp = -1;
            TAK::Engine::Port::String_compareIgnoreCase(&cmp, ext, it->c_str());
            if (cmp == 0)
                return true;
            ++it;
        }
        return false;
    }

    CopyOnWrite<ZipExtRegistry> &globalZipExtRegistry() {
        static CopyOnWrite<ZipExtRegistry> inst;
        return inst;
    }
}
