#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_INDICES_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_INDICES_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

#include "util/DataInput2.h"

#include <vector>

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

    /**
     * Generic struct for 16/32-bit indices
     */
    class Indices {
    public:

        int get(int i) const;
        int64_t getTotalSize() const;
        bool getIs32Bit() const;
        int getLength() const;
        void sort(const int16_t *vertexDataU, const int16_t *vertexDataV, bool isNorthSouth);
        
    private:
        const int length;
        const bool is32bit;

        // Only one of these is used, depending on is32bit value
        std::vector<int16_t> indices16;
        std::vector<int32_t> indices32;

        Indices(int length, bool is32bit);

        void put(int i, int value);

        friend Util::TAKErr Indices_deserialize(std::unique_ptr<Indices> &result, int length, bool is32bit, bool isCompressed, Util::DataInput2 &input);
    };

    Util::TAKErr Indices_deserialize(std::unique_ptr<Indices> &result, int length, bool is32bit, bool isCompressed, Util::DataInput2 &input);
}
}
}
}
}

#endif
