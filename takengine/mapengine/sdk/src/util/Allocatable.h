#ifndef TAK_ENGINE_UTIL_ALLOCATABLE_H_INCLUDED
#define TAK_ENGINE_UTIL_ALLOCATABLE_H_INCLUDED

#include <atomic>
#include <cstddef>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            template<class T>
            struct Allocatable
            {
            protected :
                Allocatable() NOTHROWS;
                ~Allocatable() NOTHROWS;
            public :
                static void *operator new(std::size_t sz);
                static void *operator new(std::size_t sz, void *place);
            public :
                static std::size_t getTotalInstances() NOTHROWS;
                static std::size_t getLiveInstances() NOTHROWS;
                static std::size_t getHeapAllocations() NOTHROWS;
            private :
#ifdef _DEBUG
                static std::atomic<std::size_t> totalInstances;
                static std::atomic<std::size_t> liveInstances;
                static std::atomic<std::size_t> heapAllocations;
#endif
            };

#if _DEBUG
            template<class T>
            std::atomic<std::size_t> Allocatable<T>::totalInstances(0u);
            template<class T>
            std::atomic<std::size_t> Allocatable<T>::liveInstances(0u);
            template<class T>
            std::atomic<std::size_t> Allocatable<T>::heapAllocations(0u);
#endif

            template<class T>
            Allocatable<T>::Allocatable() NOTHROWS
            {
#ifdef _DEBUG
                totalInstances++;
                liveInstances++;
#endif
            }
            template<class T>
            Allocatable<T>::~Allocatable() NOTHROWS
            {
#ifdef _DEBUG
                liveInstances--;
#endif
            }
            template<class T>
            std::size_t Allocatable<T>::getTotalInstances() NOTHROWS
            {
#ifdef _DEBUG
                return totalInstances;
#else
                return 0u;
#endif
            }
            template<class T>
            std::size_t Allocatable<T>::getLiveInstances() NOTHROWS
            {
#ifdef _DEBUG
                return liveInstances;
#else
                return 0u;
#endif
            }
            template<class T>
            std::size_t Allocatable<T>::getHeapAllocations() NOTHROWS
            {
#ifdef _DEBUG
                return heapAllocations;
#else
                return 0u;
#endif
            }
            template<class T>
            void *Allocatable<T>::operator new(std::size_t sz)
            {
#ifdef _DEBUG
                heapAllocations++;
#endif
                return ::operator new(sz);
            }
            template<class T>
            void *Allocatable<T>::operator new(std::size_t sz, void *place)
            {
                return place;
            }
        }
    }
}

#endif // TAK_ENGINE_UTIL_ALLOCATABLE_H_INCLUDED
