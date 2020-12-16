#ifndef TAK_ENGINE_FORMATS_EGM_EGM96_H_INCLUDED
#define TAK_ENGINE_FORMATS_EGM_EGM96_H_INCLUDED

#include "core/GeoPoint2.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace EGM {
                /**
                 * Computes EGM96 geoid offsets.
                 * <p/>
                 * A file with the offset grid must be passed to the constructor. This file must have 721 rows of
                 * 1440 2-byte integer values. Each row corresponding to a latitude, with the first row
                 * corresponding to +90 degrees (90 North). The integer values must be in centimeters.
                 * <p/>   Heavily based on the Nasa World Wind file of the same name.
                 *
                 * @author Andrew Scally
                 */
                class ENGINE_API EGM96
                {
                public :
                    EGM96() NOTHROWS;
                    ~EGM96() NOTHROWS;
                private :
                    EGM96(const EGM96 &);
                public :
                    Util::TAKErr open(const uint8_t *data, const std::size_t dataLen) NOTHROWS;
                    Util::TAKErr getHeight(double *value, const Core::GeoPoint2 &location) NOTHROWS;
                private :
                    Util::array_ptr<uint8_t> model;
                    std::size_t size;
                };
            }
        }
    }
}

#endif
