#include "util/ZipFile.h"

#include <string>
#include <vector>

#undef ZLIB_DLL             // causes issue with zlibextern.h
#undef ZLIB_WINAPI          // causes issue with zlibextern.h
#include "minizip/unzip.h"  // for minizip_static.lib
#include "minizip/zip.h"    // for minizip_static.lib

#include "util/Memory.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine;

//
// ZipFile::Impl
//

struct ZipFile::Impl {
    
    Impl() NOTHROWS
        : handle(nullptr) 
    { }

    ~Impl() NOTHROWS {
        if (handle)
            libkml_unzClose(handle);
    }

    TAKErr open(const char *path) NOTHROWS {
        handle = libkml_unzOpen(path);
        if (!handle)
            return TE_InvalidArg;
        return TE_Ok;
    }

    TAKErr gotoFirstEntry() NOTHROWS {
        if (libkml_unzGoToFirstFile(handle) != UNZ_OK)
            return TE_Err;
        return TE_Ok;
    }

    TAKErr gotoNextEntry() NOTHROWS {
        int result = libkml_unzGoToNextFile(handle);
        if (result == UNZ_END_OF_LIST_OF_FILE)
            return TE_Done;
        else if (result != UNZ_OK)
            return TE_IO;
        return TE_Ok;
    }

    TAKErr gotoEntry(const char *path) NOTHROWS {

        TAKErr code(TE_Ok);
        std::string corrected;

        TE_BEGIN_TRAP() {
            corrected = path;
        } TE_END_TRAP(code);

        TE_CHECKRETURN_CODE(code);

        for (size_t i = 0; i < corrected.length(); ++i) {
            if (corrected[i] == '\\')
                corrected[i] = '/';
        }

        int result = libkml_unzLocateFile(handle, corrected.c_str(), 0);
        if (result == UNZ_END_OF_LIST_OF_FILE)
            return TE_Done;

        if (result != UNZ_OK)
            return TE_IO;

        return TE_Ok;
    }

	TAKErr getGlobInfo(unz_global_info &globInfo) const NOTHROWS {
		int result = libkml_unzGetGlobalInfo(handle,
			&globInfo);
		if (result != UNZ_OK)
			return TE_IO;

		return TE_Ok;
	}

    TAKErr getCurrentInfo(unz_file_info &fileInfo) const NOTHROWS {
        int result = libkml_unzGetCurrentFileInfo(handle,
            &fileInfo,
            nullptr,
            100,
            nullptr,
            0,
            nullptr,
            0);

        if (result != UNZ_OK)
            return TE_IO;

        return TE_Ok;
    }

    TAKErr getCurrentEntryPath(Port::String &out) const NOTHROWS {
        unz_file_info fileInfo;
        int result = libkml_unzGetCurrentFileInfo(handle,
            &fileInfo,
            nullptr,
            0,
            nullptr,
            0,
            nullptr,
            0);

        if (result != UNZ_OK)
            return TE_IO;

        std::vector<char> buffer;
        TAKErr code = TE_Ok;

        TE_BEGIN_TRAP() {
            buffer.insert(buffer.end(), fileInfo.size_filename + 1, '\0');
        } TE_END_TRAP(code);

        TE_CHECKRETURN_CODE(code);

        result = libkml_unzGetCurrentFileInfo(handle,
            nullptr,
            buffer.data(),
            static_cast<uLong>(buffer.size() - 1),
            nullptr,
            0,
            nullptr,
            0);

        if (result != UNZ_OK)
            return TE_IO;

        out = buffer.data();
        return TE_Ok;
    }

    TAKErr openCurrentEntry() NOTHROWS {
        int result = libkml_unzOpenCurrentFile(handle);
        if (result != UNZ_OK)
            return TE_IO;
        return TE_Ok;
    }

    TAKErr read(void *dst, size_t &resultRead, size_t byteCount) NOTHROWS {

        size_t left = byteCount;
        TAKErr code = TE_Ok;
        auto *pos = static_cast<uint8_t *>(dst);

        while (left > 0) {

            auto step = (unsigned)left;
            int result = libkml_unzReadCurrentFile(handle, pos, step);
            
            // EOF
            if (result == 0) {
                if (left == byteCount)
                    code = TE_EOF;
                break;
            }

            // Error
            if (result < 0) {
                code = TE_IO;
                break;
            }

            left -= result;
            pos += result;
        }

        resultRead = byteCount - left;
        return code;
    }

    TAKErr closeCurrentEntry() NOTHROWS {
        int result = libkml_unzClose(handle);
        if (result != UNZ_OK)
            return TE_IO;
        return TE_Ok;
    }

    TAKErr getGlobalComment(Port::String &out) NOTHROWS {
        out = "";
        unz_global_info globalInfo;
        int result = libkml_unzGetGlobalInfo(handle, &globalInfo);
        if (result != UNZ_OK)
            return TE_IO;

        constexpr uLong maxUnzGlobalCommentSize = (1L << 16) - 1;
        if (globalInfo.size_comment > 0 && globalInfo.size_comment <= maxUnzGlobalCommentSize)
        {
            std::string buf;
            const uLong buf_size = globalInfo.size_comment + 1;
            buf.reserve(buf_size);

            result = libkml_unzGetGlobalComment(handle, &buf[0], buf_size);
            if (result != globalInfo.size_comment)
                return TE_IO;

            out = buf.data();
        }

        return TE_Ok;
    }
    
private:
    unzFile handle;
};

//
// ZipFile
//

TAKErr ZipFile::open(ZipFilePtr &zipPtr, const char *path) NOTHROWS {

    std::unique_ptr<ZipFile::Impl> impl(new(std::nothrow) ZipFile::Impl());
    if (!impl)
        return TE_OutOfMemory;

    TAKErr code = impl->open(path);
    if (code != TE_Ok)
        return code;

    ZipFilePtr result(new (std::nothrow) ZipFile(impl.release()), Memory_deleter<ZipFile>);
    
    if (!result)
        return TE_OutOfMemory;

    zipPtr = std::move(result);

    return TE_Ok;
}

ZipFile::ZipFile(Impl *impl) NOTHROWS
    : impl(impl)
{ }

ZipFile::~ZipFile() NOTHROWS {
    delete impl;
}

TAKErr ZipFile::getGlobalComment(Port::String &out) NOTHROWS {
    return impl->getGlobalComment(out);
}

TAKErr ZipFile::getNumEntries(size_t &numEntries) NOTHROWS {
    unz_global_info globInfo;
    TAKErr code = impl->getGlobInfo(globInfo);
    if (code == TE_Ok) {
        numEntries = globInfo.number_entry;
    }
    return code;
}

TAKErr ZipFile::gotoFirstEntry() NOTHROWS {
    return impl->gotoFirstEntry();
}

TAKErr ZipFile::gotoNextEntry() NOTHROWS {
    return impl->gotoNextEntry();
}

TAKErr ZipFile::gotoEntry(const char *path) NOTHROWS {
    return impl->gotoEntry(path);
}

TAKErr ZipFile::getCurrentEntryPath(Port::String &out) const NOTHROWS {
    return impl->getCurrentEntryPath(out);
}

TAKErr ZipFile::getCurrentEntryUncompressedSize(int64_t &size) const NOTHROWS {
    unz_file_info info;
    TAKErr code = impl->getCurrentInfo(info);
    TE_CHECKRETURN_CODE(code);
    size = info.uncompressed_size;
    return code;
}

TAKErr ZipFile::openCurrentEntry() NOTHROWS {
    return impl->openCurrentEntry();
}

TAKErr ZipFile::read(void *dst, size_t &resultRead, size_t byteCount) NOTHROWS {
    return impl->read(dst, resultRead, byteCount);
}

TAKErr ZipFile::closeCurrentEntry() NOTHROWS {
    return impl->closeCurrentEntry();
}

TAKErr ZipFile::setGlobalComment(const char *path, const char *comment) NOTHROWS {
    zipFile zf = zipOpen(path, APPEND_STATUS_ADDINZIP);
    if (zf != nullptr) {
        int result = zipClose(zf, comment);
        if (result != UNZ_OK)
            return TE_IO;
        return TE_Ok;
    }
    return TE_Ok;
}

//
// ZipFileDataInput2
//

ZipFileDataInput2::ZipFileDataInput2(ZipFilePtr &&zipPtr, const int64_t len) NOTHROWS
    : zip_ptr_(std::move(zipPtr)), len_(len)
{ }

ZipFileDataInput2::~ZipFileDataInput2() NOTHROWS 
{ }

TAKErr ZipFileDataInput2::open(DataInput2Ptr &outPtr, const char *zipFile, const char *zipEntry) NOTHROWS {

    ZipFilePtr zipPtr(nullptr, nullptr);
    int64_t len(-1LL);
    TAKErr code = ZipFile::open(zipPtr, zipFile);
    TE_CHECKRETURN_CODE(code);

    code = zipPtr->gotoEntry(zipEntry);
    TE_CHECKRETURN_CODE(code);

    code = zipPtr->openCurrentEntry();
    TE_CHECKRETURN_CODE(code);

    code = zipPtr->getCurrentEntryUncompressedSize(len);
    TE_CHECKRETURN_CODE(code);

    outPtr = DataInput2Ptr(new (std::nothrow) ZipFileDataInput2(std::move(zipPtr), len), Memory_deleter_const<DataInput2, ZipFileDataInput2>);

    return code;
}

TAKErr ZipFileDataInput2::close() NOTHROWS {
    zip_ptr_ = nullptr;
    return TE_Ok;
}

TAKErr ZipFileDataInput2::read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS {

    if (!zip_ptr_)
        return TE_IllegalState;

    size_t read = 0;
    TAKErr code = zip_ptr_->read(buf, read, len);
    if (numRead)
        *numRead = read;

    return code;
}

TAKErr ZipFileDataInput2::readByte(uint8_t *value) NOTHROWS {
    return read(value, nullptr, 1);
}

TAKErr ZipFileDataInput2::skip(const std::size_t n) NOTHROWS {
    return TE_Ok;
}

int64_t ZipFileDataInput2::length() const NOTHROWS
{
    return len_;
}
