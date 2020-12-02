#include "ManagedFeatureDataSource2.h"

#include <list>

#include <feature/Feature2.h>
#include <feature/LegacyAdapters.h>
#include <util/Memory.h>

#include "common.h"
#include "jfeaturedefinition.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/java/JNILocalRef.h"
#include "interop/feature/Interop.h"

using namespace TAKEngineJNI::Interop::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID getName;
        jmethodID parseVersion;
        jmethodID parse;
    } FeatureDataSource2_class;

    struct
    {
        jclass id;
        jmethodID getType;
        jmethodID getProvider;
        jmethodID moveToNext;
        jmethodID get;
        jmethodID getFeatureSetName;
        jmethodID getMinResolution;
        jmethodID getMaxResolution;
        jmethodID close;

        struct
        {
            jobject FEATURE;
            jobject FEATURE_SET;
        } ContentPointer_enum;

    } FeatureDataSource_Content_class;
    struct
    {
        jclass id;
        jfieldID rawGeom;
        jfieldID geomCoding;
        jfieldID name;
        jfieldID styleCoding;
        jfieldID rawStyle;
        jfieldID attributes;
    } FeatureDefinition_class;

    class ManagedContent : public FeatureDataSource2::Content
    {
    public :
        ManagedContent(JNIEnv *env, jobject impl) NOTHROWS;
        ~ManagedContent() NOTHROWS;
    public :
        const char *getType() const NOTHROWS;
        const char *getProvider() const NOTHROWS;
        TAKErr moveToNextFeature() NOTHROWS;
        TAKErr moveToNextFeatureSet() NOTHROWS;
        TAKErr get(FeatureDefinition2 **value) const NOTHROWS;
        TAKErr getFeatureSetName(TAK::Engine::Port::String &value) const NOTHROWS;
        TAKErr getMinResolution(double *value) const NOTHROWS;
        TAKErr getMaxResolution(double *value) const NOTHROWS;
        TAKErr getVisible(bool *value) const NOTHROWS;
        TAKErr getFeatureSetVisible(bool *value) const NOTHROWS;
    public :
        jobject impl;
        TAK::Engine::Port::String type;
        TAK::Engine::Port::String provider;
        TAK::Engine::Feature::FeatureDefinitionPtr feature;
    };

    class ManagedFeatureDefinition : public FeatureDefinition2
    {
    public :
        ManagedFeatureDefinition(JNIEnv *env, jobject impl) NOTHROWS;
        ~ManagedFeatureDefinition() NOTHROWS;
    public :
        TAKErr getRawGeometry(RawData *value) NOTHROWS;
        FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS;
        TAKErr getName(const char **value) NOTHROWS;
        AltitudeMode getAltitudeMode() NOTHROWS;
        double getExtrude() NOTHROWS;
        FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS;
        TAKErr getRawStyle(RawData *value) NOTHROWS;
        TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS;
        TAKErr get(const Feature2 **values) NOTHROWS;
    private :
        jobject impl;
        TAK::Engine::Port::String name;
        FeaturePtr_const feature;
        std::unique_ptr<RawData, void(*)(const RawData *)> rawGeom;
        std::unique_ptr<RawData, void(*)(const RawData *)> rawStyle;
        TAK::Engine::Feature::GeometryPtr geom;
        std::list<jobject> managedRefs;
    };

    bool FeatureDataSource2_class_init(JNIEnv *env) NOTHROWS;

    void raw_data_text_deleter(const FeatureDefinition2::RawData *value);
    void raw_data_binary_deleter(const FeatureDefinition2::RawData *value);

    template<class T>
    void raw_data_object_deleter(const FeatureDefinition2::RawData *value);

    void raw_data_object_leaker(const FeatureDefinition2::RawData *value);
}

ManagedFeatureDataSource2::ManagedFeatureDataSource2(JNIEnv *env_, jobject impl_) NOTHROWS :
    impl(env_->NewGlobalRef(impl_))
{
    static bool clinit = FeatureDataSource2_class_init(env_);

    JNIStringUTF jname(*env_, (jstring)env_->CallObjectMethod(impl, FeatureDataSource2_class.getName));
    name = jname;
}
ManagedFeatureDataSource2::~ManagedFeatureDataSource2() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
    }
}
TAKErr ManagedFeatureDataSource2::parse(ContentPtr &content, const char *file) NOTHROWS
{
    LocalJNIEnv env;
    jclass File_class = ATAKMapEngineJNI_findClass(env, "java/io/File");
    if(!File_class)
        return TE_Err;
    jmethodID File_ctor = env->GetMethodID(File_class, "<init>", "(Ljava/lang/String;)V");
    if(!File_ctor)
        return TE_Err;
    jstring jpath = env->NewStringUTF(file);
    jobject jfile = env->NewObject(File_class, File_ctor, jpath);
    if(!jfile)
        return TE_Err;
    jobject result = env->CallObjectMethod(impl, FeatureDataSource2_class.parse, jfile);
    if(!result)
        return TE_InvalidArg;
    content = ContentPtr(new ManagedContent(env, result), Memory_deleter_const<FeatureDataSource2::Content, ManagedContent>);
    return TE_Ok;
}

TAK::Engine::Feature::AltitudeMode ManagedFeatureDataSource2::getAltitudeMode() const NOTHROWS
{
    return altMode;
}

double ManagedFeatureDataSource2::getExtrude() const NOTHROWS 
{
    return extrude;
}

const char *ManagedFeatureDataSource2::getName() const NOTHROWS
{
    return name;
}

int ManagedFeatureDataSource2::parseVersion() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(impl, FeatureDataSource2_class.parseVersion);
}

namespace
{
    ManagedContent::ManagedContent(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_)),
        feature(NULL, NULL)
    {
        do {
            if(env_->ExceptionCheck())
                break;
            jstring result = (jstring)env_->CallObjectMethod(impl, FeatureDataSource_Content_class.getType);
            if(env_->ExceptionCheck())
                break;
            if(result) {
                JNIStringUTF jresult(*env_, result);
                type = jresult;
            }
        } while(false);
        do {
            if(env_->ExceptionCheck())
                break;
            jstring result = (jstring)env_->CallObjectMethod(impl, FeatureDataSource_Content_class.getProvider);
            if(env_->ExceptionCheck())
                break;
            if(result) {
                JNIStringUTF jresult(*env_, result);
                provider = jresult;
            }
        } while(false);
    }
    ManagedContent::~ManagedContent() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            if(!env->ExceptionCheck())
                env->CallVoidMethod(impl, FeatureDataSource_Content_class.close);
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    const char *ManagedContent::getType() const NOTHROWS
    {
        return type;
    }
    const char *ManagedContent::getProvider() const NOTHROWS
    {
        return provider;
    }
    TAKErr ManagedContent::moveToNextFeature() NOTHROWS
    {
        feature.reset();

        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        jboolean result = env->CallBooleanMethod(impl, FeatureDataSource_Content_class.moveToNext, FeatureDataSource_Content_class.ContentPointer_enum.FEATURE);
        if(env->ExceptionCheck())
            return TE_Err;
        if(!result)
            return TE_Done;

        Java::JNILocalRef jfdefn(*env, env->CallObjectMethod(impl, FeatureDataSource_Content_class.get));
        if(jfdefn)
            feature = FeatureDefinitionPtr(new ManagedFeatureDefinition(env, jfdefn), Memory_deleter_const<FeatureDefinition2, ManagedFeatureDefinition>);

        return TE_Ok;
    }
    TAKErr ManagedContent::moveToNextFeatureSet() NOTHROWS
    {
        feature.reset();

        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        jboolean result = env->CallBooleanMethod(impl, FeatureDataSource_Content_class.moveToNext, FeatureDataSource_Content_class.ContentPointer_enum.FEATURE_SET);
        if(env->ExceptionCheck())
            return TE_Err;
        return result ? TE_Ok : TE_Done;
    }
    TAKErr ManagedContent::get(FeatureDefinition2 **value) const NOTHROWS
    {
        *value = feature.get();
        return TE_Ok;
    }
    TAKErr ManagedContent::getFeatureSetName(TAK::Engine::Port::String &value) const NOTHROWS
    {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        Java::JNILocalRef result(*env, env->CallObjectMethod(impl, FeatureDataSource_Content_class.getFeatureSetName));
        if(env->ExceptionCheck())
            return TE_Err;
        if(!result) {
            value = NULL;
            return TE_Ok;
        } else {
            JNIStringUTF jresult(*env, result);
            value = jresult;
            return TE_Ok;
        }
    }
    TAKErr ManagedContent::getMinResolution(double *value) const NOTHROWS
    {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        jdouble result = env->CallDoubleMethod(impl, FeatureDataSource_Content_class.getMinResolution);
        if(env->ExceptionCheck())
            return TE_Err;
            *value = result;
        return TE_Ok;
    }
    TAKErr ManagedContent::getMaxResolution(double *value) const NOTHROWS
    {
        LocalJNIEnv env;
        jdouble result = env->CallDoubleMethod(impl, FeatureDataSource_Content_class.getMaxResolution);
        if(env->ExceptionCheck())
            return TE_Err;
            *value = result;
        return TE_Ok;
    }
    TAKErr ManagedContent::getVisible(bool *value) const NOTHROWS
    {
        *value = true;
        return TE_Ok;
    }
    TAKErr ManagedContent::getFeatureSetVisible(bool *value) const NOTHROWS
    {
        *value = true;
        return TE_Ok;
    }

    ManagedFeatureDefinition::ManagedFeatureDefinition(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_)),
        feature(NULL, NULL),
        rawGeom(NULL, NULL),
        rawStyle(NULL, NULL),
        geom(NULL, NULL)
    {}
    ManagedFeatureDefinition::~ManagedFeatureDefinition() NOTHROWS
    {
        LocalJNIEnv env;
        if(impl) {
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }

        for(auto it = managedRefs.begin(); it != managedRefs.end(); it++) {
            env->DeleteGlobalRef(*it);
        }
        managedRefs.clear();
    }
    TAKErr ManagedFeatureDefinition::getRawGeometry(RawData *value) NOTHROWS
    {
        if(!rawGeom.get()) {
            LocalJNIEnv env;
            if(env->ExceptionCheck())
                return TE_Err;
            jint geomCoding = env->GetIntField(impl, FeatureDefinition_class.geomCoding);
            if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_WKT) {
                Java::JNILocalRef result(*env, (jstring)env->GetObjectField(impl, FeatureDefinition_class.rawGeom));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawGeom = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_text_deleter);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));

                    JNIStringUTF jtext(*env, result);
                    const std::size_t len = strlen(jtext);
                    array_ptr<char> ctext(new char[len+1u]);
                    strcpy(ctext.get(), jtext);
                    ctext[len] = '\0';
                    rawGeom->text = ctext.release();
                }
            } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_WKB) {
                Java::JNILocalRef result(*env, (jbyteArray)env->GetObjectField(impl, FeatureDefinition_class.rawGeom));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawGeom = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_binary_deleter);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));

                    JNIByteArray jbinary(*env, (jbyteArray)result.get(), JNI_ABORT);
                    rawGeom->binary.len = jbinary.length();
                    array_ptr<uint8_t> cbinary(new uint8_t[rawGeom->binary.len]);
                    const jbyte *jb = jbinary;
                    memcpy(cbinary.get(), jb, rawGeom->binary.len);
                    rawGeom->binary.value = cbinary.release();
                }
            } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_SPATIALITE_BLOB) {
                Java::JNILocalRef result(*env, (jbyteArray)env->GetObjectField(impl, FeatureDefinition_class.rawGeom));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawGeom = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_binary_deleter);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));

                    JNIByteArray jbinary(*env, (jbyteArray)result.get(), JNI_ABORT);
                    rawGeom->binary.len = jbinary.length();
                    array_ptr<uint8_t> cbinary(new uint8_t[rawGeom->binary.len]);
                    const jbyte *jb = jbinary;
                    memcpy(cbinary.get(), jb, rawGeom->binary.len);
                    rawGeom->binary.value = cbinary.release();
                }
            } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_ATAK_GEOMETRY) {
                Java::JNILocalRef result(*env, env->GetObjectField(impl, FeatureDefinition_class.rawGeom));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawGeom = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_object_leaker);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));

                    Geometry2 *cgeom;
                    TAKErr code = Interop_get(&cgeom, env, result);
                    TE_CHECKRETURN_CODE(code);
                    code = LegacyAdapters_adapt(geom, *cgeom);
                    TE_CHECKRETURN_CODE(code);
                    rawGeom->object = geom.get();
                }
            } else {
                return TE_Err;
            }
        }
        *value = *rawGeom;
        return TE_Ok;
    }
    FeatureDefinition2::GeometryEncoding ManagedFeatureDefinition::getGeomCoding() NOTHROWS
    {
        LocalJNIEnv env;
        jint geomCoding = env->GetIntField(impl, FeatureDefinition_class.geomCoding);
        if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_WKT) {
            return FeatureDefinition2::GeomWkt;
        } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_WKB) {
            return FeatureDefinition2::GeomWkb;
        } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_SPATIALITE_BLOB) {
            return FeatureDefinition2::GeomBlob;
        } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_GEOM_ATAK_GEOMETRY) {
            return FeatureDefinition2::GeomGeometry;
        } else {
            return FeatureDefinition2::GeomGeometry;
        }
    }
    TAKErr ManagedFeatureDefinition::getName(const char **value) NOTHROWS
    {
        if(!name) {
            LocalJNIEnv env;
            Java::JNILocalRef result(*env, (jstring)env->GetObjectField(impl, FeatureDefinition_class.name));
            if(env->ExceptionCheck())
                return TE_Err;
            if(result) {
                managedRefs.push_back(env->NewGlobalRef(result));
                JNIStringUTF jname(*env, result);
                name = jname;
            }
        }

        *value = name;
        return TE_Ok;
    }
    TAK::Engine::Feature::AltitudeMode ManagedFeatureDefinition::getAltitudeMode() NOTHROWS
    {
        return TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround;
    }
    double ManagedFeatureDefinition::getExtrude() NOTHROWS
    {
        return 0.0;
    }
    FeatureDefinition2::StyleEncoding ManagedFeatureDefinition::getStyleCoding() NOTHROWS
    {
        LocalJNIEnv env;
        jint geomCoding = env->GetIntField(impl, FeatureDefinition_class.styleCoding);
        if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_STYLE_OGR) {
            return FeatureDefinition2::StyleOgr;
        } else if(geomCoding == com_atakmap_map_layer_feature_FeatureDefinition_STYLE_ATAK_STYLE) {
            return FeatureDefinition2::StyleStyle;
        } else {
            return FeatureDefinition2::StyleStyle;
        }
    }
    TAKErr ManagedFeatureDefinition::getRawStyle(RawData *value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!rawStyle.get()) {
            LocalJNIEnv env;
            if(env->ExceptionCheck())
                return TE_Err;
            jint styleCoding = env->GetIntField(impl, FeatureDefinition_class.styleCoding);
            if(styleCoding == com_atakmap_map_layer_feature_FeatureDefinition_STYLE_OGR) {
                Java::JNILocalRef result(*env,  (jstring)env->GetObjectField(impl, FeatureDefinition_class.rawStyle));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawStyle = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_text_deleter);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));
                    JNIStringUTF jtext(*env, result);
                    const std::size_t len = strlen(jtext);
                    array_ptr<char> ctext(new char[len+1u]);
                    strcpy(ctext.get(), jtext);
                    ctext[len] = '\0';
                    rawStyle->text = ctext.release();
                }
            } else if(styleCoding == com_atakmap_map_layer_feature_FeatureDefinition_STYLE_ATAK_STYLE) {
                Java::JNILocalRef result(*env, env->GetObjectField(impl, FeatureDefinition_class.rawStyle));
                if(env->ExceptionCheck())
                    return TE_Err;
                rawStyle = std::unique_ptr<RawData, void(*)(const RawData *)>(new RawData(), raw_data_object_leaker);
                if(result) {
                    managedRefs.push_back(env->NewGlobalRef(result));
                    Style *cstyle;
                    code = Interop_get(&cstyle, env, result);
                    TE_CHECKRETURN_CODE(code);
                    rawStyle->object = cstyle;
                }
            } else {
                return TE_Err;
            }
        }
        *value = *rawStyle;
        return TE_Ok;
    }
    TAKErr ManagedFeatureDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        Java::JNILocalRef attributes(*env, env->GetObjectField(impl, FeatureDefinition_class.attributes));
        if(!attributes) {
            *value = NULL;
            return TE_Ok;
        }

        managedRefs.push_back(env->NewGlobalRef(attributes));
        return Interop_get(value, env, attributes);
    }
    TAKErr ManagedFeatureDefinition::get(const Feature2 **value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!feature.get()) {
            code = Feature_create(feature, *this);
            TE_CHECKRETURN_CODE(code);
        }
        *value = feature.get();
        return code;
    }


    bool FeatureDataSource2_class_init(JNIEnv *env) NOTHROWS
    {
        FeatureDataSource2_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/FeatureDataSource");
        FeatureDataSource2_class.getName = env->GetMethodID(FeatureDataSource2_class.id, "getName", "()Ljava/lang/String;");
        FeatureDataSource2_class.parseVersion = env->GetMethodID(FeatureDataSource2_class.id, "parseVersion", "()I");
        FeatureDataSource2_class.parse = env->GetMethodID(FeatureDataSource2_class.id, "parse", "(Ljava/io/File;)Lcom/atakmap/map/layer/feature/FeatureDataSource$Content;");

        FeatureDataSource_Content_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/FeatureDataSource$Content");
        FeatureDataSource_Content_class.getType = env->GetMethodID(FeatureDataSource_Content_class.id, "getType", "()Ljava/lang/String;");
        FeatureDataSource_Content_class.getProvider = env->GetMethodID(FeatureDataSource_Content_class.id, "getProvider", "()Ljava/lang/String;");
        FeatureDataSource_Content_class.moveToNext = env->GetMethodID(FeatureDataSource_Content_class.id, "moveToNext", "(Lcom/atakmap/map/layer/feature/FeatureDataSource$Content$ContentPointer;)Z");
        FeatureDataSource_Content_class.get = env->GetMethodID(FeatureDataSource_Content_class.id, "get", "()Lcom/atakmap/map/layer/feature/FeatureDataSource$FeatureDefinition;");
        FeatureDataSource_Content_class.getFeatureSetName = env->GetMethodID(FeatureDataSource_Content_class.id, "getFeatureSetName", "()Ljava/lang/String;");
        FeatureDataSource_Content_class.getMinResolution = env->GetMethodID(FeatureDataSource_Content_class.id, "getMinResolution", "()D");
        FeatureDataSource_Content_class.getMaxResolution = env->GetMethodID(FeatureDataSource_Content_class.id, "getMaxResolution", "()D");
        FeatureDataSource_Content_class.close = env->GetMethodID(FeatureDataSource_Content_class.id, "close", "()V");

        jclass ContentPointer_enum = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/FeatureDataSource$Content$ContentPointer");
        FeatureDataSource_Content_class.ContentPointer_enum.FEATURE = env->NewWeakGlobalRef(env->GetStaticObjectField(ContentPointer_enum, env->GetStaticFieldID(ContentPointer_enum, "FEATURE", "Lcom/atakmap/map/layer/feature/FeatureDataSource$Content$ContentPointer;")));
        FeatureDataSource_Content_class.ContentPointer_enum.FEATURE_SET = env->NewWeakGlobalRef(env->GetStaticObjectField(ContentPointer_enum, env->GetStaticFieldID(ContentPointer_enum, "FEATURE_SET", "Lcom/atakmap/map/layer/feature/FeatureDataSource$Content$ContentPointer;")));


        FeatureDefinition_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/FeatureDataSource$FeatureDefinition");
        FeatureDefinition_class.rawGeom = env->GetFieldID(FeatureDefinition_class.id, "rawGeom", "Ljava/lang/Object;");
        FeatureDefinition_class.geomCoding = env->GetFieldID(FeatureDefinition_class.id, "geomCoding", "I");
        FeatureDefinition_class.name = env->GetFieldID(FeatureDefinition_class.id, "name", "Ljava/lang/String;");
        FeatureDefinition_class.styleCoding = env->GetFieldID(FeatureDefinition_class.id, "styleCoding", "I");
        FeatureDefinition_class.rawStyle = env->GetFieldID(FeatureDefinition_class.id, "rawStyle", "Ljava/lang/Object;");
        FeatureDefinition_class.attributes = env->GetFieldID(FeatureDefinition_class.id, "attributes", "Lcom/atakmap/map/layer/feature/AttributeSet;");

        return true;
    }

    void raw_data_text_deleter(const FeatureDefinition2::RawData *value)
    {
        delete [] value->text;
        delete value;
    }
    void raw_data_binary_deleter(const FeatureDefinition2::RawData *value)
    {
        delete [] value->binary.value;
        delete value;
    }
    template<class T>
    void raw_data_object_deleter(const FeatureDefinition2::RawData *value)
    {
        const T *impl = static_cast<const T *>(value->object);
        delete impl;
        delete value;
    }
    void raw_data_object_leaker(const FeatureDefinition2::RawData *value)
    {
        delete value;
    }
}
