#include "common.h"
#include "jglrenderbatch2.h"

#include "renderer/GL.h"

#include "renderer/GLRenderBatch2.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

JNIEXPORT jlong JNICALL Java_com_atakmap_opengl_GLRenderBatch2_create
  (JNIEnv *env, jclass clazz, jint limit)
{
    GLRenderBatch2 *retval = new GLRenderBatch2(limit);
    return INTPTR_TO_JLONG(retval);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_destroy
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    delete renderBatch;
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_begin
  (JNIEnv *env, jclass clazz, jlong ptr, jint hints)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->begin(hints);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_end
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->end();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_release
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->release();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_setMatrix
  (JNIEnv *env, jclass clazz, jlong ptr, jint mode, jfloatArray jmx, jint off)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);

    jfloat *mx = env->GetFloatArrayElements(jmx, 0);
    const TAKErr code = renderBatch->setMatrix(mode, mx+off);
    env->ReleaseFloatArrayElements(jmx, mx, JNI_ABORT);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_pushMatrix
  (JNIEnv *env, jclass clazz, jlong ptr, jint mode)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->pushMatrix(mode);
    ATAKMapEngineJNI_checkOrThrow(env, code);

}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_popMatrix
  (JNIEnv *env, jclass clazz, jlong ptr, jint mode)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->popMatrix(mode);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_setLineWidth
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat width)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    const TAKErr code = renderBatch->setLineWidth(width);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JIIIIILjava_nio_FloatBuffer_2IILjava_nio_FloatBuffer_2IFFFF
  (JNIEnv *env,
   jclass clazz,
   jlong ptr,
   jint texId,
   jint mode,
   jint count,
   jint size,
   jint vStride, jobject jvertices, jint verticesOff,
   jint tcStride, jobject jtexCoords, jint texCoordsOff,
   jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    jfloat *vertices = GET_BUFFER_POINTER(jfloat, jvertices);
    jfloat *texCoords = GET_BUFFER_POINTER(jfloat, jtexCoords);
    const TAKErr code = renderBatch->batch(texId,
                                           mode,
                                           count,
                                           size,
                                           vStride,
                                           vertices+verticesOff,
                                           tcStride,
                                           texCoords ?
                                                   (texCoords+texCoordsOff) :
                                                   NULL,
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JIIIIILjava_nio_FloatBuffer_2IILjava_nio_FloatBuffer_2IILjava_nio_ShortBuffer_2IFFFF
  (JNIEnv *env, jclass clazz, jlong ptr, jint texId, jint mode, jint count, jint size, jint vStride, jobject jvertices, jint verticesOff, jint tcStride, jobject jtexCoords, jint texCoordsOff, jint indexCount, jobject jindices, jint indicesOff, jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    jfloat *vertices = GET_BUFFER_POINTER(jfloat, jvertices);
    jfloat *texCoords = GET_BUFFER_POINTER(jfloat, jtexCoords);
    jshort *indices = GET_BUFFER_POINTER(jshort, jindices);
    const TAKErr code = renderBatch->batch(texId,
                                           mode,
                                           count,
                                           size,
                                           vStride,
                                           vertices+verticesOff,
                                           tcStride,
                                           texCoords ?
                                                   (texCoords+texCoordsOff) :
                                                   NULL,
                                           indexCount,
                                           reinterpret_cast<unsigned short *>(indices+indicesOff),
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_setMaxTextureUnits
  (JNIEnv *env, jclass clazz, jint texUnits)
{
    GLRenderBatch2_setBatchTextureUnitLimit(texUnits);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JFFFFFFFF
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    float vertices[4];
    vertices[0] = x0;
    vertices[1] = y0;
    vertices[2] = x1;
    vertices[3] = y1;

    const TAKErr code = renderBatch->batch(-1,
                                           GL_LINES,
                                           2,
                                           2u,
                                           0u,
                                           vertices,
                                           0u,
                                           NULL,
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JFFFFFFFFFF
  (JNIEnv *env, jclass clazz, jlong ptr, jfloat x0, jfloat y0, jfloat z0, jfloat x1, jfloat y1, jfloat z1, jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    float vertices[6];
    vertices[0] = x0;
    vertices[1] = y0;
    vertices[2] = z0;
    vertices[3] = x1;
    vertices[4] = y1;
    vertices[5] = z1;

    const TAKErr code = renderBatch->batch(-1,
                                           GL_LINES,
                                           2,
                                           3u,
                                           0u,
                                           vertices,
                                           0u,
                                           NULL,
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JIFFFFFFFFFFFFFFFFFFFF
  (JNIEnv *env, jclass clazz,
   jlong ptr,
   jint texId,
   jfloat x0, jfloat y0,
   jfloat x1, jfloat y1,
   jfloat x2, jfloat y2,
   jfloat x3, jfloat y3,
   jfloat u0, jfloat v0,
   jfloat u1, jfloat v1,
   jfloat u2, jfloat v2,
   jfloat u3, jfloat v3,
   jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    float vertices[12];
    float texCoords[12];

    vertices[0] = x3;
    vertices[1] = y3;
    texCoords[0] = u3;
    texCoords[1] = v3;
    vertices[2] = x0;
    vertices[3] = y0;
    texCoords[2] = u0;
    texCoords[3] = v0;
    vertices[4] = x2;
    vertices[5] = y2;
    texCoords[4] = u2;
    texCoords[5] = v2;

    vertices[6] = x0;
    vertices[7] = y0;
    texCoords[6] = u0;
    texCoords[7] = v0;
    vertices[8] = x2;
    vertices[9] = y2;
    texCoords[8] = u2;
    texCoords[9] = v2;
    vertices[10] = x1;
    vertices[11] = y1;
    texCoords[10] = u1;
    texCoords[11] = v1;

    const TAKErr code = renderBatch->batch(texId,
                                           GL_TRIANGLES,
                                           6,
                                           2u,
                                           0u,
                                           vertices,
                                           0u,
                                           texCoords,
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLRenderBatch2_batch__JIFFFFFFFFFFFFFFFFFFFFFFFF
  (JNIEnv *env, jclass clazz,
   jlong ptr,
   jint texId,
   jfloat x0, jfloat y0, jfloat z0,
   jfloat x1, jfloat y1, jfloat z1,
   jfloat x2, jfloat y2, jfloat z2,
   jfloat x3, jfloat y3, jfloat z3,
   jfloat u0, jfloat v0,
   jfloat u1, jfloat v1,
   jfloat u2, jfloat v2,
   jfloat u3, jfloat v3,
   jfloat r, jfloat g, jfloat b, jfloat a)
{
    GLRenderBatch2 *renderBatch = JLONG_TO_INTPTR(GLRenderBatch2, ptr);
    float vertices[18];
    float texCoords[12];

    vertices[0] = x3;
    vertices[1] = y3;
    vertices[2] = z3;
    texCoords[0] = u3;
    texCoords[1] = v3;
    vertices[3] = x0;
    vertices[4] = y0;
    vertices[5] = z0;
    texCoords[2] = u0;
    texCoords[3] = v0;
    vertices[6] = x2;
    vertices[7] = y2;
    vertices[8] = z2;
    texCoords[4] = u2;
    texCoords[5] = v2;

    vertices[9] = x0;
    vertices[10] = y0;
    vertices[11] = z0;
    texCoords[6] = u0;
    texCoords[7] = v0;
    vertices[12] = x2;
    vertices[13] = y2;
    vertices[14] = z2;
    texCoords[8] = u2;
    texCoords[9] = v2;
    vertices[15] = x1;
    vertices[16] = y1;
    vertices[17] = z1;
    texCoords[10] = u1;
    texCoords[11] = v1;

    const TAKErr code = renderBatch->batch(texId,
                                           GL_TRIANGLES,
                                           6,
                                           3u,
                                           0u,
                                           vertices,
                                           0u,
                                           texCoords,
                                           r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
