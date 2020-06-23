#include "util/MemBuffer2.h"

#include "util/Memory.h"

using namespace TAK::Engine::Util;

namespace
{
    void membuffer_deleter(const void *opaque)
    {
        const uint8_t *arr = (const uint8_t *)opaque;
        delete [] arr;
    }
}

MemBuffer2::MemBuffer2(const uint8_t *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(NULL),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(const uint16_t *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(NULL),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_ * sizeof(uint16_t)),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(const float *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(NULL),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_ * sizeof(float)),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(uint8_t *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(reinterpret_cast<uint8_t *>(mem_)),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(uint16_t *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(reinterpret_cast<uint8_t *>(mem_)),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_ * sizeof(uint16_t)),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(float *mem_, const std::size_t limit_) NOTHROWS :
    buffer(mem_, Memory_leaker_const<void>),
    base(reinterpret_cast<uint8_t *>(mem_)),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_ * sizeof(float)),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(const std::size_t limit_) NOTHROWS :
    buffer(new uint8_t[limit_], membuffer_deleter),
    base(const_cast<uint8_t *>(reinterpret_cast<const uint8_t *>(buffer.get()))),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(std::unique_ptr<const void, void(*)(const void *)> &&buf_, const std::size_t limit_) NOTHROWS :
    buffer(buf_.release(), buf_.get_deleter()),
    base(NULL),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_),
    pos(0u),
    lim(sz)
{}
MemBuffer2::MemBuffer2(std::unique_ptr<void, void(*)(const void *)> &&buf_, const std::size_t limit_) NOTHROWS :
    buffer(buf_.release(), buf_.get_deleter()),
    base(const_cast<uint8_t *>(reinterpret_cast<const uint8_t *>(buffer.get()))),
    base_const(reinterpret_cast<const uint8_t *>(buffer.get())),
    sz(limit_),
    pos(0u),
    lim(sz)
{}

bool MemBuffer2::isReadOnly() const NOTHROWS
{
    return !base;
}
std::size_t MemBuffer2::remaining() const NOTHROWS
{
    return lim - pos;
}
std::size_t MemBuffer2::limit() const NOTHROWS
{
    return lim;
}
TAKErr MemBuffer2::skip(const std::size_t count) NOTHROWS
{
    if (pos + count > lim)
        return TE_EOF;
    pos += count;
    return TE_Ok;
}
std::size_t MemBuffer2::position() const NOTHROWS
{
    return pos;
}
TAKErr MemBuffer2::position(const std::size_t pos_) NOTHROWS
{
    if (pos_ > lim)
        return TE_EOF;
    pos = pos_;
    return TE_Ok;
}
TAKErr MemBuffer2::limit(const std::size_t lim_) NOTHROWS
{
    if (lim_ > sz || lim_ < pos)
        return TE_InvalidArg;
    lim = lim_;
    return TE_Ok;
}
std::size_t MemBuffer2::size() const NOTHROWS
{
    return sz;
}
void MemBuffer2::reset() NOTHROWS
{
    pos = 0u;
    lim = sz;
}
void MemBuffer2::flip() NOTHROWS
{
    lim = pos;
    pos = 0u;
}
const uint8_t *MemBuffer2::get() const NOTHROWS
{
    return base_const;
}
