#include "interop/model/Interop.h"

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"
#include "interop/JNIStringUTF.h"
#include "interop/core/Interop.h"
#include "interop/feature/Interop.h"
#include "interop/math/Interop.h"
#include "interop/java/JNICollection.h"
#include "interop/java/JNIEnum.h"
#include "interop/java/JNIIterator.h"
#include "port/STLVectorAdapter.h"
#include "model/SceneLayer.h"

#include <cassert>

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jfieldID pointer;
    } NativeMesh_class;

    struct {
        jclass id;
        jfieldID /* double */ minDisplayResolution;
        jfieldID /* double */ maxDisplayResolution;
        jfieldID /* String */ uri;
        jfieldID /* GeoPoint */ location;
        jfieldID /* PointD */ originOffset;
        jfieldID /* int */ srid;
        jfieldID /* String */ name;
        jfieldID /* String */ type;
        jfieldID /* Matrix */ localFrame;
        jfieldID /* PointD */ rotation;
        jfieldID /* PointD */ scale;
        jfieldID /* AltitudeMode */ altitudeMode;
        jfieldID /* Map<String, String> */ resourceMap;
        jfieldID /* AttributeSet */ metadata;
    } ModelInfo_class;

    struct
    {
        jobject Relative;
        jobject Absolute;
        jobject ClampToGround;
    } ModelInfoAltitudeMode_enum;

    struct
    {
        jclass id;
        jmethodID size;
        jmethodID keySet;
        jmethodID get;
        jmethodID put;
    } Map_class;

    struct
    {
        jclass id;
        jmethodID ctor;
    } HashMap_class;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Interop_class_init(JNIEnv *env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Model::Interop_access(std::shared_ptr<TAK::Engine::Model::Mesh> &value, JNIEnv *env, jobject jmesh)
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!jmesh)
        return TE_InvalidArg;
    if(!Interop_isWrapped<Mesh>(env, jmesh))
        return TE_InvalidArg;
    JNILocalRef jpointer(*env, env->GetObjectField(jmesh, NativeMesh_class.pointer));
    if(!jpointer)
        return TE_IllegalState;

    if(!Pointer_makeShared<TAK::Engine::Model::Mesh>(env, jpointer))
        return TE_IllegalState;

    jlong pointer = env->GetLongField(jpointer, Pointer_class.value);
    std::shared_ptr<Mesh> *cpointer = JLONG_TO_INTPTR(std::shared_ptr<Mesh>, pointer);

    value = *cpointer;
    return TE_Ok;
}

// template specializations
template<>
bool TAKEngineJNI::Interop::Model::Interop_isWrapped<TAK::Engine::Model::Mesh>(JNIEnv *env, jobject obj) NOTHROWS
{
    if(!checkInit(env))
        return false;
    return obj && ATAKMapEngineJNI_equals(env, env->GetObjectClass(obj), NativeMesh_class.id);
}

TAKErr TAKEngineJNI::Interop::Model::Interop_marshal(jobject &modelinfo, JNIEnv *env, const TAK::Engine::Model::SceneInfo &sceneinfo) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!modelinfo)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    if(!checkInit(env))
        return TE_IllegalState;

    /* The following fields in SceneInfo are not marshaled because ModelInfo
     * doesn't have a corresponding field.
     *      resolution
     *      aabb
     */

    TAKErr code(TE_Ok);

    env->SetDoubleField(modelinfo, ModelInfo_class.minDisplayResolution, sceneinfo.minDisplayResolution);
    env->SetDoubleField(modelinfo, ModelInfo_class.maxDisplayResolution, sceneinfo.maxDisplayResolution);

    ATAKMapEngineJNI_SetStringField(*env, modelinfo, ModelInfo_class.uri, sceneinfo.uri);
    ATAKMapEngineJNI_SetStringField(*env, modelinfo, ModelInfo_class.name, sceneinfo.name);
    ATAKMapEngineJNI_SetStringField(*env, modelinfo, ModelInfo_class.type, sceneinfo.type);

    JNILocalRef jlocation(*env, nullptr);
    if (sceneinfo.location)
    {
        jlocation = JNILocalRef(*env, Core::Interop_create(env, *sceneinfo.location.get()));
    }
    env->SetObjectField(modelinfo, ModelInfo_class.location, jlocation.get());

    env->SetObjectField(modelinfo, ModelInfo_class.originOffset, nullptr); // Native SceneInfo does not have this field.

    JNILocalRef jLocalFrame(*env, nullptr);
    if (sceneinfo.localFrame)
    {
        code = Math::Interop_marshal(jLocalFrame, *env, *sceneinfo.localFrame.get());
        TE_CHECKRETURN_CODE(code);
    }
    env->SetObjectField(modelinfo, ModelInfo_class.localFrame, jLocalFrame.get());

    env->SetObjectField(modelinfo, ModelInfo_class.rotation, nullptr); // Native SceneInfo does not have this field.
    env->SetObjectField(modelinfo, ModelInfo_class.scale, nullptr); // Native SceneInfo does not have this field.

    JNILocalRef jResourceMap(*env, nullptr);
    if (sceneinfo.resourceAliases)
    {
        code = Interop_marshal(jResourceMap, env, sceneinfo.resourceAliases);
        TE_CHECKRETURN_CODE(code);
    }
    env->SetObjectField(modelinfo, ModelInfo_class.resourceMap, jResourceMap.get());

    jobject altitudeMode = nullptr;
    if (sceneinfo.altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_Relative)
        altitudeMode = ModelInfoAltitudeMode_enum.Relative;
    else if(sceneinfo.altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_Absolute)
        altitudeMode = ModelInfoAltitudeMode_enum.Absolute;
    else if (sceneinfo.altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
        altitudeMode = ModelInfoAltitudeMode_enum.ClampToGround;
    else
        return TE_Err;  // Need to handle new AltitudeMode value.
    env->SetObjectField(modelinfo, ModelInfo_class.altitudeMode, altitudeMode);

    env->SetIntField(modelinfo, ModelInfo_class.srid, sceneinfo.srid);

    atakmap::util::AttributeSet metadata;
    code = SceneLayer::getMetadata(metadata, sceneinfo.uri.get());
    TE_CHECKRETURN_CODE_DEBUG(code);
    JNILocalRef jmetadata(*env, Feature::Interop_create(env, metadata));
    env->SetObjectField(modelinfo, ModelInfo_class.metadata, jmetadata.get());

    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Model::Interop_marshal(TAK::Engine::Model::SceneInfo &sceneinfo, JNIEnv *env, jobject modelinfo) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!modelinfo)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    if(!checkInit(env))
        return TE_IllegalState;

    /* The following fields in ModelInfo are not marshaled because SceneInfo
     * doesn't have a corresponding field.
     *      originOffset
     *      rotation
     *      scale
     */

    TAKErr code(TE_Ok);

    sceneinfo.minDisplayResolution = env->GetDoubleField(modelinfo, ModelInfo_class.minDisplayResolution);
    sceneinfo.maxDisplayResolution = env->GetDoubleField(modelinfo, ModelInfo_class.maxDisplayResolution);
    sceneinfo.resolution = 0.0; // Java ModelInfo does not have this field.

    ATAKMapEngineJNI_GetStringField(sceneinfo.uri, *env, modelinfo, ModelInfo_class.uri);
    ATAKMapEngineJNI_GetStringField(sceneinfo.name, *env, modelinfo, ModelInfo_class.name);
    ATAKMapEngineJNI_GetStringField(sceneinfo.type, *env, modelinfo, ModelInfo_class.type);

    JNILocalRef jLocaton(*env, env->GetObjectField(modelinfo, ModelInfo_class.location));
    if (!env->IsSameObject(jLocaton, nullptr))
    {
        sceneinfo.location = TAK::Engine::Core::GeoPoint2Ptr(
                new TAK::Engine::Core::GeoPoint2(), Memory_deleter_const<TAK::Engine::Core::GeoPoint2>);
        code = Core::Interop_copy(sceneinfo.location.get(), env, jLocaton);
        TE_CHECKRETURN_CODE(code);
    } else {
        sceneinfo.location.reset();
    }

    JNILocalRef jMatrix(*env, env->GetObjectField(modelinfo, ModelInfo_class.localFrame));
    if (!env->IsSameObject(jMatrix, nullptr))
    {
        TAK::Engine::Math::Matrix2Ptr localFrame(new TAK::Engine::Math::Matrix2(), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
        code = Math::Interop_copy(localFrame.get(), env, jMatrix);
        TE_CHECKRETURN_CODE(code);
        sceneinfo.localFrame = TAK::Engine::Math::Matrix2Ptr_const(localFrame.get(), localFrame.get_deleter());
        localFrame.release();
    } else {
        sceneinfo.localFrame.reset();
    }

    JNILocalRef jResourceMap(*env, env->GetObjectField(modelinfo, ModelInfo_class.resourceMap));
    if (!env->IsSameObject(jResourceMap, nullptr))
    {
        code = Interop_marshal(sceneinfo.resourceAliases, env, jResourceMap);
        TE_CHECKRETURN_CODE(code);
    } else {
        sceneinfo.resourceAliases.reset();
    }

    JNILocalRef jAltitudeMode(*env, env->GetObjectField(modelinfo, ModelInfo_class.altitudeMode));
    if (!env->IsSameObject(jAltitudeMode, nullptr))
    {
        if (env->IsSameObject(jAltitudeMode, ModelInfoAltitudeMode_enum.Relative))
            sceneinfo.altitudeMode = TAK::Engine::Feature::AltitudeMode::TEAM_Relative;
        else if (env->IsSameObject(jAltitudeMode, ModelInfoAltitudeMode_enum.Absolute))
            sceneinfo.altitudeMode = TAK::Engine::Feature::AltitudeMode::TEAM_Absolute;
        else if (env->IsSameObject(jAltitudeMode, ModelInfoAltitudeMode_enum.ClampToGround))
            sceneinfo.altitudeMode = TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround;
        else
            return TE_Err;  // Need to handle new AltitudeMode value.
    }

    sceneinfo.aabb = TAK::Engine::Feature::Envelope2Ptr(nullptr,nullptr); // Java ModelInfo does not have this field.

    sceneinfo.srid = env->GetIntField(modelinfo, ModelInfo_class.srid);

    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Model::Interop_marshal(JNILocalRef &resourceMap, JNIEnv *env, const ResourceAliasCollectionPtr &resourceAliases) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    if(!checkInit(env))
        return TE_IllegalState;

    TAKErr code(TE_Ok);

    resourceMap = JNILocalRef(*env, env->NewObject(HashMap_class.id, HashMap_class.ctor));

    // XXX - const cast....
    auto &resourceAliasesNonConst = const_cast<TAK::Engine::Port::Collection<ResourceAlias> &>(*resourceAliases);
    TAK::Engine::Port::Collection<ResourceAlias>::IteratorPtr iter(nullptr, nullptr);
    resourceAliasesNonConst.iterator(iter);
    do {
        ResourceAlias alias;
        code = iter->get(alias);
        TE_CHECKBREAK_CODE(code);

        const char *resourceRef = alias.getResourceRef();
        const char *targetPath = alias.getTargetPath();
        JNILocalRef jResourceRef(*env, env->NewStringUTF(resourceRef));
        JNILocalRef jTargetPath(*env, env->NewStringUTF(targetPath));

        JNILocalRef jretval(*env, env->CallObjectMethod(resourceMap, Map_class.put, jResourceRef.get(), jTargetPath.get()));
        code = iter->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);
    
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Model::Interop_marshal(ResourceAliasCollectionPtr &resourceAliases, JNIEnv *env, jobject resourceMap) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    if(!resourceMap)
        return TE_InvalidArg;
    if(!checkInit(env))
        return TE_IllegalState;

    std::unique_ptr<TAK::Engine::Port::STLVectorAdapter<ResourceAlias>> resourceAliasesNew(
            new TAK::Engine::Port::STLVectorAdapter<ResourceAlias>());

    JNILocalRef keySet(*env, env->CallObjectMethod(resourceMap, Map_class.keySet));

    TAKErr code(TE_Ok);
    JNILocalRef iterator(*env, nullptr);
    code = JNICollection_iterator(iterator, *env, keySet);
    TE_CHECKRETURN_CODE(code);

    bool hasNext;
    code = JNIIterator_hasNext(hasNext, *env, iterator);
    TE_CHECKRETURN_CODE(code);

    while (hasNext)
    {
        JNILocalRef key(*env, nullptr);
        code = JNIIterator_next(key, *env, iterator);
        TE_CHECKRETURN_CODE(code);

        JNILocalRef value(*env, env->CallObjectMethod(resourceMap, Map_class.get, key.get()));

        TAK::Engine::Port::String keyStr;
        TAK::Engine::Port::String valueStr;
        JNIStringUTF_get(keyStr, *env, (jstring)key);
        JNIStringUTF_get(valueStr, *env, (jstring)value);
        resourceAliasesNew->add(ResourceAlias(keyStr.get(), valueStr.get()));

        code = JNIIterator_hasNext(hasNext, *env, iterator);
        TE_CHECKRETURN_CODE(code);
    }

    resourceAliases = ResourceAliasCollectionPtr(resourceAliasesNew.release(),
                                                 Memory_deleter_const<TAK::Engine::Port::Collection<ResourceAlias>,
                    TAK::Engine::Port::STLVectorAdapter<ResourceAlias>>);

    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv *env) NOTHROWS
    {
        if(!env)
            return false;

        NativeMesh_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/model/NativeMesh");
        NativeMesh_class.pointer = env->GetFieldID(NativeMesh_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");

        ModelInfo_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/model/ModelInfo");
        ModelInfo_class.minDisplayResolution = env->GetFieldID(ModelInfo_class.id, "minDisplayResolution", "D");
        ModelInfo_class.maxDisplayResolution = env->GetFieldID(ModelInfo_class.id, "maxDisplayResolution", "D");
        ModelInfo_class.uri = env->GetFieldID(ModelInfo_class.id, "uri", "Ljava/lang/String;");
        ModelInfo_class.location = env->GetFieldID(ModelInfo_class.id, "location", "Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        ModelInfo_class.originOffset = env->GetFieldID(ModelInfo_class.id, "originOffset", "Lcom/atakmap/math/PointD;");
        ModelInfo_class.srid = env->GetFieldID(ModelInfo_class.id, "srid", "I");
        ModelInfo_class.name = env->GetFieldID(ModelInfo_class.id, "name", "Ljava/lang/String;");
        ModelInfo_class.type = env->GetFieldID(ModelInfo_class.id, "type", "Ljava/lang/String;");
        ModelInfo_class.localFrame = env->GetFieldID(ModelInfo_class.id, "localFrame", "Lcom/atakmap/math/Matrix;");
        ModelInfo_class.rotation = env->GetFieldID(ModelInfo_class.id, "rotation", "Lcom/atakmap/math/PointD;");
        ModelInfo_class.scale = env->GetFieldID(ModelInfo_class.id, "scale", "Lcom/atakmap/math/PointD;");
        ModelInfo_class.altitudeMode = env->GetFieldID(ModelInfo_class.id, "altitudeMode", "Lcom/atakmap/map/layer/model/ModelInfo$AltitudeMode;");
        ModelInfo_class.resourceMap = env->GetFieldID(ModelInfo_class.id, "resourceMap", "Ljava/util/Map;");
        ModelInfo_class.metadata = env->GetFieldID(ModelInfo_class.id, "metadata", "Lcom/atakmap/map/layer/feature/AttributeSet;");

        {
            Java::JNILocalRef enumValue(*env, nullptr);
            const char enumClass[] = "com/atakmap/map/layer/model/ModelInfo$AltitudeMode";
            if(Java::JNIEnum_value(enumValue, *env, enumClass, "Relative") != TE_Ok)
                return false;
            ModelInfoAltitudeMode_enum.Relative = env->NewWeakGlobalRef(enumValue);
            if(Java::JNIEnum_value(enumValue, *env, enumClass, "Absolute") != TE_Ok)
                return false;
            ModelInfoAltitudeMode_enum.Absolute = env->NewWeakGlobalRef(enumValue);
            if(Java::JNIEnum_value(enumValue, *env, enumClass, "ClampToGround") != TE_Ok)
                return false;
            ModelInfoAltitudeMode_enum.ClampToGround = env->NewWeakGlobalRef(enumValue);
        }

        Map_class.id = ATAKMapEngineJNI_findClass(env, "java/util/Map");
        Map_class.size = env->GetMethodID(Map_class.id, "size", "()I");
        Map_class.keySet = env->GetMethodID(Map_class.id, "keySet", "()Ljava/util/Set;");
        Map_class.get = env->GetMethodID(Map_class.id, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        Map_class.put = env->GetMethodID(Map_class.id, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

        HashMap_class.id = ATAKMapEngineJNI_findClass(env, "java/util/HashMap");
        HashMap_class.ctor = env->GetMethodID(HashMap_class.id, "<init>", "()V");

        return true;
    }
}
