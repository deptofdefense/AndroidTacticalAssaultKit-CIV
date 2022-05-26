#include "GLTF.h"

#include <cstring>

#if defined(__sparcv9)
// Big endian
#else
#if (__BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__) || MINIZ_X86_OR_X64_CPU
#define TINYGLTF_LITTLE_ENDIAN 1
#endif
#endif

namespace {

    void swap4(unsigned int *val) {
#ifdef TINYGLTF_LITTLE_ENDIAN
        (void) val;
#else
        unsigned int tmp = *val;
      unsigned char *dst = reinterpret_cast<unsigned char *>(val);
      unsigned char *src = reinterpret_cast<unsigned char *>(&tmp);

      dst[0] = src[3];
      dst[1] = src[2];
      dst[2] = src[1];
      dst[3] = src[0];
#endif
    }
}

int GLTF_getVersion(const unsigned char *binary, const std::size_t len)
{
    // check version 1/2
    if (len < 8) {
        // Too short data size for glTF Binary.
        return false;
    }

    if (binary[0] == 'g' && binary[1] == 'l' && binary[2] == 'T' &&
        binary[3] == 'F') {
        // ok
    } else {
        // Invalid magic.
        return false;
    }

    unsigned int version;       // 4 bytes

    // @todo { Endian swap for big endian machine. }
    memcpy(&version, binary + 4, 4);
    swap4(&version);

    return version;
}
