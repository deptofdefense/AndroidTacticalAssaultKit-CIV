
#include "model/Material.h"

using namespace TAK::Engine::Model;

Material::Material() NOTHROWS
    : propertyType(TEPT_Diffuse), color(0xffffffff), textureCoordIndex(InvalidTextureCoordIndex), twoSided(true)
{}
