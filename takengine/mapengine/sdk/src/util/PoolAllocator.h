#ifndef TAK_ENGINE_UTIL_POOLALLOCATOR_H_INCLUDED
#define TAK_ENGINE_UTIL_POOLALLOCATOR_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Mutex.h"
#include "util/BlockPoolAllocator.h"
#include "util/Error.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            template<class T>
            class ENGINE_API PoolAllocator
            {
            private :
                struct Deleter
                {
                    void(*fn)(const void *);
                };
            public :
                PoolAllocator(const std::size_t numBlocks, const std::size_t align = 4u) NOTHROWS;
                ~PoolAllocator() NOTHROWS;
            public :
                template<class ... Args>
                TAKErr allocate(std::unique_ptr<T, void(*)(const T *)> &value, Args ... args) NOTHROWS;
            private :
                template<class Ts>
                static void deleter(const Ts *value)
                {
                    // in place destruct the object
                    value->~Ts();

                    // deallocate
                    const Deleter &deleter = *reinterpret_cast<const Deleter *>(reinterpret_cast<const unsigned char *>(value) + sizeof(Ts));
                    deleter.fn(value);
                }
            private :
                BlockPoolAllocator impl;
            };

            template<class T>
            PoolAllocator<T>::PoolAllocator(const std::size_t numBlocks, const std::size_t align) NOTHROWS :
                impl(sizeof(T) + sizeof(PoolAllocator<T>::Deleter), numBlocks, align)
            {}
            template<class T>
            PoolAllocator<T>::~PoolAllocator() NOTHROWS
            {}
            template<class T>
            template<class ... Args>
            TAKErr PoolAllocator<T>::allocate(std::unique_ptr<T, void(*)(const T *)> &value, Args ... args) NOTHROWS
            {
                TAKErr code(TE_Ok);
                std::unique_ptr<void, void(*)(const void *)> alloced(nullptr, nullptr);
                code = impl.allocate(alloced, true);
                TE_CHECKRETURN_CODE(code);
                // in-place construct the object
                new (alloced.get()) T(&args...);
                // set the deleter
                typename PoolAllocator<T>::Deleter &deleter = *reinterpret_cast<typename PoolAllocator<T>::Deleter *>(static_cast<unsigned char *>(alloced.get()) + sizeof(T));
                deleter.fn = alloced.get_deleter();
                value = std::unique_ptr<T, void(*)(const T *)>(reinterpret_cast<T *>(static_cast<unsigned char *>(alloced.release())), PoolAllocator<T>::deleter);
                return code;
            }
        }
    }
}

#endif
