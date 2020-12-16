
#include "util/Blob.h"

using namespace atakmap::util;

namespace {
    //void noopDeleter(const uint8_t *) { }
    
    void deleteDeleter(const uint8_t *bytes) {
        delete [] bytes;
    }
}

#if USE_LEAK_FREE_BLOB_IMPL
BlobImpl::BlobImpl(const uint8_t *begin, const uint8_t *end, void (* cleanup)(BlobImpl &))
: first(begin), second(end), cleanup(cleanup) { }

BlobImpl::BlobImpl(const uint8_t *begin, const uint8_t *end)
: first(begin), second(end), cleanup(nullptr) { }

BlobImpl::BlobImpl(BlobImpl &&temp) NOTHROWS
: first(temp.first), second(temp.second), cleanup(temp.cleanup) {
    cleanup = nullptr;
}

BlobImpl::BlobImpl(std::pair<const uint8_t *, const uint8_t *> bounds)
: first(bounds.first), second(bounds.second), cleanup(nullptr) { }

BlobImpl::~BlobImpl() {
    if (cleanup) {
        cleanup(*this);
    }
}

void BlobImpl::deleteCleanup(BlobImpl &blob) {
    delete[] blob.first;
}

std::unique_ptr<const uint8_t, void (*)(const uint8_t *)> BlobImpl::takeData() NOTHROWS {
    const uint8_t *data = this->first;
    if (cleanup) {
        cleanup = nullptr;
        return std::unique_ptr<const uint8_t, void (*)(const uint8_t *)>(data, ::deleteDeleter);
    }
    size_t len = this->second - this->first;
    if (len) {
        auto *dataCopy = new uint8_t[this->second - this->first];
        memcpy(dataCopy, this->first, len);
        data = dataCopy;
    }
    return std::unique_ptr<const uint8_t, void (*)(const uint8_t *)>(data, ::deleteDeleter);
}

BlobImpl atakmap::util::makeBlob(const uint8_t *begin, const uint8_t *end) {
    return BlobImpl(begin, end);
}

BlobImpl atakmap::util::makeBlobWithDeleteCleanup(const uint8_t *begin, const uint8_t *end) {
    return BlobImpl(begin, end, BlobImpl::deleteCleanup);
}
#else
BlobImpl makeBlob(const uint8_t *begin, const uint8_t *end) {
    return std::make_pair(begin, end);
}

BlobImpl makeBlobWithDeleteCleanup(const uint8_t *begin, const uint8_t *end) {
    return std::make_pair(begin, end);
}
#endif