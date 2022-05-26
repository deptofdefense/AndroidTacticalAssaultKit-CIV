#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_VERTEXDATA_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_VERTEXDATA_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

#include <cstdint>

#include "math/Vector4.h"
#include "util/DataInput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

class VertexData {
  public:
    const int vertexCount;
    const std::unique_ptr<int16_t[]> u;
    const std::unique_ptr<int16_t[]> v;
    const std::unique_ptr<int16_t[]> height;
    const int totalSize;

    void get(Math::Vector4<double> *result, int index) const;

  private:
    VertexData(int vertexCount, std::unique_ptr<int16_t[]> u, std::unique_ptr<int16_t[]> v, std::unique_ptr<int16_t[]> height);

    friend Util::TAKErr VertexData_deserialize(std::unique_ptr<VertexData> &result, Util::DataInput2 &input);
};

Util::TAKErr VertexData_deserialize(std::unique_ptr<VertexData> &result, Util::DataInput2 &input);

}  // namespace Impl
}  // namespace QuantizedMesh
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK


#endif
