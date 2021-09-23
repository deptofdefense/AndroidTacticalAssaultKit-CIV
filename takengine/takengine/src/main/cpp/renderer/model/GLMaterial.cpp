#include <memory>
#include "renderer/model/GLMaterial.h"
#include "renderer/GLWorkers.h"

#include "thread/Lock.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
	TAKErr loadTexture(GLTexture2Ptr &texture, const std::shared_ptr<Bitmap2> &bitmap) NOTHROWS {
		texture = GLTexture2Ptr(new GLTexture2(static_cast<int>(bitmap->getWidth()), static_cast<int>(bitmap->getHeight()), bitmap->getFormat()), Memory_deleter_const<GLTexture2>);
		TAKErr code = texture->load(*bitmap);
		if (code != TE_Ok) {
			texture.reset();
		}
		return code;
	}

	TAKErr loadCompressedTexture(GLTexture2Ptr &texture, const std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)> &data) NOTHROWS {
		return GLTexture2_createCompressedTexture(texture, *data);
	}

	TAKErr compressTextureData(std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)> &data, const std::shared_ptr<Bitmap2> &bitmap) NOTHROWS {
		return GLTexture2_createCompressedTextureData(data, *bitmap);
	}

	TAK::Engine::Util::FutureTask<GLTexture2Ptr> handleTextureHints(TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>> textureTask, unsigned int hints) {
		
		if (hints & GLMaterial::UncompressedTexture)
			return textureTask
				.thenOn(GLWorkers_resourceLoad(), loadTexture);
		
		return textureTask
			.thenOn(GeneralWorkers_single(), compressTextureData)
			.thenOn(GLWorkers_resourceLoad(), loadCompressedTexture);
	}
}

GLMaterial::GLMaterial(const Material &subject_) NOTHROWS :
    subject(subject_),
    texture(nullptr, nullptr),
    width(0u),
    height(0u),
    hints(0u)
{}
GLMaterial::GLMaterial(const Material &subject_, GLTexture2Ptr &&texture_, const std::size_t width_, const std::size_t height_) NOTHROWS :
    subject(subject_),
    texture(std::move(texture_)),
    width(width_),
    height(height_),
    hints(0u)
{}
GLMaterial::GLMaterial(const Material &subject_, const TAK::Engine::Util::FutureTask<std::shared_ptr<Bitmap2>>&pendingTexture_, const unsigned int hints_) NOTHROWS :
    subject(subject_),
	futureTexture(handleTextureHints(pendingTexture_, hints_)),
    texture(nullptr, nullptr),
    width(0u),
    height(0u),
    hints(hints_)
{}

GLMaterial::~GLMaterial() NOTHROWS
{
	this->futureTexture.cancel();
}

const Material &GLMaterial::getSubject() const NOTHROWS
{
    return subject;
}
GLTexture2 *GLMaterial::getTexture() NOTHROWS
{
    TAKErr code(TE_Ok);
    do {
        Lock lock(mutex);
        code = lock.status;
        if (code != TE_Ok) TE_CHECKBREAK_CODE(code);

        if (this->texture.get()) break;

        bool textureReady = false;
        this->futureTexture.isReady(textureReady);
        if (!textureReady) break;

        TAKErr texErr = TE_Ok;
        code = this->futureTexture.await(this->texture, texErr);
        TE_CHECKBREAK_CODE(code);

        if (this->texture.get()) {
            this->width = this->texture->getTexWidth();
            this->height = this->texture->getTexHeight();
        }

        this->futureTexture.detach();
    } while (false);

    return texture.get();
}
std::size_t GLMaterial::getWidth() const NOTHROWS
{
    return width;
}
std::size_t GLMaterial::getHeight() const NOTHROWS
{
    return height;
}
bool GLMaterial::isTextured() const NOTHROWS
{
    return !!this->subject.textureUri;
}
bool GLMaterial::isLoading() const NOTHROWS
{
    TAKErr code(TE_Ok);
    do {
        Lock lock(mutex);
        code = lock.status;
        if (code != TE_Ok)
            TE_CHECKBREAK_CODE(code);
		
		if (this->texture.get())
			return false;

		bool ready = false;
		TAKErr resultCode = TE_Ok;
		code = this->futureTexture.isReady(ready, &resultCode);
		TE_CHECKBREAK_CODE(code);

		if (!ready && resultCode == TE_Ok)
			return true;
    } while (false);
    return false;
}
