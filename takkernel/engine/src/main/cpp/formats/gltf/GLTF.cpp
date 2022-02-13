
#include "formats/gltf/GLTF.h"

using namespace TAK::Engine::Formats::GLTF;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Math;

namespace {
    TAKErr getVersion(uint32_t* result, DataInput2* input) NOTHROWS;
    TAKErr readFully(std::vector<uint8_t>& dst, DataInput2* input) NOTHROWS;
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_load(ScenePtr& scenePtr, DataInput2* input, const char* baseURI) NOTHROWS {

    std::vector<uint8_t> binary;
    TAKErr code = readFully(binary, input);
    if (code != TE_Ok)
        return code;
    if (binary.size() == 0)
        return TE_Unsupported;

    return GLTF_load(scenePtr, &binary.front(), binary.size(), baseURI);
}

TAKErr TAK::Engine::Formats::GLTF::GLTF_load(ScenePtr& scenePtr, const uint8_t *data, size_t size, const char* baseURI) NOTHROWS {

    MemoryInput2 memInput;
    memInput.open(data, size);
    uint32_t version = 0;
    TAKErr code = getVersion(&version, &memInput);
    if (code != TE_Ok)
        return code;

    switch (version) {
    case 1: code = GLTF_loadV1(scenePtr, data, size, baseURI); break;
    case 2: code = GLTF_loadV2(scenePtr, data, size, baseURI); break;
    default: code = TE_Unsupported; break;
    }

    return code;
}

namespace {
    TAKErr getVersion(uint32_t* result, DataInput2 *input) NOTHROWS {

        if (!result)
            return TE_InvalidArg;

        uint8_t magic[4] = { 0 };
        size_t numRead = 0;
        TAKErr code = input->read(magic, &numRead, 4);
        if (code != TE_Ok)
            return code;
        if (magic[0] != 'g' || magic[1] != 'l' ||
            magic[2] != 'T' || magic[3] != 'F')
            return TE_Unsupported;

        uint32_t version = 0;
        TAKEndian endian = input->getSourceEndian();
        input->setSourceEndian2(TE_LittleEndian);
        code = input->readInt((int32_t*)&version);
        input->setSourceEndian2(endian);
        if (code != TE_Ok)
            return TE_Unsupported;

        *result = version;
        return TE_Ok;
    }

    TAKErr readFully(std::vector<uint8_t>& dst, DataInput2 *input) NOTHROWS {

        int64_t inputLen = input->length();
        if (inputLen > 0) {
            dst.insert(dst.end(), static_cast<size_t>(inputLen), 0);
            size_t numRead = 0;
            TAKErr code = input->read(&dst[0], &numRead, static_cast<std::size_t>(inputLen));
            if (code != TE_Ok)
                return code;
            if (numRead != inputLen)
                return TE_IllegalState;
            return TE_Ok;
        }

        size_t len = 0;
        size_t numRead = 0;
        const size_t chunk = 1024;
        TAKErr code = TE_Ok;

        do {
            if (len == dst.size())
                dst.insert(dst.end(), chunk, 0);
            numRead = 0;
            code = input->read(&dst[len], &numRead, chunk);
            len += numRead;
            if (code != TE_Ok)
                break;
        } while (numRead > 0);

        return code == TE_EOF ? TE_Ok : code;
    }
}