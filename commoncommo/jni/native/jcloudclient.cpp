#include "jcloudclient.h"
#include "commojni_impl.h"
#include <string.h>

using namespace atakmap::jni::commoncommo;

/**********************************************************************/
// CloudIOJNI


jmethodID CloudIOJNI::jmethod_cloudOperationUpdate = NULL;
    
jclass CloudIOJNI::jclass_cloudIOUpdate = NULL;
jmethodID CloudIOJNI::jmethod_cloudIOUpdateCtor = NULL;
jclass CloudIOJNI::jclass_cloudEntry = NULL;
jmethodID CloudIOJNI::jmethod_cloudEntryCtor = NULL;
jglobalobjectref CloudIOJNI::nativeEntryTypesToJava[NUM_ENT_TYPES];
jglobalobjectref CloudIOJNI::nativeCloudOpsToJava[NUM_CLOUD_OPS];

CloudIOJNI::CloudIOJNI(JNIEnv *env, jobject jcloudio)
                        COMMO_THROW (int) : CloudIO(), jcloudio(NULL)
{
    this->jcloudio = env->NewGlobalRef(jcloudio);
    if (!this->jcloudio)
        throw 1;
}


void CloudIOJNI::destroy(JNIEnv *env, CloudIOJNI *cloudio)
{
    if (cloudio->jcloudio) {
        env->DeleteGlobalRef(cloudio->jcloudio);
        cloudio->jcloudio = NULL;
    }
    delete cloudio;
}


void CloudIOJNI::cloudOperationUpdate(
                    const CloudIOUpdate *update)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;
    
    jstring jinfo = NULL;
    if (update->additionalInfo != NULL)
        jinfo = env->NewStringUTF((const char *)
                                     update->additionalInfo);
    jobject jstat = SimpleFileIOJNI::getJavaFileStatus(update->status);
    jobject jop = CloudIOJNI::nativeCloudOpsToJava[update->operation];
    
    // Clamp to signed range of Java
    jlong soFar = 0;
    if (update->bytesTransferred <= INT64_MAX)
        soFar = (jlong)update->bytesTransferred;
    jlong total = 0;
    if (update->totalBytesToTransfer <= INT64_MAX)
        total = (jlong)update->totalBytesToTransfer;

    jobjectArray jentries = NULL;
    bool err = false;
    if (update->entries) {
        jentries = env->NewObjectArray(update->numEntries,
                        jclass_cloudEntry, NULL);
        if (jentries) {
            for (size_t i = 0; i < update->numEntries; ++i) {
                jstring entPath = env->NewStringUTF(update->entries[i]->path);
                if (!entPath) {
                    err = true;
                    break;
                }
                
                jlong jsize;
                if (update->entries[i]->fileSize == 
                            CloudCollectionEntry::FILE_SIZE_UNKNOWN ||
                        update->entries[i]->fileSize > (uint64_t)INT64_MAX)
                    jsize = -1;
                else
                    jsize = (jlong)update->entries[i]->fileSize;
                
                jobject jent = env->NewObject(jclass_cloudEntry,
                              jmethod_cloudEntryCtor,
                              nativeEntryTypesToJava[update->entries[i]->type],
                              entPath,
                              jsize);
                env->SetObjectArrayElement(jentries, i, jent);
            }
        }
    }
    
    if (!err) {
        jobject jupdate = env->NewObject(jclass_cloudIOUpdate, 
                                         jmethod_cloudIOUpdateCtor,
                                         jop,
                                         update->xferid,
                                         jstat,
                                         jinfo,
                                         soFar,
                                         total,
                                         jentries);

        env->CallVoidMethod(jcloudio, jmethod_cloudOperationUpdate, jupdate);
    }
    if (env->ExceptionOccurred())
        env->ExceptionClear();
}


bool CloudIOJNI::reflectionInit(JNIEnv *env)
{
    jclass class_cloudio;
    jclass class_cloudtype;
    jmethodID method_cloudtypeValues;
    jmethodID method_cloudtypeName;
    jmethodID method_cloudtypeValue;
    jobjectArray jcloudtypeValues;
    jclass class_cloudop;
    jmethodID method_cloudopValues;
    jmethodID method_cloudopName;
    jmethodID method_cloudopValue;
    jobjectArray jcloudopValues;
    jsize numVals;
    bool ret = false;

    memset(nativeEntryTypesToJava, 0, sizeof(nativeEntryTypesToJava));

    LOOKUP_CLASS(class_cloudio, COMMO_PACKAGE "CloudIO", true);
    LOOKUP_METHOD(jmethod_cloudOperationUpdate, class_cloudio, 
                  "cloudOperationUpdate",
                  "(L" COMMO_PACKAGE "CloudIOUpdate;)V");
    
    LOOKUP_CLASS(jclass_cloudIOUpdate, 
                 COMMO_PACKAGE "CloudIOUpdate", false);
    LOOKUP_METHOD(jmethod_cloudIOUpdateCtor, jclass_cloudIOUpdate,
                  "<init>",
                  "(L" COMMO_PACKAGE "CloudIOOperation;IL" COMMO_PACKAGE "SimpleFileIOStatus;Ljava/lang/String;JJ[L" COMMO_PACKAGE "CloudCollectionEntry;)V");
    
    LOOKUP_CLASS(jclass_cloudEntry, 
                 COMMO_PACKAGE "CloudCollectionEntry", false);
    LOOKUP_METHOD(jmethod_cloudEntryCtor, jclass_cloudEntry,
                  "<init>",
                  "(L" COMMO_PACKAGE "CloudCollectionEntry$Type;Ljava/lang/String;J)V");
    
    // Build mapping of native entry types to java versions
    LOOKUP_CLASS(class_cloudtype, 
                 COMMO_PACKAGE "CloudCollectionEntry$Type", true);
    LOOKUP_STATIC_METHOD(method_cloudtypeValues, class_cloudtype,
                         "values",
                         "()[L" COMMO_PACKAGE "CloudCollectionEntry$Type;");
    jcloudtypeValues = (jobjectArray)env->CallStaticObjectMethod(
                         class_cloudtype, method_cloudtypeValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jcloudtypeValues);
    
    if (numVals != NUM_ENT_TYPES)
        goto cleanup;
        
    LOOKUP_METHOD(method_cloudtypeName, class_cloudtype, 
                  "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_cloudtypeValue, class_cloudtype,
                  "getNativeVal", "()I");

#define CHECK_CTYPE(val) checkEnum(env, method_cloudtypeName,                \
                                  method_cloudtypeValue,                     \
                                  jcloudtypeValues, numVals,                 \
                                  #val,                                      \
                                  nativeEntryTypesToJava,                    \
                                  PASTE(CloudCollectionEntry::TYPE_, val),   \
                                  PASTE(CloudCollectionEntry::TYPE_, val))   \

    ret = true;
    ret = ret && CHECK_CTYPE(FILE);
    ret = ret && CHECK_CTYPE(COLLECTION);
#undef CHECK_CTYPE
    if (!ret)
        goto cleanup;

    // Build mapping of native cloud ops to java versions
    LOOKUP_CLASS(class_cloudop, 
                 COMMO_PACKAGE "CloudIOOperation", true);
    LOOKUP_STATIC_METHOD(method_cloudopValues, class_cloudop,
                         "values",
                         "()[L" COMMO_PACKAGE "CloudIOOperation;");
    jcloudopValues = (jobjectArray)env->CallStaticObjectMethod(
                         class_cloudop, method_cloudopValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jcloudopValues);
    
    if (numVals != NUM_CLOUD_OPS)
        goto cleanup;
        
    LOOKUP_METHOD(method_cloudopName, class_cloudop, 
                  "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_cloudopValue, class_cloudop,
                  "getNativeVal", "()I");

#define CHECK_CLOUDOP(val) checkEnum(env, method_cloudopName,                \
                                      method_cloudopValue,                   \
                                      jcloudopValues, numVals,               \
                                      #val,                                  \
                                      nativeCloudOpsToJava,                  \
                                      PASTE(CLOUDIO_OP_, val),               \
                                      PASTE(CLOUDIO_OP_, val))               \

    ret = true;
    ret = ret && CHECK_CLOUDOP(TEST_SERVER);
    ret = ret && CHECK_CLOUDOP(LIST_COLLECTION);
    ret = ret && CHECK_CLOUDOP(GET);
    ret = ret && CHECK_CLOUDOP(PUT);
    ret = ret && CHECK_CLOUDOP(MOVE);
    ret = ret && CHECK_CLOUDOP(MAKE_COLLECTION);
    ret = ret && CHECK_CLOUDOP(DELETE);


cleanup:
    return ret;
}

void CloudIOJNI::reflectionRelease(JNIEnv *env)
{
    for (int i = 0; i < NUM_ENT_TYPES; ++i) {
        if (nativeEntryTypesToJava[i])
            env->DeleteGlobalRef(nativeEntryTypesToJava[i]);
        nativeEntryTypesToJava[i] = NULL;
    }
    for (int i = 0; i < NUM_CLOUD_OPS; ++i) {
        if (nativeCloudOpsToJava[i])
            env->DeleteGlobalRef(nativeCloudOpsToJava[i]);
        nativeCloudOpsToJava[i] = NULL;
    }
    
    env->DeleteGlobalRef(jclass_cloudIOUpdate);
    env->DeleteGlobalRef(jclass_cloudEntry);
}



CloudIOJNI::~CloudIOJNI()
{
}






JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_testServerInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    int id = 0;
    CommoResult r = c->testServerInit(&id);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_listCollectionInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpath)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *path = env->GetStringUTFChars(jpath, NULL);
    if (!path)
        return 0;
    
    int id = 0;
    CommoResult r = c->listCollectionInit(&id, path);
    env->ReleaseStringUTFChars(jpath, path);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_getFileInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jlocalFile,
   jstring jpath)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *path = env->GetStringUTFChars(jpath, NULL);
    if (!path)
        return 0;
    const char *localFile = env->GetStringUTFChars(jlocalFile, NULL);
    if (!localFile) {
        env->ReleaseStringUTFChars(jpath, path);
        return 0;
    }
    
    int id = 0;
    CommoResult r = c->getFileInit(&id, localFile, path);
    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jlocalFile, localFile);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_putFileInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpath, jstring jlocalFile)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *path = env->GetStringUTFChars(jpath, NULL);
    if (!path)
        return 0;
    const char *localFile = env->GetStringUTFChars(jlocalFile, NULL);
    if (!localFile) {
        env->ReleaseStringUTFChars(jpath, path);
        return 0;
    }
    
    int id = 0;
    CommoResult r = c->putFileInit(&id, path, localFile);
    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jlocalFile, localFile);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_moveResourceInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jfrom, jstring jto)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *from = env->GetStringUTFChars(jfrom, NULL);
    if (!from)
        return 0;
    const char *to = env->GetStringUTFChars(jto, NULL);
    if (!to) {
        env->ReleaseStringUTFChars(jfrom, from);
        return 0;
    }
    
    int id = 0;
    CommoResult r = c->moveResourceInit(&id, from, to);
    env->ReleaseStringUTFChars(jto, to);
    env->ReleaseStringUTFChars(jfrom, from);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_deleteResourceInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpath)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *path = env->GetStringUTFChars(jpath, NULL);
    if (!path)
        return 0;
    
    int id = 0;
    CommoResult r = c->deleteResourceInit(&id, path);
    env->ReleaseStringUTFChars(jpath, path);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT jint JNICALL Java_com_atakmap_commoncommo_CloudClient_createCollectionInitNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpath)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    const char *path = env->GetStringUTFChars(jpath, NULL);
    if (!path)
        return 0;
    
    int id = 0;
    CommoResult r = c->createCollectionInit(&id, path);
    env->ReleaseStringUTFChars(jpath, path);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");

    return id;
}


JNIEXPORT void JNICALL Java_com_atakmap_commoncommo_CloudClient_startOperationNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jint jid)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    CommoResult r = c->startOperation(jid);
    if (r != COMMO_SUCCESS)
        env->ThrowNew(CommoJNI::jclass_commoexception, "");
}


JNIEXPORT void JNICALL Java_com_atakmap_commoncommo_CloudClient_cancelOperationNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jint jid)
{
    CloudClient *c = JLONG_TO_PTR(CloudClient, nativePtr);
    c->cancelOperation(jid);
}
