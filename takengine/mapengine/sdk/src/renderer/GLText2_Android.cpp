#include "renderer/GLText2.h"
#include "thread/RWMutex.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    RWMutex tf_factory_mutex_(TERW_Fair);
    std::shared_ptr<TextFormatFactory> tf_factory_;
}

TAKErr TAK::Engine::Renderer::TextFormat2_createDefaultSystemTextFormat(TextFormat2Ptr &value, const float textSize) NOTHROWS
{
    return TextFormat2_createTextFormat(value, TextFormatParams(textSize));
}
TAKErr TAK::Engine::Renderer::TextFormat2_createTextFormat(TextFormat2Ptr &value, const TextFormatParams &params) NOTHROWS
{
    {
        ReadLock lock(tf_factory_mutex_);
        if (tf_factory_)
            return tf_factory_->createTextFormat(value, params);
    }

    return TE_IllegalState;
}

TextFormatFactory::~TextFormatFactory() NOTHROWS
{}

TAKErr TAK::Engine::Renderer::TextFormat2_setTextFormatFactory(const std::shared_ptr<TAK::Engine::Renderer::TextFormatFactory>& factory) NOTHROWS
{
    {
        WriteLock lock(tf_factory_mutex_);
        tf_factory_ = factory;
    }
    return TE_Ok;
}
