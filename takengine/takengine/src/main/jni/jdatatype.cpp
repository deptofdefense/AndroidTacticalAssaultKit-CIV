#include "jdatatype.h"

#include <port/Platform.h>

using namespace TAK::Engine::Port;

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1UInt8
  (JNIEnv *env, jclass clazz)
{
    return TEDT_UInt8;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1Int8
  (JNIEnv *env, jclass clazz)
{
    return TEDT_Int8;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1UInt16
  (JNIEnv *env, jclass clazz)
{
    return TEDT_UInt16;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1Int16
  (JNIEnv *env, jclass clazz)
{
    return TEDT_Int16;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1UInt32
  (JNIEnv *env, jclass clazz)
{
    return TEDT_UInt32;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1Int32
  (JNIEnv *env, jclass clazz)
{
    return TEDT_Int32;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1Float32
  (JNIEnv *env, jclass clazz)
{
    return TEDT_Float32;
}

JNIEXPORT jint JNICALL Java_com_atakmap_interop_DataType_getTEDT_1Float64
  (JNIEnv *env, jclass clazz)
{
    return TEDT_Float64;
}
