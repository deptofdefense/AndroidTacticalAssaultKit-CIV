#ifndef TAK_ENGINE_UTIL_ERROR_H_INCLUDED
#define TAK_ENGINE_UTIL_ERROR_H_INCLUDED

#include "util/Logging.h" // legacy include, will be removed in future release
#include "util/Logging2.h"

#define TE_CHECKRETURN(c)                                                                                                        \
    if ((c) != TAK::Engine::Util::TE_Ok) {                                                                                       \
        TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Error, "return %s@%d code=%d", __FILE__, __LINE__, (c)); \
        return;                                                                                                                  \
    }
#define TE_CHECKRETURN_CODE(c) \
    if((c) != TAK::Engine::Util::TE_Ok) {\
        TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Error, "return %s@%d code=%d", __FILE__, __LINE__, (c)); \
        return (c); \
    }
#define TE_CHECKRETURN_CODE_DEBUG(c) \
    if((c) != TAK::Engine::Util::TE_Ok) {\
        TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Debug, "return %s@%d code=%d", __FILE__, __LINE__, (c)); \
        return (c); \
    }
#define TE_CHECKBREAK_CODE(c) \
    if((c) != TAK::Engine::Util::TE_Ok)  {\
        if((c) != TAK::Engine::Util::TE_Done) \
            TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Error, "break %s@%d code=%d", __FILE__, __LINE__, (c)); \
        break; \
    }
#define TE_CHECKLOGRETURN_CODE(c, lvl, what, ...) \
    if((c) != TAK::Engine::Util::TE_Ok) { \
        atakmap::util::Logger::log(lvl, what, __VA_ARGS__); \
        return (c); \
    }
#define TE_CHECKLOGRETURN_CODE2(c, lvl, ...) \
    if((c) != TAK::Engine::Util::TE_Ok) { \
        TAK::Engine::Util::Logger_log(lvl, __VA_ARGS__); \
        return (c); \
    }

#define TE_BEGIN_TRAP() try

#define TE_END_TRAP(rc) \
    catch (std::bad_alloc) { \
        rc = TE_OutOfMemory; \
    } \
    catch(...) { \
        rc = TE_Err; \
    }


namespace TAK {
    namespace Engine {
        namespace Util {

            enum TAKErr
            {
                TE_Ok,
                TE_Err,
                TE_InvalidArg,
                TE_Busy,
                TE_Done,
                TE_Interrupted,
                TE_BadIndex,
                TE_Unsupported,
                TE_IO,
                TE_OutOfMemory,
                TE_IllegalState,
                TE_ConcurrentModification,
                TE_Canceled,
                TE_EOF,
                TE_TimedOut,
                TE_NotAuthorized
            };



        }
    }
}

#endif
