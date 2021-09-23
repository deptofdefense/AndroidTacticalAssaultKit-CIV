#ifndef TAK_ENGINE_PORT_PLATFORM_H_INCLUDED
#define TAK_ENGINE_PORT_PLATFORM_H_INCLUDED

#include <cstdint>
#include <cstddef>

#ifdef __APPLE__
#include <TargetConditionals.h>
#if TARGET_OS_IPHONE == 1 || TARGET_OS_SIMULATOR == 1
#define __APPLE_IOS__
#else
#define __APPLE_OSX__
#endif
#endif

#if defined(__linux__) && !defined(__ANDROID__)
#define __LINUX__
#endif

#if __cplusplus >= 201103L || _MSC_VER >= 1900
#define NOTHROWS noexcept
#else
#define NOTHROWS throw ()
#endif

#ifdef __APPLE_IOS__
#   define TE_SHOULD_ADAPT_STORAGE_PATHS 1
#else
#   define TE_SHOULD_ADAPT_STORAGE_PATHS 0
#endif

#if defined(_MSC_VER)
#ifdef ENGINE_EXPORTS
#define ENGINE_API __declspec(dllexport)
#else
#define ENGINE_API __declspec(dllimport)
#endif
#else
#define ENGINE_API
#endif

#define TE_ISNAN(x) ((x)!=(x))

namespace TAK {
    namespace Engine {
        namespace Port {
            enum DataType
            {
                TEDT_UInt8,
                TEDT_Int8,
                TEDT_UInt16,
                TEDT_Int16,
                TEDT_UInt32,
                TEDT_Int32,
                TEDT_UInt64,
                TEDT_Int64,
                TEDT_Float32,
                TEDT_Float64,
            };

            ENGINE_API std::size_t DataType_convert(void *dst, const void *src, std::size_t count, DataType dstType, DataType srcType) NOTHROWS;

            ENGINE_API std::size_t DataType_size(DataType dataType) NOTHROWS;
            ENGINE_API bool DataType_isFloating(DataType dataType) NOTHROWS;
            ENGINE_API bool DataType_isInteger(DataType dataType) NOTHROWS;
            ENGINE_API bool DataType_isUnsignedInteger(DataType dataType) NOTHROWS;


            
            /**
             * Returns the current system time, expressed as milliseconds
             * since the epoch (Midnight 01JAN1970 UTC).
             *
             * @return  The current system time, expressed as milliseconds
             *          since the epoch (Midnight 01JAN1970 UTC).
             */
            ENGINE_API int64_t Platform_systime_millis() NOTHROWS;

            ENGINE_API char Platform_pathSep() NOTHROWS;
        }
    }
}

#endif // TAK_ENGINE_PORT_PLATFORM_H_INCLUDED
