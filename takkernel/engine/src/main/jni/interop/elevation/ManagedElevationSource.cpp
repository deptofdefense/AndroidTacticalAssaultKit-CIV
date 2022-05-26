#include "interop/elevation/ManagedElevationSource.h"

#include <cmath>

#include <thread/Lock.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/db/Interop.h"
#include "interop/elevation/ManagedElevationChunk.h"
#include "interop/feature/Interop.h"
#include "interop/java/JNICollection.h"
#include "interop/java/JNILocalRef.h"
#include "interop/java/JNIPrimitive.h"

using namespace TAKEngineJNI::Interop::Elevation;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace {
    struct {
        jclass id;
        jmethodID getName;
        jmethodID query;
        jmethodID getBounds;
        jmethodID addOnContentChangedListener;
        jmethodID removeOnContentChangedListener;
    } ElevationSource_class;

    struct {
        jclass id;
        jmethodID ctor;
        jfieldID spatialFilter;
        jfieldID maxResolution;
        jfieldID minResolution;
        jfieldID targetResolution;
        jfieldID types;
        jfieldID authoritative;
        jfieldID minCE;
        jfieldID minLE;
        jfieldID order;
        jfieldID flags;

        struct
        {
            jobject ResolutionAsc;
            jobject ResolutionDesc;
            jobject CEAsc;
            jobject CEDesc;
            jobject LEAsc;
            jobject LEDesc;
        } Order_enum;
    } ElevationSource_QueryParameters_class;

    struct {
        jclass id;
        jmethodID get;
        jmethodID getResolution;
        jmethodID isAuthoritative;
        jmethodID getCE;
        jmethodID getLE;
        jmethodID getUri;
        jmethodID getType;
        jmethodID getBounds;
        jmethodID getFlags;
        jmethodID moveToNext;
        jmethodID close;
    } ElevationSource_Cursor_class;

    struct {
        jclass id;
        jmethodID ctor;
        jmethodID finalize;
    } NativeOnContentChangedListener_class;

    class ManagedElevationChunkCursor : public ElevationChunkCursor
    {
    public :
        ManagedElevationChunkCursor(JNIEnv *env, jobject impl) NOTHROWS;
        virtual ~ManagedElevationChunkCursor() NOTHROWS;
    public :
        virtual TAKErr moveToNext() NOTHROWS;
    public :
        virtual TAKErr get(ElevationChunkPtr &value) NOTHROWS;
        virtual TAKErr getResolution(double *value) NOTHROWS;
        virtual TAKErr isAuthoritative(bool *value) NOTHROWS;
        virtual TAKErr getCE(double *value) NOTHROWS;
        virtual TAKErr getLE(double *value) NOTHROWS;
        virtual TAKErr getUri(const char **value) NOTHROWS;
        virtual TAKErr getType(const char **value) NOTHROWS;
        virtual TAKErr getBounds(const Polygon2 **value) NOTHROWS;
        virtual TAKErr getFlags(unsigned int *value) NOTHROWS;
    public :
        jobject impl;
    private :
        TAK::Engine::Port::String uri;
        TAK::Engine::Port::String type;
        Geometry2Ptr_const bounds;
    };

    bool ElevationSource_class_init(JNIEnv *env) NOTHROWS;
}

ManagedElevationSource::ManagedElevationSource(JNIEnv *env_, jobject impl_) NOTHROWS :
    impl(env_->NewGlobalRef(impl_))
{
    static bool clinit = ElevationSource_class_init(env_);

    JNILocalRef mname(*env_, env_->CallObjectMethod(impl, ElevationSource_class.getName));
    JNIStringUTF_get(name, *env_, (jstring)mname.get());
}
ManagedElevationSource::~ManagedElevationSource() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;

        // unbind all of the registered listeners
        for(auto it = listeners.begin(); it != listeners.end(); it++) {
            // remove the listener from Java
            env->CallVoidMethod(impl, ElevationSource_class.removeOnContentChangedListener, it->second);
            // finalize
            env->CallVoidMethod(it->second, NativeOnContentChangedListener_class.finalize);
            env->DeleteGlobalRef(it->second);
        }
        listeners.clear();

        env->DeleteGlobalRef(impl);
    }
}
const char *ManagedElevationSource::getName() const NOTHROWS
{
    return name;
}
TAKErr ManagedElevationSource::query(ElevationChunkCursorPtr &value, const ElevationSource::QueryParameters &cparams) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!impl)
        return TE_IllegalState;

    LocalJNIEnv env;
    if (env->ExceptionCheck())
        return TE_Err;

    JNILocalRef mparams(*env, env->NewObject(ElevationSource_QueryParameters_class.id, ElevationSource_QueryParameters_class.ctor));
    if(cparams.spatialFilter.get()) {
        env->SetObjectField(mparams, ElevationSource_QueryParameters_class.spatialFilter, Feature::Interop_create(env, *cparams.spatialFilter));
    }
    env->SetDoubleField(mparams, ElevationSource_QueryParameters_class.maxResolution, cparams.maxResolution);
    env->SetDoubleField(mparams, ElevationSource_QueryParameters_class.minResolution, cparams.minResolution);
    env->SetDoubleField(mparams, ElevationSource_QueryParameters_class.targetResolution, cparams.targetResolution);
    if(cparams.types.get()) {
        JNILocalRef mparams_types = Java::JNICollection_create(*env, Java::HashSet);
        if(!cparams.types->empty()) {
            using namespace TAK::Engine::Port;
            Collection<String>::IteratorPtr iter(NULL, NULL);
            code = cparams.types->iterator(iter);
            TE_CHECKRETURN_CODE(code);

            do {
                String cstring;
                code = iter->get(cstring);
                TE_CHECKBREAK_CODE(code);
                JNILocalRef mname(*env, env->NewStringUTF(cstring));
                code = Java::JNICollection_add(*env, mparams_types, (jstring)(jobject)mname);
                TE_CHECKBREAK_CODE(code);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while(true);
            if(code == TE_Done)
                code = TE_Ok;
            TE_CHECKRETURN_CODE(code);
        }
        env->SetObjectField(mparams, ElevationSource_QueryParameters_class.types, mparams_types);
    }
    if(cparams.authoritative.get()) {
        JNILocalRef mauthoritative = Java::Boolean_valueOf(*env, *cparams.authoritative);
        env->SetObjectField(mparams, ElevationSource_QueryParameters_class.authoritative, mauthoritative.get());
    }
    env->SetDoubleField(mparams, ElevationSource_QueryParameters_class.minCE, cparams.minCE);
    env->SetDoubleField(mparams, ElevationSource_QueryParameters_class.minLE, cparams.minLE);
    if(cparams.order.get()) {
        JNILocalRef mparams_order = Java::JNICollection_create(*env, Java::LinkedList);
        if(!cparams.order->empty()) {
            using namespace TAK::Engine::Port;
            Collection<ElevationSource::QueryParameters::Order>::IteratorPtr iter(NULL, NULL);
            code = cparams.order->iterator(iter);
            TE_CHECKRETURN_CODE(code);

            do {
                ElevationSource::QueryParameters::Order corder;
                code = iter->get(corder);
                TE_CHECKBREAK_CODE(code);
                jobject morder = NULL;
                switch(corder) {
                    case ElevationSource::QueryParameters::ResolutionAsc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.ResolutionAsc;
                        break;
                    case ElevationSource::QueryParameters::ResolutionDesc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.ResolutionDesc;
                        break;
                    case ElevationSource::QueryParameters::CEAsc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.CEAsc;
                        break;
                    case ElevationSource::QueryParameters::CEDesc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.CEDesc;
                        break;
                    case ElevationSource::QueryParameters::LEAsc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.LEAsc;
                        break;
                    case ElevationSource::QueryParameters::LEDesc:
                        morder = ElevationSource_QueryParameters_class.Order_enum.LEDesc;
                        break;
                    default :
                        return TE_InvalidArg;
                }
                code = Java::JNICollection_add(*env, mparams_order, morder);
                TE_CHECKBREAK_CODE(code);
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while(true);
            if(code == TE_Done)
                code = TE_Ok;
            TE_CHECKRETURN_CODE(code);
        }
        env->SetObjectField(mparams, ElevationSource_QueryParameters_class.order, mparams_order);
    }
    if (cparams.flags.get()) {
        JNILocalRef mflags = Java::Integer_valueOf(*env, *cparams.flags);
        env->SetObjectField(mparams, ElevationSource_QueryParameters_class.flags, mflags.get());
    }

    JNILocalRef mresult(*env, env->CallObjectMethod(impl, ElevationSource_class.query, mparams.get()));
    if(!mresult)
        return TE_Err;
    if(env->ExceptionCheck())
        return TE_Err;

    value = ElevationChunkCursorPtr(new ManagedElevationChunkCursor(env, mresult), Memory_deleter_const<ElevationChunkCursor, ManagedElevationChunkCursor>);
    return TE_Ok;
}
Envelope2 ManagedElevationSource::getBounds() const NOTHROWS
{
    LocalJNIEnv env;
    Envelope2 bounds(NAN, NAN, NAN, NAN, NAN, NAN);
    JNILocalRef mbounds(*env, env->CallObjectMethod(impl, ElevationSource_class.getBounds));
    Feature::Interop_copy(&bounds, env, mbounds);
    return bounds;
}
TAKErr ManagedElevationSource::addOnContentChangedListener(TAK::Engine::Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!l)
        return TE_InvalidArg;

    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    // already registered
    if(listeners.find(l) != listeners.end())
        return TE_Ok;

    LocalJNIEnv env;

    if(env->ExceptionCheck())
        return TE_Err;

    std::unique_ptr<ElevationSource::OnContentChangedListener, void(*)(const ElevationSource::OnContentChangedListener *)> clistener(l, Memory_leaker_const<ElevationSource::OnContentChangedListener>);
    JNILocalRef mlistener(*env, env->NewObject(NativeOnContentChangedListener_class.id, NativeOnContentChangedListener_class.ctor, NewPointer(env, std::move(clistener)), INTPTR_TO_JLONG(this)));
    if(env->ExceptionCheck())
        return TE_Err;

    listeners[l] = env->NewGlobalRef(mlistener);
    lock.reset();

    env->CallVoidMethod(impl, ElevationSource_class.addOnContentChangedListener, mlistener.get());
    return code;
}
TAKErr ManagedElevationSource::removeOnContentChangedListener(TAK::Engine::Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!l)
        return TE_InvalidArg;

    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    // not registered
    auto entry = listeners.find(l);
    if(entry == listeners.end())
        return TE_Ok;

    jobject mlistener = entry->second;
    listeners.erase(entry);
    lock.reset();

    LocalJNIEnv env;

    if(env->ExceptionCheck())
        return TE_Err;
    // remove the listener from Java
    env->CallVoidMethod(impl, ElevationSource_class.removeOnContentChangedListener, mlistener);
    if(env->ExceptionCheck())
        return TE_Err;
    // finalize
    env->CallVoidMethod(mlistener, NativeOnContentChangedListener_class.finalize);
    if(env->ExceptionCheck())
        return TE_Err;
    env->DeleteGlobalRef(mlistener);
    return code;
}

namespace {
    ManagedElevationChunkCursor::ManagedElevationChunkCursor(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_)),
        bounds(NULL, NULL)
    {}
    ManagedElevationChunkCursor::~ManagedElevationChunkCursor() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            DB::RowIterator_close(env, impl);
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    TAKErr ManagedElevationChunkCursor::moveToNext() NOTHROWS
    {
        // reset the row data
        type = NULL;
        uri = NULL;
        bounds.reset();

        LocalJNIEnv env;
        return DB::RowIterator_moveToNext(env, impl);
    }
    TAKErr ManagedElevationChunkCursor::get(ElevationChunkPtr &value) NOTHROWS
    {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        JNILocalRef mchunk(*env, env->CallObjectMethod(impl, ElevationSource_Cursor_class.get));
        if(!mchunk)
            return TE_Err;
        if(env->ExceptionCheck())
            return TE_Err;

        // XXX - check to see if it's a native wrap...not well suited with unique_ptr

        value = ElevationChunkPtr(new ManagedElevationChunk(env, mchunk), Memory_deleter_const<ElevationChunk, ManagedElevationChunk>);
        return TE_Ok;
    }
    TAKErr ManagedElevationChunkCursor::getResolution(double *value) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        *value = env->CallDoubleMethod(impl, ElevationSource_Cursor_class.getResolution);
        if(env->ExceptionCheck())
            return TE_Err;
        return TE_Ok;
    }
    TAKErr ManagedElevationChunkCursor::isAuthoritative(bool *value) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        *value = env->CallBooleanMethod(impl, ElevationSource_Cursor_class.isAuthoritative);
        if(env->ExceptionCheck())
            return TE_Err;
        return TE_Ok;
    }
    TAKErr ManagedElevationChunkCursor::getCE(double *value) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        *value = env->CallDoubleMethod(impl, ElevationSource_Cursor_class.getCE);
        if(env->ExceptionCheck())
            return TE_Err;
        return TE_Ok;
    }
    TAKErr ManagedElevationChunkCursor::getLE(double *value) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        *value = env->CallDoubleMethod(impl, ElevationSource_Cursor_class.getLE);
        if(env->ExceptionCheck())
            return TE_Err;
        return TE_Ok;
    }
    TAKErr ManagedElevationChunkCursor::getUri(const char **value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        JNILocalRef muri(*env, env->CallObjectMethod(impl, ElevationSource_Cursor_class.getUri));
        code = JNIStringUTF_get(uri, *env, (jstring)muri);
        TE_CHECKRETURN_CODE(code);
        *value = uri;
        return code;
    }
    TAKErr ManagedElevationChunkCursor::getType(const char **value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        JNILocalRef mtype(*env, env->CallObjectMethod(impl, ElevationSource_Cursor_class.getType));
        code = JNIStringUTF_get(uri, *env, (jstring)mtype);
        TE_CHECKRETURN_CODE(code);
        *value = uri;
        return code;
    }
    TAKErr ManagedElevationChunkCursor::getBounds(const Polygon2 **value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!value)
            return TE_InvalidArg;
        if(!bounds.get()) {
            LocalJNIEnv env;
            if (env->ExceptionCheck())
                return TE_Err;
            JNILocalRef mbounds(*env, env->CallObjectMethod(impl, ElevationSource_Cursor_class.getBounds));
            code = Feature::Interop_create(bounds, env, mbounds);
            TE_CHECKRETURN_CODE(code);
        }
        if(!bounds.get()) {
            *value = NULL;
        } else if(bounds->getClass() == TEGC_Polygon) {
            *value = static_cast<const Polygon2 *>(bounds.get());
        } else {
            return TE_IllegalState;
        }
        return code;
    }
    TAKErr ManagedElevationChunkCursor::getFlags(unsigned int *value) NOTHROWS
    {
        if(!value)
            return TE_InvalidArg;
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        *value = env->CallIntMethod(impl, ElevationSource_Cursor_class.getFlags);
        if(env->ExceptionCheck())
            return TE_Err;
        return TE_Ok;
    }

    bool ElevationSource_class_init(JNIEnv *env) NOTHROWS
    {
        ElevationSource_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationSource");
        ElevationSource_class.getName = env->GetMethodID(ElevationSource_class.id, "getName", "()Ljava/lang/String;");
        ElevationSource_class.query = env->GetMethodID(ElevationSource_class.id, "query", "(Lcom/atakmap/map/elevation/ElevationSource$QueryParameters;)Lcom/atakmap/map/elevation/ElevationSource$Cursor;");
        ElevationSource_class.getBounds = env->GetMethodID(ElevationSource_class.id, "getBounds", "()Lcom/atakmap/map/layer/feature/geometry/Envelope;");
        ElevationSource_class.addOnContentChangedListener = env->GetMethodID(ElevationSource_class.id, "addOnContentChangedListener", "(Lcom/atakmap/map/elevation/ElevationSource$OnContentChangedListener;)V");
        ElevationSource_class.removeOnContentChangedListener = env->GetMethodID(ElevationSource_class.id, "removeOnContentChangedListener", "(Lcom/atakmap/map/elevation/ElevationSource$OnContentChangedListener;)V");

        ElevationSource_QueryParameters_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationSource$QueryParameters");
        ElevationSource_QueryParameters_class.ctor = env->GetMethodID(ElevationSource_class.id, "<init>", "()V");
        ElevationSource_QueryParameters_class.spatialFilter = env->GetFieldID(ElevationSource_QueryParameters_class.id, "spatialFilter", "Lcom/atakmap/map/layer/feature/geometry/Geometry;");
        ElevationSource_QueryParameters_class.maxResolution = env->GetFieldID(ElevationSource_QueryParameters_class.id, "maxResolution", "D");
        ElevationSource_QueryParameters_class.minResolution = env->GetFieldID(ElevationSource_QueryParameters_class.id, "minResolution", "D");
        ElevationSource_QueryParameters_class.targetResolution = env->GetFieldID(ElevationSource_QueryParameters_class.id, "targetResolution", "D");
        ElevationSource_QueryParameters_class.types = env->GetFieldID(ElevationSource_QueryParameters_class.id, "types", "Ljava/util/Set;");
        ElevationSource_QueryParameters_class.authoritative = env->GetFieldID(ElevationSource_QueryParameters_class.id, "authoritative", "Ljava/lang/Boolean;");
        ElevationSource_QueryParameters_class.minCE = env->GetFieldID(ElevationSource_QueryParameters_class.id, "minCE", "D");
        ElevationSource_QueryParameters_class.minLE = env->GetFieldID(ElevationSource_QueryParameters_class.id, "minLE", "D");
        ElevationSource_QueryParameters_class.order = env->GetFieldID(ElevationSource_QueryParameters_class.id, "order", "Ljava/util/List;");
        ElevationSource_QueryParameters_class.flags = env->GetFieldID(ElevationSource_QueryParameters_class.id, "flags", "Ljava/lang/Integer;");

        jclass Order_enum = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationSource$QueryParameters$Order");
        ElevationSource_QueryParameters_class.Order_enum.CEAsc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "CEAsc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));
        ElevationSource_QueryParameters_class.Order_enum.CEDesc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "CEDesc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));
        ElevationSource_QueryParameters_class.Order_enum.LEAsc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "LEAsc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));
        ElevationSource_QueryParameters_class.Order_enum.LEDesc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "LEDesc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));
        ElevationSource_QueryParameters_class.Order_enum.ResolutionAsc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "ResolutionAsc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));
        ElevationSource_QueryParameters_class.Order_enum.ResolutionDesc = env->NewWeakGlobalRef(env->GetStaticObjectField(Order_enum, env->GetStaticFieldID(Order_enum, "ResolutionDesc", "Lcom/atakmap/map/elevation/ElevationSource$QueryParameters$Order;")));

        ElevationSource_Cursor_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationSource$Cursor");
        ElevationSource_Cursor_class.get = env->GetMethodID(ElevationSource_Cursor_class.id, "get", "()Lcom/atakmap/map/elevation/ElevationChunk;");
        ElevationSource_Cursor_class.getResolution = env->GetMethodID(ElevationSource_Cursor_class.id, "getResolution", "()D");
        ElevationSource_Cursor_class.isAuthoritative = env->GetMethodID(ElevationSource_Cursor_class.id, "isAuthoritative", "()Z");
        ElevationSource_Cursor_class.getCE = env->GetMethodID(ElevationSource_Cursor_class.id, "getCE", "()D");
        ElevationSource_Cursor_class.getLE = env->GetMethodID(ElevationSource_Cursor_class.id, "getLE", "()D");
        ElevationSource_Cursor_class.getUri = env->GetMethodID(ElevationSource_Cursor_class.id, "getUri", "()Ljava/lang/String;");
        ElevationSource_Cursor_class.getType = env->GetMethodID(ElevationSource_Cursor_class.id, "getType", "()Ljava/lang/String;");
        ElevationSource_Cursor_class.getBounds = env->GetMethodID(ElevationSource_Cursor_class.id, "getBounds", "()Lcom/atakmap/map/layer/feature/geometry/Geometry;");
        ElevationSource_Cursor_class.getFlags = env->GetMethodID(ElevationSource_Cursor_class.id, "getFlags", "()I");

        NativeOnContentChangedListener_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/NativeElevationSource$NativeOnContentChangedListener");
        NativeOnContentChangedListener_class.ctor = env->GetMethodID(NativeOnContentChangedListener_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;J)V");
        NativeOnContentChangedListener_class.finalize = env->GetMethodID(NativeOnContentChangedListener_class.id, "finalize", "()V");

        return true;
    }
}
