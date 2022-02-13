#ifndef TAK_ENGINE_UTIL_DATAINPUT2_H_INCLUDED
#define TAK_ENGINE_UTIL_DATAINPUT2_H_INCLUDED

#include <cstddef>
#include <cstdint>
#include <memory>

#include "port/Platform.h"
#include "util/Error.h"
#include "util/IO2.h"
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

            class ENGINE_API DataInput2
            {
            public:
                DataInput2();
            protected :
                virtual ~DataInput2();
            public:
                virtual TAKErr close() NOTHROWS = 0;

                /*
                * TE_Ok is returned on success.
                */
                virtual TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS = 0;

                /*
                * Writes exactly one byte to the sink.
                * TE_Ok is returned on success, TE_EOF returned on EOF
                */
                virtual TAKErr readByte(uint8_t *value) NOTHROWS = 0;

                /*
                * Advances the write position precisely 'n' bytes.
                */
                virtual TAKErr skip(const std::size_t n) NOTHROWS;

                /**
                 * Returns the content length or less than zero if not known.
                 */
                virtual int64_t length() const NOTHROWS;

                /*
                * Reads 4 bytes from the source and converts them to a single precision
                * floating point value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr readFloat(float *value) NOTHROWS;

                /*
                * Reads 4 bytes from the source and converts them to an integer
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr readInt(int32_t *value) NOTHROWS;

                /*
                * Reads 2 bytes from the source and converts them to an int16_t
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr readShort(int16_t *value) NOTHROWS;

                /*
                * Reads 8 bytes from the source and converts them to an integer
                * value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr readLong(int64_t *value) NOTHROWS;

                /*
                * Reads 8 bytes from the source and converts them to a double precision
                * floating point value, possibly swapping bytes depending on the configured
                * source endianness.
                */
                TAKErr readDouble(double *value) NOTHROWS;

                /*
                * Reads 'len' bytes from the source, constructing a string from the characters.
                * No character validity checking is done on the read bytes; this is basically
                * the same as readFully() but instead of receiving an array, it allocates and
                * returns a string.
                */
                TAKErr readString(char *str, std::size_t *numRead, const std::size_t len) NOTHROWS;

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

                /**
                 * Returns the endian interpretation of binary quantities read
                 * from the source stream.
                 */
                TAKEndian getSourceEndian() const NOTHROWS;

            private:
                bool swappingEndian;
            };

            typedef std::unique_ptr<DataInput2, void(*)(const DataInput2 *)> DataInput2Ptr;

            class ENGINE_API FileInput2 : public DataInput2
            {
            public:
                FileInput2() NOTHROWS;
                virtual ~FileInput2() NOTHROWS;
                virtual TAKErr open(const char *filename) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;

                virtual TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS override;
                virtual TAKErr readByte(uint8_t *value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;
                virtual int64_t length() const NOTHROWS override;

                TAKErr seek(int64_t offset) NOTHROWS;
                TAKErr tell(int64_t *value) NOTHROWS;
            private :
                TAKErr closeImpl() NOTHROWS;
            private:
                FILE *f_;
                int64_t len_;
                Port::String name_;
            };


            class ENGINE_API MemoryInput2 : public DataInput2
            {
            public:
                MemoryInput2() NOTHROWS;
                virtual ~MemoryInput2() NOTHROWS;

                virtual TAKErr open(const uint8_t *bytes, const std::size_t len) NOTHROWS;
                virtual TAKErr open(std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> &&bytes, const std::size_t len) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;
                virtual TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS override;
                virtual TAKErr readByte(uint8_t *value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;
                virtual int64_t length() const NOTHROWS override;

                virtual TAKErr remaining(std::size_t *value) NOTHROWS;

                virtual TAKErr reset() NOTHROWS;
            private:
                std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> bytes;
                std::size_t curOffset;
                std::size_t totalLen;
            };

            class ENGINE_API ByteBufferInput2 : public DataInput2
            {
            public:
                ByteBufferInput2() NOTHROWS;
                virtual ~ByteBufferInput2() NOTHROWS;

                virtual TAKErr open(atakmap::util::MemBufferT<uint8_t> *buffer) NOTHROWS;
                virtual TAKErr close() NOTHROWS override;

                virtual TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS override;
                virtual TAKErr readByte(uint8_t *value) NOTHROWS override;
                virtual TAKErr skip(const std::size_t n) NOTHROWS override;
                virtual int64_t length() const NOTHROWS override;


            private:
                atakmap::util::MemBufferT<uint8_t> *buffer_;
                int64_t len_;
            };
        }
    }
}

#endif
