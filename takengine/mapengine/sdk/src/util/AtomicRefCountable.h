
#ifndef ATAKMAP_UTIL_ATOMIC_REF_COUNTABLE_H_INCLUDED
#define ATAKMAP_UTIL_ATOMIC_REF_COUNTABLE_H_INCLUDED

#include "port/Platform.h"
#include "util/AtomicCounter.h"

namespace atakmap {
    namespace util {
        /**
         * Alternate base that works with PGSC::RefCountablePtr<> that uses an atomic
         * counter which is very fast and avoids thread exceptions.
         */
        class ENGINE_API AtomicRefCountable {
        public:
            virtual ~AtomicRefCountable();
            
            void incRef();
            void decRef();
            
        private:
            AtomicCounter counter;
        };
        
    }
}

#endif