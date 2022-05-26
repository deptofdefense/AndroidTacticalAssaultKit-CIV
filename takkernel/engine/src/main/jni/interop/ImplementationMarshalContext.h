#ifndef TAKENGINEJNI_INTEROP_IMPLEMENTATIONMARSHALCONTEXT_H_INCLUDED
#define TAKENGINEJNI_INTEROP_IMPLEMENTATIONMARSHALCONTEXT_H_INCLUDED

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
        class ImplementationMarshalContext
        {
        public :
            ImplementationMarshalContext() NOTHROWS;
            ~ImplementationMarshalContext() NOTHROWS;
        public :
            void init(JNIEnv &env, jclass javaWrapperType, jfieldID javaWrapperPointerField, jmethodID javaWrapperCtor) NOTHROWS;

            TAK::Engine::Util::TAKErr marshal(std::shared_ptr<T> &value, JNIEnv &env, jobject obj) NOTHROWS;
            TAK::Engine::Util::TAKErr marshal(Java::JNILocalRef &value, JNIEnv &env, const T &obj) NOTHROWS;
        private :
            template<class Ti>
            static void onShutdown(JNIEnv &env, void *opaque) NOTHROWS
            {
                ImplementationMarshalContext<Ti> &ctx = *static_cast<ImplementationMarshalContext<Ti> *>(opaque);
                for(auto it = ctx.nativeImplToManagedWrapper.begin(); it != ctx.nativeImplToManagedWrapper.end(); it++)
                    env.DeleteWeakGlobalRef(it->second);
                ctx.nativeImplToManagedWrapper.clear();
            }
        private :
            std::map<const T *, jweak> nativeImplToManagedWrapper;
            TAK::Engine::Thread::Mutex mutex;

            jclass javaWrapperType;
            jfieldID javaWrapperPointerField;
            jmethodID javaWrapperCtor;
        };

        template<class T>
        inline ImplementationMarshalContext<T>::ImplementationMarshalContext() NOTHROWS :
            javaWrapperType(NULL),
            javaWrapperPointerField(NULL),
            javaWrapperCtor(NULL)
        {
            ATAKMapEngineJNI_registerShutdownHook(ImplementationMarshalContext<T>::onShutdown<T>, std::unique_ptr<void, void(*)(const void *)>(this, TAK::Engine::Util::Memory_leaker_const<void>));
        }
        template<class T>
        inline ImplementationMarshalContext<T>::~ImplementationMarshalContext() NOTHROWS
        {
            std::unique_ptr<void, void(*)(const void *)> discard(nullptr, nullptr);
            ATAKMapEngineJNI_unregisterShutdownHook(discard, this);
#ifdef __ANDROID__
            LocalJNIEnv env;
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
        void ImplementationMarshalContext<T>::init(JNIEnv &env, jclass javaWrapperType_, jfieldID javaWrapperPointerField_, jmethodID javaWrapperCtor_) NOTHROWS
        {
            javaWrapperType = (jclass)env.NewGlobalRef(javaWrapperType_);
            javaWrapperPointerField = javaWrapperPointerField_;
            javaWrapperCtor = javaWrapperCtor_;
        }
        template<class T>
        inline TAK::Engine::Util::TAKErr ImplementationMarshalContext<T>::marshal(std::shared_ptr<T> &value, JNIEnv &env, jobject obj) NOTHROWS
        {
            TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
            if(!obj)
                return TAK::Engine::Util::TE_InvalidArg;
            TAK::Engine::Thread::LockPtr lock(NULL, NULL);
            code = Lock_create(lock, mutex);
            TE_CHECKRETURN_CODE(code);

            // extract wrapped native
            do {
                Java::JNILocalRef msourcePtr(env, env.GetObjectField(obj, javaWrapperPointerField));
                if(!Pointer_makeShared<T>(&env, msourcePtr))
                    break;
                code = Pointer_get(value, env, msourcePtr);
                TE_CHECKRETURN_CODE(code);
                // intern native-to-managed
                if(nativeImplToManagedWrapper.find(value.get()) == nativeImplToManagedWrapper.end())
                    nativeImplToManagedWrapper[value.get()] = env.NewWeakGlobalRef(obj);
                return TAK::Engine::Util::TE_Ok;
            } while(false);

            return TAK::Engine::Util::TE_IllegalState;
        }
        template<class T>
        inline TAK::Engine::Util::TAKErr ImplementationMarshalContext<T>::marshal(Java::JNILocalRef &value, JNIEnv &env, const T &obj) NOTHROWS
        {
            TAK::Engine::Util::TAKErr  code(TAK::Engine::Util::TE_Ok);
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
    }
}

#endif
