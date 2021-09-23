#include "util/MemBuffer.h"

#include <algorithm>
#include <cstring>
#include <memory>

#include "util/Memory.h"

using namespace atakmap::util;

using namespace TAK::Engine::Util;

MemBufferImpl::MemBufferImpl()
: buffer(nullptr), capacity(0) { }

MemBufferImpl::MemBufferImpl(size_t size) :
    buffer(new uint8_t[size]),
    capacity(size)
{}

MemBufferImpl::~MemBufferImpl()
{
    if (buffer) {
        delete [] static_cast<uint8_t *>(buffer);
        buffer = nullptr;
    }
}

void MemBufferImpl::resize(size_t size)
{
    if (capacity != size) {
        array_ptr<unsigned char> nbuf(size > 0 ? new uint8_t[size] : nullptr);
        if (buffer) {
            memcpy(nbuf.get(), buffer, std::min(size, capacity));
            delete [] static_cast<uint8_t *>(buffer);
        }
        buffer = nbuf.release();
        capacity = size;
    }
}

size_t MemBufferImpl::getCapacity() const
{
    return capacity;
}

void *MemBufferImpl::get()
{
    return buffer;
}

const void *MemBufferImpl::get() const
{
    return buffer;
}
