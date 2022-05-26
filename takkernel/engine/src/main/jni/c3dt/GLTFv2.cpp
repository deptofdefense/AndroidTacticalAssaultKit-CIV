#include "GLTF.h"

#include <memory>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include <tinygltf/tiny_gltf.h>

using namespace tinygltf;

Model *GLTF_loadV2(const unsigned char *binary, const std::size_t len, std::string baseDir)
{
    std::unique_ptr<Model> cmodel(new Model());
    TinyGLTF gltf;
    std::string err;
    std::string warn;
    if(gltf.LoadBinaryFromMemory(cmodel.get(), &err, &warn, binary, len, baseDir))
        return cmodel.release();
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_VERBOSE, "Cesium3DTiles", "err=%s warn=%s", err.c_str(), warn.c_str());
#endif
    return nullptr;
}
static bool loadExtBmp(GLTFBitmapLoader* loader, const std::string& baseUri, const std::string &uri) {
    std::string fullUri;
    if (baseUri.size() > 0 && baseUri[baseUri.size() - 1] == '/')
        fullUri = baseUri + uri;
    else
        fullUri = baseUri + "/" + uri;
    return loader->load(fullUri.c_str());
}
void GLTF_loadExtImagesV2(Model* model, GLTFBitmapLoader* loader, const std::string &baseUri)
{
    if (!model || !loader)
        return;

    for (size_t i = 0; i < model->images.size(); ++i) {
        tinygltf::Image& image = model->images[i];
        if (image.image.empty() && loadExtBmp(loader, baseUri, image.uri)) {
            image.image.insert(image.image.begin(), loader->getData(), loader->getDataEnd());
            image.width = loader->getWidth();
            image.height = loader->getHeight();
            image.bits = loader->getBits();
            image.pixel_type = loader->getPixelType();
            loader->unload();
        }
    }
}
void GLTF_destructV2(const tinygltf::Model *model)
{
    delete model;
}
