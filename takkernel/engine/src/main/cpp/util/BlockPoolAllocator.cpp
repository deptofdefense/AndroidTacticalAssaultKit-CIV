#include "util/BlockPoolAllocator.h"

#include <algorithm>
#include <atomic>
#include <cassert>

using namespace TAK::Engine::Util;

// MSVC warning-as-error`s this as convenience function...
#define Pool_getMetadata(p) \
    reinterpret_cast<PoolHeader *>(static_cast<unsigned char *>((p)))

namespace
{
    // XXX - free list as linked list with node pointer member of allocated block
    struct PoolHeader
    {
        std::size_t numBlocks;
        struct {
            std::size_t data;
            std::size_t reserved;
            std::size_t total;
        } blockSize;
        std::atomic<void *> freeList;
    };

    std::size_t getAlignedSize(const std::size_t sz, const std::size_t align) NOTHROWS
    {
        if (!align)
            return sz;
        return ((sz + (align - 1u)) / align)*align;
    }

    void Pool_deleter(void *pool)
    {
        PoolHeader &poolHeader = *Pool_getMetadata(pool);
        // destruct the atomic
        poolHeader.freeList.~atomic<void *>();
        free(pool);
    }

    void Block_deleter(const void *blockData) NOTHROWS
    {
        // obtain the pool pointer from the data section
        const void *ptrloc = static_cast<const unsigned char *>(blockData) - sizeof(std::shared_ptr<void>);
        const std::shared_ptr<void> &blockPoolPtr = *reinterpret_cast<const std::shared_ptr<void> *>(ptrloc);
        // obtain the pool header
        PoolHeader &poolHeader = *Pool_getMetadata(blockPoolPtr.get());
        // lock the pool
        const std::shared_ptr<void> pool(blockPoolPtr);

        // destruct the shared pointer to the pool
        blockPoolPtr.~shared_ptr<void>();
        {
            // obtain the block from the data section
            void * const block = const_cast<unsigned char *>(static_cast<const unsigned char *>(blockData) - poolHeader.blockSize.reserved);

            // adapted from example https://en.cppreference.com/w/cpp/atomic/atomic_compare_exchange

            // add the block to the free list
            void **blockNextFree = (void**)block;
            do {
                // push the current head
                *blockNextFree = poolHeader.freeList.load(std::memory_order_relaxed);
            } while (!std::atomic_compare_exchange_weak_explicit(
                                    &poolHeader.freeList,
                                    blockNextFree,
                                    block,
                                    std::memory_order_release,
                                    std::memory_order_relaxed));
        }

        // pool is unlocked on exit and eligible for destruct if no remaining
        // references
    }

    void allocatePool(std::unique_ptr<void, void(*)(void *)> &value, const std::size_t blockSize, const std::size_t numBlocks, const std::size_t align) NOTHROWS
    {
        assert(!!align);

        // compute the aligned sizes
        const std::size_t alignedBlockHeaderSize = getAlignedSize(std::max(sizeof(void *), sizeof(std::shared_ptr<void>)), align);
        const std::size_t alignedBlockSize = getAlignedSize(blockSize, align);

        const std::size_t poolSize =
            sizeof(PoolHeader) + // metadata section
            (align - 1u) + // add a padding of `align` bytes to align the block section
            ((alignedBlockHeaderSize + alignedBlockSize)*numBlocks); // blocks

        // allocate the pool
        void *pool = malloc(poolSize);
        if (!pool) {
            Logger_log(TELL_Warning, "BlockPoolAllocator: failed to allocate %ul, revert to heap allocation", (unsigned long)poolSize);
            return;
        }

        // the blocks section
        unsigned char *blocks = (static_cast<unsigned char *>(pool) + sizeof(PoolHeader));
        // align the blocks
        const std::size_t outOfAlign = ((intptr_t)blocks%align);
        if (outOfAlign)
            blocks += (align - outOfAlign);

        // the header section
        PoolHeader &metadata = *Pool_getMetadata(pool);
        metadata.numBlocks = numBlocks;
        metadata.blockSize.data = alignedBlockSize;
        metadata.blockSize.reserved = alignedBlockHeaderSize;
        metadata.blockSize.total = metadata.blockSize.data+metadata.blockSize.reserved;
        // in-place construct the free list, pointing to the blocks section
        new (&metadata.freeList) std::atomic<void *>(blocks);

        // build the free list
        if (numBlocks) {
            // populate the free list and block indices
            for (std::size_t i = 0u; i < numBlocks-1u; i++) {
                void *block = blocks + (metadata.blockSize.total*i);
                void **blockNextFree = (void**)block;
                *blockNextFree = static_cast<unsigned char *>(block) + (metadata.blockSize.total);
            }
            // last block terminates the free list
            {
                void *block = blocks + (metadata.blockSize.total*(numBlocks - 1u));
                void **blockNextFree = (void**)block;
                *blockNextFree = nullptr;
            }
        }

        value = std::unique_ptr<void, void(*)(void *)>(pool, Pool_deleter);
    }
}

BlockPoolAllocator::BlockPoolAllocator(const std::size_t blockSize_, const std::size_t numBlocks_, const std::size_t align_) NOTHROWS :
    blockSize(blockSize_)
{
    // allocate the pool
    std::unique_ptr<void, void(*)(void *)> p(nullptr, nullptr);
    allocatePool(p, blockSize_, numBlocks_, !!align_ ? align_ : 1u);
    pool = std::move(p);
}
BlockPoolAllocator::~BlockPoolAllocator() NOTHROWS
{}

TAKErr BlockPoolAllocator::allocate(std::unique_ptr<void, void(*)(const void *)> &value, const bool heapAllocOnExhaust) NOTHROWS
{
    do {
        // if the pool failed to allocate, go to regular heap allocation
        if (!pool)
            break;
        PoolHeader &metadata = *Pool_getMetadata(pool.get());

        // adapted from example https://en.cppreference.com/w/cpp/atomic/atomic_compare_exchange

        // pop next free block
        void *block;
        void **blockNextFree;
        do {
            block = metadata.freeList.load(std::memory_order_relaxed);
            // free list is empty
            if (!block)
                break;
            // get the pointer to the next free
            blockNextFree = (void**)block;
        } while (!std::atomic_compare_exchange_weak_explicit(
                                &metadata.freeList,
                                &block,
                                *blockNextFree, // store next free pointer
                                std::memory_order_release,
                                std::memory_order_relaxed));

        if (!block) {
            if (heapAllocOnExhaust)
                break; // fall through to heap allocation
            else
                return TE_OutOfMemory;
        }

        // in place construct shared pointer to pool, aligned to data boundary
        void *ptrloc = reinterpret_cast<unsigned char *>(block) + (metadata.blockSize.reserved - sizeof(std::shared_ptr<void>));
        new (ptrloc) std::shared_ptr<void>(pool);

        // return the data section of the block to the client
        value = std::unique_ptr<void, void(*)(const void *)>(static_cast<unsigned char *>(block) + metadata.blockSize.reserved, Block_deleter);
        return TE_Ok;
    } while (false);

    // allocate off the heap
    value = std::unique_ptr<void, void(*)(const void *)>(new(std::nothrow) uint8_t[blockSize], Memory_void_array_deleter_const<uint8_t>);
    return !value ? TE_OutOfMemory : TE_Ok;
}
