#include <cstdlib>
#include <string>

#include "model/Material.h"
#include "port/StringBuilder.h"
#include <string>

#include "port/StringBuilder.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

Material::Material() NOTHROWS
    : propertyType(TEPT_Diffuse), color(0xffffffff), textureCoordIndex(InvalidTextureCoordIndex), twoSided(true)
{}

TAKErr TAK::Engine::Model::Material_setBufferIndexTextureURI(Material* material, size_t bufferIndex) NOTHROWS {
    if (!material)
        return TE_InvalidArg;

    StringBuilder sb;
    TAKErr code = StringBuilder_combine(sb, "meshbuffer:", bufferIndex);
    TE_CHECKRETURN_CODE(code);

    material->textureUri = sb.c_str();
    return TE_Ok;
}

ENGINE_API TAKErr TAK::Engine::Model::Material_getBufferIndexTextureURI(size_t* bufferIndex, const char* URI) NOTHROWS {
    if (!URI || !bufferIndex)
        return TE_InvalidArg;

    if (strncmp(URI, "meshbuffer:", 11) != 0)
        return TE_IllegalState;

    const char *numStart = URI + 11;
    char *end = nullptr;

    // strto* allows for leading whitespace
    size_t parseResult = strtol(numStart, &end, 10);

    // nothing was parsed?
    if (numStart == end)
        return TE_InvalidArg;

    *bufferIndex = parseResult;
    return TE_Ok;
}
