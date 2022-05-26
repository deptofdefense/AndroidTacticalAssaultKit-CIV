#ifndef ATAKJNI_COMMON_H
#define ATAKJNI_COMMON_H

#include <jni.h>

#include <port/Platform.h>
#include <port/String.h>
#include <util/Logging2.h>
#include <util/Error.h>

#include "jpointer.h"

/******************************************************************************/
// MACROS

#define GET_BUFFER_POINTER(t, buffer) \
        (buffer ? reinterpret_cast<t *>(env->GetDirectBufferAddress(buffer)) : NULL)

#define ATAKMAP_GL_PKG "com/atakmap/opengl"
#define INTPTR_TO_JLONG(a)           ((jlong)(intptr_t)(a))
#define JLONG_TO_INTPTR(type, var)   ((type *)(intptr_t)(var))
#define JLONG_TO_FNPTR(type, var)   ((type)(intptr_t)(var))

/******************************************************************************/
// EXTERNS

// exceptions

extern jclass java_lang_RuntimeException_class;
extern jclass java_lang_IllegalArgumentException_class;
extern jclass java_lang_IndexOutOfBoundsException_class;
extern jclass java_lang_InterruptedException_class;
extern jclass java_lang_UnsupportedOperationException_class;
extern jclass java_io_IOException_class;
extern jclass java_lang_OutOfMemoryError_class;
extern jclass java_lang_IllegalStateException_class;
extern jclass java_util_ConcurrentModificationException_class;
extern jclass java_io_EOFException_class;

// java.nio.Buffer

extern jmethodID glLineBatch_flush;

extern jclass nioBufferClass;
extern jclass glLineBatchClass;

// PointD
extern jclass pointDClass;
extern jmethodID pointD_ctor__DDD;
extern jfieldID pointD_x;
extern jfieldID pointD_y;
extern jfieldID pointD_z;

// ProgressCallback
extern jclass ProgressCallback_class;
extern jmethodID ProgressCallback_progress;
extern jmethodID ProgressCallback_error;

// CLASSES

// XXX - can be done with templates ??? create function pointer for JNIEnv
//       functions ???

class JavaIntFieldAccess {
private :
	typedef jint (*getFn)(JavaIntFieldAccess *self, JNIEnv *env, jobject);
	typedef void (*setFn)(JavaIntFieldAccess *self, JNIEnv *env, jobject, jint);

	typedef void (*envSetMethodFn)(JNIEnv *, jobject, jmethodID, ...);

public :
	JavaIntFieldAccess(JNIEnv *env,
	                   jclass clazz,
	                   const char *fieldName,
	                   const char *getMethodName,
	                   const char *getMethodSig,
	                   const char *setMethodName,
	                   const char *setMethodSig);

	~JavaIntFieldAccess();
public :
	jint get(JNIEnv *env, jobject object);
	void set(JNIEnv *env, jobject object, jint value);
	bool isValid(bool get, bool set);
private :
	static jint getDirect(JavaIntFieldAccess *self, JNIEnv *env, jobject object);
	static void setDirect(JavaIntFieldAccess *self, JNIEnv *env, jobject object, jint value);

	static jint getMethod(JavaIntFieldAccess *self, JNIEnv *env, jobject object);
	static void setMethod(JavaIntFieldAccess *self, JNIEnv *env, jobject object, jint value);

private :
	jfieldID fieldId;
	jmethodID setMethodId;
	jmethodID getMethodId;
	getFn getImpl;
	setFn setImpl;
	envSetMethodFn envSetMethodImpl;
};

extern JavaIntFieldAccess *nioBuffer_position;
extern JavaIntFieldAccess *nioBuffer_limit;

class LocalJNIEnv
{
public :
    LocalJNIEnv() NOTHROWS;
    ~LocalJNIEnv() NOTHROWS;
public :
    bool valid() NOTHROWS;
public :
    JNIEnv *operator->() const NOTHROWS;
    JNIEnv &operator*() const NOTHROWS;
    operator JNIEnv*() const NOTHROWS;
private :
    JNIEnv *env;
    bool detach;
};

/**
 * Returns 'true' if an exception was raised and execution should halt.
 */
bool ATAKMapEngineJNI_checkOrThrow(JNIEnv *env, const TAK::Engine::Util::TAKErr code) NOTHROWS;
jclass ATAKMapEngineJNI_findClass(JNIEnv *env, const char *name) NOTHROWS;
JavaVM *ATAKMapEngineJNI_getJVM();
bool ATAKMapEngineJNI_equals(JNIEnv *env, jobject a, jobject b) NOTHROWS;

/**
 * Registers the specified shutdown hook.
 */
void ATAKMapEngineJNI_registerShutdownHook(void(*hook)(JNIEnv &, void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS;
/**
 * Unregisters a previously registered. The registered hook associated with
 * the specified opaque context is returned.
 */
void ATAKMapEngineJNI_unregisterShutdownHook(std::unique_ptr<void, void(*)(const void *)> &hook, const void *opaque) NOTHROWS;

TAK::Engine::Util::TAKErr ProgressCallback_dispatchProgress(jobject jcallback, jint value) NOTHROWS;
TAK::Engine::Util::TAKErr ProgressCallback_dispatchError(jobject jcallback, const char *value) NOTHROWS;

void Thread_dumpStack() NOTHROWS;
TAK::Engine::Port::String Object_toString(jobject obj) NOTHROWS;

#endif
