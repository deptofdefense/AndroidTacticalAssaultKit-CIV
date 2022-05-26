#include "GLTF.h"

#include <memory>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#define tinygltf tinygltfloader
#define TAK_TINYGLTFLOADER_MODS
#include <tinygltfloader/tiny_gltf_loader.h>
#undef tinygltf

using namespace tinygltfloader;

tinygltfloader::Scene *GLTF_loadV1(const unsigned char *binary, const std::size_t len, std::string baseDir)
{
    std::unique_ptr<Scene> cmodel(new Scene());
    TinyGLTFLoader gltf;
    std::string err;
    std::string warn;
    if(gltf.LoadBinaryFromMemory(cmodel.get(), &err, binary, len, baseDir))
        return cmodel.release();
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_VERBOSE, "Cesium3DTiles", "err=%s warn=%s", err.c_str(), warn.c_str());
#endif
    return nullptr;
}
static bool loadExtBmp(GLTFBitmapLoader* loader, const std::string& baseUri, const std::string& uri) {
    std::string fullUri;
    if (baseUri.size() > 0 && baseUri[baseUri.size() - 1] == '/')
        fullUri = baseUri + uri;
    else
        fullUri = baseUri + "/" + uri;
    return loader->load(fullUri.c_str());
}
void GLTF_loadExtImagesV1(tinygltfloader::Scene* scene, GLTFBitmapLoader* loader, const std::string &baseUri)
{
    if (!scene || !loader)
        return;
#ifdef TAK_TINYGLTFLOADER_MODS
    for (auto it = scene->images.begin(); it != scene->images.end(); it++) {
        tinygltfloader::Image &image = it->second;
        if (image.image.empty() && loadExtBmp(loader, baseUri, image.uri)) {
            image.image.insert(image.image.begin(), loader->getData(), loader->getDataEnd());
            image.width = loader->getWidth();
            image.height = loader->getHeight();
            //image.bits = loader->getBits();
            loader->unload();
        }
    }
#endif
}
void GLTF_destructV1(const tinygltfloader::Scene *scene)
{
    delete scene;
}
