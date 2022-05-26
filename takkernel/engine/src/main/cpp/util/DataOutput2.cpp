#include "util/DataOutput2.h"

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

DataOutput2::DataOutput2() :
    swappingEndian(false)
{}

DataOutput2::~DataOutput2()
{}

TAKErr DataOutput2::skip(const std::size_t n) NOTHROWS
{
    array_ptr<uint8_t> buf(new uint8_t[n]);
    return write(buf.get(), n);
}

TAKErr DataOutput2::writeFloat(const float value) NOTHROWS
{
    assert(sizeof(float) == 4);
    Convert c;
    c.f = value;
    if (swappingEndian)
        swapBytes(c.b, 4);
    return write(c.b, 4);
}

TAKErr DataOutput2::writeInt(const int32_t value) NOTHROWS
{
    Convert c;
    c.i = value;
    if (swappingEndian)
        swapBytes(c.b, sizeof(int32_t));
    return write(c.b, 4);
}

TAKErr DataOutput2::writeShort(const int16_t value) NOTHROWS
{
    Convert c;
    c.s = value;
    if (swappingEndian)
        swapBytes(c.b, sizeof(int16_t));
    return write(c.b, 2);
}

TAKErr DataOutput2::writeLong(const int64_t value) NOTHROWS
{
    Convert c;
    c.l = value;
    if (swappingEndian)
        swapBytes(c.b, sizeof(int64_t));
    return write(c.b, 8);
}

TAKErr DataOutput2::writeDouble(const double value) NOTHROWS
{
    assert(sizeof(double) == 8);
    Convert c;
    c.d = value;
    if (swappingEndian)
        swapBytes(c.b, 8);
    return write(c.b, 8);
}

TAKErr DataOutput2::writeString(const char *str) NOTHROWS
{
    return write(reinterpret_cast<const uint8_t *>(str), strlen(str));
}

void DataOutput2::setSourceEndian(const atakmap::util::Endian e) NOTHROWS
{
    setSourceEndian2((e == atakmap::util::BIG_ENDIAN) ? TE_BigEndian : TE_LittleEndian);
}

void DataOutput2::setSourceEndian2(const TAKEndian e) NOTHROWS
{
    swappingEndian = (e != TE_PlatformEndian);
}


FileOutput2::FileOutput2() NOTHROWS :
    f(nullptr)
{}

FileOutput2::~FileOutput2() NOTHROWS
{
    // XXX - log error
    close();
}

TAKErr FileOutput2::open(const char *filename) NOTHROWS
{
    return open(filename, "wb");
}

TAKErr FileOutput2::open(const char* filename, const char* openMode) NOTHROWS
{
    if (f)
        return TE_IO;
    f = fopen(filename, openMode);
    if (!f)
        return TE_IO;
    return TE_Ok;
}
TAKErr FileOutput2::close() NOTHROWS
{
    if (!f)
        // Already closed
        return TE_Ok;
    if (fclose(f) != 0)
        return TE_IO;
    else
        f = nullptr;
    return TE_Ok;
}

TAKErr FileOutput2::writeByte(const uint8_t value) NOTHROWS
{
    return write(&value, 1);
}

TAKErr FileOutput2::write(const uint8_t *buf, const std::size_t len) NOTHROWS
{
    if (len == 0)
        return TE_Ok;

    const std::size_t written = fwrite(buf, 1, len, f);
    return (written < len) ? TE_IO : TE_Ok;
}

TAKErr FileOutput2::skip(size_t n) NOTHROWS
{
    int r;
#ifdef WIN32
    r = _fseeki64(f, n, SEEK_CUR);
#else
    r = fseeko(f, n, SEEK_CUR);
#endif

    return (r==0) ? TE_Ok : TE_IO;
}

TAKErr FileOutput2::seek(const int64_t offset) NOTHROWS
{
    int r;
#ifdef WIN32
    r = _fseeki64(f, offset, SEEK_SET);
#else
    r = fseeko(f, offset, SEEK_SET);
#endif
    return (r == 0) ? TE_Ok : TE_IO;
}

TAKErr FileOutput2::tell(int64_t *value) NOTHROWS
{
#ifdef WIN32
    *value = _ftelli64(f);
#else
    *value = ftello(f);
#endif
    return (*value == -1LL) ? TE_IO : TE_Ok;
}




MemoryOutput2::MemoryOutput2() NOTHROWS :
    bytes(nullptr),
    curOffset(0),
    totalLen(0)
{}

MemoryOutput2::~MemoryOutput2() NOTHROWS
{}

TAKErr MemoryOutput2::open(uint8_t *buffer, const std::size_t len) NOTHROWS
{
    bytes = buffer;
    totalLen = len;
    curOffset = 0;
    return TE_Ok;
}

TAKErr MemoryOutput2::close() NOTHROWS
{
    bytes = nullptr;
    return TE_Ok;
}

TAKErr MemoryOutput2::writeByte(const uint8_t value) NOTHROWS
{
    if (curOffset >= totalLen) {
        Logger::log(Logger::Error, "MemoryOutput2: EOF");
        return TE_IO;
    }

    bytes[curOffset] = value;
    curOffset++;
    return TE_Ok;
}

TAKErr MemoryOutput2::write(const uint8_t *buf, const std::size_t len) NOTHROWS
{
    if (len == 0)
        return TE_Ok;

    size_t rem = totalLen - curOffset;
    if (len > rem)
        return TE_IO;

    memcpy(bytes + curOffset, buf, len);
    curOffset += len;
    return TE_Ok;
}

TAKErr MemoryOutput2::skip(const std::size_t n) NOTHROWS
{
    size_t rem = totalLen - curOffset;
    if (n > rem) {
        Logger::log(Logger::Error, "MemoryOutput2: Skipping indicated number of bytes would go past EOF");
        return TE_IO;
    }

    curOffset += n;

    return TE_Ok;
}

TAKErr MemoryOutput2::remaining(std::size_t *value) NOTHROWS
{
    *value = totalLen - curOffset;
    return TE_Ok;
}

ByteBufferOutput2::ByteBufferOutput2() NOTHROWS :
    buffer(nullptr)
{}

ByteBufferOutput2::~ByteBufferOutput2()
{}

TAKErr ByteBufferOutput2::open(MemBufferT<uint8_t> *buffer_) NOTHROWS
{
    this->buffer = buffer_;
    return TE_Ok;
}

TAKErr ByteBufferOutput2::close() NOTHROWS
{
    this->buffer = nullptr;
    return TE_Ok;
}

TAKErr ByteBufferOutput2::write(const uint8_t *buf, const std::size_t len) NOTHROWS
{
    const std::size_t actual = this->buffer->put2(buf, len);
    if (actual != len)
        return TE_IO;
    return TE_Ok;
}

TAKErr ByteBufferOutput2::writeByte(const uint8_t value) NOTHROWS
{
    try {
        this->buffer->put(value);
        return TE_Ok;
    } catch (std::out_of_range) {
        return TE_IO;
    }
}

TAKErr ByteBufferOutput2::skip(const std::size_t n) NOTHROWS
{
    if (n > this->buffer->remaining())
        return TE_IO;
    const std::size_t pos = std::min(this->buffer->position() + n, this->buffer->limit());
    this->buffer->position(pos);
    return TE_Ok;
}


DynamicOutput::DynamicOutput() NOTHROWS :
    buffer(nullptr),
    writePtr(nullptr),
    capacity(0)
{}

DynamicOutput::~DynamicOutput() NOTHROWS
{}

TAKErr DynamicOutput::open(const std::size_t capacity_) NOTHROWS
{
    if (buffer.get())
        return TE_IllegalState;
    capacity = capacity_;
    buffer.reset(new uint8_t[capacity]);
    writePtr = buffer.get();
    return TE_Ok;
}
    
TAKErr DynamicOutput::close() NOTHROWS
{
    buffer.reset();
    writePtr = nullptr;
    capacity = 0;
    return TE_Ok;
}

TAKErr DynamicOutput::write(const uint8_t *buf, const std::size_t len) NOTHROWS
{
    if (!buffer.get())
        return TE_IllegalState;

    std::size_t used = (writePtr - buffer.get());
    std::size_t remaining = (capacity-used);
    if (len > remaining) {
        std::size_t ncapacity = std::max(capacity * 2, used+len);
        array_ptr<uint8_t> scratch(new uint8_t[ncapacity]);
        memcpy(scratch.get(), buffer.get(), used);
        buffer.reset(scratch.release());
        capacity = ncapacity;
        writePtr = buffer.get() + used;
    }

    if (buf)
        memcpy(writePtr, buf, len);
    writePtr += len;
    return TE_Ok;
}

TAKErr DynamicOutput::writeByte(const uint8_t value) NOTHROWS
{
    return write(&value, 1);
}

TAKErr DynamicOutput::skip(const std::size_t n) NOTHROWS
{
    return write(nullptr, n);
}

TAKErr DynamicOutput::get(const uint8_t **buf, std::size_t *len) NOTHROWS
{
    if (!buffer.get())
        return TE_IllegalState;
    *buf = buffer.get();
    *len = (writePtr - buffer.get());
    return TE_Ok;
}

TAKErr DynamicOutput::reset() NOTHROWS
{
    writePtr = buffer.get();
    return TE_Ok;
}
