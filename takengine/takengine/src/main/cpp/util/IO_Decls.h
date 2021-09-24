#ifndef TAK_ENGINE_UTIL_IO_DECLS_H_INCLUDED
#define TAK_ENGINE_UTIL_IO_DECLS_H_INCLUDED

#include <stdexcept>

namespace atakmap {
    namespace util {
        struct IO_Error
        : std::runtime_error
        {
            IO_Error(const char* errString)
            : std::runtime_error(errString)
            { }
        };
        
///
///  These are sometimes #defined (e.g., when __BSD_VISIBLE is also defined).
///
#ifdef BIG_ENDIAN
#undef BIG_ENDIAN
#endif
#ifdef LITTLE_ENDIAN
#undef LITTLE_ENDIAN
#endif
        
        enum Endian
        {
            BIG_ENDIAN,
            LITTLE_ENDIAN
        };
    }
}

#endif
