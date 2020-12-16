#ifndef TAK_ENGINE_UTIL_DATAOUTPUT_H_INCLUDED
#define TAK_ENGINE_UTIL_DATAOUTPUT_H_INCLUDED

#include <cstddef>
#include <cstdint>

#include "port/Platform.h"
#include "util/Error.h"
#include "util/IO2.h"
#include "util/Memory.h"
#include "util/IO_Decls.h"

namespace atakmap {
    namespace util {
        template<class T>
        class MemBufferT;
    }
}

namespace TAK {
    namespace Engine {
        namespace Util {

            class ENGINE_API DataOutput2
            {
            public:
                DataOutput2();
                virtual ~DataOutput2();
            public :
                virtual TAKErr close() NOTHROWS = 0;

                /*
                * TE_Ok is returned on success.
                */
                virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS = 0;

                /*
                * Writes exactly one byte to the sink.
                * TE_Ok is returned on success
                */
                virtual TAKErr writeByte(const uint8_t value) NOTHROWS = 0;

                /*
                * Advances the write position precisely 'n' bytes.
                */
                virtual TAKErr skip(const std::size_t n) NOTHROWS;


                /*
                * Reads 4 bytes from the source and converts them to a single precision
                * floating point value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr writeFloat(const float value) NOTHROWS;

                /*
                * Reads 4 bytes from the source and converts them to an integer
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr writeInt(const int32_t value) NOTHROWS;

                /*
                * Reads 2 bytes from the source and converts them to an int16_t
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr writeShort(const int16_t value) NOTHROWS;

                /*
                * Reads 8 bytes from the source and converts them to an integer
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr writeLong(const int64_t value) NOTHROWS;

                /*
                * Reads 8 bytes from the source and converts them to a double precision
                * floating point value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr writeDouble(const double value) NOTHROWS;

                /*
                * Reads 'len' bytes from the source, constructing a string from the characters.
                * No character validity checking is done on the read bytes; this is basically
                * the same as readFully() but instead of receiving an array, it allocates and
                * returns a string.
                */
                TAKErr writeString(const char *value) NOTHROWS;

                /** @deprecated use setSourceEndian2 */
                void setSourceEndian(const atakmap::util::Endian e) NOTHROWS;

                /*
                * Sets the endianness of binary quantities (int, short, double, float, etc)
                * in the source input.
                * If the source endianness is contrary to the native platform endianness,
                * binary quantities will be byte swapped appropriately.
                * This may be changed as desired in between read calls.
                * Default source endian value is assumed same as platform endianness.
                */
                void setSourceEndian2(const TAKEndian e) NOTHROWS;

            private:
                bool swappingEndian;
            };

            class ENGINE_API FileOutput2 : public DataOutput2
            {
            public:
                FileOutput2() NOTHROWS;
                virtual ~FileOutput2() NOTHROWS;
                virtual TAKErr open(const char *filename) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;

                virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS override;
                virtual TAKErr writeByte(const uint8_t value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;

                TAKErr seek(int64_t offset) NOTHROWS;
                TAKErr tell(int64_t *value) NOTHROWS;
            private:
                FILE *f;
            };

            typedef std::unique_ptr<DataOutput2, void(*)(const DataOutput2 *)> DataOutput2Ptr;

            class ENGINE_API MemoryOutput2 : public DataOutput2
            {
            public:
                MemoryOutput2() NOTHROWS;
                virtual ~MemoryOutput2() NOTHROWS;

                virtual TAKErr open(uint8_t *bytes, const std::size_t len) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;
                virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS override;
                virtual TAKErr writeByte(const uint8_t value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;

                virtual TAKErr remaining(std::size_t *value) NOTHROWS;
            private:
                uint8_t *bytes;
                std::size_t curOffset;
                std::size_t totalLen;
            };

            class ENGINE_API ByteBufferOutput2 : public DataOutput2
            {
            public:
                ByteBufferOutput2() NOTHROWS;
                virtual ~ByteBufferOutput2() NOTHROWS;

                virtual TAKErr open(atakmap::util::MemBufferT<uint8_t> *buffer) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;

                virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS override;
                virtual TAKErr writeByte(const uint8_t value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;

            private:
                atakmap::util::MemBufferT<uint8_t> *buffer;
            };

            class ENGINE_API DynamicOutput : public DataOutput2
            {
            public:
                DynamicOutput() NOTHROWS;
                virtual ~DynamicOutput() NOTHROWS;

                virtual TAKErr open(const std::size_t capacity) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;

                virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS override;
                virtual TAKErr writeByte(const uint8_t value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;

                TAKErr get(const uint8_t **buf, std::size_t *len) NOTHROWS;
                TAKErr reset() NOTHROWS;
            private:
                array_ptr<uint8_t> buffer;
                uint8_t *writePtr;
                std::size_t capacity;
            };
        }
    }
}

#endif
