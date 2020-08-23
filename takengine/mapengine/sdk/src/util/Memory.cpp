#include "util/Memory.h"

#ifdef _MSC_VER
#include <Windows.h>
#ifndef _X86_
#define _X86_
#endif
#include <memoryapi.h>
#endif

#define __EXP_ENABLE_VIRTUALALLOC 0

extern "C" void te_free_v(const void *buf)
{
#if defined(_MSC_VER) && __EXP_ENABLE_VIRTUALALLOC
        VirtualFree(const_cast<void *>(buf), 0, MEM_RELEASE);
#else
        const auto *arg = static_cast<const uint8_t *>(buf);
        delete[] arg;
#endif
}

extern "C" void *te_alloc_v(const std::size_t size)
{
#if defined(_MSC_VER) && __EXP_ENABLE_VIRTUALALLOC
        DWORD err;
        std::unique_ptr<void, void(*)(const void *)> value(VirtualAlloc(nullptr, size, MEM_RESERVE/*|MEM_PHYSICAL*/, PAGE_READWRITE), te_free_v);
        void *mem = value.get();
        err = GetLastError();
        if (!mem)
            return nullptr;
        void *committed = VirtualAlloc(value.get(), size, MEM_COMMIT, PAGE_READWRITE);
        err = GetLastError();
        if (!committed)
            return nullptr;
        value.release();
        return committed;
#else
    return new(std::nothrow) uint8_t[size];
#endif
}


