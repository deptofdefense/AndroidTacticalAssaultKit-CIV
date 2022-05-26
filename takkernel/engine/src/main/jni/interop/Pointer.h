#ifndef TAKENGINEJNI_INTEROP_POINTER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_POINTER_H_INCLUDED

#include <memory>

#include <jni.h>

#include <util/Logging2.h>
#include <util/Memory.h>

#include "common.h"
#include "jpointer.h"

namespace TAKEngineJNI {
    namespace Interop {

        extern struct Pointer_class_defn
        {
            jclass id;
            jfieldID type;
            jfieldID value;
            jfieldID raw;
            jfieldID deleter;
            jmethodID ctor__JJIJ;
        } Pointer_class;

        template<class T>
        void Pointer_destruct(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        void Pointer_destruct_iface(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        T *Pointer_get(JNIEnv &env, jobject jpointer) NOTHROWS;
        template<class T>
        TAK::Engine::Util::TAKErr Pointer_get(std::shared_ptr<T> &value, JNIEnv &env, jobject jpointer) NOTHROWS;
        template<class T>
        T *Pointer_get(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        bool Pointer_makeShared(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        bool Pointer_isShared(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        jobject NewPointer(JNIEnv *env, T *ptr, const bool ref) NOTHROWS;
        template<class T>
        jobject NewPointer(JNIEnv *env, std::unique_ptr<T, void (*)(const T *)> &&ptr) NOTHROWS;
        template<class T>
        jobject NewPointer(JNIEnv *env, const std::shared_ptr<T> &ptr) NOTHROWS;


        template<class T>
        inline void Pointer_destruct(JNIEnv *env, jobject jpointer) NOTHROWS
        {
            if(!jpointer)
                return;
            jlong pointer = env->GetLongField(jpointer, Pointer_class.value);
            jint type = env->GetIntField(jpointer, Pointer_class.type);
            jlong deleter = env->GetLongField(jpointer, Pointer_class.deleter);
            if(type == com_atakmap_interop_Pointer_SHARED) {
                delete JLONG_TO_INTPTR(std::shared_ptr<T>, pointer);
            } else {
                typedef void(*deleter_fn)(const T *);
                const T *p = JLONG_TO_INTPTR(T, pointer);
                deleter_fn d = JLONG_TO_FNPTR(deleter_fn, deleter);
                if(d)
                    d(p);
                else // log warning
                    TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Warning, "Pointer_destruct called with no deleter, leaking");
            }

            env->SetLongField(jpointer, Pointer_class.value, 0LL);
            env->SetLongField(jpointer, Pointer_class.raw, 0LL);
            env->SetLongField(jpointer, Pointer_class.deleter, 0LL);
        }

        template<class T>
        inline void Pointer_destruct_iface(JNIEnv *env, jobject jpointer) NOTHROWS
        {
            Pointer_destruct<T>(env, jpointer);
        }

        template<class T>
        inline T *Pointer_get(JNIEnv &env, jobject jpointer) NOTHROWS
        {
            jlong pointer = env.GetLongField(jpointer, Pointer_class.raw);
            if (pointer == 0LL)
                return NULL;
            return JLONG_TO_INTPTR(T, pointer);
        }

        template<class T>
        inline T *Pointer_get(JNIEnv *env, jobject jpointer) NOTHROWS
        {
            return Pointer_get<T>(*env, jpointer);
        }
        template<class T>
        inline TAK::Engine::Util::TAKErr Pointer_get(std::shared_ptr<T> &value, JNIEnv &env, jobject mpointer) NOTHROWS
        {
            TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
            if(!mpointer)
                return TAK::Engine::Util::TE_InvalidArg;
            if(!Pointer_isShared<T>(&env, mpointer) && !Pointer_makeShared<T>(&env, mpointer))
                return TAK::Engine::Util::TE_Err;
            jlong ptr = env.GetLongField(mpointer, Pointer_class.value);
            value = *JLONG_TO_INTPTR(std::shared_ptr<T>, ptr);
            return code;
        }

        template<class T>
        inline jobject NewPointer(JNIEnv *env, std::unique_ptr<T, void (*)(const T *)> &&ptr) NOTHROWS
        {
            typedef void(*deleter_fn)(const T *);
            T *raw = ptr.release();
            deleter_fn d = ptr.get_deleter();
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJIJ,
                                  INTPTR_TO_JLONG(raw),
                                  INTPTR_TO_JLONG(raw),
                                  com_atakmap_interop_Pointer_UNIQUE,
                                  INTPTR_TO_JLONG(d));
        }

        template<class T>
        inline jobject NewPointer(JNIEnv *env, const std::shared_ptr<T> &ptr) NOTHROWS
        {
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJIJ,
                                  INTPTR_TO_JLONG(new std::shared_ptr<T>(ptr)),
                                  INTPTR_TO_JLONG(ptr.get()),
                                  com_atakmap_interop_Pointer_SHARED,
                                  0LL);
        }

        template<class T>
        inline jobject NewPointer(JNIEnv *env, T *ptr, const bool ref) NOTHROWS
        {
            typedef void(*deleter_fn)(const T *);
            deleter_fn d;
            if (ref)
                d = TAK::Engine::Util::Memory_leaker_const<T>;
            else
                d = TAK::Engine::Util::Memory_deleter_const<T>;
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJIJ,
                                  INTPTR_TO_JLONG(ptr),
                                  INTPTR_TO_JLONG(ptr),
                                  ref ? com_atakmap_interop_Pointer_REFERENCE : com_atakmap_interop_Pointer_RAW,
                                  INTPTR_TO_JLONG(d));
        }

        template<class T>
        inline bool Pointer_makeShared(JNIEnv *env, jobject mpointer) NOTHROWS
        {
            jint type = env->GetIntField(mpointer, Pointer_class.type);
            jlong ptr = env->GetLongField(mpointer, Pointer_class.value);
            jlong deleter = env->GetLongField(mpointer, Pointer_class.deleter);
            if(type == com_atakmap_interop_Pointer_REFERENCE) {
                // reference can't be shared
                return false;
            } else if(type == com_atakmap_interop_Pointer_SHARED) {
                // already shared
                return true;
            } else {
                typedef void(*deleter_fn)(const T *);
                typedef std::unique_ptr<T, void(*)(const T *)> TPtr;
                deleter_fn d =  JLONG_TO_FNPTR(deleter_fn, deleter);
                TPtr cpointer(JLONG_TO_INTPTR(T, ptr), d);

                // promote to shared pointer
                std::shared_ptr<T> *model = new std::shared_ptr<T>(std::move(cpointer));
                env->SetLongField(mpointer, Pointer_class.value, INTPTR_TO_JLONG(model));
                env->SetIntField(mpointer, Pointer_class.type, com_atakmap_interop_Pointer_SHARED);
                env->SetLongField(mpointer, Pointer_class.deleter, 0LL);
                return true;
            }
        }
        template<class T>
        inline bool Pointer_isShared(JNIEnv *env, jobject mpointer) NOTHROWS
        {
            jint type = env->GetIntField(mpointer, Pointer_class.type);
            return (type == com_atakmap_interop_Pointer_SHARED);
        }
    }
}

#endif
