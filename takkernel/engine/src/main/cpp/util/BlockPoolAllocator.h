#ifndef TAK_ENGINE_UTIL_BLOCKPOOLALLOCATOR_H_INCLUDED
#define TAK_ENGINE_UTIL_BLOCKPOOLALLOCATOR_H_INCLUDED

#include <cstddef>
#include <memory>

#include "port/Platform.h"
#include "util/Error.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            /**
             * <P>Lifetime of the underlying pool is tied to both the
             * `BlockPoolAllocator` instance and all allocated blocks.
             * Allocated blocks will remain valid even if the
             * `BlockPoolAllocator` is destructed. The pool is only destructed
             * once both the `BlockPoolAllocator` and all outstanding
             * allocated blocks have been destructed.
             *
             * <P>This class is lock-free thread-safe.
             */
            class ENGINE_API BlockPoolAllocator
            {
            public :
                /**
                 * @param blockSize The size of a block, in bytes
                 * @param numBlocks The number of blocks in the pool
                 * @param align     The byte alignment requirement (default to `1`)
                 */
                BlockPoolAllocator(const std::size_t blockSize, const std::size_t numBlocks, const std::size_t align = alignof(std::max_align_t)) NOTHROWS;
                ~BlockPoolAllocator() NOTHROWS;
            public :
                /**
                 * Allocates a block from the pool. Lifetime of all returned
                 * blocks is independent of this `BlockPoolAllocator`; blocks
                 * will remain valid until destructed, even if the
                 * `BlockPoolAllocator` has been destructed.
                 * 
                 * @param value                 Returns the block, if allocated
                 * @param heapAllocOnExhaust    If `true`, performs heap
                 *                              allocations if the pool is
                 *                              empty; if `false` will return
                 *                              `TE_OutOfMemory`
                 *
                 * @return  TE_Ok on success, TE_OutOfMemory if allocation
                 *          fails; various codes on failure.
                 */
                TAKErr allocate(std::unique_ptr<void, void(*)(const void *)> &value, const bool heapAllocOnExhaust = true) NOTHROWS;
            private :
                std::shared_ptr<void> pool;
                std::size_t blockSize;
            };
        }
    }
}

#endif

