
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <auth-db.h>
#include <android/log.h>

static JavaVM* g_vm = 0;
static JNIEnv* g_env = 0;
static jclass g_credentials = 0;
static jmethodID g_constructor = 0;

void logMsg(const char* msg)
{
	__android_log_write(ANDROID_LOG_INFO, "authdb", msg);
}

void logString(const char* file, int line, const char* msg)
{
	__android_log_write(ANDROID_LOG_INFO, "authdb", msg);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	logString(__FILE__, __LINE__, ">> JNI_OnLoad");
	g_vm = vm;

	if ((*g_vm)->GetEnv(g_vm, (void**)&g_env, JNI_VERSION_1_6) != JNI_OK)
	{
		logString(__FILE__, __LINE__, "getEnv failed!");
		logString(__FILE__, __LINE__, "<< JNI_OnLoad");
		return JNI_VERSION_1_6;
	}
	
	jclass jcredentials = (*g_env)->FindClass(g_env, "com/atakmap/net/AtakAuthenticationCredentials");
	g_credentials = (jclass)(*g_env)->NewGlobalRef(g_env, jcredentials);
	g_constructor = (*g_env)->GetMethodID(g_env, g_credentials, "<init>", "()V");
	
	logString(__FILE__, __LINE__, "<< JNI_OnLoad");
	return JNI_VERSION_1_6; 
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{
	logString(__FILE__, __LINE__, ">> JNI_OnUnload");
	(*g_env)->DeleteGlobalRef(g_env, g_credentials);
	logString(__FILE__, __LINE__, "<< JNI_OnUnload");
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_openOrCreateDatabase(
	JNIEnv* env, jobject instance, jstring jfilename, jstring jpassword) 
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
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_close(JNIEnv *env, jobject instance) 
{
	logString(__FILE__, __LINE__, ">> close");
	int rc = closedb();
	logString(__FILE__, __LINE__, "<< close");
	return rc;
}

JNIEXPORT jobjectArray JNICALL 
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_getDistinctSitesAndTypes(JNIEnv *env, jobject instance) 
{
	logString(__FILE__, __LINE__, ">> getDistinctSitesAndTypes");

	int size = 0;
	char** sites = 0;
	char** types = 0;
	
	int rc = getDistinctSitesAndTypes(&sites, &types, &size);
	logInt(__FILE__, __LINE__, rc);
	
	jobjectArray credsJni = (jobjectArray)(*env)->NewObjectArray(env, size, (*env)->FindClass(env, "com/atakmap/net/AtakAuthenticationCredentials"), 0);
	int i;
	for (i=0; i < size; i++) 
	{
		jobject obj = (*env)->NewObject(env, g_credentials, g_constructor);
		jfieldID typeId = (*env)->GetFieldID(env, g_credentials, "type", "Ljava/lang/String;");
		jfieldID siteId = (*env)->GetFieldID(env, g_credentials, "site", "Ljava/lang/String;");
				
		jstring jtype = (*env)->NewStringUTF(env, *(types+i)); 
		(*env)->SetObjectField(env, obj, typeId, jtype);			
		
		jstring jsite = (*env)->NewStringUTF(env, *(sites+i)); 
		(*env)->SetObjectField(env, obj, siteId, jsite);
							
		(*env)->SetObjectArrayElement(env, credsJni, i, obj);
	}
	
	if (size > 0 && sites != 0 && types != 0) 
	{
		for (i=0; i < size; i++) 
		{
			free(*(sites+i));
			free(*(types+i));
		}
		
		free(sites);		
		free(types);		
	}
		
	logString(__FILE__, __LINE__, "<< getDistinctSitesAndTypes");
	return credsJni;
}

JNIEXPORT jobject JNICALL
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_getCredentials(
	JNIEnv* env, jobject instance, jstring jtype, jstring jsite)
{
	logString(__FILE__, __LINE__, ">> getCredentials");

	jobject obj = (*env)->NewObject(env, g_credentials, g_constructor);
	jfieldID typeId = (*env)->GetFieldID(env, g_credentials, "type", "Ljava/lang/String;");
	jfieldID siteId = (*env)->GetFieldID(env, g_credentials, "site", "Ljava/lang/String;");
	jfieldID usernameId = (*env)->GetFieldID(env, g_credentials, "username", "Ljava/lang/String;");
	jfieldID passwordId = (*env)->GetFieldID(env, g_credentials, "password", "Ljava/lang/String;");
	
	char* username = 0;
	char* password = 0;
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* site = (*env)->GetStringUTFChars(env, jsite, &iscopy);

	int rc = getCredentials(type, site, &username, &password);
	if (rc == 0)
	{
		if (username != 0 && password != 0) 
		{
			jstring jtype = (*env)->NewStringUTF(env, type); 
			(*env)->SetObjectField(env, obj, typeId, jtype);			
			
			jstring jsite = (*env)->NewStringUTF(env, site); 
			(*env)->SetObjectField(env, obj, siteId, jsite);
						
			jstring jusername = (*env)->NewStringUTF(env, username); 
			(*env)->SetObjectField(env, obj, usernameId, jusername);
			free(username);
			
			jstring jpassword = (*env)->NewStringUTF(env, password); 
			(*env)->SetObjectField(env, obj, passwordId, jpassword);
			free(password);
		}
	} 
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jsite, site);	
		
	logString(__FILE__, __LINE__, "<< getCredentials");
	return obj;
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_saveCredentials(
	JNIEnv* env, jobject instance, jstring jtype, jstring jsite,
	jstring jusername, jstring jpassword, jlong jexpires)
{
	logString(__FILE__, __LINE__, ">> saveCredentials");
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* site = (*env)->GetStringUTFChars(env, jsite, &iscopy);	
	const char* username = (*env)->GetStringUTFChars(env, jusername, &iscopy);
	const char* password = (*env)->GetStringUTFChars(env, jpassword, &iscopy);	

	int rc = saveCredentials(type, site, username, password, jexpires);
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jsite, site);
	(*env)->ReleaseStringUTFChars(env, jusername, username);		
	(*env)->ReleaseStringUTFChars(env, jpassword, password);	
	
	logString(__FILE__, __LINE__, "<< saveCredentials");
	return rc;
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_invalidate(
	JNIEnv* env, jobject instance, jstring jtype, jstring jsite)
{
	logString(__FILE__, __LINE__, ">> invalidate");
	
	jboolean iscopy = 0;
	const char* type = (*env)->GetStringUTFChars(env, jtype, &iscopy);
	const char* site = (*env)->GetStringUTFChars(env, jsite, &iscopy);	

	int rc = invalidate(type, site);
	
	(*env)->ReleaseStringUTFChars(env, jtype, type);		
	(*env)->ReleaseStringUTFChars(env, jsite, site);
	
	logString(__FILE__, __LINE__, "<< invalidate");
	return rc;
}

JNIEXPORT jint JNICALL
Java_com_atakmap_net_AtakAuthenticationDatabaseAdapter_deleteExpiredCredentials(
	JNIEnv* env, jobject instance, jlong time)
{
	logString(__FILE__, __LINE__, ">> deleteExpiredCredentials");
	int rc = deleteExpiredCredentials(time);
	logString(__FILE__, __LINE__, "<< deleteExpiredCredentials");
	return rc;
}
