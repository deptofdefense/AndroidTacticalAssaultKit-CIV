////============================================================================
////
////    FILE:           IO.h
////
////    DESCRIPTION:    Definition of IO_Error exception class and declaration
////                    of convenience utility functions for encoding and
////                    decoding.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 16, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_UTIL_IO_H_INCLUDED
#define ATAKMAP_UTIL_IO_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <istream>
#include <ostream>
#include <sstream>
#include <stdexcept>
#include <stdint.h>
#include <string>
#include <sys/stat.h>
#include <vector>

// XXX- fixes platform detection on iOS
#if defined(__APPLE__) && !defined(UNIX)
#define UNIX
// st_mtimensec is missing on iOS
#ifndef st_mtimensec
#define st_mtimensec st_mtimespec.tv_nsec
#endif
#endif

#include "port/Platform.h"
#include "port/String.h"
#include "util/IO_Decls.h"

#ifdef __APPLE__
// This isn't available in darwin with _C_POSIX_SOURCE set
extern "C" char *mkdtemp(char *);
#endif

#include "util/MemBuffer.h"

////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace util                          // Open util namespace.
{


///
///  Constants.
///
#ifdef BIG_ENDIAN
#undef BIG_ENDIAN
#endif
#ifdef LITTLE_ENDIAN
#undef LITTLE_ENDIAN
#endif


#if __BIG_ENDIAN__ || __ARMEB__ || __THUMBEB__ \
  || defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
    const Endian PlatformEndian = BIG_ENDIAN;
#else
    const Endian PlatformEndian = LITTLE_ENDIAN;
#endif


enum
  {
#if __BIG_ENDIAN__ || __ARMEB__ || __THUMBEB__ \
  || defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
    ENDIAN_BYTE = 0x00,
#else
    ENDIAN_BYTE = 0x01,
#endif
    WKB_HEADER_SIZE = sizeof (char) + sizeof (int)
  };



///=============================================================================
///
///  struct atakmap::util::IO_Error
///
///     Exception class for IO errors.
///
///=============================================================================


class ENGINE_API DataInput {
public:
    static const size_t EndOfStream = SIZE_MAX; //-XXX

    DataInput();
    virtual ~DataInput();
    virtual void close() throw (IO_Error) = 0;

    /*
     * Read at most 'len' bytes from the source and store them into 'buf', which
     * must be a valid buffer of at least 'len' bytes.  Returns the number of bytes
     * actually read. Always reads at least one byte or throws an error, never
     * returns zero except in the specific case that 'len' is zero in which case
     * zero is returned and nothing else is done.
     * If EOF is encountered, the bytes read up to EOF are returned.
     * If this is zero bytes, an IO_Error is generated to signal EOF.
     * IO_Error is also thrown for any IO error that occurs.
     */
    virtual size_t read(uint8_t *buf, size_t len) throw (IO_Error) = 0;

    /*
     * Read exactly one byte from the source and return it.  If this
     * cannot be done, IO_Error is generated.
     */
    virtual uint8_t readByte() throw (IO_Error) = 0;

    /*
     * Read precisely 'len' bytes, storing them in 'buf'.  If EOF or other error
     * is encountered before all 'len' bytes can be read, IO_Error is thrown.
     */
    virtual size_t readFully(uint8_t *buf, size_t len) throw (IO_Error);

    /*
     * Discard precisely 'n' bytes from the source, advancing the read position by
     * that same amount.  If EOF or other error is encountered before all 'n'
     * bytes can be skipped, IO_Error is thrown.
     */
    virtual size_t skip(size_t n) throw (IO_Error);

    /*
     * Read 'len' bytes and interpret them as an ASCII character string representing
     * decimal numbers with an optional + or - as the first character. Returns
     * the signed integer version of the decimal number string.
     * Throws IO_Error for IO issues, out_of_range if the number represented
     * by the string is larger or smaller than can be represented by an
     * integer or if any portion of the string is not a valid number.
     */
    int readAsciiInt(size_t len) throw (IO_Error, std::out_of_range);

    /*
    * Read 'len' bytes and interpret them as an ASCII character string representing
    * decimal numbers with an optional + or - as the first character. The string is
    * trimmed of whitespace prior to parsing. Returns
    * the signed double version of the decimal number string.
    * Throws IO_Error for IO issues, out_of_range
    * if any portion of the string is not a valid number.
    */
    double readAsciiDouble(size_t len) throw (IO_Error, std::out_of_range);

    /*
     * Reads 4 bytes from the source and converts them to a single precision
     * floating point value, possibly swapping bytes depending on the configured
     * source endianness.
     */
    float readFloat() throw (IO_Error);

    /*
     * Reads 4 bytes from the source and converts them to an integer
     * value, possibly swapping bytes depending on the configured
     * source endianness.
     */
    int32_t readInt() throw (IO_Error);

    /*
     * Reads 2 bytes from the source and converts them to an int16_t
     * value, possibly swapping bytes depending on the configured
     * source endianness.
     */
    int16_t readShort() throw (IO_Error);

    /*
    * Reads 8 bytes from the source and converts them to an integer
    * value, possibly swapping bytes depending on the configured
    * source endianness.
    */
    int64_t readLong() throw (IO_Error);

    /*
     * Reads 8 bytes from the source and converts them to a double precision
     * floating point value, possibly swapping bytes depending on the configured
     * source endianness.
     */
    double readDouble() throw (IO_Error);

    /*
     * Reads 'len' bytes from the source, constructing a string from the characters.
     * No character validity checking is done on the read bytes; this is basically
     * the same as readFully() but instead of receiving an array, it allocates and
     * returns a string.
     */
    std::string readString(size_t len) throw (IO_Error);

    /*
     * Sets the endianness of binary quantities (int, short, double, float, etc)
     * in the source input.
     * If the source endianness is contrary to the native platform endianness,
     * binary quantities will be byte swapped appropriately.
     * This may be changed as desired in between read calls.
     * Default source endian value is assumed same as platform endianness.
     */
    void setSourceEndian(Endian e);

private:
    bool swappingEndian;
};


class ENGINE_API FileInput : public DataInput {
public:
    FileInput();
    virtual ~FileInput();
    virtual void open(const char *filename) throw (IO_Error);
    virtual void close() throw (IO_Error) override;

    virtual size_t read(uint8_t *buf, size_t len) throw (IO_Error) override;
    virtual uint8_t readByte() throw (IO_Error) override;
    virtual size_t skip(size_t n) throw (IO_Error) override;

    void seek(int64_t offset) throw (IO_Error);
    int64_t tell() throw (IO_Error);
private:
    FILE *f;
};


class MemoryInput : public DataInput {
public:
    MemoryInput();
    virtual ~MemoryInput();
    // bytes assumed valid for duration of this object; does not take ownership
    virtual void open(const uint8_t *bytes, size_t len);
    virtual void close() throw (IO_Error) override;

    virtual size_t read(uint8_t *buf, size_t len) throw (IO_Error) override;
    virtual uint8_t readByte() throw (IO_Error) override;
    virtual size_t skip(size_t n) throw (IO_Error) override;

private:
    const uint8_t *bytes;
    size_t curOffset;
    size_t totalLen;
};

class ENGINE_API RewindDataInput : public atakmap::util::DataInput {
public:
    RewindDataInput(atakmap::util::DataInput &input);
    virtual ~RewindDataInput();
    virtual void close() throw (IO_Error) override;
    virtual size_t read(uint8_t *buf, size_t len) throw (IO_Error) override;
    virtual uint8_t readByte() throw (IO_Error) override;
    void rewind();

private:
    atakmap::util::DataInput &innerInput;
    std::vector<uint8_t> rewindCache;
    size_t readOffset;
};

class ByteBufferInput : public DataInput {
public:
    ByteBufferInput(atakmap::util::MemBufferT<uint8_t> *buffer);
    virtual ~ByteBufferInput();
    // bytes assumed valid for duration of this object; does not take ownership
    virtual void close() throw (IO_Error) override;

    virtual size_t read(uint8_t *buf, size_t len) throw (IO_Error) override;
    virtual uint8_t readByte() throw (IO_Error) override;
    virtual size_t skip(size_t n) throw (IO_Error) override;

private:
    atakmap::util::MemBufferT<uint8_t> *buffer;
};


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

ENGINE_API
char*
createTempDir(const char* parentPath,
    const char* prefix,
    const char* suffix = ".tmp");

ENGINE_API std::string trimASCII(const std::string &src);

ENGINE_API int parseASCIIInt(const char *src) throw (std::out_of_range);

ENGINE_API double parseASCIIDouble(const char *src) throw (std::out_of_range);

ENGINE_API
std::string
toLowerCase(const std::string &s);

ENGINE_API
std::string
toUpperCase(const std::string &s);

const std::string whitespaceDelims(" \t\n\x0B\f\r");

ENGINE_API
std::vector<std::string>
splitString(const std::string &str, const std::string &delimiters = " ", bool trimEmpty = false);

ENGINE_API
std::string
getDirectoryForFile(const char *filepath);

ENGINE_API
std::string
getFileName(const char *filepath);

ENGINE_API
std::string
getFileAsAbsolute(const char *file);

// Returns filePath as a relative path to basePath
ENGINE_API
std::string
computeRelativePath(const char *basePath, const char *filePath);

ENGINE_API bool
deletePath(const char* path);

ENGINE_API typedef bool (*DirContentsAcceptFilter)(const std::string &file);
ENGINE_API
std::vector<std::string>
getDirContents(const char *path, DirContentsAcceptFilter filter = nullptr);

ENGINE_API unsigned long
getFileCount(const char* path);


ENGINE_API unsigned long
getFileSize(const char* path);


ENGINE_API unsigned long
getLastModified(const char* path);

ENGINE_API
bool
isDirectory(const char* path);

ENGINE_API
bool
isFile(const char* path);

ENGINE_API
bool
pathExists(const char* path);

ENGINE_API
bool
removeDir(const char* dirPath);

ENGINE_API
bool
createDir(const char* dirPath);

///
///  Convenience functions for encode/decode implementations.
///


//
// Read a value of the template type from the supplied stream.
//
template <typename T>
inline
T
read (std::istream& strm)
  {
    T val;

    strm.read (reinterpret_cast<char*> (&val), sizeof (T));

    return val;
  }

template <typename T>
inline
T
read (std::istream& strm,
      bool swapEndian)
  {
    union
      {
        T val;
        char bytes[sizeof (T)];
      };

    if (!swapEndian)
      {
        strm.read (reinterpret_cast<char*> (&val), sizeof (T));
      }
    else
      {
        char* p (bytes + sizeof (T));

        while (p > bytes)
          {
            *--p = strm.get ();
          }
      }
    return val;
  }


//
// Read a string in MUTF-8 form from the supplied stream.
//
ENGINE_API TAK::Engine::Port::String
readUTF(std::istream& strm);


//
// Write the supplied value to the supplied stream.
//
template <typename T>
inline
std::ostream&
write (std::ostream& strm,
       T val)
  { return strm.write (reinterpret_cast<char*> (&val), sizeof (T)); }


//
// Write the supplied string in MUTF-8 to the supplied stream.
//
ENGINE_API std::ostream&
writeUTF(std::ostream& strm,
    const char* begin);

}                                       // Close util namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_UTIL_IO_H_INCLUDED
