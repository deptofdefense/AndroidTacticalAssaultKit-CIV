
#ifndef TAK_ENGINE_UTIL_ZIPFILE_H_INCLUDED
#define TAK_ENGINE_UTIL_ZIPFILE_H_INCLUDED

#include <memory>
#include "util/Error.h"
#include "port/Collection.h"
#include "port/String.h"
#include "util/DataInput2.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            class ZipFile;

            typedef std::unique_ptr<ZipFile, void(*)(ZipFile *)> ZipFilePtr;
            typedef std::unique_ptr<const ZipFile, void(*)(const ZipFile *)> ZipFilePtr_const;

            /**
             *
             */
            class ENGINE_API ZipFile {
            public:
                ZipFile(const ZipFile&) = delete;
                void operator=(const ZipFile &) = delete;

                ~ZipFile() NOTHROWS;

                TAKErr listEntries(Port::Collection<Port::String> &value, const char *path, bool(*filter)(const char *file) = nullptr) const NOTHROWS;

                TAKErr openEntry(DataInput2Ptr &dataInputPtr, const char *entryPath) const NOTHROWS;

                static TAKErr open(ZipFilePtr &zipPtr, const char *path) NOTHROWS;

                TAKErr getGlobalComment(Port::String &out) NOTHROWS;
                static TAKErr setGlobalComment(const char *path, const char *comment) NOTHROWS;

                TAKErr getNumEntries(size_t &numEntries) NOTHROWS;
                TAKErr gotoFirstEntry() NOTHROWS;
                TAKErr gotoNextEntry() NOTHROWS;
                TAKErr gotoEntry(const char *path) NOTHROWS;
                TAKErr getCurrentEntryPath(Port::String &out) const NOTHROWS;
                TAKErr getCurrentEntryUncompressedSize(int64_t &outSize) const NOTHROWS;
                TAKErr openCurrentEntry() NOTHROWS;
                TAKErr read(void *dst, size_t &resultRead, size_t byteCount) NOTHROWS;
                TAKErr closeCurrentEntry() NOTHROWS;

            private:
                struct Impl;
                explicit ZipFile(Impl *impl) NOTHROWS;
                Impl *impl;
            };

            class ENGINE_API ZipFileDataInput2 : public DataInput2 {
            public:
                static TAKErr open(DataInput2Ptr &outPtr, const char *zipFile, const char *zipEntry) NOTHROWS;

                ZipFileDataInput2(const ZipFileDataInput2 &) = delete;
                void operator=(const ZipFileDataInput2 &) = delete;

                virtual ~ZipFileDataInput2() NOTHROWS;

                virtual TAKErr close() NOTHROWS;
                virtual TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS;
                virtual TAKErr readByte(uint8_t *value) NOTHROWS;
                virtual TAKErr skip(const std::size_t n) NOTHROWS;
                virtual int64_t length() const NOTHROWS;
            private:
                ZipFileDataInput2(ZipFilePtr &&zipPtr, const int64_t len) NOTHROWS;
                ZipFilePtr zip_ptr_;
                int64_t len_;
            };
        }
    }
}

#endif