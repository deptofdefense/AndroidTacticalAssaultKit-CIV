#include "IO.h"

#include <assert.h>
#include <cerrno>
#include <cstdio>
#include <unistd.h>

#include <platformstl/filesystem/directory_functions.hpp>
#include <platformstl/filesystem/filesystem_traits.hpp>
#include <platformstl/filesystem/readdir_sequence.hpp>
#include <platformstl/filesystem/path.hpp>

#include "thread/Lock.h"
#include "thread/Mutex.h"
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

    static TAK::Engine::Thread::Mutex mtx;
#if _MSC_VER >= 1900
    // P_tmpdir doesn't exist.  Use TMP environment variable.
    static const char* P_tmpdir(std::getenv("TMP"));
#endif
    TAK::Engine::Thread::Lock lock(mtx);
    for (int i = 0; i < 20; i++) {
		typedef std::unique_ptr<char[]> mutable_buffer;
        std::ostringstream tmpStrm;

        tmpStrm << (parentPath ? parentPath : P_tmpdir) << '/'
            << (prefix ? prefix : "") << "XXXXXX";
		const std::size_t tmpLength = tmpStrm.str().length();
		mutable_buffer tmpPath(new char[tmpLength + 1]);
		tmpStrm.str().copy(tmpPath.get(), tmpLength);
		tmpPath.get()[tmpStrm.str().length()] = 0;

        if (!mkdtemp(tmpPath.get()))
        {
            throw IO_Error(MEM_FN("createTempDir") "Call to mkdtemp failed");
        }

        if (suffix) {
            //
            // Attempt to create a directory with the suffixed name.  If successful,
            // remove the original tmp directory and return the suffixed name.
            //
			std::string suffixPath(tmpPath.get());
			suffixPath.append(suffix);
            if (platformstl::filesystem_traits<char>::file_exists(suffixPath.c_str()))
                continue;

            if (platformstl::create_directory_recurse(suffixPath.c_str()))
            {
                platformstl::remove_directory_recurse(tmpPath.get());
				mutable_buffer suffixBuffer(new char[suffixPath.length() + 1]);
				suffixPath.copy(suffixBuffer.get(), suffixPath.length());
				suffixBuffer.get()[suffixPath.length()] = 0;
				tmpPath.swap(suffixBuffer);
            }
        }

        return tmpPath.release();
    }
    return nullptr;
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
    platformstl::basic_path<char> p(filepath);
    if (p.has_sep())
        return std::string(filepath);
    p = p.pop();
    return std::string(p.c_str());
}


std::string
atakmap::util::getFileName(const char *filepath)
{
    platformstl::basic_path<char> p(filepath);
    return std::string(p.get_file());
}


std::string
atakmap::util::getFileAsAbsolute(const char *file)
{
    platformstl::basic_path<char> f(file);
    f.make_absolute();
    return std::string(f.c_str());

}


// Returns filePath as a relative path to basePath
std::string
atakmap::util::computeRelativePath(const char *basePath, const char *filePath)
{
    platformstl::basic_path<char> base(basePath);
    platformstl::basic_path<char> file(filePath);

    // Make both absolute
    base = base.make_absolute();
    file = file.make_absolute();

    // Compare paths up to inequality point.
    const char *baseStr = base.c_str();
    const char *fileStr = file.c_str();
    size_t i = 0;
    while (baseStr[i] == fileStr[i] && baseStr[i] != '\0' && baseStr[i] != '\0')
    {
        i++;
    }
    platformstl::basic_path<char> commonPath(baseStr, i);
    if (commonPath.empty())
        // Nothing in common
        return std::string(filePath);

    // Count pop's on base to equality point.
    platformstl::basic_path<char> ret;
    while (!base.empty()) {
        if (base.equal(commonPath))
            break;
        base.pop();
        ret.push("..");
    }

    // Put remainder of original file path on from equality point forward.
    size_t remains = file.size() - i;
    while (remains > 0 && (fileStr[i] == '/' || fileStr[i] == '\\')) {
        // skip separator
        i++;
        remains--;
    }
    if (remains > 0)
        ret.push(platformstl::basic_path<char>(fileStr + i, remains));

    return std::string(ret.c_str());
}


bool
atakmap::util::deletePath(const char* path)
{
    typedef platformstl::filesystem_traits<char>        FS_Traits;

    return path
        && (FS_Traits::is_file(path)
            ? FS_Traits::delete_file(path)
            : platformstl::remove_directory_recurse(path));
}


std::vector<std::string>
atakmap::util::getDirContents(const char *path, DirContentsAcceptFilter filter)
{
    typedef platformstl::filesystem_traits<char>        FS_Traits;
    typedef platformstl::readdir_sequence               DirSequence;

    std::vector<std::string> result;

    FS_Traits::stat_data_type statData;

    if (path && FS_Traits::stat(path, &statData))
    {
        if (FS_Traits::is_directory(&statData))
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
                if (!filter || filter(*dIter))
                    result.push_back(*dIter);
            }
        }
    }

    return result;

}


unsigned long
atakmap::util::getFileCount(const char* path)
{
    typedef platformstl::filesystem_traits<char>        FS_Traits;
    typedef platformstl::readdir_sequence               DirSequence;

    unsigned long result(0);
    FS_Traits::stat_data_type statData;

    if (path && FS_Traits::stat(path, &statData))
    {
        if (FS_Traits::is_file(&statData))
        {
            result = 1;
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
                result += getFileCount(*dIter);
            }
        }
    }

    return result;
}


unsigned long
atakmap::util::getFileSize(const char* path)
{
    typedef platformstl::filesystem_traits<char>        FS_Traits;
    typedef platformstl::readdir_sequence               DirSequence;

    unsigned long result(0);
    FS_Traits::stat_data_type statData;

    if (path && FS_Traits::stat(path, &statData))
    {
        if (FS_Traits::is_file(&statData))
        {
            result = static_cast<unsigned long>(FS_Traits::get_file_size(statData));
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
                result += getFileSize(*dIter);
            }
        }
    }

    return result;
}



unsigned long
atakmap::util::getLastModified(const char* path)
{
    typedef platformstl::filesystem_traits<char>        FS_Traits;
    typedef platformstl::readdir_sequence               DirSequence;

    unsigned long result(0);
    FS_Traits::stat_data_type statData;

    if (path && FS_Traits::stat(path, &statData))
    {
        if (FS_Traits::is_file(&statData))
        {
#ifdef PLATFORMSTL_OS_IS_UNIX
            result = statData.st_mtime * 1000 + statData.st_mtimensec / 1000000;
#else
            result = (static_cast<uint64_t> (statData.ftLastWriteTime.dwHighDateTime)
                << 32 | statData.ftLastWriteTime.dwLowDateTime)
                / 10000;
#endif
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
                result = std::max(result, getLastModified(*dIter));
            }
        }
    }

    return result;
}



bool
atakmap::util::isDirectory(const char* path)
{
    return path && platformstl::filesystem_traits<char>::is_directory(path);
}



bool
atakmap::util::isFile(const char* path)
{
    return path && platformstl::filesystem_traits<char>::is_file(path);
}



bool
atakmap::util::pathExists(const char* path)
{
    return path && platformstl::filesystem_traits<char>::file_exists(path);
}


bool
atakmap::util::removeDir(const char* dirPath)
{
    return dirPath ? platformstl::remove_directory_recurse(dirPath) : false;
}


bool
atakmap::util::createDir(const char* dirPath)
{
    return dirPath ? platformstl::create_directory_recurse(dirPath) : false;
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
