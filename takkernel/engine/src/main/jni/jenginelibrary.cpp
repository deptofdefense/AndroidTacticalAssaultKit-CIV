#include "jenginelibrary.h"

#include <renderer/GLText2.h>

using namespace TAK::Engine::Renderer;

JNIEXPORT void JNICALL Java_com_atakmap_map_EngineLibrary_shutdownImpl
  (JNIEnv *env, jclass clazz)
{
    TextFormat2_setTextFormatFactory(std::shared_ptr<TextFormatFactory>(nullptr));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_EngineLibrary_initJOGL
        (JNIEnv *env, jclass clazz)
{
    glGetError();
}
