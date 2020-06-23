
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <cert-db.h>
#include <android/log.h>

void logMsg(const char* msg)
{
	__android_log_write(ANDROID_LOG_INFO, "certdb", msg);
}

void logString(const char* file, int line, const char* msg)
{
	__android_log_write(ANDROID_LOG_INFO, "certdb", msg);
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_openOrCreateDatabase(JNIEnv *env, jobject instance, jstring jfilename, jstring jpassword) 
{
	logString(__FILE__, __LINE__, ">> openOrCreateDatabase");
	
	setLogger(logMsg);
	
	jboolean iscopy = 0;
	const char* filename = (*env)->GetStringUTFChars(env, jfilename, &iscopy);
	const char* password = (*env)->GetStringUTFChars(env, jpassword, &iscopy);
	
	int rc = openOrCreateDatabase(filename, password);
		
	(*env)->ReleaseStringUTFChars(env, jfilename, filename);		
	(*env)->ReleaseStringUTFChars(env, jpassword, password);		
	
	logString(__FILE__, __LINE__, "<< openOrCreateDatabase");
	return rc;
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_close(JNIEnv *env, jobject instance) 
{
	logString(__FILE__, __LINE__, ">> close");
	int rc = closedb();
	logString(__FILE__, __LINE__, "<< close");
	return rc;
}

JNIEXPORT jbyteArray JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_getCertificate(JNIEnv *env, jobject instance, jstring jtype) 
{
	logString(__FILE__, __LINE__, ">> getCertificate");
	
	jboolean iscopy = 0;
	void* cert = 0;
	int size = 0;
	char* hash = 0;
	
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	logString(__FILE__, __LINE__, type);
	
	int rc = getCertificate(type, &cert, &size, &hash);
	if (rc != 0 || size == 0) 
	{
		logString(__FILE__, __LINE__, "cert not found.");
		(*env)->ReleaseStringUTFChars(env, jtype, type);		
		logString(__FILE__, __LINE__, "<< getCertificate");
		return 0;
	}

	jbyteArray data = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, data, 0, size, cert);
	
	free(cert);
	if (hash != 0)
	{
		free(hash);
	}

	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	
	logString(__FILE__, __LINE__, "<< getCertificate");
	return data;
}

JNIEXPORT jbyteArray JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_getCertificateForServer(JNIEnv *env, jobject instance, jstring jtype, jstring jserver) 
{
	logString(__FILE__, __LINE__, ">> getCertificateForServer");
	
	jboolean iscopy = 0;
	void* cert = 0;
	int size = 0;
	char* hash = 0;
	
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	logString(__FILE__, __LINE__, type);

	const char* server = (*env)->GetStringUTFChars(env, jserver, &iscopy);
	logString(__FILE__, __LINE__, server);
	
	int rc = getCertificateForServer(type, server, &cert, &size, &hash);
	if (rc != 0 || size == 0) 
	{
		logString(__FILE__, __LINE__, "cert not found.");
		(*env)->ReleaseStringUTFChars(env, jtype, type);		
		logString(__FILE__, __LINE__, "<< getCertificateForServer");
		return 0;
	}

	jbyteArray data = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, data, 0, size, cert);
	
	free(cert);
	if (hash != 0)
	{
		free(hash);
	}

	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jserver, server);		
	
	logString(__FILE__, __LINE__, "<< getCertificateForServer");
	return data;
}

static jobjectArray make_row(JNIEnv *env, jsize count, const char* elements[])
{
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray row = (*env)->NewObjectArray(env, count, stringClass, 0);
    jsize i;

    for (i = 0; i < count; ++i) {
        (*env)->SetObjectArrayElement(env, row, i, (*env)->NewStringUTF(env, elements[i]));
    }
    return row;
}

JNIEXPORT jobjectArray JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_getServers(JNIEnv *env, jobject instance, jstring jtype) 
{
	logString(__FILE__, __LINE__, ">> getServers");
	
	jboolean iscopy = 0;
	char** servers;
	int size = 0;
	
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	logString(__FILE__, __LINE__, type);
		
	logString(__FILE__, __LINE__, "  >> ");
	int rc = getServers(type, &servers, &size);
	logString(__FILE__, __LINE__, "  >> ");
	
	if (rc != 0 || size == 0) 
	{
		logString(__FILE__, __LINE__, "servers not found.");
		(*env)->ReleaseStringUTFChars(env, jtype, type);		
		logString(__FILE__, __LINE__, "<< getServers");
		return 0;
	}
	
	jobjectArray serversJni = (jobjectArray)(*env)->NewObjectArray(env, size, (*env)->FindClass(env, "java/lang/String"), 0);
	for (int i=0; i < size; i++) 
	{
		jstring jserver = (*env)->NewStringUTF(env, *(servers+i));
		(*env)->SetObjectArrayElement(env, serversJni, i, jserver);
	}
	
	for (int i=0; i < size; i++) 
	{
		free(*(servers+i));
	}
	
	free(servers);

	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	
	logString(__FILE__, __LINE__, "<< getServers");
	return serversJni;
}

JNIEXPORT jstring JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_getCertificateHash(JNIEnv *env, jobject instance, jstring jtype) 
{
	logString(__FILE__, __LINE__, ">> getCertificateHash");	
	
	jboolean iscopy = 0;
	void* cert = 0;
	int size = 0;
	char* hash = 0;
	
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	logString(__FILE__, __LINE__, type);
	
	int rc = getCertificate(type, &cert, &size, &hash);
	if (rc != 0 || size == 0) 
	{
		logString(__FILE__, __LINE__, "cert not found.");
		(*env)->ReleaseStringUTFChars(env, jtype, type);		
		logString(__FILE__, __LINE__, "<< getCertificateHash");	
		return 0;
	}

	free(cert);

	jstring jhash;
	if (hash != 0) 
	{
		jhash = (*env)->NewStringUTF(env, hash); 
		free(hash);
	}
	else
	{
		logString(__FILE__, __LINE__, "cert had  no hash.");
		jhash = (*env)->NewStringUTF(env, ""); 
	}
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	
	logString(__FILE__, __LINE__, "<< getCertificateHash");	
	return jhash;  	
}

JNIEXPORT jint JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_saveCertificate(JNIEnv *env, jobject instance, 
	jstring jtype, jbyteArray cert, jstring jhash) 
{
	logString(__FILE__, __LINE__, ">> saveCertificate");	
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* hash = (*env)->GetStringUTFChars(env, jhash, &iscopy);
	void* buffer = (void*)(*env)->GetByteArrayElements(env, cert, &iscopy);
	jsize size = (*env)->GetArrayLength(env, cert);

	int rc = saveCertificate(type, buffer, size, hash);

	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jhash, hash);		
	(*env)->ReleaseByteArrayElements(env, cert, buffer, 0);
	
	logString(__FILE__, __LINE__, "<< saveCertificate");	
	return rc;	
}

JNIEXPORT jstring JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_getCertificateHashForServer(JNIEnv *env, jobject instance, jstring jtype, jstring jserver) 
{
	logString(__FILE__, __LINE__, ">> getCertificateHashForServer");	
	
	jboolean iscopy = 0;
	void* cert = 0;
	int size = 0;
	char* hash = 0;
	
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	logString(__FILE__, __LINE__, type);

	const char* server = (*env)->GetStringUTFChars(env, jserver, &iscopy);
	logString(__FILE__, __LINE__, server);
	
	int rc = getCertificateForServer(type, server, &cert, &size, &hash);
	if (rc != 0 || size == 0) 
	{
		logString(__FILE__, __LINE__, "cert not found.");
		(*env)->ReleaseStringUTFChars(env, jtype, type);		
		logString(__FILE__, __LINE__, "<< getCertificateHashForServer");	
		return 0;
	}

	free(cert);

	jstring jhash;
	if (hash != 0) 
	{
		jhash = (*env)->NewStringUTF(env, hash); 
		free(hash);
	}
	else
	{
		logString(__FILE__, __LINE__, "cert had  no hash.");
		jhash = (*env)->NewStringUTF(env, ""); 
	}
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jserver, server);
	
	logString(__FILE__, __LINE__, "<< getCertificateHashForServer");	
	return jhash;  	
}

JNIEXPORT jint JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_saveCertificateForServer(JNIEnv *env, jobject instance, 
	jstring jtype, jstring jserver, jbyteArray cert, jstring jhash) 
{
	logString(__FILE__, __LINE__, ">> saveCertificateForServer");	
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* server = (*env)->GetStringUTFChars(env, jserver, &iscopy);
	const char* hash = (*env)->GetStringUTFChars(env, jhash, &iscopy);
	void* buffer = (void*)(*env)->GetByteArrayElements(env, cert, &iscopy);
	jsize size = (*env)->GetArrayLength(env, cert);

	int rc = saveCertificateForServer(type, server, buffer, size, hash);

	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jserver, server);		
	(*env)->ReleaseStringUTFChars(env, jhash, hash);		
	(*env)->ReleaseByteArrayElements(env, cert, buffer, 0);
	
	logString(__FILE__, __LINE__, "<< saveCertificateForServer");	
	return rc;	
}

JNIEXPORT jint JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_deleteCertificate(JNIEnv *env, jobject instance, jstring jtype) 
{
	logString(__FILE__, __LINE__, ">> deleteCertificate");	
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	
	int rc = deleteCertificate(type);
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	
	logString(__FILE__, __LINE__, "<< deleteCertificate");	
	return rc;	
}

JNIEXPORT jint JNICALL 
Java_com_atakmap_net_AtakCertificateDatabaseAdapter_deleteCertificateForServer(JNIEnv *env, jobject instance, jstring jtype, jstring jserver) 
{
	logString(__FILE__, __LINE__, ">> deleteCertificateForServer");	
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* server = (*env)->GetStringUTFChars(env, jserver, &iscopy);
	
	int rc = deleteCertificateForServer(type, server);
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jserver, server);		
	
	logString(__FILE__, __LINE__, "<< deleteCertificateForServer");	
	return rc;	
}
