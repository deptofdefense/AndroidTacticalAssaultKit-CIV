#ifndef ATAKMAP_UTIL_ATOMIC_COUNTER_H_INCLUDED
#define ATAKMAP_UTIL_ATOMIC_COUNTER_H_INCLUDED

#include "port/Platform.h"

#if defined(__LINUX__) || defined(__ANDROID__)
#include <atomic>
#endif
#include <stdint.h>

namespace atakmap {
    namespace util {
        
        class AtomicCounter {
        public:
            AtomicCounter()
                : value(0)
            { }

            inline explicit AtomicCounter(int32_t initialValue)
                : value(initialValue)
            { }

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
#elif defined(__LINUX__) || defined(__ANDROID__)
            std::atomic<int_fast32_t> value;
#else
            int32_t value;
#endif
        };
        
    }
}

#endif
