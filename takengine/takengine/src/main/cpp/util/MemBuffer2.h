#ifndef TAK_ENGINE_UTIL_MEMBUFFER2_H_INCLUDED
#define TAK_ENGINE_UTIL_MEMBUFFER2_H_INCLUDED

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <memory>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            class ENGINE_API MemBuffer2
            {
            public :
                MemBuffer2() NOTHROWS;
                MemBuffer2(const uint8_t *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(const uint16_t *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(const float *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(uint8_t *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(uint16_t *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(float *mem, const std::size_t count) NOTHROWS;
                MemBuffer2(const std::size_t size) NOTHROWS;
                MemBuffer2(std::unique_ptr<void, void(*)(const void *)> &&buf, const std::size_t size) NOTHROWS;
                MemBuffer2(std::unique_ptr<const void, void(*)(const void *)> &&buf, const std::size_t size) NOTHROWS;
                MemBuffer2(MemBuffer2 &&other) NOTHROWS;
            private :
                MemBuffer2(const MemBuffer2 &other) NOTHROWS;
            public :
                template<typename T>
                TAKErr get(T *value) NOTHROWS;
                template<typename T>
                TAKErr get(T *value, const std::size_t count) NOTHROWS;
                template<typename T>
                TAKErr put(const T &value) NOTHROWS;
                template<typename T>
                TAKErr put(const T *value, const std::size_t count) NOTHROWS;
                bool isReadOnly() const NOTHROWS;
                std::size_t remaining() const NOTHROWS;
                TAKErr skip(const std::size_t count) NOTHROWS;
                std::size_t position() const NOTHROWS;
                TAKErr position(const std::size_t pos) NOTHROWS;
                /**
                 * Returns the current limit.
                 */
                std::size_t limit() const NOTHROWS;
                /**
                 * Sets the new limit.
                 * @param lim   The new limit
                 * @return  TE_Ok on success, various codes on failure
                 */
                TAKErr limit(const std::size_t lim) NOTHROWS;
                /**
                 * Returns the size of the memory block in bytes.
                 */
                std::size_t size() const NOTHROWS;
                /**
                 * Sets the position to '0' and the limit to the size.
                 */
                void reset() NOTHROWS;
                /**
                 * Sets the limit to the current position and the position to '0'.
                 */
                void flip() NOTHROWS;
                const uint8_t *get() const NOTHROWS;
            public :
                MemBuffer2 &operator=(MemBuffer2 &&other) NOTHROWS;
            private :
                std::unique_ptr<const void, void(*)(const void *)> buffer;
                uint8_t *base;
                const uint8_t *base_const;
                std::size_t sz;
                std::size_t pos;
                std::size_t lim;
            };

            typedef std::unique_ptr<MemBuffer2, void(*)(const MemBuffer2 *)> MemBuffer2Ptr;

            template<typename T>
            inline TAKErr MemBuffer2::get(T *value) NOTHROWS
            {
                if ((pos + sizeof(T)) > lim)
                    return TE_EOF;
                *value = *reinterpret_cast<const T *>(base_const + pos);
                pos += sizeof(T);
                return TE_Ok;
            }

            template<typename T>
            inline TAKErr MemBuffer2::get(T *value, const std::size_t count) NOTHROWS
            {
                if ((pos + (sizeof(T)*count)) > lim)
                    return TE_EOF;
                memcpy(value, base_const + pos, sizeof(T)*count);
                pos += sizeof(T)*count;
                return TE_Ok;
            }

            template<typename T>
            inline TAKErr MemBuffer2::put(const T &value) NOTHROWS
            {
                if (!base)
                    return TE_IllegalState;
                if ((pos + sizeof(T)) > lim)
                    return TE_EOF;
                *reinterpret_cast<T *>(base + pos) = value;
                pos += sizeof(T);
                return TE_Ok;
            }

            template<typename T>
            inline TAKErr MemBuffer2::put(const T *value, const std::size_t count) NOTHROWS
            {
                if (!base)
                    return TE_IllegalState;
                if ((pos + (count*sizeof(T))) > lim)
                    return TE_EOF;
                memcpy(base + pos, value, count * sizeof(T));
                pos += count * sizeof(T);
                return TE_Ok;
            }
        }
    }
}

#endif
