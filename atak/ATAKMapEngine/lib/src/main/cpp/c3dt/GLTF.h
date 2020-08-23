#ifndef CESIUM3DTILES_GLTF_H_INCLUDED
#define CESIUM3DTILES_GLTF_H_INCLUDED

#include <cstdlib>
#include <string>

#include "tinygltf_decls.h"

/**
 * This class is NOT thread-safe; only one bitmap may be loaded at a time per instance.
 */
class GLTFBitmapLoader {
public:
    virtual bool load(const char *uri) = 0;
    virtual void unload() = 0;

    inline int getWidth() const { return width; }
    inline int getHeight() const { return height; }
    inline int getBits() const { return bits; }
    inline int getPixelType() const { return pixel_type; }
    inline const unsigned char *getData() const { return begin; }
    inline const unsigned char* getDataEnd() const { return end; }

protected:
    int width, height, bits, pixel_type, component;
    const unsigned char *begin, *end;
};

int GLTF_getVersion(const unsigned char *binary, const std::size_t len);
// Version 1
tinygltfloader::Scene *GLTF_loadV1(const unsigned char *binary, const std::size_t len, std::string baseDir);
void GLTF_destructV1(const tinygltfloader::Scene *scene);
void GLTF_loadExtImagesV1(tinygltfloader::Scene *scene, GLTFBitmapLoader* loader, const std::string &baseUri);
// Version 2
tinygltf::Model *GLTF_loadV2(const unsigned char *binary, const std::size_t len, std::string baseDir);
void GLTF_loadExtImagesV2(tinygltf::Model* model, GLTFBitmapLoader *loader, const std::string &baseUri);
void GLTF_destructV2(const tinygltf::Model *model);

#endif
