#include "IO.h"

#include <assert.h>
#include <cerrno>
#include <cstdio>
#include <unistd.h>

#include <platformstl/filesystem/directory_functions.hpp>
#include <platformstl/filesystem/filesystem_traits.hpp>
#include <platformstl/filesystem/readdir_sequence.hpp>
#include <platformstl/filesystem/path.hpp>

#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/IO2.h"
#include "util/Memory.h"

namespace {
    union Convert {
        uint8_t b[sizeof(int64_t)];
        int32_t i;
        int16_t s;
        int64_t l;
        float f;
        double d;
    };

    void swapBytes(uint8_t *b, size_t len)
    {
        assert((len % 2) == 0);

        size_t half = len / 2;
        for (size_t i = 0; i < half; ++i) {
            uint8_t left = b[i];
            b[i] = b[len - 1 - i];
            b[len - 1 - i] = left;
        }
    }



}

using namespace atakmap::util;

DataInput::DataInput() : swappingEndian(false)
{

}

DataInput::~DataInput()
{

}

size_t DataInput::readFully(uint8_t *buf, size_t len)
    throw (IO_Error)
{
    size_t origLen = len;
    size_t step;
    do {
        step = read(buf, len);
        len -= step;
        buf += step;
    } while (step != EndOfStream && len > 0);

    return origLen - len;
}

size_t DataInput::skip(size_t n)
    throw (IO_Error)
{
    auto *discardBuf = new uint8_t[n];
    size_t result = 0;

    try {
        result = readFully(discardBuf, n);
        delete[] discardBuf;
    } catch (...) {
        delete[] discardBuf;
        throw;
    }

    return result;
}

int DataInput::readAsciiInt(size_t len)
    throw (IO_Error, std::out_of_range)
{
    std::string s = readString(len);
    return parseASCIIInt(s.c_str());
}

double DataInput::readAsciiDouble(size_t len)
    throw (IO_Error, std::out_of_range)
{
    std::string s = readString(len);
    return parseASCIIDouble(s.c_str());
}

float DataInput::readFloat()
    throw (IO_Error)
{
    assert(sizeof(float) == 4);
    Convert c;
    readFully(c.b, 4);
    if (swappingEndian)
        swapBytes(c.b, 4);
    return c.f;
}

int32_t DataInput::readInt()
    throw (IO_Error)
{
    Convert c;
    c.i = 0;
    readFully(c.b, 4);
    if (swappingEndian)
        swapBytes(c.b, sizeof(int32_t));
    return c.i;
}

int16_t DataInput::readShort()
    throw (IO_Error)
{
    Convert c;
    c.s = 0;
    readFully(c.b, 2);
    if (swappingEndian)
        swapBytes(c.b, sizeof(int16_t));
    return c.s;
}

int64_t DataInput::readLong()
    throw (IO_Error)
{
    Convert c;
    c.l = 0LL;
    readFully(c.b, 8);
    if (swappingEndian)
        swapBytes(c.b, sizeof(int64_t));
    return c.l;
}

double DataInput::readDouble()
    throw (IO_Error)
{
    assert(sizeof(double) == 8);
    Convert c;
    readFully(c.b, 8);
    if (swappingEndian)
        swapBytes(c.b, 8);
    return c.d;

}

std::string DataInput::readString(size_t len)
    throw (IO_Error)
{
    char *b = new char[len];
    try {
        readFully((uint8_t *)b, len);
    } catch (...) {
        delete[] b;
        throw;
    }
    std::string ret(b, len);
    delete[] b;
    return ret;
}

void DataInput::setSourceEndian(Endian e)
{
    swappingEndian = (e != PlatformEndian);
}


FileInput::FileInput() : f(nullptr)
{

}

FileInput::~FileInput()
{
    close();
}

void FileInput::open(const char *filename)
    throw (IO_Error)
{
    if (f)
        throw IO_Error("This file already open");
    f = fopen(filename, "rb");
    if (!f)
        throw IO_Error("Failed to open file");
}

void FileInput::close()
    throw (IO_Error)
{
    if (!f)
        // Already closed
        return;
    if (fclose(f) != 0)
        throw IO_Error("Error closing file");
    else
        f = nullptr;
}

uint8_t FileInput::readByte()
    throw (IO_Error)
{
    uint8_t b;
    readFully(&b, 1);
    return b;
}

size_t FileInput::read(uint8_t *buf, size_t len)
    throw (IO_Error)
{
    if (len == 0)
        return 0;

    size_t r = fread(buf, 1, len, f);
    if (r == 0) {
        if (feof(f))
            throw IO_Error("End of file reached");
        throw IO_Error("IO Error reading from file");
    }
    return r;
}

size_t FileInput::skip(size_t n)
    throw (IO_Error)
{
    int r;
#ifdef WIN32
    r = _fseeki64(f, n, SEEK_CUR);
#else
    r = fseeko(f, n, SEEK_CUR);
#endif
    if (r != 0)
        throw IO_Error("IO Error skipping forward in file");

    return n;
}

void FileInput::seek(int64_t offset)
    throw (IO_Error)
{
    int r;
#ifdef WIN32
    r = _fseeki64(f, offset, SEEK_SET);
#else
    r = fseeko(f, offset, SEEK_SET);
#endif
    if (r != 0)
        throw IO_Error("IO Error seeking in file");
}

int64_t FileInput::tell()
    throw (IO_Error)
{
    int64_t r;
#ifdef WIN32
    r = _ftelli64(f);
#else
    r = ftello(f);
#endif
    if (r == -1)
        throw IO_Error("IO Error getting file position");

    return r;
}




MemoryInput::MemoryInput() : bytes(nullptr), curOffset(0), totalLen(0)
{

}

MemoryInput::~MemoryInput()
{

}

void MemoryInput::open(const uint8_t *buffer, size_t len)
{
    bytes = buffer;
    totalLen = len;
    curOffset = 0;
}

void MemoryInput::close()
    throw (IO_Error)
{
    bytes = nullptr;
}

uint8_t MemoryInput::readByte()
    throw (IO_Error)
{
    if (curOffset >= totalLen)
        throw IO_Error("MemoryInput: EOF");

    uint8_t b = bytes[curOffset];
    curOffset++;
    return b;
}

size_t MemoryInput::read(uint8_t *buf, size_t len)
    throw (IO_Error)
{
    if (len == 0)
        return 0;

    size_t rem = totalLen - curOffset;
    if (len > rem)
        len = rem;

    if (len == 0)
        return EndOfStream;

    memcpy(buf, bytes + curOffset, len);
    curOffset += len;
    return len;
}

size_t MemoryInput::skip(size_t n)
    throw (IO_Error)
{
    size_t rem = totalLen - curOffset;
    if (n > rem)
        throw IO_Error("MemoryInput: Skipping indicated number of bytes would go past EOF");

    curOffset += n;

    return n;
}

ByteBufferInput::ByteBufferInput(atakmap::util::MemBufferT<uint8_t> *buffer)
: buffer(buffer) { }

ByteBufferInput::~ByteBufferInput() { }

void ByteBufferInput::close() throw (IO_Error) {
    this->buffer = nullptr;
}

size_t ByteBufferInput::read(uint8_t *buf, size_t len) throw (IO_Error) {
    return this->buffer->get(buf, len);
}

uint8_t ByteBufferInput::readByte() throw (IO_Error) {
    return this->buffer->get();
}

size_t ByteBufferInput::skip(size_t n) throw (IO_Error) {
    size_t pos = std::min(this->buffer->position() + n, this->buffer->limit());
    this->buffer->position(pos);
    return pos;
}

RewindDataInput::RewindDataInput(atakmap::util::DataInput &input)
: innerInput(input),
readOffset(0) { }

RewindDataInput::~RewindDataInput() { }

void RewindDataInput::close() throw (IO_Error) {
    innerInput.close();
    rewindCache.resize(0);
}

size_t RewindDataInput::read(uint8_t *buf, size_t len) throw(IO_Error) {

    size_t totalRead = 0;

    if (readOffset < rewindCache.size()) {
        totalRead = std::min(rewindCache.size() - readOffset, len);
        memcpy(buf, &rewindCache[readOffset], totalRead);
        readOffset += totalRead;
    }

    if (totalRead < len) {
        size_t readLeft = len - totalRead;
        readLeft = innerInput.read(buf + totalRead, readLeft);

        if (readLeft != DataInput::EndOfStream) {
            rewindCache.insert(rewindCache.end(), buf + totalRead, buf + totalRead + readLeft);
            totalRead += readLeft;
            readOffset += totalRead;
        }
    }

    return totalRead == 0 ? EndOfStream : totalRead;
}

uint8_t RewindDataInput::readByte() throw (IO_Error) {
    uint8_t byte = innerInput.readByte();
    rewindCache.push_back(byte);
    ++readOffset;
    return byte;
}

void RewindDataInput::rewind() {
    readOffset = 0;
}

#define MEM_FN( fn )    "atakmap::util::IO::" fn ": "

char*
atakmap::util::createTempDir(const char* parentPath,
    const char* prefix,
    const char* suffix)
{
    using namespace TAK::Engine::Util;
    TAK::Engine::Port::String tmpdir;
    TAKErr code = IO_createTempDirectory(tmpdir, prefix, suffix, parentPath);
    if(code == TE_TimedOut || !tmpdir)
        return nullptr;
    else if(code != TE_Ok)
        throw IO_Error(MEM_FN("createTempDir") "Call to mkdtemp failed");

    const std::size_t len = strlen(tmpdir);
    array_ptr<char> dupe(new char[len+1u]);
    memcpy(dupe.get(), tmpdir.get(), sizeof(char)*len);
    dupe[len] = '\0';
    return dupe.release();
}

std::string atakmap::util::trimASCII(const std::string &src)
{
    size_t n = src.size();
    if (n == 0)
        return src;

    size_t i;
    for (i = 0; i < n; ++i) {
        if (src.at(i) > 0x20)
            break;
    }
    size_t lastidx;
    for (lastidx = n - 1; lastidx > i; --lastidx) {
        if (src.at(lastidx) > 0x20)
            break;
    }
    return src.substr(i, lastidx - i + 1);
}

int atakmap::util::parseASCIIInt(const char *src) throw (std::out_of_range)
{
    char *end = nullptr;
    const char *start = src;
    long v = strtol(start, &end, 10);
    if (((v == LONG_MAX || v == LONG_MIN) && errno == ERANGE) || v > INT_MAX || v < INT_MIN)
        throw std::out_of_range("ASCII int too large/small");
    if ((v == 0 && end == start) || *end != '\0')
        // No valid digits or did not use full string
        throw std::out_of_range("Invalid ASCII integer string");
    return (int)v;
}

double atakmap::util::parseASCIIDouble(const char *src) throw (std::out_of_range)
{
    std::string trimmed = trimASCII(src);
    char *end = nullptr;
    const char *start = trimmed.c_str();
    errno = 0;
    double v = strtod(start, &end);
    if ((v == HUGE_VAL || v == -HUGE_VAL || v == 0) && errno == ERANGE)
        throw std::out_of_range("ASCII double too large/small");
    if ((v == 0 && end == start) || *end != '\0')
        // No valid digits or did not use full string
        throw std::out_of_range("Invalid ASCII double string");
    return v;
}

std::string
atakmap::util::toLowerCase(const std::string &s)
{
    std::string ret;
    size_t n = s.length();
    for (size_t i = 0; i < n; ++i)
        ret.push_back(tolower(s[i]));
    return ret;
}


std::string
atakmap::util::toUpperCase(const std::string &s)
{
    std::string ret;
    size_t n = s.length();
    for (size_t i = 0; i < n; ++i)
        ret.push_back(toupper(s[i]));
    return ret;
}


std::vector<std::string>
atakmap::util::splitString(const std::string &str, const std::string &delimiters, bool trimEmpty)
{
    std::vector<std::string> tokens;
    std::string::size_type pos, lastPos = 0;

    while (true)
    {
        pos = str.find_first_of(delimiters, lastPos);
        if (pos == std::string::npos)
        {
            pos = str.length();

            if (pos != lastPos || !trimEmpty)
                tokens.push_back(std::string(str.data() + lastPos, pos - lastPos));

            break;
        }
        else
        {
            if (pos != lastPos || !trimEmpty)
                tokens.push_back(std::string(str.data() + lastPos, pos - lastPos));
        }

        lastPos = pos + 1;
    }
    return tokens;
}

std::string
atakmap::util::getDirectoryForFile(const char *filepath)
{
    using namespace TAK::Engine::Util;
    TAK::Engine::Port::String value;
    TAKErr code = IO_getParentFile(value, filepath);
    if(code != TE_Ok)
        throw IO_Error("IO_getParentFile failed");
    return std::string(value);
}


std::string
atakmap::util::getFileName(const char *filepath)
{
    using namespace TAK::Engine::Util;
    TAK::Engine::Port::String value;
    TAKErr code = IO_getName(value, filepath);
    if(code != TE_Ok)
        throw IO_Error("IO_getName failed");
    return std::string(value);
}


std::string
atakmap::util::getFileAsAbsolute(const char *file)
{
    using namespace TAK::Engine::Util;
    TAK::Engine::Port::String value;
    TAKErr code = IO_getAbsolutePath(value, file);
    if(code != TE_Ok)
        throw IO_Error("IO_getAbsolutePath failed");
    return std::string(value);
}


// Returns filePath as a relative path to basePath
std::string
atakmap::util::computeRelativePath(const char *basePath, const char *filePath)
{
    using namespace TAK::Engine::Util;
    TAK::Engine::Port::String value;
    TAKErr code = IO_getRelativePath(value, basePath, filePath);
    if(code != TE_Ok)
        throw IO_Error("IO_getRelativePath failed");
    return std::string(value);
}


bool
atakmap::util::deletePath(const char* path)
{
    using namespace TAK::Engine::Util;
    return (IO_delete(path) == TE_Ok);
}


std::vector<std::string>
atakmap::util::getDirContents(const char *path, DirContentsAcceptFilter filter)
{
    using namespace TAK::Engine::Util;

    std::vector<std::string> result;
    std::vector<TAK::Engine::Port::String> contents;
    TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> contents_w;
    if(IO_listFiles(contents_w, path, TELFM_Immediate, nullptr))
        return result;

    for (auto dIter(contents.begin());
        dIter != contents.end();
        ++dIter)
    {
        if (!filter || filter((*dIter).get()))
            result.push_back((*dIter).get());
    }

    return result;

}


unsigned long
atakmap::util::getFileCount(const char* path)
{
    using namespace TAK::Engine::Util;
    std::size_t value;
    return (IO_getFileCount(&value, path) == TE_Ok) ? static_cast<unsigned long>(value) : 0u;
}


unsigned long
atakmap::util::getFileSize(const char* path)
{
    using namespace TAK::Engine::Util;
    int64_t value;
    return (IO_length(&value, path) == TE_Ok) ? static_cast<unsigned long>(value) : 0u;
}



unsigned long
atakmap::util::getLastModified(const char* path)
{
    using namespace TAK::Engine::Util;
    int64_t value;
    return (IO_getLastModified(&value, path) == TE_Ok) ? static_cast<unsigned long>(value) : 0u;
}



bool
atakmap::util::isDirectory(const char* path)
{
    using namespace TAK::Engine::Util;
    bool value;
    return (IO_isDirectory(&value, path) == TE_Ok) ? value : false;
}



bool
atakmap::util::isFile(const char* path)
{
    using namespace TAK::Engine::Util;
    bool value;
    return (IO_isFile(&value, path) == TE_Ok) ? value : false;
}



bool
atakmap::util::pathExists(const char* path)
{
    using namespace TAK::Engine::Util;
    bool value;
    return (IO_exists(&value, path) == TE_Ok) ? value : false;
}


bool
atakmap::util::removeDir(const char* dirPath)
{
    using namespace TAK::Engine::Util;
    if(!isDirectory(dirPath))
        return false;
    return IO_delete(dirPath) == TE_Ok;
}


bool
atakmap::util::createDir(const char* dirPath)
{
    using namespace TAK::Engine::Util;
    return IO_mkdirs(dirPath) == TE_Ok;
}

///
///  Convenience functions for encode/decode implementations.
///


//
// Read a string in MUTF-8 form from the supplied stream.
//
TAK::Engine::Port::String
atakmap::util::readUTF(std::istream& strm)
{
    std::ostringstream str(std::ios_base::out | std::ios_base::binary);

    char c;
    char utf = 0;
    while (strm.get(c))
    {
        if (!(c & 0x80)) {
            str.put(c);
        }
        else {
            if ((c & 0xE0) != 0xC0)
                throw IO_Error("Invalid encoding");
            utf = (c & 0x1F) << 5;
            if (!strm.get(c))
                throw IO_Error("eof");
            if ((c & 0xC0) != 0x80)
                throw IO_Error("Invalid encoding");
            utf |= (c & 0x3F);

            if (utf == 0)
                break;
            str.put(utf);
        }
    }

    return str.str().c_str();
}

//
// Write the supplied string in MUTF-8 to the supplied stream.
//
std::ostream&
atakmap::util::writeUTF(std::ostream& strm,
    const char* begin)
{
    const char* const end(begin + std::strlen(begin) + 1);

    while (strm && begin < end)
    {
        if (*begin > 0 && *begin <= 127) // U+0000 uses two bytes.
        {
            strm.put(*begin);
        }
        else
        {
            strm.put(0xC0 | (0x1F & *begin >> 6)).put(0x80 | (0x3F & *begin));
        }
        begin++;
    }

    return strm;
}


#undef  MEM_FN
