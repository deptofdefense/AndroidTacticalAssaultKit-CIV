// XXX - when including 'cstdlib' the following error is raised by the NDK
//  In file included from junsafe.cpp:1:
//  android-ndk-r7c/sources/cxx-stl/system/include/cstdlib:53: error: '::clearenv' has not been declared

//#include <cstdlib>
#include <stdlib.h>
#ifdef __linux__
#include <cstring>
#endif

#include "common.h"
#include "junsafe.h"

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    allocate
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_com_atakmap_lang_Unsafe_allocate
  (JNIEnv *env, jclass clazz, jint len)
{
    return INTPTR_TO_JLONG(calloc(len, 1u));
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_free
  (JNIEnv *env, jclass clazz, jlong jptr)
{
    void *ptr = JLONG_TO_INTPTR(void, jptr);
    free(ptr);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_lang_Unsafe_newDirectBuffer
  (JNIEnv *env, jclass clazz, jlong pointer, jint capacity)
{
    return env->NewDirectByteBuffer(JLONG_TO_INTPTR(unsigned char, pointer), capacity);
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    memset
 * Signature: (JBI)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_memset
  (JNIEnv *env, jclass clazz, jlong jptr, jbyte jv, jint len)
{
    void *ptr = JLONG_TO_INTPTR(void, jptr);
    unsigned char v = (unsigned char)jv;

    memset(ptr, v, len);
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    memcpy
 * Signature: (JIJ)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_memcpy__JJI
  (JNIEnv *env, jclass clazz, jlong jdst, jlong jsrc, jint len)
{
    void *dst = JLONG_TO_INTPTR(void, jdst);
    void *src = JLONG_TO_INTPTR(void, jsrc);

    memcpy(dst, src, len);
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    memcpy
 * Signature: (Ljava/nio/ByteBuffer;IJI)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_memcpy__Ljava_nio_ByteBuffer_2IJI
  (JNIEnv *env, jclass clazz, jobject jdst, jint dstOff, jlong jsrc, jint len)
{
    jbyte *dst = GET_BUFFER_POINTER(jbyte, jdst) + dstOff;
    void *src = JLONG_TO_INTPTR(void, jsrc);

    memcpy(dst, src, len);
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    memcpy
 * Signature: (JLjava/nio/ByteBuffer;II)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_memcpy__JLjava_nio_ByteBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jdst, jobject jsrc, jint srcOff, jint len)
{
    void *dst = JLONG_TO_INTPTR(void, jdst);
    jbyte *src = GET_BUFFER_POINTER(jbyte, jsrc) + srcOff;


    memcpy(dst, src, len);
}

JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_memmove
  (JNIEnv *env, jclass clazz, jlong jdst, jlong jsrc, jint len)
{
    void *dst = JLONG_TO_INTPTR(void, jdst);
    void *src = JLONG_TO_INTPTR(void, jsrc);

    memmove(dst, src, len);
}

JNIEXPORT jlong JNICALL Java_com_atakmap_lang_Unsafe_getBufferPointer
  (JNIEnv *env, jclass jclazz, jobject jbuffer)
{
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);
    return INTPTR_TO_JLONG(buffer);
}

/******************************************************************************/
// NIO Set Elements Function Definitions

#define FN_DEFN_SET_ELEM_NIO(elemName, elemType)                               \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## Nio     \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint off, elemType v)           \
{                                                                              \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    elemType *ebuffer = reinterpret_cast<elemType *>(buffer + off);            \
    ebuffer[0] = v;                                                            \
}

#define FN_DEFN_SET_ELEMS2_NIO(elemName, elemType)                             \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## sNio2   \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint off, elemType v0, elemType v1) \
{                                                                              \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    elemType *ebuffer = reinterpret_cast<elemType *>(buffer + off);            \
    ebuffer[0] = v0;                                                           \
    ebuffer[1] = v1;                                                           \
}

#define FN_DEFN_SET_ELEMS3_NIO(elemName, elemType)                             \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## sNio3   \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint off, elemType v0, elemType v1, elemType v2) \
{                                                                              \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    elemType *ebuffer = reinterpret_cast<elemType *>(buffer + off);            \
    ebuffer[0] = v0;                                                           \
    ebuffer[1] = v1;                                                           \
    ebuffer[2] = v2;                                                           \
}

#define FN_DEFN_SET_ELEMS4_NIO(elemName, elemType)                             \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## sNio4   \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint off, elemType v0, elemType v1, elemType v2, elemType v3) \
{                                                                              \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    elemType *ebuffer = reinterpret_cast<elemType *>(buffer + off);            \
    ebuffer[0] = v0;                                                           \
    ebuffer[1] = v1;                                                           \
    ebuffer[2] = v2;                                                           \
    ebuffer[3] = v3;                                                           \
}

#define FN_DEFN_SET_ELEMS_ARRAY_NIO(elemName, elemType)                        \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## sNioArray \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint off, elemType ## Array jarr, jint len) \
{                                                                              \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    void *arr = env->GetPrimitiveArrayCritical(jarr, 0);                       \
    memcpy(buffer+off, arr, len*sizeof(elemType));                             \
    env->ReleasePrimitiveArrayCritical(jarr, arr, JNI_ABORT);                  \
}

/*
 * Class:     com_atakmap_lang_Unsafe
 * Method:    setBytesNioArrayRegion
 * Signature: (Ljava/nio/Buffer;I[BII)V
 */
#define FN_DEFN_SET_ELEMS_ARRAY_REGION_NIO(elemName, elemType)                 \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## sNioArrayRegion \
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint bufOff, elemType ## Array jarr, jint arrOff, jint arrLen) \
{ \
    jbyte *buffer = GET_BUFFER_POINTER(jbyte, jbuffer);                        \
    elemType *ebuffer = reinterpret_cast<elemType *>(buffer + bufOff);         \
    env->Get ## elemName ## ArrayRegion(jarr, arrOff, arrLen, ebuffer);        \
}

#define FN_DEFN_SET_ELEMS_NIO(elemName, elemType)                              \
    FN_DEFN_SET_ELEM_NIO(elemName, elemType)                                   \
    FN_DEFN_SET_ELEMS2_NIO(elemName, elemType)                                 \
    FN_DEFN_SET_ELEMS3_NIO(elemName, elemType)                                 \
    FN_DEFN_SET_ELEMS4_NIO(elemName, elemType)                                 \
    FN_DEFN_SET_ELEMS_ARRAY_NIO(elemName, elemType)                            \
    FN_DEFN_SET_ELEMS_ARRAY_REGION_NIO(elemName, elemType)

FN_DEFN_SET_ELEMS_NIO(Byte, jbyte)
FN_DEFN_SET_ELEMS_NIO(Short, jshort)
FN_DEFN_SET_ELEMS_NIO(Int, jint)
FN_DEFN_SET_ELEMS_NIO(Long, jlong)
FN_DEFN_SET_ELEMS_NIO(Float, jfloat)
FN_DEFN_SET_ELEMS_NIO(Double, jdouble)

#undef FN_DEFN_SET_ELEMS_NIO
#undef FN_DEFN_SET_ELEMS_ARRAY_REGION_NIO
#undef FN_DEFN_SET_ELEMS_ARRAY_NIO
#undef FN_DEFN_SET_ELEMS4_NIO
#undef FN_DEFN_SET_ELEMS3_NIO
#undef FN_DEFN_SET_ELEMS2_NIO
#undef FN_DEFN_SET_ELEM_NIO

/******************************************************************************/
// Pointer Get Element Function Definitions

#define FN_DEFN_GET_ELEM(elemName, elemType) \
JNIEXPORT elemType JNICALL Java_com_atakmap_lang_Unsafe_get ## elemName        \
  (JNIEnv *env, jclass clazz, jlong ptr)                                       \
{                                                                              \
    return JLONG_TO_INTPTR(elemType, ptr)[0];                                  \
}

FN_DEFN_GET_ELEM(Byte, jbyte)
FN_DEFN_GET_ELEM(Short, jshort)
FN_DEFN_GET_ELEM(Int, jint)
FN_DEFN_GET_ELEM(Long, jlong)
FN_DEFN_GET_ELEM(Float, jfloat)
FN_DEFN_GET_ELEM(Double, jdouble)

#undef FN_DEFN_GET_ELEM

#define FN_DEFN_SET_ELEM(elemName, elemType) \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName \
  (JNIEnv *env, jclass clazz, jlong ptr, elemType v) \
{ \
    elemType *arr = JLONG_TO_INTPTR(elemType, ptr); \
    arr[0] = v; \
}

#define FN_DEFN_SET_ELEMS2(elemName, elemType, elemSig) \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## s__J ## elemSig ## elemSig \
  (JNIEnv *env, jclass clazz, jlong ptr, elemType v0, elemType v1) \
{ \
    elemType *arr = JLONG_TO_INTPTR(elemType, ptr); \
    arr[0] = v0; \
    arr[1] = v1; \
}

#define FN_DEFN_SET_ELEMS3(elemName, elemType, elemSig) \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## s__J ## elemSig ## elemSig ## elemSig \
  (JNIEnv *env, jclass clazz, jlong ptr, elemType v0, elemType v1, elemType v2) \
{ \
    elemType *arr = JLONG_TO_INTPTR(elemType, ptr); \
    arr[0] = v0; \
    arr[1] = v1; \
    arr[2] = v2; \
}

#define FN_DEFN_SET_ELEMS4(elemName, elemType, elemSig) \
JNIEXPORT void JNICALL Java_com_atakmap_lang_Unsafe_set ## elemName ## s__J ## elemSig ## elemSig ## elemSig ## elemSig \
  (JNIEnv *env, jclass clazz, jlong ptr, elemType v0, elemType v1, elemType v2, elemType v3) \
{ \
    elemType *arr = JLONG_TO_INTPTR(elemType, ptr); \
    arr[0] = v0; \
    arr[1] = v1; \
    arr[2] = v2; \
    arr[3] = v3; \
}

#define FN_DEFN_SET_ELEMS(elemName, elemType, elemSig) \
    FN_DEFN_SET_ELEM(elemName, elemType) \
    FN_DEFN_SET_ELEMS2(elemName, elemType, elemSig) \
    FN_DEFN_SET_ELEMS3(elemName, elemType, elemSig) \
    FN_DEFN_SET_ELEMS4(elemName, elemType, elemSig) \

FN_DEFN_SET_ELEMS(Byte, jbyte, B)
FN_DEFN_SET_ELEMS(Short, jshort, S)
FN_DEFN_SET_ELEMS(Int, jint, I)
FN_DEFN_SET_ELEMS(Long, jlong, J)
FN_DEFN_SET_ELEMS(Float, jfloat, F)
FN_DEFN_SET_ELEMS(Double, jdouble, D)

#undef FN_DEFN_SET_ELEMS
#undef FN_DEFN_SET_ELEMS4
#undef FN_DEFN_SET_ELEMS3
#undef FN_DEFN_SET_ELEMS2
#undef FN_DEFN_SET_ELEM
