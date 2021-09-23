#ifndef ATAKMAP_UTIL_ATOMIC_COUNTER_H_INCLUDED
#define ATAKMAP_UTIL_ATOMIC_COUNTER_H_INCLUDED

#include "port/Platform.h"

#ifdef __ANDROID__
#include <stdatomic.h>
#elif defined(__LINUX__)
#include <atomic>
#endif
#include <stdint.h>

namespace atakmap {
    namespace util {
        
        class AtomicCounter {
        public:
            AtomicCounter()
#ifdef __ANDROID__
            {
                atomic_init(&value, 0);
            }
#elif defined(__LINUX__)
            {
                std::atomic_init(&value, 0);
            }
#else
                : value(0)
            { }
#endif
            
            inline explicit AtomicCounter(int32_t initialValue)
#ifdef __ANDROID__
            {
                atomic_init(&value, initialValue);
            }
#elif defined(__LINUX__)
            {
                std::atomic_init(&value, initialValue);
            }
#else
                : value(initialValue)
            { }
#endif
            
            /**
             * Atomically add a value to the counter value with full memory barrier
             *
             * @return the result value of the add
             */
            int32_t add(int32_t amount);
            
            /**
             * Read the current value. This should only be used for debugging
             */
            int32_t currentValue() const;
            
        private:
#if defined(_MSC_VER)
            long value;
#elif defined(__ANDROID__)
            atomic_int_fast32_t value;
#elif defined(__LINUX__)
            std::atomic_int_fast32_t value;
#else
            int32_t value;
#endif
        };
        
    }
}

#endif
