#ifndef TAKENGINEJNI_INTEROP_JNIPRIMITIVEARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIPRIMITIVEARRAY_H_INCLUDED

#include <cstdlib>

#include <jni.h>

#include <port/Platform.h>

namespace TAKEngineJNI {
    namespace Interop {
        template<class JavaArrayType, class CArrayType>
        class JNIPrimitiveArray
        {
        protected :
            JNIPrimitiveArray(JNIEnv &env, JavaArrayType jarr, int releaseMode, CArrayType *(*pin_fn)(JNIEnv *, JavaArrayType, jboolean *), void(*unpin_fn)(JNIEnv *, JavaArrayType, CArrayType *, jint)) NOTHROWS;
        public :
            ~JNIPrimitiveArray() NOTHROWS;
        public :
            std::size_t length() const NOTHROWS;
            template<class T>
            const T *get() const NOTHROWS;
            template<class T>
            T *get() NOTHROWS;
        public : // operators
            CArrayType &operator[] (int) NOTHROWS;
            CArrayType operator[] (int) const NOTHROWS;
            operator CArrayType * () const NOTHROWS;
        private :
            JNIEnv &env;
            JavaArrayType jarr;
            CArrayType *carr;
            int releaseMode;
            CArrayType *(*pin_fn)(JNIEnv *, JavaArrayType, jboolean *);
            void(*unpin_fn)(JNIEnv *, JavaArrayType, CArrayType *, jint);
        };

        template<class JavaArrayType, class CArrayType>
        inline JNIPrimitiveArray<JavaArrayType, CArrayType>::JNIPrimitiveArray(
            JNIEnv &env_,
            JavaArrayType jarr_,
            int releaseMode_,
            CArrayType *(*pin_fn_)(JNIEnv *, JavaArrayType, jboolean *),
            void(*unpin_fn_)(JNIEnv *, JavaArrayType, CArrayType *, jint)) NOTHROWS :
                    env(env_),
                    jarr(jarr_),
                    carr(jarr_ ? pin_fn_(&env, jarr_, NULL) : NULL),
                    releaseMode(releaseMode_),
                    pin_fn(pin_fn_),
                    unpin_fn(unpin_fn_)
        {}
        template<class JavaArrayType, class CArrayType>
        inline JNIPrimitiveArray<JavaArrayType, CArrayType>::~JNIPrimitiveArray() NOTHROWS
        {
            if(carr) {
                unpin_fn(&env, jarr, carr, releaseMode);
                carr = NULL;
            }
        }
        template<class JavaArrayType, class CArrayType>
        inline std::size_t JNIPrimitiveArray<JavaArrayType, CArrayType>::length() const NOTHROWS
        {
            return env.GetArrayLength(jarr);
        }
        template<class JavaArrayType, class CArrayType>
        template<class T>
        const T *JNIPrimitiveArray<JavaArrayType, CArrayType>::get() const NOTHROWS
        {
            return reinterpret_cast<const T *>(carr);
        }
        template<class JavaArrayType, class CArrayType>
        template<class T>
        T *JNIPrimitiveArray<JavaArrayType, CArrayType>::get() NOTHROWS
        {
            return reinterpret_cast<T *>(carr);
        }
        template<class JavaArrayType, class CArrayType>
        inline CArrayType &JNIPrimitiveArray<JavaArrayType, CArrayType>::operator[] (int idx) NOTHROWS
        {
            return carr[idx];
        }
        template<class JavaArrayType, class CArrayType>
        inline CArrayType JNIPrimitiveArray<JavaArrayType, CArrayType>::operator[] (int idx) const NOTHROWS
        {
            return carr[idx];
        }
        template<class JavaArrayType, class CArrayType>
        inline JNIPrimitiveArray<JavaArrayType, CArrayType>::operator CArrayType * () const NOTHROWS
        {
            return carr;
        }
    }
}
#endif
