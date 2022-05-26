#ifndef TAKKERNEL_LAS_H
#define TAKKERNEL_LAS_H

#include "db/Database2.h"
#include "feature/Envelope2.h"
#include "model/SceneInfo.h"
#include "util/Error.h"
#include "util/DataInput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace LAS {
    enum class LAS_Method {
        SQLITE,
        CHUNK,
        CHUNK_PARALLEL,
        DEFAULT,
    };
    ENGINE_API Util::TAKErr LAS_createTiles(const char* lasFilePath, const char* outputPath, std::size_t maxPoints, LAS_Method method = LAS_Method::DEFAULT) NOTHROWS;
}  // namespace LAS
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK
#endif  // TAKKERNEL_LAS_H
