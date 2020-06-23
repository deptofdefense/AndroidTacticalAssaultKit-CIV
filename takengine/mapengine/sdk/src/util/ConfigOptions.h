#ifndef TAK_ENGINE_UTIL_CONFIGOPTIONS_H_INCLUDED
#define TAK_ENGINE_UTIL_CONFIGOPTIONS_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            /**
             * Returns the value of the specified option.
             *
             * @param value Returns the value
             * @param key   The key
             *
             * @return TE_Ok on success, TE_InvalidArg if no such option exists.
             */
            ENGINE_API TAKErr ConfigOptions_getOption(Port::String &value, const char *key) NOTHROWS;

            /**
             * Sets the named option.  A value of 'NULL' may be used to clear.
             *
             * @param key   The key
             * @param value The value
             *
             * @return  TE_Ok on success
             */
            ENGINE_API TAKErr ConfigOptions_setOption(const char *key, const char *value) NOTHROWS;
        }
    }
}

#endif
