#include "interop/feature/Interop.h"

#include <util/AttributeSet.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    template<class T>
    class InteropImpl
    {
    public :
        InteropImpl(JNIEnv &env, const char *classname, TAKErr (*cloneFn_)(std::unique_ptr<T, void(*)(const T *)> &, const T &) NOTHROWS) NOTHROWS;
    public :
        TAKErr create(std::unique_ptr<T, void(*)(const T *)> &value, JNIEnv &env, jobject managed) NOTHROWS;
        TAKErr get(std::shared_ptr<T> &value, JNIEnv &env, jobject managed) NOTHROWS;
        TAKErr get(T **value, JNIEnv &env, jobject managed) NOTHROWS;
    private :
        jfieldID pointerFieldId;
        TAKErr (*cloneFn)(std::unique_ptr<T, void(*)(const T *)> &, const T &) NOTHROWS;
    };

    struct
    {
        jclass id;
        jfieldID minX;
        jfieldID minY;
        jfieldID minZ;
        jfieldID maxX;
        jfieldID maxY;
        jfieldID maxZ;
        jmethodID ctor__DDDDDD;
    } Envelope_class;

    TAKErr geometry_clone(Geometry2Ptr &value, const Geometry2 &geom) NOTHROWS;
    TAKErr style_clone(TAK::Engine::Feature::StylePtr &value, const Style &style) NOTHROWS;
    TAKErr attributeset_clone(AttributeSetPtr &value, const AttributeSet &attrs) NOTHROWS;
    InteropImpl<Geometry2> &geometryInterop(JNIEnv &env) NOTHROWS;
    InteropImpl<Style> &styleInterop(JNIEnv &env) NOTHROWS;
    InteropImpl<AttributeSet> &attributeSetInterop(JNIEnv &env) NOTHROWS;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Feature_interop_init(JNIEnv &env) NOTHROWS;
}

#define INTEROP_FN_DEFNS(pclass, interopfn) \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_create(std::unique_ptr<pclass, void(*)(const pclass *)> &value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        InteropImpl<pclass> &impl = interopfn(*env); \
        return impl.create(value, *env, managed); \
    } \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_create(std::unique_ptr<const pclass, void(*)(const pclass *)> &value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        std::unique_ptr<pclass, void(*)(const pclass *)> native(NULL, NULL); \
        code = Interop_create(native, env, managed); \
        TE_CHECKRETURN_CODE(code); \
        value = std::unique_ptr<const pclass, void(*)(const pclass *)>(native.release(), native.get_deleter()); \
        return code; \
    } \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_get(pclass **value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        InteropImpl<pclass> &impl = interopfn(*env); \
        return impl.get(value, *env, managed); \
    } \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_get(const pclass **value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        pclass *native; \
        code = Interop_get(&native, env, managed); \
        TE_CHECKRETURN_CODE(code); \
        *value = native; \
        return code; \
    } \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_get(std::shared_ptr<pclass> &value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        InteropImpl<pclass> &impl = interopfn(*env); \
        return impl.get(value, *env, managed); \
    } \
    TAKErr TAKEngineJNI::Interop::Feature::Interop_get(std::shared_ptr<const pclass> &value, JNIEnv *env, jobject managed) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        std::shared_ptr<pclass> svalue; \
        code = Interop_get(svalue, env, managed); \
        TE_CHECKRETURN_CODE(code); \
        value = std::const_pointer_cast<const pclass>(svalue); \
        return code; \
    }

INTEROP_FN_DEFNS(Geometry2, geometryInterop)
INTEROP_FN_DEFNS(Style, styleInterop)
INTEROP_FN_DEFNS(AttributeSet, attributeSetInterop)

#undef INTEROP_FN_DEFNS

jobject TAKEngineJNI::Interop::Feature::Interop_create(JNIEnv *env, const Geometry2 &cgeom) NOTHROWS
{
    jclass Interop_class = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/Interop");
    jmethodID Interop_createGeometry = env->GetStaticMethodID(Interop_class, "createGeometry", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)Lcom/atakmap/map/layer/feature/geometry/Geometry;");
    Geometry2Ptr retval(NULL, NULL);
    if(Geometry_clone(retval, cgeom) != TE_Ok)
        return NULL;
    return env->CallStaticObjectMethod(Interop_class, Interop_createGeometry, NewPointer(env, std::move(retval)), NULL);
}
jobject TAKEngineJNI::Interop::Feature::Interop_create(JNIEnv *env, const Style &cstyle) NOTHROWS
{
    jclass Interop_class = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/Interop");
    jmethodID Interop_createStyle = env->GetStaticMethodID(Interop_class, "createStyle", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)Lcom/atakmap/map/layer/feature/style/Style;");
    TAK::Engine::Feature::StylePtr retval(cstyle.clone(), Style::destructStyle);
    return env->CallStaticObjectMethod(Interop_class, Interop_createStyle, NewPointer(env, std::move(retval)), NULL);
}
jobject TAKEngineJNI::Interop::Feature::Interop_create(JNIEnv *env, const AttributeSet &cattr) NOTHROWS
{
    jclass AttributeSet_class = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/feature/AttributeSet");
    jmethodID AttributeSet_ctor = env->GetMethodID(AttributeSet_class, "<init>", "(Lcom/atakmap/interop/Pointer;)V");
    AttributeSetPtr retval(new AttributeSet(cattr), Memory_deleter_const<AttributeSet>);
    return env->NewObject(AttributeSet_class, AttributeSet_ctor, NewPointer(env, std::move(retval)));
}

TAKErr TAKEngineJNI::Interop::Feature::Interop_copy(Envelope2 *value, JNIEnv *env, jobject jenvelope) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!env)
        return TE_InvalidArg;
    if(!jenvelope)
        return TE_InvalidArg;
    if(!checkInit(*env))
        return TE_IllegalState;

    value->minX = env->GetDoubleField(jenvelope, Envelope_class.minX);
    value->minY = env->GetDoubleField(jenvelope, Envelope_class.minY);
    value->minZ = env->GetDoubleField(jenvelope, Envelope_class.minZ);
    value->maxX = env->GetDoubleField(jenvelope, Envelope_class.maxX);
    value->maxY = env->GetDoubleField(jenvelope, Envelope_class.maxY);
    value->maxZ = env->GetDoubleField(jenvelope, Envelope_class.maxZ);

    return TE_Ok;
}

TAK::Engine::Util::TAKErr TAKEngineJNI::Interop::Feature::Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Feature::Envelope2 &cenvelope) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!checkInit(env))
        return TE_IllegalState;

    env.SetDoubleField(value, Envelope_class.minX, cenvelope.minX);
    env.SetDoubleField(value, Envelope_class.minY, cenvelope.minY);
    env.SetDoubleField(value, Envelope_class.minZ, cenvelope.minZ);
    env.SetDoubleField(value, Envelope_class.maxX, cenvelope.maxX);
    env.SetDoubleField(value, Envelope_class.maxY, cenvelope.maxY);
    env.SetDoubleField(value, Envelope_class.maxZ, cenvelope.maxZ);

    return TE_Ok;
}
TAK::Engine::Util::TAKErr TAKEngineJNI::Interop::Feature::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Feature::Envelope2 &cenvelope) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    value = JNILocalRef(env, env.NewObject(Envelope_class.id, Envelope_class.ctor__DDDDDD, cenvelope.minX, cenvelope.minY, cenvelope.minZ, cenvelope.maxX, cenvelope.maxY, cenvelope.maxZ));
    return TE_Ok;
}

namespace
{
    template<class T>
    InteropImpl<T>::InteropImpl(JNIEnv &env, const char *classname, TAKErr (*cloneFn_)(std::unique_ptr<T, void(*)(const T *)> &, const T &) NOTHROWS) NOTHROWS :
        cloneFn(cloneFn_),
        pointerFieldId(0)
    {
        do {
            jclass clazz = ATAKMapEngineJNI_findClass(&env, classname);
            if(!clazz)
                break;
            pointerFieldId = env.GetFieldID(clazz, "pointer", "Lcom/atakmap/interop/Pointer;");
            if(!pointerFieldId)
                break;
        } while(false);
    }
    template<class T>
    TAKErr InteropImpl<T>::create(std::unique_ptr<T, void(*)(const T *)> &value, JNIEnv &env, jobject managed) NOTHROWS
    {
        TAKErr code(TE_Ok);
        T *pvalue;
        code = get(&pvalue, env, managed);
        TE_CHECKRETURN_CODE(code);
        code = cloneFn(value, *pvalue);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    template<class T>
    TAKErr InteropImpl<T>::get(std::shared_ptr<T> &value, JNIEnv &env, jobject managed) NOTHROWS
    {
        if(!pointerFieldId)
            return TE_InvalidArg;
        TAKErr code(TE_Ok);
        jobject jpointer = env.GetObjectField(managed, pointerFieldId);
        if(!jpointer)
            return TE_Err;
        if(env.GetIntField(jpointer, Pointer_class.type) == com_atakmap_interop_Pointer_SHARED) {
            jlong pointer = env.GetLongField(jpointer, Pointer_class.value);
            std::shared_ptr<T> *ptr = JLONG_TO_INTPTR(std::shared_ptr<T>, pointer);
            value = std::shared_ptr<T>(*ptr);
        } else {
            std::unique_ptr<T, void(*)(const T *)> uvalue(NULL, NULL);
            code = create(uvalue, env, managed);
            TE_CHECKRETURN_CODE(code);
            value = std::move(uvalue);
        }
        env.DeleteLocalRef(jpointer);
        return code;
    }
    template<class T>
    TAKErr InteropImpl<T>::get(T **value, JNIEnv &env, jobject managed) NOTHROWS
    {
        if(!pointerFieldId)
            return TE_InvalidArg;
        jobject jpointer = env.GetObjectField(managed, pointerFieldId);
        if(!jpointer)
            return TE_Err;
        *value = Pointer_get<T>(env, jpointer);
        env.DeleteLocalRef(jpointer);
        return TE_Ok;
    }

    TAKErr geometry_clone(Geometry2Ptr &value, const Geometry2 &geom) NOTHROWS
    {
        return Geometry_clone(value, geom);
    }
    TAKErr style_clone(TAK::Engine::Feature::StylePtr &value, const Style &style) NOTHROWS
    {
        value = TAK::Engine::Feature::StylePtr(style.clone(), Style::destructStyle);
        return TE_Ok;
    }
    TAKErr attributeset_clone(AttributeSetPtr &value, const AttributeSet &attrs) NOTHROWS
    {
        value = AttributeSetPtr(new AttributeSet(attrs), Memory_deleter_const<AttributeSet>);
        return TE_Ok;
    }

    InteropImpl<Geometry2> &geometryInterop(JNIEnv &env) NOTHROWS
    {
        static InteropImpl<Geometry2> geom(env, "com/atakmap/map/layer/feature/geometry/Geometry", geometry_clone);
        return geom;
    }
    InteropImpl<Style> &styleInterop(JNIEnv &env) NOTHROWS
    {
        static InteropImpl<Style> style(env, "com/atakmap/map/layer/feature/style/Style", style_clone);
        return style;
    }
    InteropImpl<AttributeSet> &attributeSetInterop(JNIEnv &env) NOTHROWS
    {
        static InteropImpl<AttributeSet> attr(env, "com/atakmap/map/layer/feature/AttributeSet", attributeset_clone);
        return attr;
    }

    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Feature_interop_init(env);
        return clinit;
    }
    bool Feature_interop_init(JNIEnv &env) NOTHROWS
    {
        Envelope_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/feature/geometry/Envelope");
        Envelope_class.minX = env.GetFieldID(Envelope_class.id, "minX", "D");
        Envelope_class.minY = env.GetFieldID(Envelope_class.id, "minY", "D");
        Envelope_class.minZ = env.GetFieldID(Envelope_class.id, "minZ", "D");
        Envelope_class.maxX = env.GetFieldID(Envelope_class.id, "maxX", "D");
        Envelope_class.maxY = env.GetFieldID(Envelope_class.id, "maxY", "D");
        Envelope_class.maxZ = env.GetFieldID(Envelope_class.id, "maxZ", "D");
        Envelope_class.ctor__DDDDDD = env.GetMethodID(Envelope_class.id, "<init>", "(DDDDDD)V");

        return true;
    }
}