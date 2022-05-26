
#ifndef ATAKMAP_UTIL_BLOB_H_INCLUDED
#define ATAKMAP_UTIL_BLOB_H_INCLUDED

#include <stdint.h>
#include <stdlib.h>
#include <utility>
#include <memory>

#include "port/Platform.h"

/*
 Many of the Cursor::Blob, Statement::Blob, DatasetDescriptor::ByteBuffer interfaces leak their bytes. Some of
 the leaks are quite significant (i.e. DatasetDescriptor::encode). This class is designed to take care of those
 leaks with only minor modification to the code that uses blobs while allowing fallback to the std::pair impl if the
 needed c++11 featuers are not available. rvalue references are needed to move blobs on return by transfering
 the cleanup to the returned blob.
 */
#define USE_LEAK_FREE_BLOB_IMPL 1

namespace atakmap {
    
    namespace util {
        
#if USE_LEAK_FREE_BLOB_IMPL
        class ENGINE_API BlobImpl {
        public:
            BlobImpl(const uint8_t *begin, const uint8_t *end, void (* cleanup)(BlobImpl &));
            BlobImpl(const uint8_t *begin, const uint8_t *end);
            BlobImpl(BlobImpl &&temp) NOTHROWS;
            BlobImpl(const BlobImpl&) = delete;
            
            // allows for implicit conversions
            BlobImpl(std::pair<const uint8_t *, const uint8_t *> bounds);
            
            ~BlobImpl();
            
            static void deleteCleanup(BlobImpl &blob);
            
            const uint8_t *first;
            const uint8_t *second;
            
            std::unique_ptr<const uint8_t, void (*)(const uint8_t *)> takeData() NOTHROWS;
            
        private:
            void (* cleanup) (BlobImpl &);
        };
        
		ENGINE_API BlobImpl makeBlob(const uint8_t *begin, const uint8_t *end);
		ENGINE_API BlobImpl makeBlobWithDeleteCleanup(const uint8_t *begin, const uint8_t *end);
#else
        typedef std::pair<const uint8_t *, const uint8_t *> BlobImpl;
        
		ENGINE_API BlobImpl makeBlob(const uint8_t *begin, const uint8_t *end);
		ENGINE_API BlobImpl makeBlobWithDeleteCleanup(const uint8_t *begin, const uint8_t *end);
#endif
    }
    
}

#endif