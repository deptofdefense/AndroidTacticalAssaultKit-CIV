#ifndef TAK_ENGINE_RENDERER_MODEL_GLMATERIAL_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLMATERIAL_H_INCLUDED

#include "model/Material.h"
#include "renderer/AsyncBitmapLoader2.h"
#include "renderer/GLTexture2.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/Tasking.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                class ENGINE_API GLMaterial
                {
                public :
                    enum Hints
                    {
                        UncompressedTexture = 0x1u,
                    };
                public :
                    GLMaterial(const TAK::Engine::Model::Material &subject) NOTHROWS;
                    GLMaterial(const TAK::Engine::Model::Material &subject, TAK::Engine::Renderer::GLTexture2Ptr &&texture, const std::size_t width, const std::size_t height) NOTHROWS;
                    GLMaterial(const TAK::Engine::Model::Material &subject, const TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> &pendingTexture, const unsigned int hints) NOTHROWS;

                    ~GLMaterial() NOTHROWS;
                private :
                    GLMaterial(const GLMaterial &);
                public :
                    const TAK::Engine::Model::Material &getSubject() const NOTHROWS;
                    TAK::Engine::Renderer::GLTexture2 *getTexture() NOTHROWS;
                    std::size_t getWidth() const NOTHROWS;
                    std::size_t getHeight() const NOTHROWS;
                    bool isTextured() const NOTHROWS;
                    bool isLoading() const NOTHROWS;
                private :
                    mutable Thread::Mutex mutex;
                    TAK::Engine::Model::Material subject;
                    TAK::Engine::Renderer::GLTexture2Ptr texture;
                    //RRR TAK::Engine::Renderer::AsyncBitmapLoader2::Task pendingTexture;
					TAK::Engine::Util::FutureTask<GLTexture2Ptr> futureTexture;

                    std::size_t width;
                    std::size_t height;
                    unsigned int hints;
                };
            }
        }
    }
}

#endif
