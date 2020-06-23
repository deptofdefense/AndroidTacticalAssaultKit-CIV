#ifndef TAKENGINEJNI_INTEROP_POINTER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_POINTER_H_INCLUDED

#include <memory>

#include <jni.h>

#include <util/Logging2.h>

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
            jmethodID ctor__JJI;
        } Pointer_class;

        template<class T>
        void Pointer_destruct(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        void Pointer_destruct_iface(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        T *Pointer_get(JNIEnv &env, jobject jpointer) NOTHROWS;
        template<class T>
        T *Pointer_get(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        bool Pointer_makeShared(JNIEnv *env, jobject jpointer) NOTHROWS;
        template<class T>
        bool Pointer_isShared(JNIEnv *env, jobject jpointer) NOTHROWS;
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
            if(type == com_atakmap_interop_Pointer_RAW) {
                delete JLONG_TO_INTPTR(T, pointer);
            } else if(type == com_atakmap_interop_Pointer_UNIQUE) {
                std::unique_ptr<T, void (*)(const T *)> *cpointer = (std::unique_ptr < T, void(*)(const T *)> *)(intptr_t)pointer;
                delete cpointer;
            } else if(type == com_atakmap_interop_Pointer_SHARED) {
                delete JLONG_TO_INTPTR(std::shared_ptr<T>, pointer);
            } else {
                // log warning
                TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Warning, "Pointer_destruct called with invalid type, leaking");
            }

            env->SetLongField(jpointer, Pointer_class.value, 0LL);
            env->SetLongField(jpointer, Pointer_class.raw, 0LL);
        }


        template<class T>
        inline void Pointer_destruct_iface(JNIEnv *env, jobject jpointer) NOTHROWS
        {
            if(!jpointer)
                return;
            jlong pointer = env->GetLongField(jpointer, Pointer_class.value);
            jint type = env->GetIntField(jpointer, Pointer_class.type);
            if(type == com_atakmap_interop_Pointer_RAW) {
                // can't delete on iface, log warning
                TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Warning,
                "Pointer_destruct_iface called with raw pointer, leaking");
            } else if(type == com_atakmap_interop_Pointer_UNIQUE) {
                std::unique_ptr<T, void (*)(const T *)> *cpointer = (std::unique_ptr < T, void(*)(const T *)> *)(intptr_t) pointer;
                delete cpointer;
            } else if(type == com_atakmap_interop_Pointer_SHARED) {
                delete JLONG_TO_INTPTR(std::shared_ptr<T>, pointer);
            } else {
                // log warning
                TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Warning, "Pointer_destruct_iface called with invalid type, leaking");
            }
            
            env->SetLongField(jpointer, Pointer_class.value, 0LL);
            env->SetLongField(jpointer, Pointer_class.raw, 0LL);
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
        inline jobject NewPointer(JNIEnv *env, std::unique_ptr<T, void (*)(const T *)> &&ptr) NOTHROWS
        {
            if(env->IsSameObject(Pointer_class.id, NULL))
                env->ExceptionCheck();
            T *raw = ptr.get();
#if 0
            return env->NewObject((jclass)0x12, Pointer_class.ctor__JJI);
#else
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJI,
                                  (jlong)(intptr_t) (new  std::unique_ptr<T, void (*)(const T *)> (std::move(ptr))),
                                  INTPTR_TO_JLONG(raw),
                                  com_atakmap_interop_Pointer_UNIQUE);
#endif
        }
        
        template<class T>
        inline jobject NewPointer(JNIEnv *env, const std::shared_ptr<T> &ptr) NOTHROWS
        {
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJI,
                                  INTPTR_TO_JLONG(new std::shared_ptr<T>(ptr)),
                                  INTPTR_TO_JLONG(ptr.get()),
                                  com_atakmap_interop_Pointer_SHARED);
        }
        
        template<class T>
        inline jobject NewPointer(JNIEnv *env, T *ptr) NOTHROWS
        {
            return env->NewObject(Pointer_class.id,
                                  Pointer_class.ctor__JJI,
                                  INTPTR_TO_JLONG(ptr),
                                  INTPTR_TO_JLONG(ptr),
                                  com_atakmap_interop_Pointer_RAW);
        }

        template<class T>
        inline bool Pointer_makeShared(JNIEnv *env, jobject mpointer) NOTHROWS
        {
            jint type = env->GetIntField(mpointer, Pointer_class.type);
            jint ptr = env->GetLongField(mpointer, Pointer_class.value);
            if(type == com_atakmap_interop_Pointer_UNIQUE) {
                typedef std::unique_ptr<T, void(*)(const T *)> TPtr;
                TPtr *cpointer = JLONG_TO_INTPTR(TPtr, ptr);

                // promote to shared pointer
                std::shared_ptr<T> *model = new std::shared_ptr<T>(std::move(*cpointer));
                env->SetLongField(mpointer, Pointer_class.value, INTPTR_TO_JLONG(model));
                env->SetIntField(mpointer, Pointer_class.type, com_atakmap_interop_Pointer_SHARED);
                return true;
            } else if(type == com_atakmap_interop_Pointer_SHARED) {
                // already shared
                return true;
            } else {
                return false;
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
