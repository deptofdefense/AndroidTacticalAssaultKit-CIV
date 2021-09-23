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
             * Looks up the specified ConfigOption as though by ConfigOptions_getOption()
             * and convert the resulting string value to an integer. If obtaining the option
             * fails for any reason (including not existing) or if the value cannot be converted
             * to an integer, return the specified default value.
             * @param key The key
             * @param defVal the default value to use if the value is invalid or not present
             * @return the integer option value or default, as described above.
             */
            ENGINE_API int ConfigOptions_getIntOptionOrDefault(const char *key, int defVal) NOTHROWS;

            /**
             * Looks up the specified ConfigOption as though by ConfigOptions_getOption()
             * and convert the resulting string value to a double. If obtaining the option
             * fails for any reason (including not existing) or if the value cannot be converted
             * to a double, return the specified default value.
             * @param key The key
             * @param defVal the default value to use if the value is invalid or not present
             * @return the double option value or default, as described above.
             */
            ENGINE_API double ConfigOptions_getDoubleOptionOrDefault(const char *option, double defVal) NOTHROWS;

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
