#include "jfeature.h"

#include <feature/Feature2.h>
#include <feature/LegacyAdapters.h>
#include <feature/Style.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_Feature_create
  (JNIEnv *env, jclass clazz, jlong fsid, jlong fid, jstring jname, jlong jgeomPtr, jint altitudemode, jdouble extrude, jlong jstylePtr, jlong jattrPtr, jlong timestamp, jlong version)
{
    TAKErr code(TE_Ok);
    JNIStringUTF name(*env, jname);

    GeometryPtr_const cgeom(NULL, NULL);
    if(jgeomPtr) {
        const Geometry2 *cgeomPtr = JLONG_TO_INTPTR(Geometry2, jgeomPtr);
        code = LegacyAdapters_adapt(cgeom, *cgeomPtr);
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return NULL;
    }
    
    AltitudeMode altMode;
    switch(altitudemode) {
        case 0:
            altMode = AltitudeMode::TEAM_ClampToGround;
            break;
        case 1:
            altMode = AltitudeMode::TEAM_Relative;
            break;
        case 2:
            altMode = AltitudeMode::TEAM_Absolute;
            break;
        default :
            altMode = AltitudeMode::TEAM_ClampToGround;
    }

    StylePtr_const cstyle(NULL, NULL);
    if(jstylePtr) {
        const Style *cstylePtr = JLONG_TO_INTPTR(Style, jstylePtr);
        cstyle = StylePtr_const(cstylePtr->clone(), Style::destructStyle);
    }
    AttributeSetPtr_const cattrs(NULL, NULL);
    if(jattrPtr) {
        const AttributeSet *cattrPtr = JLONG_TO_INTPTR(AttributeSet, jattrPtr);
        cattrs = AttributeSetPtr_const(new AttributeSet(*cattrPtr), Memory_deleter_const<AttributeSet>);
    }

    FeaturePtr retval(new Feature2(fid, fsid, name, std::move(cgeom), altMode, extrude, std::move(cstyle), std::move(cattrs), timestamp, version), Memory_deleter_const<Feature2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_Feature_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<Feature2>(env, jpointer);
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_Feature_getFeatureSetId
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return feature->getFeatureSetId();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_Feature_getId
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return feature->getId();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_Feature_getVersion
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return feature->getVersion();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_Feature_getName
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    const char *cname = feature->getName();
    if(!cname)
        return NULL;
    return env->NewStringUTF(cname);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_Feature_getGeometry
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    const Geometry *cgeom = feature->getGeometry();
    if(!cgeom)
        return NULL;
    Geometry2Ptr retval(NULL, NULL);
    TAKErr code = LegacyAdapters_adapt(retval, *cgeom);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_Feature_getStyle
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    const Style *cstyle = feature->getStyle();
    if(!cstyle)
        return NULL;
    TAK::Engine::Feature::StylePtr retval(cstyle->clone(), Style::destructStyle);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_Feature_getAttributes
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    const AttributeSet *cattrs = feature->getAttributes();
    if(!cattrs)
        return NULL;
    AttributeSetPtr retval(new AttributeSet(*cattrs), Memory_deleter_const<AttributeSet>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_Feature_getTimestamp
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return feature->getTimestamp();
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_Feature_getAltitudeMode
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    AltitudeMode altMode =  feature->getAltitudeMode();
    switch(altMode) {
        case AltitudeMode::TEAM_Relative:
            return 1;
        case AltitudeMode::TEAM_Absolute:
            return 2;
        default:
            return 0;
    }
}

JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_Feature_getExtrude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, ptr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    return feature->getExtrude();
}

