#ifndef TAK_ENGINE_FEATURE_ENVELOPE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_ENVELOPE2_H_INCLUDED

#include <memory>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API Envelope2
            {
            public :
                /**
                 * Creates a new, empty Envelope2. All fields are initialized
                 * to 'NAN'.
                 */
                Envelope2() NOTHROWS;
                /**
                 * Creates a new Envelope2, defining x and y bounds. Bounds for
                 * z are initialized to '0.0'.
                 */
                Envelope2(const double minX, const double minY, const double maxX, const double maxY) NOTHROWS;
                /**
                 * Creates a new Envelope2.
                 */
                Envelope2(const double minX, const double minY, const double minZ, const double maxX, const double maxY, const double maxZ) NOTHROWS;

                bool operator==(const Envelope2 &other) const NOTHROWS;

            public :
                double minX;
                double minY;
                double minZ;
                double maxX;
                double maxY;
                double maxZ;
            };

            typedef std::unique_ptr<Envelope2, void(*)(const Envelope2 *)> Envelope2Ptr;
            typedef std::unique_ptr<const Envelope2, void(*)(const Envelope2 *)> Envelope2Ptr_const;
        }
    }
}

#endif
