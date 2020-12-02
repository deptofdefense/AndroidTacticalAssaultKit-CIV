#ifndef TAKENGINEJNI_INTEROP_MARSHALCONTEXT_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MARSHALCONTEXT_H_INCLUDED

#include <jni.h>

#include <map>

#include <port/Platform.h>
#include <thread/Lock.h>
#include <thread/Mutex.h>
#include <util/Error.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        template<class T>
        class InterfaceMarshalContext
        {
        public :
            InterfaceMarshalContext() NOTHROWS;
            ~InterfaceMarshalContext() NOTHROWS;
        public :
            void init(JNIEnv &env, jclass javaWrapperType, jfieldID javaWrapperPointerField, jmethodID javaWrapperCtor) NOTHROWS;

            template<class WrapperType>
            bool isWrapper(const T &) NOTHROWS;
            bool isWrapper(JNIEnv &env, jobject obj) NOTHROWS;

            template<class WrapperType>
            TAK::Engine::Util::TAKErr marshal(std::shared_ptr<T> &value, JNIEnv &env, jobject obj) NOTHROWS;
            template<class WrapperType>
            TAK::Engine::Util::TAKErr marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<T> &obj) NOTHROWS;
            template<class WrapperType>
            TAK::Engine::Util::TAKErr marshal(Java::JNILocalRef &value, JNIEnv &env, const T &obj) NOTHROWS;

            template<class WrapperType>
            TAK::Engine::Util::TAKErr marshal(std::unique_ptr<T, void(*)(const T *)> &value, JNIEnv &env, jobject obj) NOTHROWS;
            template<class WrapperType>
            TAK::Engine::Util::TAKErr marshal(Java::JNILocalRef &value, JNIEnv &env, std::unique_ptr<T, void(*)(const T *)> &&obj) NOTHROWS;
        private :
            template<class Ti>
            static void onShutdown(JNIEnv &env, void *opaque) NOTHROWS
            {
                InterfaceMarshalContext<Ti> &ctx = *static_cast<InterfaceMarshalContext<Ti> *>(opaque);
                for(auto it = ctx.managedImplToNativeWrapper.begin(); it != ctx.managedImplToNativeWrapper.end(); it++)
                    env.DeleteWeakGlobalRef(it->first);
                ctx.managedImplToNativeWrapper.clear();
                for(auto it = ctx.nativeImplToManagedWrapper.begin(); it != ctx.nativeImplToManagedWrapper.end(); it++)
                    env.DeleteWeakGlobalRef(it->second);
                ctx.nativeImplToManagedWrapper.clear();
            }
        private :
            std::map<jweak, std::weak_ptr<T>> managedImplToNativeWrapper;
            std::map<const T *, jweak> nativeImplToManagedWrapper;
            TAK::Engine::Thread::Mutex mutex;

            jclass javaWrapperType;
            jfieldID javaWrapperPointerField;
            jmethodID javaWrapperCtor;
        };

        template<class T>
        inline InterfaceMarshalContext<T>::InterfaceMarshalContext() NOTHROWS :
            javaWrapperType(NULL),
            javaWrapperPointerField(NULL),
            javaWrapperCtor(NULL)
        {
            ATAKMapEngineJNI_registerShutdownHook(InterfaceMarshalContext<T>::onShutdown<T>, std::unique_ptr<void, void(*)(const void *)>(this, TAK::Engine::Util::Memory_leaker_const<void>));
        }
        template<class T>
        inline InterfaceMarshalContext<T>::~InterfaceMarshalContext() NOTHROWS
        {
            std::unique_ptr<void, void(*)(const void *)> discard(nullptr, nullptr);
            ATAKMapEngineJNI_unregisterShutdownHook(discard, this);
#ifdef __ANDROID__
            LocalJNIEnv env;
            for(auto it = managedImplToNativeWrapper.begin(); it != managedImplToNativeWrapper.end(); it++)
                env->DeleteWeakGlobalRef(it->first);
            managedImplToNativeWrapper.clear();
            for(auto it = nativeImplToManagedWrapper.begin(); it != nativeImplToManagedWrapper.end(); it++)
                env->DeleteWeakGlobalRef(it->second);
            nativeImplToManagedWrapper.clear();

            if(javaWrapperType) {
                env->DeleteGlobalRef(javaWrapperType);
                javaWrapperType = NULL;
            }
#endif
        }
        template<class T>
        void InterfaceMarshalContext<T>::init(JNIEnv &env, jclass javaWrapperType_, jfieldID javaWrapperPointerField_, jmethodID javaWrapperCtor_) NOTHROWS
        {
            javaWrapperType = (jclass)env.NewGlobalRef(javaWrapperType_);
            javaWrapperPointerField = javaWrapperPointerField_;
            javaWrapperCtor = javaWrapperCtor_;
        }
        template<class T>
        template<class WrapperType>
        inline bool InterfaceMarshalContext<T>::isWrapper(const T &obj) NOTHROWS
        {
            return !!dynamic_cast<const WrapperType *>(&obj);
        }
        template<class T>
        inline bool InterfaceMarshalContext<T>::isWrapper(JNIEnv &env, jobject obj) NOTHROWS
        {
            if(!javaWrapperType)
                return false;
            return ATAKMapEngineJNI_equals(&env, javaWrapperType, env.GetObjectClass(obj));
        }
        template<class T>
        template<class WrapperType>
        inline TAK::Engine::Util::TAKErr InterfaceMarshalContext<T>::marshal(std::shared_ptr<T> &value, JNIEnv &env, jobject obj) NOTHROWS
        {
            TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
            if(!obj)
                return TAK::Engine::Util::TE_InvalidArg;
            TAK::Engine::Thread::LockPtr lock(NULL, NULL);
            // check if it's a wrapper, and return
            if(isWrapper(env, obj)) {
                do {
                    jobject msourcePtr = env.GetObjectField(obj, javaWrapperPointerField);
                    if(!Pointer_makeShared<T>(&env, msourcePtr))
                        break;
                    value = *JLONG_TO_INTPTR(std::shared_ptr<T>, env.GetLongField(msourcePtr, Pointer_class.value));
                    return TAK::Engine::Util::TE_Ok;
                } while(false);
            }

            code = Lock_create(lock, mutex);
            TE_CHECKRETURN_CODE(code);
            for(auto it = managedImplToNativeWrapper.begin(); it != managedImplToNativeWrapper.end(); it++) {
                if(env.IsSameObject(obj, it->first)) {
                    value = it->second.lock();
                    if(value.get())
                        return TAK::Engine::Util::TE_Ok;
                    env.DeleteWeakGlobalRef(it->first);
                    managedImplToNativeWrapper.erase(it);
                    break;
                } else if(env.IsSameObject(NULL, it->first)) {
                    // erase cleared references
                    env.DeleteWeakGlobalRef(it->first);
                    managedImplToNativeWrapper.erase(it);
                }
            }

            value = std::unique_ptr<T, void(*)(const T *)>(new WrapperType(env, obj), TAK::Engine::Util::Memory_deleter_const<T, WrapperType>);
            managedImplToNativeWrapper[env.NewWeakGlobalRef(obj)] = value;

            return code;
        }
        template<class T>
        template<class WrapperType>
        inline TAK::Engine::Util::TAKErr InterfaceMarshalContext<T>::marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<T> &obj) NOTHROWS
        {
            TAK::Engine::Util::TAKErr  code(TAK::Engine::Util::TE_Ok);
            const WrapperType *impl = dynamic_cast<const WrapperType *>(obj.get());
            if(impl) {
                // unwrap
                value = Java::JNILocalRef(env, env.NewLocalRef(impl->impl));
                return TAK::Engine::Util::TE_Ok;
            }

            TAK::Engine::Thread::LockPtr lock(NULL, NULL);
            code = TAK::Engine::Thread::Lock_create(lock, mutex);
            TE_CHECKRETURN_CODE(code);

            // look up in intern
            do {
                auto entry = nativeImplToManagedWrapper.find(obj.get());
                if(entry == nativeImplToManagedWrapper.end())
                    break;
                value = Java::JNILocalRef(env, env.NewLocalRef(entry->second));
                if(value)
                    return code;
                // weak ref was cleared
                env.DeleteWeakGlobalRef(entry->second);
                nativeImplToManagedWrapper.erase(entry);
            } while(false);

            Java::JNILocalRef mpointer(env, NewPointer(&env, obj));
            value = Java::JNILocalRef(env, env.NewObject(javaWrapperType, javaWrapperCtor, mpointer.get(), NULL));
            nativeImplToManagedWrapper[obj.get()] = env.NewWeakGlobalRef(value);
            return TAK::Engine::Util::TE_Ok;
        }
        template<class T>
        template<class WrapperType>
        inline TAK::Engine::Util::TAKErr InterfaceMarshalContext<T>::marshal(Java::JNILocalRef &value, JNIEnv &env, const T &obj) NOTHROWS
        {
            TAK::Engine::Util::TAKErr  code(TAK::Engine::Util::TE_Ok);
            const WrapperType *impl = dynamic_cast<const WrapperType *>(&obj);
            if(impl) {
                // unwrap
                value = Java::JNILocalRef(env, env.NewLocalRef(impl->impl));
                return TAK::Engine::Util::TE_Ok;
            }

            // look up in intern
            do {
                TAK::Engine::Thread::LockPtr lock(NULL, NULL);
                code = TAK::Engine::Thread::Lock_create(lock, mutex);
                TE_CHECKBREAK_CODE(code);

                auto entry = nativeImplToManagedWrapper.find(&obj);
                if(entry == nativeImplToManagedWrapper.end())
                    break;
                value = Java::JNILocalRef(env, env.NewLocalRef(entry->second));
                if(value)
                    return code;
                // weak ref was cleared
                env.DeleteWeakGlobalRef(entry->second);
                nativeImplToManagedWrapper.erase(entry);
            } while(false);
            return TAK::Engine::Util::TE_InvalidArg;
        }

        template<class T>
        template<class WrapperType>
        TAK::Engine::Util::TAKErr InterfaceMarshalContext<T>::marshal(std::unique_ptr<T, void(*)(const T *)> &value, JNIEnv &env, jobject obj) NOTHROWS
        {
            if(!obj)
                return TAK::Engine::Util::TE_InvalidArg;
            value = std::unique_ptr<T, void(*)(const T *)>(new WrapperType(env, obj), TAK::Engine::Util::Memory_deleter_const<T, WrapperType>);
            return TAK::Engine::Util::TE_Ok;
        }
        template<class T>
        template<class WrapperType>
        TAK::Engine::Util::TAKErr InterfaceMarshalContext<T>::marshal(Java::JNILocalRef &value, JNIEnv &env, std::unique_ptr<T, void(*)(const T *)> &&obj) NOTHROWS
        {
            if(!obj.get())
                return TAK::Engine::Util::TE_InvalidArg;
            Java::JNILocalRef mpointer(env, NewPointer<T>(&env, std::move(obj)));
            value = Java::JNILocalRef(env, env.NewObject(javaWrapperType, javaWrapperCtor, mpointer.get(), NULL));
            return TAK::Engine::Util::TE_Ok;
        }
    }
}

#endif
