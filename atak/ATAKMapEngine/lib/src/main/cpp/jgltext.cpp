#include "jgltext.h"

#include <renderer/GLText2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/renderer/ManagedTextFormat2.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Renderer;

JNIEXPORT jlong JNICALL Java_com_atakmap_opengl_GLText_intern
  (JNIEnv *env, jclass clazz, jobject mtextFormat, jobject mglyphRenderer)
{
    TextFormat2Ptr cfmt(new ManagedTextFormat2(*env, mtextFormat, mglyphRenderer), Memory_deleter_const<TextFormat2, ManagedTextFormat2>);
    return INTPTR_TO_JLONG(GLText2_intern(std::move(cfmt)));
}
JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLText_invalidate
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
}
JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLText_batch__JJLjava_lang_String_2FFFFFFF
  (JNIEnv *env, jclass clazz, jlong gltextPtr, jlong glbatchPtr, jstring mtext, jfloat x, jfloat y, jfloat z, jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLText2 *gltext = JLONG_TO_INTPTR(GLText2, gltextPtr);
    GLRenderBatch2 *batch = JLONG_TO_INTPTR(GLRenderBatch2, glbatchPtr);

    y += gltext->getTextFormat().getBaselineSpacing();

    JNIStringUTF text(*env, mtext);
    gltext->batch(*batch, text, x, y, z, r, g, b, a);
}
JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLText_batch__JJLjava_lang_String_2FFFFFFFFF
  (JNIEnv *env, jclass clazz, jlong gltextPtr, jlong glbatchPtr, jstring mtext, jfloat x, jfloat y, jfloat z, jfloat r, jfloat g, jfloat b, jfloat a, jfloat scissorX0, jfloat scissorX1)
{
    GLText2 *gltext = JLONG_TO_INTPTR(GLText2, gltextPtr);
    GLRenderBatch2 *batch = JLONG_TO_INTPTR(GLRenderBatch2, glbatchPtr);

    // XXX -
    y += gltext->getTextFormat().getBaselineSpacing();

    JNIStringUTF text(*env, mtext);
    gltext->batch(*batch, text, x, y, z, r, g, b, a, scissorX0, scissorX1);
}
