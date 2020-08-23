#ifndef TAK_ENGINE_MODEL_MATERIAL_H_INCLUDED
#define TAK_ENGINE_MODEL_MATERIAL_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            struct ENGINE_API Material
            {
                Material() NOTHROWS;

                /**
                 * Material property type (currently only diffuse materials are supported)
                 */
                enum PropertyType
                {
                    TEPT_Diffuse,
                };

                enum 
                {
                    InvalidTextureCoordIndex = -1
                };

                /**
                 * The texture file URI
                 */
                Port::String textureUri;

                /**
                 * Texture property type (currently only TEPT_Diffuse is supported)
                 */
                PropertyType propertyType;
                
                /**
                 * The packed color of the texture
                 */
                uint32_t color;

                /**
                 * The index of the texture coordinates
                 */
                int textureCoordIndex;

                bool twoSided : 1;
            };

            ENGINE_API Util::TAKErr Material_setBufferIndexTextureURI(Material *material, size_t bufferIndex) NOTHROWS;
            ENGINE_API Util::TAKErr Material_getBufferIndexTextureURI(size_t *bufferIndex, const char *URI) NOTHROWS;
        }
    }
}
#endif
