#include "jnativefeaturedatasource.h"

#include <feature/Feature2.h>
#include <feature/FeatureDataSource2.h>
#include <feature/FeatureDefinition2.h>
#include <feature/Geometry.h>
#include <feature/Geometry2.h>
#include <feature/GpxDriverDefinition2.h>
#include <feature/LegacyAdapters.h>
#include <feature/ShapefileDriverDefinition2.h>
#include <feature/Style.h>
#include <feature/OGRDriverDefinition2.h>
#include <feature/OGR_DriverDefinition.h>
#include <feature/KMLDriverDefinition2.h>
#include <formats/ogr/OGR_FeatureDataSource2.h>

#include <util/Memory.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/feature/ManagedFeatureDataSource2.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Feature;

/*****************************************************************************/
// FeatureDataSource

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_wrap
  (JNIEnv *env, jclass clazz, jobject managed)
{
    FeatureDataSourcePtr retval(new ManagedFeatureDataSource2(env, managed), Memory_deleter_const<FeatureDataSource2, ManagedFeatureDataSource2>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSource_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<FeatureDataSource2>(env, jpointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSource_1parse
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2 *source = JLONG_TO_INTPTR(FeatureDataSource2, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    
    JNIStringUTF path(*env, jpath);
    FeatureDataSource2::ContentPtr retval(NULL, NULL);
    code = source->parse(retval, path);
    if(code == TE_InvalidArg)
        return NULL;
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSource_1getName
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2 *source = JLONG_TO_INTPTR(FeatureDataSource2, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    
    return env->NewStringUTF(source->getName());
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSource_1parseVersion
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2 *source = JLONG_TO_INTPTR(FeatureDataSource2, ptr);
    if(!source) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    
    return source->parseVersion();
}

/*****************************************************************************/
// FeatureDataSource.Content

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<FeatureDataSource2::Content>(env, jpointer);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1getType
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return env->NewStringUTF(content->getType());
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1getProvider
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return env->NewStringUTF(content->getProvider());
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1moveToNextFeature
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    code = content->moveToNextFeature();
    if(code == TE_Done)
        return false;

    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;

    return (code == TE_Ok);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1moveToNextFeatureSet
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    code = content->moveToNextFeatureSet();
    if(code == TE_Done)
        return false;

    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;

    return (code == TE_Ok);
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1get
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }

    FeatureDefinition2 *cdefinition;
    code = content->get(&cdefinition);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0LL;
    if(!cdefinition)
        return 0LL;
    return INTPTR_TO_JLONG(cdefinition);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1getFeatureSetName
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAK::Engine::Port::String cname;
    code = content->getFeatureSetName(cname);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cname)
        return NULL;
    return env->NewStringUTF(cname);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1getMinResolution
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    double retval;
    code = content->getMinResolution(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0.0;
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_Content_1getMaxResolution
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    FeatureDataSource2::Content *content = JLONG_TO_INTPTR(FeatureDataSource2::Content, ptr);
    if(!content) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0.0;
    }

    double retval;
    code = content->getMaxResolution(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0.0;
    return retval;
}

/*****************************************************************************/
// FeatureDefinition2

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getRawGeometry
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    TAKErr code(TE_Ok);
    FeatureDefinition2::RawData raw;
    code = defn->getRawGeometry(&raw);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
        
    switch(defn->getGeomCoding()) {
        case FeatureDefinition2::GeomWkb :
        case FeatureDefinition2::GeomBlob :
            if(!raw.binary.value)
                return NULL;
            return JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(raw.binary.value), raw.binary.len);
        case FeatureDefinition2::GeomWkt :
            if(!raw.text)
                return NULL;
            return env->NewStringUTF(raw.text);
        case FeatureDefinition2::GeomGeometry :
            if(!raw.object)
                return NULL;
            Geometry2Ptr cgeom(NULL, NULL);
            code = LegacyAdapters_adapt(cgeom, *static_cast<const Geometry *>(raw.object));
            if(ATAKMapEngineJNI_checkOrThrow(env, code))
                return NULL;
            return NewPointer(env, std::move(cgeom));
    }

    ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
    return NULL;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getGeomCoding
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return defn->getGeomCoding();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getName
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    const char *cname;
    TAKErr code = defn->getName(&cname);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return env->NewStringUTF(cname);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getStyleCoding
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return defn->getStyleCoding();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getRawStyle
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    TAKErr code(TE_Ok);
    FeatureDefinition2::RawData raw;
    code = defn->getRawStyle(&raw);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
        
    switch(defn->getStyleCoding()) {
        case FeatureDefinition2::StyleOgr :
            if(!raw.text)
                return NULL;
            return env->NewStringUTF(raw.text);
        case FeatureDefinition2::StyleStyle :
            if(!raw.object)
                return NULL;
            TAK::Engine::Feature::StylePtr cstyle(static_cast<const Style *>(raw.object)->clone(), Style::destructStyle);
            return NewPointer(env, std::move(cstyle));
    }
    ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
    return NULL;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDefinition_1getAttributes
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDefinition2 *defn = JLONG_TO_INTPTR(FeatureDefinition2, ptr);
    if(!defn) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    const AttributeSet *cattrs;
    TAKErr code = defn->getAttributes(&cattrs);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cattrs)
        return NULL;
    AttributeSetPtr retval(new AttributeSet(*cattrs), Memory_deleter_const<AttributeSet>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSourceContentFactory_1register
  (JNIEnv *env, jclass clazz, jobject jpointer, jint priority)
{
    if(!Pointer_makeShared<FeatureDataSource2>(env, jpointer)) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
        return;
    }

    // get shared_ptr from pointer
    jlong pointer = env->GetLongField(jpointer, Pointer_class.value);

    // set the value to register
    std::shared_ptr<FeatureDataSource2> toRegister(*JLONG_TO_INTPTR(std::shared_ptr<FeatureDataSource2>, pointer));

    TAKErr code = FeatureDataSourceFactory_registerProvider(toRegister, priority);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSourceContentFactory_1unregister
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    // get raw pointer
    FeatureDataSource2 *source = Pointer_get<FeatureDataSource2>(*env, jpointer);
    if(!source)
        return;
    // unregister
    FeatureDataSourceFactory_unregisterProvider(*source);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSourceContentFactory_1getProvider
  (JNIEnv *env, jclass clazz, jstring jtype)
{
    if(!jtype)
        return NULL;
    TAKErr code(TE_Ok);
    TAK::Engine::Port::String ctype;
    code = JNIStringUTF_get(ctype, *env, jtype);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::shared_ptr<FeatureDataSource2> provider;
    code = FeatureDataSourceFactory_getProvider(provider, ctype);
    if(code == TE_InvalidArg)
        return NULL;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    else
        return NewPointer(env, provider);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_FeatureDataSourceContentFactory_1parse
  (JNIEnv *env, jclass clazz, jstring jpath, jstring jtypeHint)
{
    if(!jpath)
        return NULL;
    TAKErr code(TE_Ok);
    TAK::Engine::Port::String cpath;
    code = JNIStringUTF_get(cpath, *env, jpath);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    TAK::Engine::Port::String ctypeHint;
    if(jtypeHint) {
        code = JNIStringUTF_get(ctypeHint, *env, jtypeHint);
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return NULL;
    }
    FeatureDataSource2::ContentPtr content(NULL, NULL);
    code = FeatureDataSourceFactory_parse(content, cpath, ctypeHint);
    if(code == TE_InvalidArg)
        return NULL;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    else
        return NewPointer(env, std::move(content));
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1GeometryEncoding_1GeomWkt
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::GeomWkt;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1GeometryEncoding_1GeomWkb
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::GeomWkb;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1GeometryEncoding_1GeomBlob
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::GeomBlob;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1GeometryEncoding_1GeomGeom
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::GeomGeometry;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1StyleEncoding_1StyleOgr
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::StyleOgr;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataSource_getFeatureDefinition_1StyleEncoding_1StyleStyle
  (JNIEnv *env, jclass clazz)
{
    return FeatureDefinition2::StyleStyle;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_ogr_OgrFeatureDataSource_create
  (JNIEnv *env, jclass clazz)
{
    using namespace TAK::Engine::Formats::OGR;
    FeatureDataSourcePtr csrc(new OGR_FeatureDataSource2(),
        Memory_deleter_const<FeatureDataSource2, OGR_FeatureDataSource2>);
    return NewPointer(env, std::move(csrc));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_ogr_OgrFeatureDataSource_registerDrivers
  (JNIEnv *env, jclass clazz)
{
    std::shared_ptr<OGRDriverDefinition2Spi> kml(new KMLDriverDefinition2::Spi());
    OGRDriverDefinition2_registerSpi(kml);

    std::shared_ptr<OGRDriverDefinition2Spi> gpx(new GpxDriverDefinition2::Spi());
    OGRDriverDefinition2_registerSpi(gpx);

    std::shared_ptr<OGRDriverDefinition2Spi> shp(new ShapefileDriverDefinition2::Spi());
    OGRDriverDefinition2_registerSpi(shp);
}
