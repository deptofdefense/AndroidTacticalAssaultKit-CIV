#include "util/DataInput2.h"

#include <assert.h>

#include "util/IO.h"
#include "util/Logging.h"
#include "util/Memory.h"

using namespace TAK::Engine::Util;

using namespace atakmap::util;

namespace {
    union Convert
    {
        uint8_t b[sizeof(int64_t)];
        int32_t i;
        int16_t s;
        int64_t l;
        float f;
        double d;
    };

    void swapBytes(uint8_t *b, const std::size_t len)
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

DataInput2::DataInput2() :
swappingEndian(false)
{}

DataInput2::~DataInput2()
{}

TAKErr DataInput2::skip(const std::size_t n) NOTHROWS
{
    array_ptr<uint8_t> buf(new uint8_t[n]);
    std::size_t scratch;
    return read(buf.get(), &scratch, n);
}
int64_t DataInput2::length() const NOTHROWS
{
    return -1LL;
}
TAKErr DataInput2::readFloat(float *value) NOTHROWS
{
    TAKErr code;
    assert(sizeof(float) == 4);
    Convert c;
    std::size_t numRead;
    code = read(c.b, &numRead, 4);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 4)
        return TE_IO;
    if (swappingEndian)
        swapBytes(c.b, 4);
    *value = c.f;
    return code;
}

TAKErr DataInput2::readInt(int32_t *value) NOTHROWS
{
    TAKErr code;
    Convert c;
    std::size_t numRead;
    code = read(c.b, &numRead, 4);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 4)
        return TE_IO;
    if (swappingEndian)
        swapBytes(c.b, 4);
    *value = c.i;
    return code;
}

TAKErr DataInput2::readShort(int16_t *value) NOTHROWS
{
    TAKErr code;
    Convert c;
    std::size_t numRead;
    code = read(c.b, &numRead, 2);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 2)
        return TE_IO;
    if (swappingEndian)
        swapBytes(c.b, sizeof(int16_t));
    *value = c.s;
    return code;
}

TAKErr DataInput2::readLong(int64_t *value) NOTHROWS
{
    TAKErr code;
    Convert c;
    std::size_t numRead;
    code = read(c.b, &numRead, 8);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 8)
        return TE_IO;
    if (swappingEndian)
        swapBytes(c.b, sizeof(int64_t));
    *value = c.l;
    return code;
}

TAKErr DataInput2::readDouble(double *value) NOTHROWS
{
    assert(sizeof(double) == 8);
    TAKErr code;
    Convert c;
    std::size_t numRead;
    code = read(c.b, &numRead, 8);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 8)
        return TE_IO;
    if (swappingEndian)
        swapBytes(c.b, 8);
    *value = c.d;
    return code;
}

TAKErr DataInput2::readString(char *str, std::size_t *numRead, const std::size_t len) NOTHROWS
{
    TAKErr code;
    code = read(reinterpret_cast<uint8_t *>(str), numRead, len);
    TE_CHECKRETURN_CODE(code);
    str[*numRead] = '\0';
    return code;
}

void DataInput2::setSourceEndian(const atakmap::util::Endian e) NOTHROWS
{
    setSourceEndian2((e == atakmap::util::BIG_ENDIAN) ? TE_BigEndian : TE_LittleEndian);
}

void DataInput2::setSourceEndian2(const TAKEndian e) NOTHROWS
{
    swappingEndian = (e != TE_PlatformEndian);
}

TAKEndian DataInput2::getSourceEndian() const NOTHROWS
{
    if (!swappingEndian)
        return TE_PlatformEndian;
    else if (TE_PlatformEndian == TE_BigEndian)
        return TE_LittleEndian;
    else
        return TE_BigEndian;
}

FileInput2::FileInput2() NOTHROWS : f_(nullptr), len_(-1LL), name_(nullptr) {}

FileInput2::~FileInput2() NOTHROWS
{
    // XXX - log error
    closeImpl();
}

TAKErr FileInput2::open(const char *filename) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (f_)
        return TE_IllegalState;
    name_ = filename;
    f_ = fopen(filename, "rb");
    if (!f_) {
        name_ = nullptr;
        len_ = -1LL;
        return TE_IO;
    }
    return TE_Ok;
}

TAKErr FileInput2::close() NOTHROWS
{
    return closeImpl();
}

TAKErr FileInput2::closeImpl() NOTHROWS
{
    if (!f_)
        // Already closed
        return TE_Ok;
    int err = fclose(f_);
    f_ = nullptr;
    len_ = -1LL;
    return (!err) ? TE_Ok : TE_IO;
}

TAKErr FileInput2::readByte(uint8_t *value) NOTHROWS
{
    TAKErr code;
    std::size_t numRead;
    code = read(value, &numRead, 1);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FileInput2::read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS
{
    if (len == 0)
        return TE_Ok;

    const std::size_t read = fread(buf, 1, len, f_);
    *numRead = read;
    if (!read)
        return TE_EOF;
    return TE_Ok;
}

TAKErr FileInput2::skip(size_t n) NOTHROWS
{
    int r;
#ifdef WIN32
    r = _fseeki64(f_, n, SEEK_CUR);
#else
    r = fseeko(f_, n, SEEK_CUR);
#endif

    return (r == 0) ? TE_Ok : TE_IO;
}
int64_t FileInput2::length() const NOTHROWS
{
    if (len_ == -1LL) {
        int64_t len(-1LL);
        if (IO_length(&len, name_) == TE_Ok)
            const_cast<FileInput2 *>(this)->len_ = len;
    }
    return len_;
}
TAKErr FileInput2::seek(const int64_t offset) NOTHROWS
{
    int r;
#ifdef WIN32
    r = _fseeki64(f_, offset, SEEK_SET);
#else
    r = fseeko(f_, offset, SEEK_SET);
#endif
    return (r == 0) ? TE_Ok : TE_IO;
}

TAKErr FileInput2::tell(int64_t *value) NOTHROWS
{
#ifdef WIN32
    *value = _ftelli64(f_);
#else
    *value = ftello(f_);
#endif
    return (*value == -1LL) ? TE_IO : TE_Ok;
}




MemoryInput2::MemoryInput2() NOTHROWS :
    bytes(nullptr, nullptr),
    curOffset(0),
    totalLen(0)
{}

MemoryInput2::~MemoryInput2() NOTHROWS
{}

TAKErr MemoryInput2::open(const uint8_t *buffer, const std::size_t len) NOTHROWS
{
    bytes = std::unique_ptr<const uint8_t, void(*)(const uint8_t *)>(buffer, Memory_leaker_const<uint8_t>);
    totalLen = len;
    curOffset = 0;
    return TE_Ok;
}

TAKErr MemoryInput2::open(std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> &&buffer, const std::size_t len) NOTHROWS
{
    bytes = std::move(buffer);
    totalLen = len;
    curOffset = 0;
    return TE_Ok;
}

TAKErr MemoryInput2::close() NOTHROWS
{
    bytes.reset();
    return TE_Ok;
}

TAKErr MemoryInput2::readByte(uint8_t *value) NOTHROWS
{
    if (!bytes.get())
        return TE_IllegalState;
    if (curOffset >= totalLen) {
        Logger::log(Logger::Error, "MemoryInput2: EOF");
        return TE_EOF;
    }

    *value = bytes.get()[curOffset];
    curOffset++;
    return TE_Ok;
}

TAKErr MemoryInput2::read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS
{
    if (!bytes.get())
        return TE_IllegalState;
    if (len == 0)
        return TE_Ok;

    size_t rem = totalLen - curOffset;
    if (rem == 0)
        return TE_EOF;
    
    size_t numCopy = std::min(len, rem);
    memcpy(buf, bytes.get() + curOffset, numCopy);
    curOffset += numCopy;
    *numRead = numCopy;
    return TE_Ok;
}

TAKErr MemoryInput2::skip(const std::size_t n) NOTHROWS
{
    if (!bytes.get())
        return TE_IllegalState;
    size_t rem = totalLen - curOffset;
    if (n > rem) {
        Logger::log(Logger::Error, "MemoryInput2: Skipping indicated number of bytes would go past EOF");
        return TE_IO;
    }

    curOffset += n;

    return TE_Ok;
}
int64_t MemoryInput2::length() const NOTHROWS
{
    return bytes.get() ? totalLen : -1LL;
}
TAKErr MemoryInput2::remaining(std::size_t *value) NOTHROWS
{
    if (!bytes.get())
        return TE_IllegalState;
    *value = (totalLen - curOffset);
    return TE_Ok;
}

TAKErr MemoryInput2::reset() NOTHROWS
{
    if (!bytes.get())
        return TE_IllegalState;
    curOffset = 0;
    return TE_Ok;
}


ByteBufferInput2::ByteBufferInput2() NOTHROWS :
    buffer_(nullptr),
    len_(-1LL)
{}

ByteBufferInput2::~ByteBufferInput2()
{}

TAKErr ByteBufferInput2::open(MemBufferT<uint8_t> *buffer) NOTHROWS
{
    this->buffer_ = buffer;
    this->len_ = this->buffer_->remaining();
    return TE_Ok;
}

TAKErr ByteBufferInput2::close() NOTHROWS
{
    this->buffer_ = nullptr;
    this->len_ = -1LL;
    return TE_Ok;
}

TAKErr ByteBufferInput2::read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS
{
    *numRead = this->buffer_->get(buf, len);
    if ((*numRead) != len)
        return TE_IO;
    return TE_Ok;
}

TAKErr ByteBufferInput2::readByte(uint8_t *value) NOTHROWS
{
    try {
        std::size_t actual = this->buffer_->get(value, 1);
        if (actual != 1)
            return TE_EOF;
        return TE_Ok;
    }
    catch (std::out_of_range) {
        return TE_IO;
    }
}

TAKErr ByteBufferInput2::skip(const std::size_t n) NOTHROWS
{
    if (n > this->buffer_->remaining())
    return TE_IO;
    const std::size_t pos = std::min(this->buffer_->position() + n, this->buffer_->limit());
    this->buffer_->position(pos);
    return TE_Ok;
}
int64_t ByteBufferInput2::length() const NOTHROWS
{
    return this->len_;
}

