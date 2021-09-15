#include "jnativefeaturecursor.h"

#include <feature/FeatureCursor2.h>
#include <feature/FeatureDataStore2.h>
#include <feature/Geometry2.h>
#include <feature/LegacyAdapters.h>
#include <feature/Style.h>
#include <util/AttributeSet.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<FeatureCursor2>(env, jpointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getRawGeometry
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    FeatureDefinition2::RawData rawGeom;
    code = result->getRawGeometry(&rawGeom);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    const FeatureDefinition2::GeometryEncoding geomCoding = result->getGeomCoding();
    switch(geomCoding) {
        case FeatureDefinition2::GeomWkt :
            if(!rawGeom.text)
                return NULL;
            return env->NewStringUTF(rawGeom.text);
        case FeatureDefinition2::GeomWkb :
        case FeatureDefinition2::GeomBlob :
            if(!rawGeom.binary.value)
                return NULL;
            return JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(rawGeom.binary.value), rawGeom.binary.len);
        case FeatureDefinition2::GeomGeometry :
            if(!rawGeom.object)
                return NULL;
            Geometry2Ptr cgeom(NULL, NULL);
            LegacyAdapters_adapt(cgeom, *static_cast<const atakmap::feature::Geometry *>(rawGeom.object));
            if(ATAKMapEngineJNI_checkOrThrow(env, code))
                return NULL;
            return NewPointer(env, std::move(cgeom));
    }

    ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
    return NULL;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getGeomCoding
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return (jint)result->getGeomCoding();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getName
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    const char *cname;
    code = result->getName(&cname);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cname)
        return NULL;
    return env->NewStringUTF(cname);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getStyleCoding
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return (jint)result->getStyleCoding();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getRawStyle
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    FeatureDefinition2::RawData rawStyle;
    code = result->getRawStyle(&rawStyle);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    const FeatureDefinition2::StyleEncoding styleCoding = result->getStyleCoding();
    switch(styleCoding) {
        case FeatureDefinition2::StyleOgr :
            if(!rawStyle.text)
                return NULL;
            return env->NewStringUTF(rawStyle.text);
        case FeatureDefinition2::StyleStyle :
            if(!rawStyle.object)
                return NULL;
            TAK::Engine::Feature::StylePtr cstyle(static_cast<const Style *>(rawStyle.object)->clone(), Style::destructStyle);
            return NewPointer(env, std::move(cstyle));
    }

    ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
    return NULL;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getAttributes
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    const AttributeSet *cattrs;
    code = result->getAttributes(&cattrs);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cattrs)
        return NULL;
    AttributeSetPtr cattrsPtr(new AttributeSet(*cattrs), Memory_deleter_const<AttributeSet>);
    return NewPointer(env, std::move(cattrsPtr));
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getId
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }

    TAKErr code(TE_Ok);
    int64_t fid;
    code = result->getId(&fid);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURE_ID_NONE;
    return fid;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getVersion
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_VERSION_NONE;
    }

    TAKErr code(TE_Ok);
    int64_t version;
    code = result->getVersion(&version);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURE_VERSION_NONE;
    return version;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getFsid
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURESET_ID_NONE;
    }

    TAKErr code(TE_Ok);
    int64_t fsid;
    code = result->getFeatureSetId(&fsid);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURESET_ID_NONE;
    return fsid;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_moveToNext
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    code = result->moveToNext();
    if(code == TE_Ok)
        return true;
    else if(code == TE_Done)
        return false;

    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getTimestamp
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    return TE_TIMESTAMP_NONE;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getAltitudeMode
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    AltitudeMode altMode =  result->getAltitudeMode();
    switch(altMode) {
        case AltitudeMode::TEAM_Relative:
            return 1;
        case AltitudeMode::TEAM_Absolute:
            return 2;
        default:
            return 0;
    }
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureCursor_getExtrude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureCursor2 *result = JLONG_TO_INTPTR(FeatureCursor2, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return result->getExtrude();
}

