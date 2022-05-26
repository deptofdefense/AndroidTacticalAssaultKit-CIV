#include "jattributeset.h"

#include <list>
#include <vector>

#include <feature/Feature2.h>
#include <util/AttributeSet.h>
#include <util/Logging2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIIntArray.h"
#include "interop/JNILongArray.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;

#define CHECK_NON_NULL_KEY(env, arg, rv) \
    if(!arg) { \
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
        return rv; \
    }

#define CHECK_ATTR_PRESENT(env, attrs, arg, expectedType, rv) \
    if(!attrs->containsAttribute(arg)) {\
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
        return rv; \
    } \
    if(attrs->getAttributeType(arg) != expectedType) {\
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
        return rv; \
    }


JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_create__
  (JNIEnv *env, jclass clazz)
{
    AttributeSetPtr retval(new AttributeSet(), Memory_deleter_const<AttributeSet>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_create__Lcom_atakmap_interop_Pointer_2
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    AttributeSetPtr retval(new AttributeSet(*attr), Memory_deleter_const<AttributeSet>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    if(!jpointer)
        return;
    Pointer_destruct<AttributeSet>(env, jpointer);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getIntAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    CHECK_NON_NULL_KEY(env, jkey, 0);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;

        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::INT, 0);
        return attr->getInt(ckey);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getLongAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    CHECK_NON_NULL_KEY(env, jkey, 0LL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::LONG, 0LL);
        return attr->getLong(ckey);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getDoubleAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    CHECK_NON_NULL_KEY(env, jkey, 0.0);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::DOUBLE, 0.0);
        return attr->getDouble(ckey);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getStringAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::STRING, NULL);
        return env->NewStringUTF(attr->getString(ckey));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jbyteArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getBinaryAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::BLOB, NULL);
        AttributeSet::Blob blob = attr->getBlob(ckey);
        if(!blob.first)
            return NULL;
        return JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(blob.first), blob.second-blob.first);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jintArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getIntArrayAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::INT_ARRAY, NULL);
        AttributeSet::IntArray blob = attr->getIntArray(ckey);
        if(!blob.first)
            return NULL;
        return JNIIntArray_newIntArray(env, reinterpret_cast<const jint*>(blob.first), blob.second-blob.first);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jlongArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getLongArrayAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::LONG_ARRAY, NULL);
        AttributeSet::LongArray blob = attr->getLongArray(ckey);
        if(!blob.first)
            return NULL;
        return JNILongArray_newLongArray(env, reinterpret_cast<const jlong*>(blob.first), blob.second-blob.first);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jdoubleArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getDoubleArrayAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::DOUBLE_ARRAY, 0);
        AttributeSet::DoubleArray blob = attr->getDoubleArray(ckey);
        if(!blob.first)
            return NULL;
        return JNIDoubleArray_newDoubleArray(env, reinterpret_cast<const jdouble*>(blob.first), blob.second-blob.first);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jobjectArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getStringArrayAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::STRING_ARRAY, NULL);
        AttributeSet::StringArray blob = attr->getStringArray(ckey);
        if(!blob.first)
            return NULL;

        const std::size_t count = blob.second-blob.first;
        jobjectArray retval = env->NewObjectArray(count, ATAKMapEngineJNI_findClass(env, "java/lang/String"), NULL);
        for(std::size_t i = 0u; i < count; i++) {
            env->SetObjectArrayElement(retval, i, env->NewStringUTF(blob.first[i]));
        }
        return retval;
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jobjectArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getBinaryArrayAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::BLOB_ARRAY, NULL);
        AttributeSet::BlobArray blobArray = attr->getBlobArray(ckey);
        if(!blobArray.first)
            return NULL;

        const std::size_t count = blobArray.second-blobArray.first;
        jobjectArray retval = env->NewObjectArray(count, ATAKMapEngineJNI_findClass(env, "[B"), NULL);
        for(std::size_t i = 0u; i < count; i++) {
            AttributeSet::Blob blob = blobArray.first[i];
            if(!blob.first)
                continue;
            env->SetObjectArrayElement(retval, i, JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(blob.first), blob.second-blob.first));
        }
        return retval;
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getAttributeSetAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    CHECK_NON_NULL_KEY(env, jkey, NULL);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        CHECK_ATTR_PRESENT(env, attr, ckey, AttributeSet::ATTRIBUTE_SET, NULL);
        std::shared_ptr<AttributeSet> value;
        attr->getAttributeSet(value, ckey);
        return NewPointer(env, value);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getAttributeType
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    CHECK_NON_NULL_KEY(env, jkey, 0);
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        return attr->getAttributeType(ckey);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2I
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jint value)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        attr->setInt(ckey, value);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2J
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jlong value)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        attr->setLong(ckey, value);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2D
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jdouble value)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        attr->setDouble(ckey, value);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2Ljava_lang_String_2
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jstring jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        JNIStringUTF value(*env, jvalue);
        const char *cvalue = value;
        attr->setString(ckey, cvalue);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3B
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jbyteArray jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        JNIByteArray value(*env, jvalue, JNI_ABORT);
        jbyte *arr = value;
        AttributeSet::Blob blob(NULL, NULL);
        if(arr) {
            blob.first = reinterpret_cast<unsigned char *>(arr);
            blob.second = reinterpret_cast<unsigned char *>(arr)+value.length();
        }
        attr->setBlob(ckey, blob);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3I
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jintArray jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        JNIIntArray value(*env, jvalue, JNI_ABORT);
        jint *arr = value;
        AttributeSet::IntArray blob(NULL, NULL);
        if(arr) {
            blob.first = reinterpret_cast<int *>(arr);
            blob.second = reinterpret_cast<int *>(arr)+value.length();
        }
        attr->setIntArray(ckey, blob);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3J
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jlongArray jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        JNILongArray value(*env, jvalue, JNI_ABORT);
        jlong *arr = value;
        AttributeSet::LongArray blob(NULL, NULL);
        if(arr) {
            blob.first = reinterpret_cast<int64_t *>(arr);
            blob.second = reinterpret_cast<int64_t *>(arr)+value.length();
        }
        attr->setLongArray(ckey, blob);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3D
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jdoubleArray jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        JNIDoubleArray value(*env, jvalue, JNI_ABORT);
        jdouble *arr = value;
        AttributeSet::DoubleArray blob(NULL, NULL);
        if(arr) {
            blob.first = reinterpret_cast<double *>(arr);
            blob.second = reinterpret_cast<double *>(arr)+value.length();
        }
        attr->setDoubleArray(ckey, blob);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3Ljava_lang_String_2
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jobjectArray jstringArrayValue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        const std::size_t numStrings = jstringArrayValue ? env->GetArrayLength(jstringArrayValue) : 0u;
        std::vector<std::string> stringArrays;
        stringArrays.reserve(numStrings);
        array_ptr<const char *> value;
        AttributeSet::StringArray stringArray(NULL, NULL);
        if(numStrings) {
            value.reset(new const char *[numStrings]);
            for(std::size_t i = 0u; i < numStrings; i++) {
                jstring str = (jstring)env->GetObjectArrayElement(jstringArrayValue, i);
                if(str) {
                    JNIStringUTF jstr(*env, str);
                    stringArrays.push_back((const char *)jstr);
                    value[i] = stringArrays[stringArrays.size()-1u].c_str();
                } else {
                    value[i] = NULL;
                }
            }

            stringArray.first = value.get();
            stringArray.second = value.get()+numStrings;
        }

        attr->setStringArray(ckey, stringArray);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2_3_3B
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jobjectArray jbinaryArrayValue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        
        const std::size_t numBlobs = jbinaryArrayValue ? env->GetArrayLength(jbinaryArrayValue) : 0u;
        std::list<JNIByteArray> binaryArrays;
        array_ptr<AttributeSet::Blob> value;
        AttributeSet::BlobArray blobArray(NULL, NULL);
        if(numBlobs) {
            value.reset(new AttributeSet::Blob[numBlobs]);
            for(std::size_t i = 0u; i < numBlobs; i++) {
                jbyteArray blob = (jbyteArray)env->GetObjectArrayElement(jbinaryArrayValue, i);
                if(blob) {
                    binaryArrays.emplace_front(*env, blob, JNI_ABORT);
                    JNIByteArray &jblob = *binaryArrays.begin();
                    const jbyte *data = jblob;
                    value[i].first = reinterpret_cast<const unsigned char *>(data);
                    value[i].second = reinterpret_cast<const unsigned char *>(data + jblob.length());
                } else {
                    value[i].first = NULL;
                    value[i].second = NULL;
                }
            }

            blobArray.first = value.get();
            blobArray.second = value.get()+numBlobs;
        }

        attr->setBlobArray(ckey, blobArray);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_setAttribute__Lcom_atakmap_interop_Pointer_2Ljava_lang_String_2Lcom_atakmap_interop_Pointer_2
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey, jobject jvalue)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        
        AttributeSet *value = Pointer_get<AttributeSet>(env, jvalue);
        if(!value) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        
        attr->setAttributeSet(ckey, *value);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_removeAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    CHECK_NON_NULL_KEY(env, jkey, );
    try {
        JNIStringUTF key(*env, jkey);
        const char *ckey = key;
        attr->removeAttribute(ckey);
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }
}
JNIEXPORT jobjectArray JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getAttributeNames
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    try {
        std::vector<const char *> cnames = attr->getAttributeNames();
        jobjectArray retval = env->NewObjectArray(cnames.size(), ATAKMapEngineJNI_findClass(env, "java/lang/String"), NULL);
        for(std::size_t i = 0u; i < cnames.size(); i++) {
            env->SetObjectArrayElement(retval, i, env->NewStringUTF(cnames[i]));
        }
        return retval;
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_containsAttribute
  (JNIEnv *env, jclass clazz, jobject jpointer, jstring jkey)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    CHECK_NON_NULL_KEY(env, jkey, false);
    JNIStringUTF key(*env, jkey);
    const char *ckey = key;
    return attr->containsAttribute(ckey);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_clear
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    AttributeSet *attr = Pointer_get<AttributeSet>(env, jpointer);
    if(!attr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    attr->clear();
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getINT
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::INT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getLONG
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::LONG;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getDOUBLE
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::DOUBLE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getSTRING
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::STRING;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getBLOB
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::BLOB;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getATTRIBUTE_1SET
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::ATTRIBUTE_SET;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getINT_1ARRAY
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::INT_ARRAY;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getLONG_1ARRAY
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::LONG_ARRAY;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getDOUBLE_1ARRAY
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::DOUBLE_ARRAY;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getBLOB_1ARRAY
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::BLOB_ARRAY;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_AttributeSet_getSTRING_1ARRAY
  (JNIEnv *env, jclass clazz)
{
    return AttributeSet::STRING_ARRAY;
}
