#include "jcommo.h"
#include "commojni_impl.h"
#include "openssl/ssl.h"
#include "openssl/crypto.h"
#include "libxml/tree.h"
#include "curl/curl.h"

#include <Mutex.h>
#include <Lock.h>

#include <string.h>
#include <stdint.h>

using namespace atakmap::jni::commoncommo;


/**********************************************************************/
// Global impl methods

bool atakmap::jni::commoncommo::checkEnum(JNIEnv *env,
               jmethodID method_enumName,
               jmethodID method_enumVal,
               jobjectArray jenumValues,
               jsize numLevels, const char *enumName,
               jglobalobjectref *nativeToJObjMap,
               int enumIdx, int enumVal)
{
    if (enumIdx >= numLevels)
        return false;
    
    jobject enumObj = env->GetObjectArrayElement(jenumValues, (jsize)enumIdx);
    jstring jname = (jstring)env->CallObjectMethod(enumObj, method_enumName);
    jint jval = env->CallIntMethod(enumObj, method_enumVal);
    const char *name = env->GetStringUTFChars(jname, NULL);
    if (!name)
        return false;
    
    bool ret = jval == enumVal && strcmp(name, enumName) == 0;
    env->ReleaseStringUTFChars(jname, name);
    
    if (ret && nativeToJObjMap) {
        nativeToJObjMap[enumVal] = env->NewGlobalRef(enumObj);
        if (!nativeToJObjMap[enumVal])
            return false;
    }
    
    return ret;
}




/**********************************************************************/
// CommoLoggerJNI

jmethodID CommoLoggerJNI::jmethod_log = NULL;
jglobalobjectref CommoLoggerJNI::nativeLevelsToJava[NUM_LOGGER_LEVELS];


CommoLoggerJNI::CommoLoggerJNI(JNIEnv *env, jobject jlogger) COMMO_THROW (int) :
                CommoLogger(), jlogger(NULL)
{
    this->jlogger = env->NewGlobalRef(jlogger);
    if (!this->jlogger)
        throw 1;
}

void CommoLoggerJNI::destroy(JNIEnv *env, CommoLoggerJNI *logger)
{
    if (logger->jlogger) {
        env->DeleteGlobalRef(logger->jlogger);
        logger->jlogger = NULL;
    }
    delete logger;
}

void CommoLoggerJNI::log(Level level, const char *message)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;
    
    jobject jlevel = nativeLevelsToJava[level];
    jstring jmessage = env->NewStringUTF(message);
    if (!jmessage)
        return;
    
    env->CallVoidMethod(jlogger, jmethod_log, jlevel, jmessage);
}


bool CommoLoggerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_log;
    jclass class_log_level;
    jobjectArray jlevelVals;
    jmethodID method_levelValues;
    jmethodID method_levelName;
    jmethodID method_levelValue;
    jsize numVals;
    
    memset(nativeLevelsToJava, 0, sizeof(nativeLevelsToJava));

    LOOKUP_CLASS(class_log, COMMO_PACKAGE "CommoLogger", true);
    LOOKUP_CLASS(class_log_level, COMMO_PACKAGE "CommoLogger$Level", true);
    LOOKUP_METHOD(jmethod_log, class_log, "log", "(L" COMMO_PACKAGE "CommoLogger$Level;Ljava/lang/String;)V");

    // Build mapping of native levels to java levels
    LOOKUP_STATIC_METHOD(method_levelValues, class_log_level, "values", "()[L" COMMO_PACKAGE "CommoLogger$Level;");
    jlevelVals = (jobjectArray)env->CallStaticObjectMethod(class_log_level, method_levelValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jlevelVals);
    
    if (numVals != NUM_LOGGER_LEVELS)
        goto cleanup;
        
    LOOKUP_METHOD(method_levelName, class_log_level, "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_levelValue, class_log_level, "ordinal", "()I");

#define CHECK_LEVEL(level) checkEnum(env, method_levelName,                  \
                                      method_levelValue,                     \
                                      jlevelVals, numVals,                   \
                                      #level,                                \
                                      nativeLevelsToJava,                    \
                                      PASTE(CommoLogger::LEVEL_, level),     \
                                      PASTE(CommoLogger::LEVEL_, level))     \

    ret = true;
    #undef ERROR
    ret = ret && CHECK_LEVEL(VERBOSE);
    ret = ret && CHECK_LEVEL(DEBUG);
    ret = ret && CHECK_LEVEL(WARNING);
    ret = ret && CHECK_LEVEL(INFO);
    ret = ret && CHECK_LEVEL(ERROR);
#undef CHECK_LEVEL

cleanup:
    return ret;
}

void CommoLoggerJNI::reflectionRelease(JNIEnv *env)
{
    for (int i = 0; i < NUM_LOGGER_LEVELS; ++i) {
        if (nativeLevelsToJava[i])
            env->DeleteGlobalRef(nativeLevelsToJava[i]);
        nativeLevelsToJava[i] = NULL;
    }
}

CommoLoggerJNI::~CommoLoggerJNI()
{
}





/**********************************************************************/
// MissionPackageIOJNI

jclass MissionPackageIOJNI::jclass_file = NULL;
jmethodID MissionPackageIOJNI::jmethod_fileCtor = NULL;
jmethodID MissionPackageIOJNI::jmethod_fileGetAbsPath = NULL;
jclass MissionPackageIOJNI::jclass_mptransferex = NULL;
jfieldID MissionPackageIOJNI::jfield_mptransferexStatus = NULL;
jmethodID MissionPackageIOJNI::jmethod_mptransferstatGetNative = NULL;
jmethodID MissionPackageIOJNI::jmethod_mpReceiveInit = NULL;
jmethodID MissionPackageIOJNI::jmethod_mpReceiveStatus = NULL;
jmethodID MissionPackageIOJNI::jmethod_mpSendStatus = NULL;
jmethodID MissionPackageIOJNI::jmethod_getCurrentPoint = NULL;
jmethodID MissionPackageIOJNI::jmethod_createUUID = NULL;
jclass MissionPackageIOJNI::jclass_mpReceiveUpdate = NULL;
jmethodID MissionPackageIOJNI::jmethod_mpReceiveUpdateCtor = NULL;
jclass MissionPackageIOJNI::jclass_mpSendUpdate = NULL;
jmethodID MissionPackageIOJNI::jmethod_mpSendUpdateCtor = NULL;
jfieldID MissionPackageIOJNI::jfield_cotPointLat = NULL;
jfieldID MissionPackageIOJNI::jfield_cotPointLon = NULL;
jfieldID MissionPackageIOJNI::jfield_cotPointHae = NULL;
jfieldID MissionPackageIOJNI::jfield_cotPointCe = NULL;
jfieldID MissionPackageIOJNI::jfield_cotPointLe = NULL;
jglobalobjectref MissionPackageIOJNI::nativeMPStatusToJava[NUM_MP_STATUS];

MissionPackageIOJNI::MissionPackageIOJNI(JNIEnv *env, jobject jmpio)
                        COMMO_THROW (int) : MissionPackageIO(), jmpio(NULL)
{
    this->jmpio = env->NewGlobalRef(jmpio);
    if (!this->jmpio)
        throw 1;
}


void MissionPackageIOJNI::destroy(JNIEnv *env, MissionPackageIOJNI *mpio)
{
    if (mpio->jmpio) {
        env->DeleteGlobalRef(mpio->jmpio);
        mpio->jmpio = NULL;
    }
    delete mpio;
}

MissionPackageTransferStatus MissionPackageIOJNI::missionPackageReceiveInit(
                    char *destFile, size_t destFileSize,
                    const char *transferName, const char *sha256hash,
                    uint64_t sizeInBytes,
                    const char *senderCallsign)
{
    MissionPackageTransferStatus ret = MP_TRANSFER_FINISHED_FAILED;
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return ret;
    
    jstring jfilename = env->NewStringUTF(destFile);
    jstring jtransferName = env->NewStringUTF(transferName);
    jstring jsha256hash = env->NewStringUTF(sha256hash);
    jstring jsenderCallsign = env->NewStringUTF(senderCallsign);
    jlong jsizeBytes;
    if (sizeInBytes > INT64_MAX)
        jsizeBytes = INT64_MAX;
    else
        jsizeBytes = (jlong)sizeInBytes;
    
    jobject jfile = env->CallObjectMethod(jmpio, jmethod_mpReceiveInit,
                                          jfilename, jtransferName,
                                          jsha256hash, 
                                          jsizeBytes,
                                          jsenderCallsign);
    
    jthrowable jex = env->ExceptionOccurred();
    if (jex) {
        env->ExceptionClear();
        if (env->IsInstanceOf(jex, jclass_mptransferex)) {
            jobject jstatus = env->GetObjectField(jex, jfield_mptransferexStatus);
            jint id = env->CallIntMethod(jstatus, jmethod_mptransferstatGetNative);
            ret = (MissionPackageTransferStatus)id;
        }
    } else if (jfile != NULL) {
        jstring jpath = (jstring)
                env->CallObjectMethod(jfile, jmethod_fileGetAbsPath);
        if (env->ExceptionOccurred())
            env->ExceptionClear();
        else {
            const char *path = env->GetStringUTFChars(jpath, NULL);
            if (!path) {
                env->ExceptionClear();
            } else {
                if (strlen(path) < destFileSize) {
                    strcpy(destFile, path);
                    ret = MP_TRANSFER_FINISHED_SUCCESS;
                }
                env->ReleaseStringUTFChars(jpath, path);
            }
        }
    }

    return ret;
}

void MissionPackageIOJNI::missionPackageReceiveStatusUpdate(
                    const MissionPackageReceiveStatusUpdate *update)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;
    
    jstring jdestFile = env->NewStringUTF(update->localFile);
    jobject jmpstat = nativeMPStatusToJava[update->status];
    jobject jfile = env->NewObject(jclass_file, jmethod_fileCtor, jdestFile);
    jstring jerror = update->errorDetail == NULL ? NULL : 
                               env->NewStringUTF(update->errorDetail);
    uint64_t received = update->totalBytesReceived;
    if (received > INT64_MAX)
        received = INT64_MAX;
    uint64_t expected = update->totalBytesExpected;
    if (expected > INT64_MAX)
        expected = INT64_MAX;
        
    jobject jupdate = env->NewObject(jclass_mpReceiveUpdate, 
                                     jmethod_mpReceiveUpdateCtor,
                                     jfile,
                                     jmpstat,
                                     static_cast<jlong>(received),
                                     static_cast<jlong>(expected),
                                     update->attempt,
                                     update->maxAttempts,
                                     jerror);

    env->CallVoidMethod(jmpio, jmethod_mpReceiveStatus, jupdate);
}


void MissionPackageIOJNI::missionPackageSendStatusUpdate(
                    const MissionPackageSendStatusUpdate *update)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    uint64_t bcount = update->totalBytesTransferred;
    if (bcount > INT64_MAX)
        bcount = INT64_MAX;
    
    jstring juid = NULL;
    if (update->recipient) {
        std::string str((const char *)update->recipient->contactUID,
                        update->recipient->contactUIDLen);
        juid = env->NewStringUTF(str.c_str());
    }
    jobject jmpstat = nativeMPStatusToJava[update->status];
    jstring jdetail = update->additionalDetail ?
                      env->NewStringUTF(update->additionalDetail) : 
                      NULL;
    
    jobject jupdate = env->NewObject(jclass_mpSendUpdate, 
                                     jmethod_mpSendUpdateCtor,
                                     update->xferid,
                                     juid, jmpstat, 
                                     jdetail,
                                     static_cast<jlong>(bcount));

    env->CallVoidMethod(jmpio, jmethod_mpSendStatus, jupdate);
}


CoTPointData MissionPackageIOJNI::getCurrentPoint()
{
    static const CoTPointData failPoint(0, 0, COMMO_COT_POINT_NO_VALUE,
                                              COMMO_COT_POINT_NO_VALUE,
                                              COMMO_COT_POINT_NO_VALUE);
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return failPoint;
    
    jobject jpoint = env->CallObjectMethod(jmpio, jmethod_getCurrentPoint);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return failPoint;
    }
    
    double lat = env->GetDoubleField(jpoint, jfield_cotPointLat);
    double lon = env->GetDoubleField(jpoint, jfield_cotPointLon);
    double ce = env->GetDoubleField(jpoint, jfield_cotPointCe);
    double le = env->GetDoubleField(jpoint, jfield_cotPointLe);
    double hae = env->GetDoubleField(jpoint, jfield_cotPointHae);
    
    return CoTPointData(lat, lon, hae, ce, le);
}

void MissionPackageIOJNI::createUUID(char *uuidString)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;
    
    jstring juuid = (jstring)env->CallObjectMethod(jmpio, jmethod_createUUID);
    const char *uuid = env->GetStringUTFChars(juuid, NULL);
    strncpy(uuidString, uuid, COMMO_UUID_STRING_BUFSIZE);
    uuidString[COMMO_UUID_STRING_BUFSIZE - 1] = '\0';
    env->ReleaseStringUTFChars(juuid, uuid);
}


bool MissionPackageIOJNI::reflectionInit(JNIEnv *env)
{
    jclass class_mpio;
    jclass class_cotPoint;
    jclass class_mptransferstat;
    jclass class_commo;
    jfieldID field_localportdisable;
    jmethodID method_mptransferstatValues;
    jmethodID method_mptransferstatName;
    jmethodID method_mptransferstatValue;
    jobjectArray jmptransferValues;
    jint localportdisable;
    jsize numVals;
    bool ret = false;

    memset(nativeMPStatusToJava, 0, sizeof(nativeMPStatusToJava));

    LOOKUP_CLASS(jclass_file, "java/io/File", false);
    LOOKUP_METHOD(jmethod_fileCtor, jclass_file,
                  "<init>", "(Ljava/lang/String;)V");
    LOOKUP_METHOD(jmethod_fileGetAbsPath, jclass_file,
                  "getAbsolutePath", "()Ljava/lang/String;");
    
    LOOKUP_CLASS(jclass_mptransferex,
                 COMMO_PACKAGE "MissionPackageTransferException", false);
    LOOKUP_FIELD(jfield_mptransferexStatus, jclass_mptransferex, "status",
                 "L" COMMO_PACKAGE "MissionPackageTransferStatus;");
    LOOKUP_CLASS(class_mpio, COMMO_PACKAGE "MissionPackageIO", true);
    LOOKUP_METHOD(jmethod_mpReceiveInit, class_mpio, 
                  "missionPackageReceiveInit",
                  "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)Ljava/io/File;");
    LOOKUP_METHOD(jmethod_mpReceiveStatus, class_mpio, 
                  "missionPackageReceiveStatusUpdate",
                  "(L" COMMO_PACKAGE "MissionPackageReceiveStatusUpdate;)V");
    LOOKUP_METHOD(jmethod_mpSendStatus, class_mpio, 
                  "missionPackageSendStatusUpdate",
                  "(L" COMMO_PACKAGE "MissionPackageSendStatusUpdate;)V");
    LOOKUP_METHOD(jmethod_getCurrentPoint, class_mpio,
                  "getCurrentPoint",
                  "()L" COMMO_PACKAGE "CoTPointData;");
    LOOKUP_METHOD(jmethod_createUUID, class_mpio,
                  "createUUID",
                  "()Ljava/lang/String;");
    
    LOOKUP_CLASS(jclass_mpReceiveUpdate, 
                 COMMO_PACKAGE "MissionPackageReceiveStatusUpdate", false);
    LOOKUP_METHOD(jmethod_mpReceiveUpdateCtor, jclass_mpReceiveUpdate,
                  "<init>",
                  "(Ljava/io/File;L" COMMO_PACKAGE "MissionPackageTransferStatus;JJIILjava/lang/String;)V");
    
    LOOKUP_CLASS(jclass_mpSendUpdate, 
                 COMMO_PACKAGE "MissionPackageSendStatusUpdate", false);
    LOOKUP_METHOD(jmethod_mpSendUpdateCtor, jclass_mpSendUpdate,
                  "<init>",
                  "(ILjava/lang/String;L" COMMO_PACKAGE "MissionPackageTransferStatus;Ljava/lang/String;J)V");
    
    LOOKUP_CLASS(class_cotPoint, COMMO_PACKAGE "CoTPointData", true);
    LOOKUP_FIELD(jfield_cotPointLat, class_cotPoint,
                  "lat",
                  "D");
    LOOKUP_FIELD(jfield_cotPointLon, class_cotPoint,
                  "lon",
                  "D");
    LOOKUP_FIELD(jfield_cotPointHae, class_cotPoint,
                  "hae",
                  "D");
    LOOKUP_FIELD(jfield_cotPointCe, class_cotPoint,
                  "ce",
                  "D");
    LOOKUP_FIELD(jfield_cotPointLe, class_cotPoint,
                  "le",
                  "D");
    
    // Check static field for correct value
    LOOKUP_CLASS(class_commo,
                 COMMO_PACKAGE "Commo", true);
    field_localportdisable = 
        env->GetStaticFieldID(class_commo, "MPIO_LOCAL_PORT_DISABLE", "I");
    if (!field_localportdisable)
        goto cleanup;
    localportdisable = env->GetStaticIntField(class_commo, 
                                              field_localportdisable);
    if (env->ExceptionOccurred())
        goto cleanup;
    if (localportdisable != MP_LOCAL_PORT_DISABLE)
        goto cleanup;

    // Build mapping of native MP Transfer Status to java versions
    LOOKUP_CLASS(class_mptransferstat, 
                 COMMO_PACKAGE "MissionPackageTransferStatus", true);
    LOOKUP_METHOD(jmethod_mptransferstatGetNative, class_mptransferstat,
                  "getNativeVal", "()I");
    LOOKUP_STATIC_METHOD(method_mptransferstatValues, class_mptransferstat,
                         "values",
                         "()[L" COMMO_PACKAGE "MissionPackageTransferStatus;");
    jmptransferValues = (jobjectArray)env->CallStaticObjectMethod(
                         class_mptransferstat, method_mptransferstatValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jmptransferValues);
    
    if (numVals != NUM_MP_STATUS)
        goto cleanup;
        
    LOOKUP_METHOD(method_mptransferstatName, class_mptransferstat, 
                  "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_mptransferstatValue, class_mptransferstat,
                  "getNativeVal", "()I");

#define CHECK_MPSTAT(val) checkEnum(env, method_mptransferstatName,          \
                                      method_mptransferstatValue,            \
                                      jmptransferValues, numVals,            \
                                      #val,                                  \
                                      nativeMPStatusToJava,                  \
                                      PASTE(MP_TRANSFER_, val),              \
                                      PASTE(MP_TRANSFER_, val))              \

    ret = true;
    ret = ret && CHECK_MPSTAT(FINISHED_SUCCESS);
    ret = ret && CHECK_MPSTAT(FINISHED_TIMED_OUT);
    ret = ret && CHECK_MPSTAT(FINISHED_CONTACT_GONE);
    ret = ret && CHECK_MPSTAT(FINISHED_FAILED);
    ret = ret && CHECK_MPSTAT(FINISHED_FILE_EXISTS);
    ret = ret && CHECK_MPSTAT(FINISHED_DISABLED_LOCALLY);
    ret = ret && CHECK_MPSTAT(ATTEMPT_IN_PROGRESS);
    ret = ret && CHECK_MPSTAT(ATTEMPT_FAILED);
    ret = ret && CHECK_MPSTAT(SERVER_UPLOAD_PENDING);
    ret = ret && CHECK_MPSTAT(SERVER_UPLOAD_IN_PROGRESS);
    ret = ret && CHECK_MPSTAT(SERVER_UPLOAD_SUCCESS);
    ret = ret && CHECK_MPSTAT(SERVER_UPLOAD_FAILED);
#undef CHECK_MPSTAT

cleanup:
    return ret;
}

void MissionPackageIOJNI::reflectionRelease(JNIEnv *env)
{
    for (int i = 0; i < NUM_MP_STATUS; ++i) {
        if (nativeMPStatusToJava[i])
            env->DeleteGlobalRef(nativeMPStatusToJava[i]);
        nativeMPStatusToJava[i] = NULL;
    }
    
    env->DeleteGlobalRef(jclass_file);
    env->DeleteGlobalRef(jclass_mpReceiveUpdate);
    env->DeleteGlobalRef(jclass_mpSendUpdate);
    env->DeleteGlobalRef(jclass_mptransferex);
}

MissionPackageIOJNI::~MissionPackageIOJNI()
{
}





/**********************************************************************/
// SimpleFileIOJNI


jmethodID SimpleFileIOJNI::jmethod_fileTransferUpdate = NULL;
    
jclass SimpleFileIOJNI::jclass_fileIOUpdate = NULL;
jmethodID SimpleFileIOJNI::jmethod_fileIOUpdateCtor = NULL;
jglobalobjectref SimpleFileIOJNI::nativeFileStatusToJava[NUM_FILE_STATUS];

SimpleFileIOJNI::SimpleFileIOJNI(JNIEnv *env, jobject jfileio)
                        COMMO_THROW (int) : SimpleFileIO(), jfileio(NULL)
{
    this->jfileio = env->NewGlobalRef(jfileio);
    if (!this->jfileio)
        throw 1;
}


void SimpleFileIOJNI::destroy(JNIEnv *env, SimpleFileIOJNI *fileio)
{
    if (fileio->jfileio) {
        env->DeleteGlobalRef(fileio->jfileio);
        fileio->jfileio = NULL;
    }
    delete fileio;
}


void SimpleFileIOJNI::fileTransferUpdate(
                    const SimpleFileIOUpdate *update)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;
    
    jstring jinfo = NULL;
    if (update->additionalInfo != NULL)
        jinfo = env->NewStringUTF((const char *)
                                     update->additionalInfo);
    jobject jstat = nativeFileStatusToJava[update->status];
    
    // Clamp to signed range of Java
    jlong soFar = 0;
    if (update->bytesTransferred <= INT64_MAX)
        soFar = (jlong)update->bytesTransferred;
    jlong total = 0;
    if (update->totalBytesToTransfer <= INT64_MAX)
        total = (jlong)update->totalBytesToTransfer;
    
    
    jobject jupdate = env->NewObject(jclass_fileIOUpdate, 
                                     jmethod_fileIOUpdateCtor,
                                     update->xferid,
                                     jstat,
                                     jinfo,
                                     soFar,
                                     total);

    env->CallVoidMethod(jfileio, jmethod_fileTransferUpdate, jupdate);
}


bool SimpleFileIOJNI::reflectionInit(JNIEnv *env)
{
    jclass class_fileio;
    jclass class_filetransferstat;
    jmethodID method_filetransferstatValues;
    jmethodID method_filetransferstatName;
    jmethodID method_filetransferstatValue;
    jobjectArray jfiletransferValues;
    jsize numVals;
    bool ret = false;

    memset(nativeFileStatusToJava, 0, sizeof(nativeFileStatusToJava));

    LOOKUP_CLASS(class_fileio, COMMO_PACKAGE "SimpleFileIO", true);
    LOOKUP_METHOD(jmethod_fileTransferUpdate, class_fileio, 
                  "fileTransferUpdate",
                  "(L" COMMO_PACKAGE "SimpleFileIOUpdate;)V");
    
    LOOKUP_CLASS(jclass_fileIOUpdate, 
                 COMMO_PACKAGE "SimpleFileIOUpdate", false);
    LOOKUP_METHOD(jmethod_fileIOUpdateCtor, jclass_fileIOUpdate,
                  "<init>",
                  "(IL" COMMO_PACKAGE "SimpleFileIOStatus;Ljava/lang/String;JJ)V");
    
    // Build mapping of native file io status to java versions
    LOOKUP_CLASS(class_filetransferstat, 
                 COMMO_PACKAGE "SimpleFileIOStatus", true);
    LOOKUP_STATIC_METHOD(method_filetransferstatValues, class_filetransferstat,
                         "values",
                         "()[L" COMMO_PACKAGE "SimpleFileIOStatus;");
    jfiletransferValues = (jobjectArray)env->CallStaticObjectMethod(
                         class_filetransferstat, method_filetransferstatValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jfiletransferValues);
    
    if (numVals != NUM_FILE_STATUS)
        goto cleanup;
        
    LOOKUP_METHOD(method_filetransferstatName, class_filetransferstat, 
                  "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_filetransferstatValue, class_filetransferstat,
                  "getNativeVal", "()I");

#define CHECK_FSTAT(val) checkEnum(env, method_filetransferstatName,         \
                                      method_filetransferstatValue,          \
                                      jfiletransferValues, numVals,          \
                                      #val,                                  \
                                      nativeFileStatusToJava,                \
                                      PASTE(FILEIO_, val),                   \
                                      PASTE(FILEIO_, val))                   \

    ret = true;
    ret = ret && CHECK_FSTAT(INPROGRESS);
    ret = ret && CHECK_FSTAT(SUCCESS);
    ret = ret && CHECK_FSTAT(HOST_RESOLUTION_FAIL);
    ret = ret && CHECK_FSTAT(CONNECT_FAIL);
    ret = ret && CHECK_FSTAT(URL_INVALID);
    ret = ret && CHECK_FSTAT(URL_UNSUPPORTED);
    ret = ret && CHECK_FSTAT(URL_NO_RESOURCE);
    ret = ret && CHECK_FSTAT(LOCAL_FILE_OPEN_FAILURE);
    ret = ret && CHECK_FSTAT(LOCAL_IO_ERROR);
    ret = ret && CHECK_FSTAT(SSL_UNTRUSTED_SERVER);
    ret = ret && CHECK_FSTAT(SSL_OTHER_ERROR);
    ret = ret && CHECK_FSTAT(AUTH_ERROR);
    ret = ret && CHECK_FSTAT(ACCESS_DENIED);
    ret = ret && CHECK_FSTAT(TRANSFER_TIMEOUT);
    ret = ret && CHECK_FSTAT(OTHER_ERROR);
#undef CHECK_FSTAT

cleanup:
    return ret;
}

void SimpleFileIOJNI::reflectionRelease(JNIEnv *env)
{
    for (int i = 0; i < NUM_FILE_STATUS; ++i) {
        if (nativeFileStatusToJava[i])
            env->DeleteGlobalRef(nativeFileStatusToJava[i]);
        nativeFileStatusToJava[i] = NULL;
    }
    
    env->DeleteGlobalRef(jclass_fileIOUpdate);
}

jglobalobjectref SimpleFileIOJNI::getJavaFileStatus(SimpleFileIOStatus stat)
{
    return nativeFileStatusToJava[stat];
}

SimpleFileIOJNI::~SimpleFileIOJNI()
{
}



/**********************************************************************/
// InterfaceStatusListenerJNI

jmethodID InterfaceStatusListenerJNI::jmethod_ifaceUp = NULL;
jmethodID InterfaceStatusListenerJNI::jmethod_ifaceDown = NULL;
jmethodID InterfaceStatusListenerJNI::jmethod_ifaceError = NULL;
jglobalobjectref InterfaceStatusListenerJNI::nativeErrCodesToJava[NUM_IFACE_ERRCODES];

InterfaceStatusListenerJNI::InterfaceStatusListenerJNI(JNIEnv *env,
                                       jobject jifaceListener,
                                       PGSC::Thread::Mutex *ifaceMapMutex,
                                       NetInterfaceMap *ifaceMap) COMMO_THROW (int) :
           JNIObjWrapper(), InterfaceStatusListener(), jifaceListener(NULL),
           ifaceMapMutex(ifaceMapMutex), ifaceMap(ifaceMap)
{
    this->jifaceListener = env->NewGlobalRef(jifaceListener);
    if (!this->jifaceListener)
        throw 1;
}

void InterfaceStatusListenerJNI::destroy(JNIEnv *env,
           InterfaceStatusListenerJNI *iface)
{
    if (iface->jifaceListener) {
        env->DeleteGlobalRef(iface->jifaceListener);
        iface->jifaceListener = NULL;
    }
    delete iface;
}

void InterfaceStatusListenerJNI::interfaceChange(NetInterface *iface,
                                                 jmethodID jmethodUpDown)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    jobject jiface = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, *ifaceMapMutex);
        NetInterfaceMap::iterator iter = ifaceMap->find(iface);
        if (iter == ifaceMap->end())
            return;
        jiface = env->NewLocalRef(iter->second);
    }

    env->CallVoidMethod(jifaceListener, jmethodUpDown, jiface);
}


void InterfaceStatusListenerJNI::interfaceUp(NetInterface *iface)
{
    interfaceChange(iface, jmethod_ifaceUp);
}

void InterfaceStatusListenerJNI::interfaceDown(NetInterface *iface)
{
    interfaceChange(iface, jmethod_ifaceDown);
}

void InterfaceStatusListenerJNI::interfaceError(NetInterface *iface, 
                    netinterfaceenums::NetInterfaceErrorCode errCode)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    jobject jiface = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, *ifaceMapMutex);
        NetInterfaceMap::iterator iter = ifaceMap->find(iface);
        if (iter == ifaceMap->end())
            return;
        jiface = env->NewLocalRef(iter->second);
    }

    env->CallVoidMethod(jifaceListener, jmethod_ifaceError, jiface,
                        nativeErrCodesToJava[(int)errCode]);
}

jglobalobjectref InterfaceStatusListenerJNI::getWrappedRef() const
{
    return jifaceListener;
}
        
bool InterfaceStatusListenerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_ifaceListener;
    jclass class_netErrCode;
    jmethodID method_errCodeValues;
    jmethodID method_errCodeName;
    jmethodID method_errCodeValue;
    jobjectArray jerrCodeVals;
    jsize numVals;

    LOOKUP_CLASS(class_ifaceListener, COMMO_PACKAGE "InterfaceStatusListener", true);
    LOOKUP_METHOD(jmethod_ifaceUp, class_ifaceListener,
                  "interfaceUp",
                  "(L" COMMO_PACKAGE "NetInterface;)V");
    LOOKUP_METHOD(jmethod_ifaceDown, class_ifaceListener,
                  "interfaceDown",
                  "(L" COMMO_PACKAGE "NetInterface;)V");
    LOOKUP_METHOD(jmethod_ifaceError, class_ifaceListener,
                  "interfaceError",
                  "(L" COMMO_PACKAGE "NetInterface;L" COMMO_PACKAGE "NetInterfaceErrorCode;)V");

    // Build mapping of native levels to java levels
    LOOKUP_CLASS(class_netErrCode, COMMO_PACKAGE "NetInterfaceErrorCode", true);
    LOOKUP_STATIC_METHOD(method_errCodeValues, class_netErrCode,
                         "values",
                         "()[L" COMMO_PACKAGE "NetInterfaceErrorCode;");
    jerrCodeVals = (jobjectArray)env->CallStaticObjectMethod(class_netErrCode,
                                                        method_errCodeValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jerrCodeVals);
    
    if (numVals != NUM_IFACE_ERRCODES)
        goto cleanup;
        
    LOOKUP_METHOD(method_errCodeName, class_netErrCode, "name", "()Ljava/lang/String;");
    LOOKUP_METHOD(method_errCodeValue, class_netErrCode, "getNativeVal", "()I");

#define CHECK_ERRCODE(errCode) checkEnum(env, method_errCodeName,              \
                                      method_errCodeValue,                     \
                                      jerrCodeVals, numVals,                   \
                                      #errCode,                                \
                                      nativeErrCodesToJava,                    \
                                      PASTE(netinterfaceenums::ERR_, errCode), \
                                      PASTE(netinterfaceenums::ERR_, errCode)) \

    ret = true;
    ret = ret && CHECK_ERRCODE(CONN_NAME_RES_FAILED);
    ret = ret && CHECK_ERRCODE(CONN_REFUSED);
    ret = ret && CHECK_ERRCODE(CONN_TIMEOUT);
    ret = ret && CHECK_ERRCODE(CONN_HOST_UNREACHABLE);
    ret = ret && CHECK_ERRCODE(CONN_SSL_NO_PEER_CERT);
    ret = ret && CHECK_ERRCODE(CONN_SSL_PEER_CERT_NOT_TRUSTED);
    ret = ret && CHECK_ERRCODE(CONN_SSL_HANDSHAKE);
    ret = ret && CHECK_ERRCODE(CONN_OTHER);
    ret = ret && CHECK_ERRCODE(IO_RX_DATA_TIMEOUT);
    ret = ret && CHECK_ERRCODE(IO);
    ret = ret && CHECK_ERRCODE(INTERNAL);
    ret = ret && CHECK_ERRCODE(OTHER);
#undef CHECK_ERRCODE

cleanup:
    return ret;
}

void InterfaceStatusListenerJNI::reflectionRelease(JNIEnv *env)
{
}

InterfaceStatusListenerJNI::~InterfaceStatusListenerJNI()
{
}




/**********************************************************************/
// ContactListenerJNI

jclass ContactListenerJNI::jclass_contact = NULL;
jmethodID ContactListenerJNI::jmethod_contactCtor = NULL;
jmethodID ContactListenerJNI::jmethod_contactAdded = NULL;
jmethodID ContactListenerJNI::jmethod_contactRemoved = NULL;

ContactListenerJNI::ContactListenerJNI(JNIEnv *env,
                    jobject jcontactListener) COMMO_THROW (int) :
          JNIObjWrapper(), ContactPresenceListener(),
          jcontactListener(NULL)
{
    this->jcontactListener = env->NewGlobalRef(jcontactListener);
    if (!this->jcontactListener)
        throw 1;
}


void ContactListenerJNI::destroy(JNIEnv *env, ContactListenerJNI *listener)
{
    if (listener->jcontactListener) {
        env->DeleteGlobalRef(listener->jcontactListener);
        listener->jcontactListener = NULL;
    }
    delete listener;
}

void ContactListenerJNI::contactAdded(const ContactUID *contact)
{
    contactChanged(contact, jmethod_contactAdded);
}

void ContactListenerJNI::contactRemoved(const ContactUID *contact)
{
    contactChanged(contact, jmethod_contactRemoved);
}

jglobalobjectref ContactListenerJNI::getWrappedRef() const
{
    return jcontactListener;
}

bool ContactListenerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_contactListener = NULL;
    
    LOOKUP_CLASS(jclass_contact, COMMO_PACKAGE "Contact", false);
    LOOKUP_METHOD(jmethod_contactCtor, jclass_contact,
                  "<init>", "(Ljava/lang/String;)V");
    LOOKUP_CLASS(class_contactListener, COMMO_PACKAGE "ContactPresenceListener", true);
    LOOKUP_METHOD(jmethod_contactAdded, class_contactListener,
                  "contactAdded",
                  "(L" COMMO_PACKAGE "Contact;)V");
    LOOKUP_METHOD(jmethod_contactRemoved, class_contactListener,
                  "contactRemoved",
                  "(L" COMMO_PACKAGE "Contact;)V");
    ret = true;

cleanup:
    return ret;
}

void ContactListenerJNI::reflectionRelease(JNIEnv *env)
{
    env->DeleteGlobalRef(jclass_contact);
}

ContactListenerJNI::~ContactListenerJNI()
{
}

void ContactListenerJNI::contactChanged(const ContactUID *contact,
                    jmethodID jmethodAddRem)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    std::string str((const char *)contact->contactUID,
                    contact->contactUIDLen);
    jstring juid = env->NewStringUTF(str.c_str());
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }
    jobject jcontact = env->NewObject(jclass_contact, jmethod_contactCtor, 
                                      juid);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }

    env->CallVoidMethod(jcontactListener, jmethodAddRem, jcontact);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }
}



/**********************************************************************/
// CoTListenerJNI

jmethodID CoTListenerJNI::jmethod_cotReceived = NULL;

CoTListenerJNI::CoTListenerJNI(JNIEnv *env, jobject jcotListener)
                                       COMMO_THROW (int) :
          JNIObjWrapper(), CoTMessageListener(),
          jcotListener(NULL)
{
    this->jcotListener = env->NewGlobalRef(jcotListener);
    if (!this->jcotListener)
        throw 1;
}

void CoTListenerJNI::destroy(JNIEnv *env, CoTListenerJNI *listener)
{
    if (listener->jcotListener) {
        env->DeleteGlobalRef(listener->jcotListener);
        listener->jcotListener = NULL;
    }
    delete listener;
}

void CoTListenerJNI::cotMessageReceived(const char *cotMessage, const char *rxEndpointId)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    jstring jcotMessage = env->NewStringUTF(cotMessage);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }

    jstring jrxEndpointId = NULL;
    if (rxEndpointId) {
        jrxEndpointId = env->NewStringUTF(rxEndpointId);
        if (env->ExceptionOccurred()) {
            env->ExceptionClear();
            return;
        }
    }

    env->CallVoidMethod(jcotListener, jmethod_cotReceived, jcotMessage, jrxEndpointId);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }
}

jglobalobjectref CoTListenerJNI::getWrappedRef() const
{
    return jcotListener;
}

bool CoTListenerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_cotListener = NULL;
    
    LOOKUP_CLASS(class_cotListener, COMMO_PACKAGE "CoTMessageListener", true);
    LOOKUP_METHOD(jmethod_cotReceived, class_cotListener,
                  "cotMessageReceived",
                  "(Ljava/lang/String;Ljava/lang/String;)V");
    ret = true;

cleanup:
    return ret;
}

void CoTListenerJNI::reflectionRelease(JNIEnv *env)
{
}

CoTListenerJNI::~CoTListenerJNI()
{
}



/**********************************************************************/
// GenericDataListenerJNI

jmethodID GenericDataListenerJNI::jmethod_genericDataReceived = NULL;

GenericDataListenerJNI::GenericDataListenerJNI(JNIEnv *env, 
                                               jobject jgenericListener)
                                       COMMO_THROW (int) :
          JNIObjWrapper(), GenericDataListener(),
          jgenericListener(NULL)
{
    this->jgenericListener = env->NewGlobalRef(jgenericListener);
    if (!this->jgenericListener)
        throw 1;
}

void GenericDataListenerJNI::destroy(JNIEnv *env, 
                                     GenericDataListenerJNI *listener)
{
    if (listener->jgenericListener) {
        env->DeleteGlobalRef(listener->jgenericListener);
        listener->jgenericListener = NULL;
    }
    delete listener;
}

void GenericDataListenerJNI::genericDataReceived(const uint8_t *data, 
                                        size_t len,
                                        const char *rxEndpointId)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    jbyteArray jbytes = env->NewByteArray(len);
    if (!jbytes) {
        env->ExceptionClear();
        return;
    }

    env->SetByteArrayRegion(jbytes, 0, len, (const jbyte *)data);    

    jstring jrxEndpointId = NULL;
    if (rxEndpointId) {
        jrxEndpointId = env->NewStringUTF(rxEndpointId);
        if (env->ExceptionOccurred()) {
            env->ExceptionClear();
            return;
        }
    }

    env->CallVoidMethod(jgenericListener, jmethod_genericDataReceived,
                        jbytes, jrxEndpointId);
    if (env->ExceptionOccurred())
        env->ExceptionClear();
}

jglobalobjectref GenericDataListenerJNI::getWrappedRef() const
{
    return jgenericListener;
}

bool GenericDataListenerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_genericListener = NULL;
    
    LOOKUP_CLASS(class_genericListener, COMMO_PACKAGE "GenericDataListener",
                 true);
    LOOKUP_METHOD(jmethod_genericDataReceived, class_genericListener,
                  "genericDataReceived",
                  "([BLjava/lang/String;)V");
    ret = true;

cleanup:
    return ret;
}

void GenericDataListenerJNI::reflectionRelease(JNIEnv *env)
{
}

GenericDataListenerJNI::~GenericDataListenerJNI()
{
}



/**********************************************************************/
// CoTFailListenerJNI

jmethodID CoTFailListenerJNI::jmethod_sendCoTFailure = NULL;

CoTFailListenerJNI::CoTFailListenerJNI(JNIEnv *env, jobject jcotFailListener)
                                       COMMO_THROW (int) :
          JNIObjWrapper(), CoTSendFailureListener(),
          jcotFailListener(NULL)
{
    this->jcotFailListener = env->NewGlobalRef(jcotFailListener);
    if (!this->jcotFailListener)
        throw 1;
}

void CoTFailListenerJNI::destroy(JNIEnv *env, CoTFailListenerJNI *listener)
{
    if (listener->jcotFailListener) {
        env->DeleteGlobalRef(listener->jcotFailListener);
        listener->jcotFailListener = NULL;
    }
    delete listener;
}

void CoTFailListenerJNI::sendCoTFailure(const char *host, int port,
                                        const char *errorReason)
{
    JNIEnv *env = NULL;
    LocalJNIEnv localEnv(&env);
    if (!env)
        return;

    jstring jhost = env->NewStringUTF(host);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }

    jstring jerrorReason = env->NewStringUTF(errorReason);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }

    env->CallVoidMethod(jcotFailListener, jmethod_sendCoTFailure,
                        jhost, (jint)port, jerrorReason);
    if (env->ExceptionOccurred()) {
        env->ExceptionClear();
        return;
    }
}

jglobalobjectref CoTFailListenerJNI::getWrappedRef() const
{
    return jcotFailListener;
}

bool CoTFailListenerJNI::reflectionInit(JNIEnv *env)
{
    bool ret = false;
    jclass class_cotFailListener = NULL;
    
    LOOKUP_CLASS(class_cotFailListener,
                 COMMO_PACKAGE "CoTSendFailureListener", true);
    LOOKUP_METHOD(jmethod_sendCoTFailure, class_cotFailListener,
                  "sendCoTFailure",
                  "(Ljava/lang/String;ILjava/lang/String;)V");
    ret = true;

cleanup:
    return ret;
}

void CoTFailListenerJNI::reflectionRelease(JNIEnv *env)
{
}

CoTFailListenerJNI::~CoTFailListenerJNI()
{
}



/**********************************************************************/
// CommoJNI


jclass CommoJNI::jclass_physnetiface = NULL;
jmethodID CommoJNI::jmethod_physnetifaceCtor = NULL;
jclass CommoJNI::jclass_tcpnetiface = NULL;
jmethodID CommoJNI::jmethod_tcpnetifaceCtor = NULL;
jclass CommoJNI::jclass_streamnetiface = NULL;
jmethodID CommoJNI::jmethod_streamnetifaceCtor = NULL;
jclass CommoJNI::jclass_cloudclient = NULL;
jmethodID CommoJNI::jmethod_cloudclientCtor = NULL;
jclass CommoJNI::jclass_commoexception = NULL;
jclass CommoJNI::jclass_mpnativeresult = NULL;
jmethodID CommoJNI::jmethod_mpnativeresultCtor = NULL;


CommoJNI::CommoJNI(JNIEnv *env, jobject jlogger,
                   jstring jourUID, jstring jourCallsign) COMMO_THROW (int) :
        commo(NULL), mpio(NULL), fileio(NULL), logger(NULL),
        ifaceListenersMutex(), ifaceListeners(),
        contactListenersMutex(), contactListeners(),
        cotListenersMutex(), cotListeners(),
        genericListenersMutex(), genericListeners(),
        cotFailListenersMutex(), cotFailListeners(),
        netInterfaceMapMutex(), netInterfaceMap(),
        cloudclientMapMutex(), cloudclientMap()
{
    const char *uid = NULL;
    const char *callsign = NULL;

    logger = new CommoLoggerJNI(env, jlogger);
    uid = env->GetStringUTFChars(jourUID, NULL);
    if (!uid)
        goto err;

    callsign = env->GetStringUTFChars(jourCallsign, NULL);
    if (!callsign)
        goto err;
    
    {
        ContactUID uidobj((const uint8_t *)uid, env->GetStringUTFLength(jourUID));
    
        commo = new Commo(logger, &uidobj, callsign);
    }
    if (!commo)
        goto err;
    
    env->ReleaseStringUTFChars(jourUID, uid);
    env->ReleaseStringUTFChars(jourCallsign, callsign);
    return;

err:
    if (uid)
        env->ReleaseStringUTFChars(jourUID, uid);
    if (callsign)
        env->ReleaseStringUTFChars(jourCallsign, callsign);
    if (logger)
        CommoLoggerJNI::destroy(env, logger);
    uid = callsign = NULL;
    logger = NULL;
    
    throw 1;
}

void CommoJNI::destroy(JNIEnv *env, CommoJNI *commoJNI)
{
    commoJNI->commo->shutdown();
    delete commoJNI->commo;

    CommoLoggerJNI::destroy(env, commoJNI->logger);
    if (commoJNI->mpio)
        MissionPackageIOJNI::destroy(env, commoJNI->mpio);
    if (commoJNI->fileio)
        SimpleFileIOJNI::destroy(env, commoJNI->fileio);

    CloudclientMap::iterator iter;
    for (iter = commoJNI->cloudclientMap.begin(); iter != commoJNI->cloudclientMap.end(); ++iter)
        CloudIOJNI::destroy(env, iter->second.first);
        
    delete commoJNI;
}

void CommoJNI::setupMPIO(JNIEnv *env, jobject jmpio) COMMO_THROW (int)
{
    if (mpio)
        throw 1;
    MissionPackageIOJNI *newmpio = new MissionPackageIOJNI(env, jmpio);
    if (commo->setupMissionPackageIO(newmpio) != COMMO_SUCCESS) {
        MissionPackageIOJNI::destroy(env, newmpio);
        throw 1;
    }
    mpio = newmpio;
}
        
void CommoJNI::enableFileIO(JNIEnv *env, jobject jfileio) COMMO_THROW (int)
{
    if (fileio)
        throw 1;
    SimpleFileIOJNI *newfileio = new SimpleFileIOJNI(env, jfileio);
    if (commo->enableSimpleFileIO(newfileio) != COMMO_SUCCESS) {
        SimpleFileIOJNI::destroy(env, newfileio);
        throw 1;
    }
    fileio = newfileio;
}

jlong CommoJNI::registerFileIOProvider(JNIEnv* env, jobject jfileioprovider)
{
    using namespace atakmap::commoncommo;
    try{
        auto provider(std::static_pointer_cast<FileIOProvider>(
            std::make_shared<JNIFileIOProvider>(*env, jfileioprovider)));
        commo->registerFileIOProvider(provider);
        return (jlong)(intptr_t)provider.get();
    }catch (int &) {
        return jlong(0);
    }
}

void CommoJNI::deregisterFileIOProvider(JNIEnv* env, jlong jfileioprovider)
{
    using namespace atakmap::commoncommo;
    FileIOProvider *provider = (FileIOProvider *)(intptr_t)jfileioprovider;
    if(!provider)
        return;
    commo->deregisterFileIOProvider(*provider);
}

bool CommoJNI::addIfaceStatusListener(JNIEnv *env, jobject jifaceListener)
{
    InterfaceStatusListenerJNI *newListener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, ifaceListenersMutex);

        std::set<InterfaceStatusListenerJNI *>::iterator iter;
        for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
            InterfaceStatusListenerJNI *listener = *iter;
            if (env->IsSameObject(jifaceListener, listener->getWrappedRef()))
                return false;
        }
        
        try {
            newListener = new InterfaceStatusListenerJNI(env,
                                                         jifaceListener,
                                                         &netInterfaceMapMutex,
                                                         &netInterfaceMap);
        } catch (int &) {
            return false;
        }
        ifaceListeners.insert(newListener);
    }
    // then add it
    if (commo->addInterfaceStatusListener(newListener) != COMMO_SUCCESS) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, ifaceListenersMutex);
        ifaceListeners.erase(newListener);
        InterfaceStatusListenerJNI::destroy(env, newListener);
        return false;
    }
    return true;
}

bool CommoJNI::removeIfaceStatusListener(JNIEnv *env, jobject jifaceListener)
{
    InterfaceStatusListenerJNI *listener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, ifaceListenersMutex);

        std::set<InterfaceStatusListenerJNI *>::iterator iter;
        for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
            InterfaceStatusListenerJNI *listener = *iter;
            if (env->IsSameObject(jifaceListener, listener->getWrappedRef()))
                break;
        }
        
        if (iter == ifaceListeners.end())
            return false;
        
        listener = *iter;
        ifaceListeners.erase(iter);
    }
    commo->removeInterfaceStatusListener(listener);
    InterfaceStatusListenerJNI::destroy(env, listener);
    return true;
}

bool CommoJNI::addContactListener(JNIEnv *env, jobject jcontactListener)
{
    ContactListenerJNI *newListener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, contactListenersMutex);

        std::set<ContactListenerJNI *>::iterator iter;
        for (iter = contactListeners.begin(); iter != contactListeners.end(); ++iter) {
            ContactListenerJNI *listener = *iter;
            if (env->IsSameObject(jcontactListener, listener->getWrappedRef()))
                return false;
        }
        
        try {
            newListener = new ContactListenerJNI(env,
                                                 jcontactListener);
        } catch (int &) {
            return false;
        }
        contactListeners.insert(newListener);
    }
    // then add it
    if (commo->addContactPresenceListener(newListener) != COMMO_SUCCESS) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, contactListenersMutex);
        contactListeners.erase(newListener);
        ContactListenerJNI::destroy(env, newListener);
        return false;
    }
    return true;
}

bool CommoJNI::removeContactListener(JNIEnv *env, jobject jcontactListener)
{
    ContactListenerJNI *listener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, contactListenersMutex);

        std::set<ContactListenerJNI *>::iterator iter;
        for (iter = contactListeners.begin(); iter != contactListeners.end(); ++iter) {
            ContactListenerJNI *listener = *iter;
            if (env->IsSameObject(jcontactListener, listener->getWrappedRef()))
                break;
        }
        
        if (iter == contactListeners.end())
            return false;
        
        listener = *iter;
        contactListeners.erase(iter);
    }
    commo->removeContactPresenceListener(listener);
    ContactListenerJNI::destroy(env, listener);
    return true;
}

bool CommoJNI::addCoTListener(JNIEnv *env, jobject jcotListener)
{
    CoTListenerJNI *newListener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotListenersMutex);

        std::set<CoTListenerJNI *>::iterator iter;
        for (iter = cotListeners.begin(); iter != cotListeners.end(); ++iter) {
            CoTListenerJNI *listener = *iter;
            if (env->IsSameObject(jcotListener, listener->getWrappedRef()))
                return false;
        }
        
        try {
            newListener = new CoTListenerJNI(env,
                                             jcotListener);
        } catch (int &) {
            return false;
        }
        cotListeners.insert(newListener);
    }
    // then add it
    if (commo->addCoTMessageListener(newListener) != COMMO_SUCCESS) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotListenersMutex);
        cotListeners.erase(newListener);
        CoTListenerJNI::destroy(env, newListener);
        return false;
    }
    return true;
}

bool CommoJNI::removeCoTListener(JNIEnv *env, jobject jcotListener)
{
    CoTListenerJNI *listener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotListenersMutex);

        std::set<CoTListenerJNI *>::iterator iter;
        for (iter = cotListeners.begin(); iter != cotListeners.end(); ++iter) {
            CoTListenerJNI *listener = *iter;
            if (env->IsSameObject(jcotListener, listener->getWrappedRef()))
                break;
        }
        
        if (iter == cotListeners.end())
            return false;
        
        listener = *iter;
        cotListeners.erase(iter);
    }
    commo->removeCoTMessageListener(listener);
    CoTListenerJNI::destroy(env, listener);
    return true;
}

bool CommoJNI::addGenericListener(JNIEnv *env, jobject jgenericListener)
{
    GenericDataListenerJNI *newListener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, genericListenersMutex);

        std::set<GenericDataListenerJNI *>::iterator iter;
        for (iter = genericListeners.begin(); iter != genericListeners.end(); ++iter) {
            GenericDataListenerJNI *listener = *iter;
            if (env->IsSameObject(jgenericListener, listener->getWrappedRef()))
                return false;
        }
        
        try {
            newListener = new GenericDataListenerJNI(env,
                                             jgenericListener);
        } catch (int &) {
            return false;
        }
        genericListeners.insert(newListener);
    }
    // then add it
    if (commo->addGenericDataListener(newListener) != COMMO_SUCCESS) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, genericListenersMutex);
        genericListeners.erase(newListener);
        GenericDataListenerJNI::destroy(env, newListener);
        return false;
    }
    return true;
}

bool CommoJNI::removeGenericListener(JNIEnv *env, jobject jgenericListener)
{
    GenericDataListenerJNI *listener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, genericListenersMutex);

        std::set<GenericDataListenerJNI *>::iterator iter;
        for (iter = genericListeners.begin(); iter != genericListeners.end(); ++iter) {
            GenericDataListenerJNI *listener = *iter;
            if (env->IsSameObject(jgenericListener, listener->getWrappedRef()))
                break;
        }
        
        if (iter == genericListeners.end())
            return false;
        
        listener = *iter;
        genericListeners.erase(iter);
    }
    commo->removeGenericDataListener(listener);
    GenericDataListenerJNI::destroy(env, listener);
    return true;
}

bool CommoJNI::addCoTSendFailureListener(JNIEnv *env, jobject jcotFailListener)
{
    CoTFailListenerJNI *newListener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotFailListenersMutex);

        std::set<CoTFailListenerJNI *>::iterator iter;
        for (iter = cotFailListeners.begin(); 
                         iter != cotFailListeners.end(); ++iter) {
            CoTFailListenerJNI *listener = *iter;
            if (env->IsSameObject(jcotFailListener, listener->getWrappedRef()))
                return false;
        }
        
        try {
            newListener = new CoTFailListenerJNI(env,
                                                 jcotFailListener);
        } catch (int &) {
            return false;
        }
        cotFailListeners.insert(newListener);
    }
    // then add it
    if (commo->addCoTSendFailureListener(newListener) != COMMO_SUCCESS) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotFailListenersMutex);
        cotFailListeners.erase(newListener);
        CoTFailListenerJNI::destroy(env, newListener);
        return false;
    }
    return true;
}

bool CommoJNI::removeCoTSendFailureListener(JNIEnv *env,
                                            jobject jcotFailListener)
{
    CoTFailListenerJNI *listener = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cotFailListenersMutex);

        std::set<CoTFailListenerJNI *>::iterator iter;
        for (iter = cotFailListeners.begin();
                             iter != cotFailListeners.end(); ++iter) {
            CoTFailListenerJNI *listener = *iter;
            if (env->IsSameObject(jcotFailListener, listener->getWrappedRef()))
                break;
        }
        
        if (iter == cotFailListeners.end())
            return false;
        
        listener = *iter;
        cotFailListeners.erase(iter);
    }
    commo->removeCoTSendFailureListener(listener);
    CoTFailListenerJNI::destroy(env, listener);
    return true;
}

jobject CommoJNI::wrapPhysicalNetInterface(JNIEnv *env, 
                                           PhysicalNetInterface *iface,
                                           jbyteArray jhwAddr)
{
    // Wrap in global java obj
    jobject ret = env->NewObject(jclass_physnetiface, jmethod_physnetifaceCtor,
                                 PTR_TO_JLONG(iface),
                                 jhwAddr);
    if (env->ExceptionOccurred())
        return NULL;
    
    ret = env->NewGlobalRef(ret);
    if (!ret)
        return NULL;
    
        
    // add to map of objs
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, netInterfaceMapMutex);
        netInterfaceMap.insert(NetInterfaceMap::value_type(iface, ret));
    }
    return ret;
}

jobject CommoJNI::wrapTcpNetInterface(JNIEnv *env, 
                                      TcpInboundNetInterface *iface,
                                      jint port)
{
    // Wrap in global java obj
    jobject ret = env->NewObject(jclass_tcpnetiface, jmethod_tcpnetifaceCtor,
                                 PTR_TO_JLONG(iface),
                                 port);
    if (env->ExceptionOccurred())
        return NULL;
    
    ret = env->NewGlobalRef(ret);
    if (!ret)
        return NULL;
    
        
    // add to map of objs
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, netInterfaceMapMutex);
        netInterfaceMap.insert(NetInterfaceMap::value_type(iface, ret));
    }
    return ret;
}

jobject CommoJNI::wrapStreamingNetInterface(JNIEnv *env,
                                            StreamingNetInterface *iface)
{
    // Wrap in global java obj
    jstring jstreamid = env->NewStringUTF(iface->remoteEndpointId);
    if (!jstreamid)
        return NULL;
    
    jobject ret = env->NewObject(jclass_streamnetiface, jmethod_streamnetifaceCtor,
                                 PTR_TO_JLONG(iface),
                                 jstreamid);
    if (env->ExceptionOccurred())
        return NULL;
    
    ret = env->NewGlobalRef(ret);
    if (!ret)
        return NULL;
    
        
    // add to map of objs
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, netInterfaceMapMutex);
        netInterfaceMap.insert(NetInterfaceMap::value_type(iface, ret));
    }
    return ret;

}

void CommoJNI::unwrapNetInterface(JNIEnv *env, NetInterface *iface)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    Lock_create(lock, netInterfaceMapMutex);
    NetInterfaceMap::iterator iter =
        netInterfaceMap.find(iface);
    if (iter == netInterfaceMap.end())
        return;
    env->DeleteGlobalRef(iter->second);
    netInterfaceMap.erase(iter);
}
        
jobject CommoJNI::wrapCloudClient(JNIEnv *env,
                                  CloudClient *client, CloudIOJNI *cloudiojni)
{
    // Wrap in global java obj
    jobject ret = env->NewObject(jclass_cloudclient, jmethod_cloudclientCtor,
                                 PTR_TO_JLONG(client),
                                 PTR_TO_JLONG(cloudiojni));
    if (env->ExceptionOccurred())
        return NULL;
    
    ret = env->NewGlobalRef(ret);
    if (!ret)
        return NULL;
    
        
    // add to map of objs
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        Lock_create(lock, cloudclientMapMutex);
        cloudclientMap.insert(CloudclientMap::value_type(client, 
                 std::pair<CloudIOJNI *, jglobalobjectref>(cloudiojni, ret)));
    }
    return ret;

}

void CommoJNI::unwrapCloudClient(JNIEnv *env, CloudClient *client)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    Lock_create(lock, cloudclientMapMutex);
    CloudclientMap::iterator iter =
        cloudclientMap.find(client);
    if (iter == cloudclientMap.end())
        return;
    env->DeleteGlobalRef(iter->second.second);
    cloudclientMap.erase(iter);
}
        
bool CommoJNI::reflectionInit(JNIEnv *env)
{
    jclass class_send;
    jobjectArray jsendVals;
    jmethodID method_sendValues;
    jmethodID method_sendName;
    jmethodID method_sendValue;
    jsize numVals;

    bool ret = false;

    LOOKUP_CLASS(jclass_physnetiface, COMMO_PACKAGE "PhysicalNetInterface", false);
    LOOKUP_CLASS(jclass_tcpnetiface, COMMO_PACKAGE "TcpInboundNetInterface", false);
    LOOKUP_CLASS(jclass_streamnetiface, COMMO_PACKAGE "StreamingNetInterface", false);
    LOOKUP_CLASS(jclass_cloudclient, COMMO_PACKAGE "CloudClient", false);
    LOOKUP_CLASS(jclass_commoexception, COMMO_PACKAGE "CommoException", false);
    LOOKUP_CLASS(jclass_mpnativeresult, COMMO_PACKAGE "Commo$MPNativeResult", false);
    LOOKUP_METHOD(jmethod_physnetifaceCtor, jclass_physnetiface, 
                  "<init>", "(J[B)V");
    LOOKUP_METHOD(jmethod_tcpnetifaceCtor, jclass_tcpnetiface, 
                  "<init>", "(JI)V");
    LOOKUP_METHOD(jmethod_streamnetifaceCtor, jclass_streamnetiface,
                  "<init>", "(JLjava/lang/String;)V");
    LOOKUP_METHOD(jmethod_cloudclientCtor, jclass_cloudclient,
                  "<init>", "(JJ)V");
    LOOKUP_METHOD(jmethod_mpnativeresultCtor, jclass_mpnativeresult,
                  "<init>", "(Ljava/lang/String;I[Ljava/lang/String;)V");

    
    // Check mapping of native send methods to java equivalent
    LOOKUP_CLASS(class_send, COMMO_PACKAGE "CoTSendMethod", true);
    LOOKUP_STATIC_METHOD(method_sendValues, class_send, "values", "()[L" COMMO_PACKAGE "CoTSendMethod;");
    jsendVals = (jobjectArray)env->CallStaticObjectMethod(class_send, method_sendValues);
    if (env->ExceptionOccurred())
        goto cleanup;
    
    numVals = env->GetArrayLength(jsendVals);
    
    LOOKUP_METHOD(method_sendName, class_send, "name", 
                  "()Ljava/lang/String;");
    LOOKUP_METHOD(method_sendValue, class_send, "getNativeVal", "()I");


    ret = true;
#define CHECK_SEND(i,send) checkEnum(env, method_sendName,                   \
                                      method_sendValue,                      \
                                      jsendVals, numVals,                    \
                                      #send,                                 \
                                      NULL,                                  \
                                      i,                                     \
                                      PASTE(SEND_, send))                    \

    ret = ret && numVals == 3;
    ret = ret && CHECK_SEND(0, TAK_SERVER);
    ret = ret && CHECK_SEND(1, POINT_TO_POINT);
    ret = ret && CHECK_SEND(2, ANY);
#undef CHECK_SEND

    ret = ret && CommoLoggerJNI::reflectionInit(env);
    ret = ret && MissionPackageIOJNI::reflectionInit(env);
    ret = ret && SimpleFileIOJNI::reflectionInit(env);
    ret = ret && CloudIOJNI::reflectionInit(env);
    ret = ret && InterfaceStatusListenerJNI::reflectionInit(env);
    ret = ret && ContactListenerJNI::reflectionInit(env);
    ret = ret && CoTListenerJNI::reflectionInit(env);
    ret = ret && GenericDataListenerJNI::reflectionInit(env);
    ret = ret && CoTFailListenerJNI::reflectionInit(env);
    ret = ret && JNIFileIOProvider::reflectionInit(env);

cleanup:
    return ret;
}

void CommoJNI::reflectionRelease(JNIEnv *env)
{
    CoTFailListenerJNI::reflectionRelease(env);
    GenericDataListenerJNI::reflectionRelease(env);
    CoTListenerJNI::reflectionRelease(env);
    ContactListenerJNI::reflectionRelease(env);
    InterfaceStatusListenerJNI::reflectionRelease(env);
    SimpleFileIOJNI::reflectionRelease(env);
    CloudIOJNI::reflectionRelease(env);
    MissionPackageIOJNI::reflectionRelease(env);
    CommoLoggerJNI::reflectionRelease(env);
    JNIFileIOProvider::reflectionRelease(env);
    env->DeleteGlobalRef(jclass_physnetiface);
    env->DeleteGlobalRef(jclass_tcpnetiface);
    env->DeleteGlobalRef(jclass_streamnetiface);
    env->DeleteGlobalRef(jclass_cloudclient);
    env->DeleteGlobalRef(jclass_commoexception);
    env->DeleteGlobalRef(jclass_mpnativeresult);
}

CommoJNI::~CommoJNI()
{
}



/**********************************************************************/
// Actual JNI methods

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_Commo_commoCreateNative
    (JNIEnv *env, jclass selfCls, jobject jlogger, jstring jourUID,
     jstring jourCallsign)
{
    jlong ret = 0;
    try {
        CommoJNI *c = new CommoJNI(env, jlogger, jourUID, jourCallsign);
        ret = PTR_TO_JLONG(c);
    } catch (int &) {
    }
    return ret;
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_commoDestroyNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    CommoJNI::destroy(env, c);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setupMissionPackageIONative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jobject jmissionPackageIO)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    bool ret = false;
    try {
        c->setupMPIO(env, jmissionPackageIO);
        ret = true;
    } catch (int &) {
    }
    return ret;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_enableSimpleFileIONative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jobject jSimpleFileIO)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    bool ret = false;
    try {
        c->enableFileIO(env, jSimpleFileIO);
        ret = true;
    } catch (int &) {
    }
    return ret;
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setCallsignNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jcallsign)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *callsign = env->GetStringUTFChars(jcallsign, NULL);
    if (callsign) {
        c->commo->setCallsign(callsign);
        env->ReleaseStringUTFChars(jcallsign, callsign);
    }
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setMagtabWorkaroundEnabledNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean jen)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setWorkaroundQuirks(jen == JNI_TRUE ? QUIRK_MAGTAB : 0);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setPreferStreamEndpointNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean jprefstream)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setPreferStreamEndpoint(jprefstream == JNI_TRUE);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setAdvertiseEndpointAsUdpNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean jen)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setAdvertiseEndpointAsUdp(jen == JNI_TRUE);
}


JNIEXPORT jboolean JNICALL Java_com_atakmap_commoncommo_Commo_setCryptoKeysNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jbyteArray jauth, 
                                                 jbyteArray jcrypt)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jbyte *auth = NULL;
    jbyte *crypt = NULL;
    
    if (jauth) {
        auth = env->GetByteArrayElements(jauth, NULL);
        if (!auth)
            return JNI_FALSE;

        crypt = env->GetByteArrayElements(jcrypt, NULL);
        if (!crypt) {
            env->ReleaseByteArrayElements(jauth, auth, JNI_ABORT);
            return JNI_FALSE;
        }
    }
    
    CommoResult r = c->commo->setCryptoKeys((const uint8_t *)auth, 
                                            (const uint8_t *)crypt);
    if (jauth) {
        env->ReleaseByteArrayElements(jauth, auth, JNI_ABORT);
        env->ReleaseByteArrayElements(jcrypt, crypt, JNI_ABORT);
    }
    return r == COMMO_SUCCESS ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT void JNICALL Java_com_atakmap_commoncommo_Commo_setEnableAddressReuseNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean en)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setEnableAddressReuse(en == JNI_TRUE);
}


JNIEXPORT void JNICALL Java_com_atakmap_commoncommo_Commo_setMulticastLoopbackEnabledNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean en)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setMulticastLoopbackEnabled(en == JNI_TRUE);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setTTLNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint ttl)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setTTL(ttl);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setUdpNoDataTimeoutNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint seconds)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setUdpNoDataTimeout(seconds);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setTcpConnTimeoutNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint sec)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setTcpConnTimeout(sec);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setStreamMonitorEnabledNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean en)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setStreamMonitorEnabled(en == JNI_TRUE);
}


JNIEXPORT jint JNICALL
Java_com_atakmap_commoncommo_Commo_getBroadcastProtoNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->commo->getBroadcastProto();
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMPLocalPortNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint jlocalPort)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageLocalPort(jlocalPort) == COMMO_SUCCESS);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setMPLocalHttpsParamsNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint jlocalPort,
     jbyteArray jcert, jint certLen, jstring jcertPass)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jbyte *cert = NULL;
    const char *certPass = NULL;
    
    if (certLen > 0) {
        cert = env->GetByteArrayElements(jcert, NULL);
        if (!cert)
            return;
    }
    if (jcertPass) {
        certPass = env->GetStringUTFChars(jcertPass, NULL);
        if (!certPass) {
            if (cert)
                env->ReleaseByteArrayElements(jcert, cert, JNI_ABORT);
            return;
        }
    }
    
    CommoResult r = c->commo->setMissionPackageLocalHttpsParams(jlocalPort,
            (uint8_t *)cert, certLen, certPass);
    
    if (cert)
        env->ReleaseByteArrayElements(jcert, cert, JNI_ABORT);
    if (certPass)
        env->ReleaseStringUTFChars(jcertPass, certPass);

    const char *err = NULL;

    switch (r) {
    case COMMO_SUCCESS:
        break;
    case COMMO_ILLEGAL_ARGUMENT:
        err = "MPIO not configured, https port taken, or missing certificate or password";
        break;
    case COMMO_INVALID_CERT:
        err = "Https certificate is invalid";
        break;
    case COMMO_INVALID_CERT_PASSWORD:
        err = "Https certificate password does not match certificate";
        break;
    default:
        err = "Unknown error";
        break;
    }
    if (err)
        env->ThrowNew(CommoJNI::jclass_commoexception, err);
}


JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_setMPViaServerEnabledNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jboolean en)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->commo->setMissionPackageViaServerEnabled(en == JNI_TRUE);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMissionPackageHttpPortNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint port)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageHttpPort(port) == COMMO_SUCCESS);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMissionPackageHttpsPortNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint port)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageHttpsPort(port) == COMMO_SUCCESS);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMissionPackageNumTriesNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint nTries)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageNumTries(nTries) == COMMO_SUCCESS);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMissionPackageConnTimeoutNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint seconds)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageConnTimeout(seconds) == COMMO_SUCCESS);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_setMissionPackageTransferTimeoutNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint seconds)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->setMissionPackageTransferTimeout(seconds) == COMMO_SUCCESS);
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_addBroadcastNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jbyteArray jhwAddr,
     jint hwAddrLen,
     jintArray jtypeIds, jint typesLen,
     jstring jmcastAddr, jint destPort)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jbyte *jbytes = env->GetByteArrayElements(jhwAddr, NULL);
    if (!jbytes)
        return 0;
    
    jint *jints = env->GetIntArrayElements(jtypeIds, NULL);
    if (!jints)
        return 0;
    
    const char *mcastAddr = env->GetStringUTFChars(jmcastAddr, NULL);
    if (!mcastAddr)
        return 0;
        
    HwAddress hwaddr((uint8_t *)jbytes, hwAddrLen);
    CoTMessageType *types = new CoTMessageType[typesLen];
    for (jint i = 0; i < typesLen; ++i) {
        types[i] = (CoTMessageType)jints[i];
    }
    
    PhysicalNetInterface *phys = 
        c->commo->addBroadcastInterface(&hwaddr,
                                        types, typesLen, mcastAddr,
                                        destPort);
    delete[] types;

    env->ReleaseByteArrayElements(jhwAddr, jbytes, JNI_ABORT);
    env->ReleaseIntArrayElements(jtypeIds, jints, JNI_ABORT);
    env->ReleaseStringUTFChars(jmcastAddr, mcastAddr);

    if (!phys)
        return NULL;

    jobject ret = c->wrapPhysicalNetInterface(env, phys, jhwAddr);
    if (!ret)
        // can't wrap it, so don't orphan it - take it back down
        c->commo->removeBroadcastInterface(phys);

    return ret;
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_addUniBroadcastNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jintArray jtypeIds, jint typesLen,
     jstring juniAddr, jint destPort)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jint *jints = env->GetIntArrayElements(jtypeIds, NULL);
    if (!jints)
        return 0;
    
    const char *uniAddr = env->GetStringUTFChars(juniAddr, NULL);
    if (!uniAddr)
        return 0;
        
    CoTMessageType *types = new CoTMessageType[typesLen];
    for (jint i = 0; i < typesLen; ++i) {
        types[i] = (CoTMessageType)jints[i];
    }
    
    PhysicalNetInterface *phys = 
        c->commo->addBroadcastInterface(NULL,
                                        types, typesLen, uniAddr,
                                        destPort);
    delete[] types;

    env->ReleaseIntArrayElements(jtypeIds, jints, JNI_ABORT);
    env->ReleaseStringUTFChars(juniAddr, uniAddr);

    if (!phys)
        return NULL;

    jobject ret = c->wrapPhysicalNetInterface(env, phys, NULL);
    if (!ret)
        // can't wrap it, so don't orphan it - take it back down
        c->commo->removeBroadcastInterface(phys);

    return ret;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeBroadcastNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong ifaceNativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    PhysicalNetInterface *iface = JLONG_TO_PTR(PhysicalNetInterface,
                                               ifaceNativePtr);
    CommoResult res = c->commo->removeBroadcastInterface(iface);
    if (res == COMMO_SUCCESS) {
        c->unwrapNetInterface(env, iface);
        return true;
    } else
        return false;
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_addInboundNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jbyteArray jhwAddr, 
     jint hwAddrLen, jint port, jobjectArray jmcastAddrs, jint mcastAddrsLen,
     jboolean jforGeneric)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jbyte *jbytes = env->GetByteArrayElements(jhwAddr, NULL);
    if (!jbytes)
        return 0;

    const char **mcastAddrs = new const char *[mcastAddrsLen];
    for (jint i = 0; i < mcastAddrsLen; ++i) {
        jstring jmcastAddr = (jstring)
                env->GetObjectArrayElement(jmcastAddrs, i);
        mcastAddrs[i] = env->GetStringUTFChars(jmcastAddr, NULL);
        if (!mcastAddrs[i])
            return 0;
    }

    HwAddress hwaddr((uint8_t *)jbytes, hwAddrLen);
    
    PhysicalNetInterface *phys = 
        c->commo->addInboundInterface(&hwaddr,
                                      port, mcastAddrs, mcastAddrsLen,
                                      jforGeneric == JNI_TRUE);

    for (jint i = 0; i < mcastAddrsLen; ++i) {
        jstring jmcastAddr = (jstring)
                env->GetObjectArrayElement(jmcastAddrs, i);
        env->ReleaseStringUTFChars(jmcastAddr, mcastAddrs[i]);
    }

    env->ReleaseByteArrayElements(jhwAddr, jbytes, JNI_ABORT);

    if (!phys)
        return NULL;

    jobject ret = c->wrapPhysicalNetInterface(env, phys, jhwAddr);
    if (!ret)
        // can't wrap it, so don't orphan it - take it back down
        c->commo->removeInboundInterface(phys);

    return ret;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeInboundNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong ifaceNativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    PhysicalNetInterface *iface = JLONG_TO_PTR(PhysicalNetInterface,
                                               ifaceNativePtr);
    CommoResult res = c->commo->removeInboundInterface(iface);
    if (res == COMMO_SUCCESS) {
        c->unwrapNetInterface(env, iface);
        return true;
    } else
        return false;
}


JNIEXPORT jobject JNICALL Java_com_atakmap_commoncommo_Commo_addTcpInboundNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint port)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    TcpInboundNetInterface *tcpIn = 
        c->commo->addTcpInboundInterface(port);

    if (!tcpIn)
        return NULL;

    jobject ret = c->wrapTcpNetInterface(env, tcpIn, port);
    if (!ret)
        // can't wrap it, so don't orphan it - take it back down
        c->commo->removeTcpInboundInterface(tcpIn);

    return ret;
}


JNIEXPORT jboolean JNICALL Java_com_atakmap_commoncommo_Commo_removeTcpInboundNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong ifaceNativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    TcpInboundNetInterface *iface = JLONG_TO_PTR(TcpInboundNetInterface,
                                                 ifaceNativePtr);
    CommoResult res = c->commo->removeTcpInboundInterface(iface);
    if (res == COMMO_SUCCESS) {
        c->unwrapNetInterface(env, iface);
        return true;
    } else
        return false;
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_addStreamingNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jhostname,
     jint port, jintArray jtypeIds, jint typesLen,
     jbyteArray jclientCert, jint clientCertLen,
     jbyteArray jcaCert, jint caCertLen,
     jstring jcertPassword,
     jstring jcaCertPassword,
     jstring juser, jstring jpassword)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    jbyte *clientCert = NULL;
    jbyte *caCert = NULL;
    const char *certPassword = NULL;
    const char *caCertPassword = NULL;
    const char *user = NULL;
    const char *password = NULL;
    
    CoTMessageType *types = new CoTMessageType[typesLen];
    
    const char *hostname = env->GetStringUTFChars(jhostname, NULL);
    if (!hostname)
        return 0;
    
    // All other params are optional!
    if (typesLen > 0) {
        jint *jtypes = env->GetIntArrayElements(jtypeIds, NULL);
        if (!jtypes)
            return 0;
    
        for (jint i = 0; i < typesLen; ++i)
            types[i] = (CoTMessageType)jtypes[i];
        env->ReleaseIntArrayElements(jtypeIds, jtypes, JNI_ABORT);
    }
    
    if (clientCertLen > 0) {
        clientCert = env->GetByteArrayElements(jclientCert, NULL);
        if (!clientCert)
            return 0;
    }
    if (caCertLen > 0) {
        caCert = env->GetByteArrayElements(jcaCert, NULL);
        if (!caCert)
            return 0;
    }

    if (jcertPassword) {
        certPassword = env->GetStringUTFChars(jcertPassword, NULL);
        if (!certPassword)
            return 0;
    }
    
    if (jcaCertPassword) {
        caCertPassword = env->GetStringUTFChars(jcaCertPassword, NULL);
        if (!caCertPassword)
            return 0;
    }
    
    if (juser) {
        user = env->GetStringUTFChars(juser, NULL);
        if (!user)
            return 0;
    }

    if (jpassword) {
        password = env->GetStringUTFChars(jpassword, NULL);
        if (!password)
            return 0;
    }
    
    CommoResult resultCode;
    StreamingNetInterface *stream = 
        c->commo->addStreamingInterface(hostname,
                                        port,
                                        types,
                                        typesLen,
                                        (uint8_t *)clientCert, clientCertLen,
                                        (uint8_t *)caCert, caCertLen,
                                        certPassword,
                                        caCertPassword,
                                        user,
                                        password,
                                        &resultCode);
    
    env->ReleaseStringUTFChars(jhostname, hostname);
    delete[] types;
    if (clientCert)
        env->ReleaseByteArrayElements(jclientCert, clientCert, JNI_ABORT);
    if (caCert)
        env->ReleaseByteArrayElements(jcaCert, caCert, JNI_ABORT);
    if (certPassword)
        env->ReleaseStringUTFChars(jcertPassword, certPassword);
    if (caCertPassword)
        env->ReleaseStringUTFChars(jcaCertPassword, caCertPassword);
    if (user)
        env->ReleaseStringUTFChars(juser, user);
    if (password)
        env->ReleaseStringUTFChars(jpassword, password);

    if (!stream) {
        const char *err;
        switch (resultCode) {
        case COMMO_ILLEGAL_ARGUMENT:
            err = "Missing certificate, ca certificate, certificate passwords, or illegal combination of username/password";
            break;
        case COMMO_INVALID_CERT:
            err = "Client certificate is invalid";
            break;
        case COMMO_INVALID_CACERT:
            err = "Truststore is invalid";
            break;
        case COMMO_INVALID_CERT_PASSWORD:
            err = "Certificate password does not match certificate";
            break;
        case COMMO_INVALID_CACERT_PASSWORD:
            err = "Truststore password does not match truststore";
            break;
        default:
            err = "Unknown error";
            break;
        }
        env->ThrowNew(CommoJNI::jclass_commoexception, err);
        return NULL;
    }

    jobject ret = c->wrapStreamingNetInterface(env, stream);
    if (!ret)
        // Above throws java-level exceptions on NULL, so no need to
        // throw our own
        // can't wrap it, so don't orphan it - take it back down
        c->commo->removeStreamingInterface(stream);

    return ret;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeStreamingNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong ifaceNativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    StreamingNetInterface *iface = JLONG_TO_PTR(StreamingNetInterface,
                                                ifaceNativePtr);
    CommoResult res = c->commo->removeStreamingInterface(iface);
    if (res == COMMO_SUCCESS) {
        c->unwrapNetInterface(env, iface);
        return true;
    } else
        return false;
}


JNIEXPORT jstring JNICALL
Java_com_atakmap_commoncommo_Commo_getStreamingInterfaceIdNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong ifaceNativePtr)
{
    StreamingNetInterface *iface = JLONG_TO_PTR(StreamingNetInterface,
                                                ifaceNativePtr);
    return env->NewStringUTF(iface->remoteEndpointId);
}



JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_Commo_registerFileIOProviderNative
(JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jfileioprovider)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->registerFileIOProvider(env, jfileioprovider);
}

JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_Commo_deregisterFileIOProviderNative
(JNIEnv *env, jclass selfCls, jlong nativePtr, jlong jfileioprovider)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    c->deregisterFileIOProvider(env, jfileioprovider);
}



JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_addInterfaceStatusListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jifaceListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->addIfaceStatusListener(env, jifaceListener);
}

JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeInterfaceStatusListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jifaceListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->removeIfaceStatusListener(env, jifaceListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_addContactListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcontactListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->addContactListener(env, jcontactListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeContactListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcontactListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->removeContactListener(env, jcontactListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_addCoTListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcotListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->addCoTListener(env, jcotListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeCoTListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcotListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->removeCoTListener(env, jcotListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_addGenericDataListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jgenericListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->addGenericListener(env, jgenericListener);
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_removeGenericDataListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jgenericListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->removeGenericListener(env, jgenericListener);
}


JNIEXPORT jboolean JNICALL Java_com_atakmap_commoncommo_Commo_addCoTSendFailureListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcotFailListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->addCoTSendFailureListener(env, jcotFailListener);
}


JNIEXPORT jboolean JNICALL Java_com_atakmap_commoncommo_Commo_removeCoTSendFailureListenerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jcotFailListener)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return c->removeCoTSendFailureListener(env, jcotFailListener);
}


JNIEXPORT jobjectArray JNICALL
Java_com_atakmap_commoncommo_Commo_sendCoTNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jobjectArray jcontactUIDs, jint ncontacts, jstring jcotMsg, jint method)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *cotMsg = env->GetStringUTFChars(jcotMsg, NULL);
    if (!cotMsg)
        return NULL;
    
    const ContactUID **contacts = new const ContactUID *[ncontacts];
    const char **contactsCopy = new const char *[ncontacts];
    memset(contactsCopy, 0, sizeof(const char *) * ncontacts);
    bool err = false;
    for (jint i = 0; i < ncontacts; ++i) {
        jstring jcontact = (jstring)env->GetObjectArrayElement(jcontactUIDs, i);
        const char *contactString = env->GetStringUTFChars(jcontact, NULL);
        if (!contactString) {
            err = true;
            break;
        }
        contactsCopy[i] = contactString;
        contacts[i] = new ContactUID(
                            (uint8_t *)contactString, strlen(contactString));
    }

    jobjectArray ret = NULL;
    if (!err) {
        ContactList list(ncontacts, contacts);
        CommoResult result = c->commo->sendCoT(&list, cotMsg,
                                               (CoTSendMethod)method);
        
        if (result == COMMO_SUCCESS) {
            ret = env->NewObjectArray(0, env->FindClass("java/lang/String"), NULL);
        } else if (result == COMMO_CONTACT_GONE) {
            ret = env->NewObjectArray(list.nContacts,
                            env->FindClass("java/lang/String"), NULL);
            if (!ret) {
                err = true;
            } else {
                for (size_t i = 0; i < list.nContacts; ++i) {
                    std::string str((const char *)list.contacts[i]->contactUID,
                                    list.contacts[i]->contactUIDLen);
                    jstring string = env->NewStringUTF(str.c_str());
                    if (!string) {
                        err = true;
                        break;
                    }
                    env->SetObjectArrayElement(ret, i, string);
                }
            }
        } else {
            err = true;
        }
    }
    
    for (jint i = 0; i < ncontacts; ++i) {
        if (!contactsCopy[i])
            break;
        jstring jcontact = (jstring)env->GetObjectArrayElement(jcontactUIDs, i);
        env->ReleaseStringUTFChars(jcontact, contactsCopy[i]);
    }
    env->ReleaseStringUTFChars(jcotMsg, cotMsg);
    
    if (err)
        return NULL;
    else
        return ret;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_sendCoTServerControlNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jstreamId,
     jstring jcotMsg)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *cotMsg = env->GetStringUTFChars(jcotMsg, NULL);
    if (!cotMsg)
        return JNI_FALSE;
    
    const char *streamId = NULL;
    if (jstreamId) {
        streamId = env->GetStringUTFChars(jstreamId, NULL);
        if (!streamId) {
            env->ReleaseStringUTFChars(jcotMsg, cotMsg);
            return JNI_FALSE;
        }
    }

    CommoResult r = c->commo->sendCoTServerControl(streamId, cotMsg);

    env->ReleaseStringUTFChars(jcotMsg, cotMsg);
    if (streamId)
        env->ReleaseStringUTFChars(jstreamId, streamId);
    return r == COMMO_SUCCESS;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_sendCoTToServerMissionDestNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jstring jstreamId,
     jstring jmission, jstring jcotMsg)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *cotMsg = env->GetStringUTFChars(jcotMsg, NULL);
    if (!cotMsg)
        return JNI_FALSE;

    const char *mission = env->GetStringUTFChars(jmission, NULL);
    if (!mission) {
        env->ReleaseStringUTFChars(jcotMsg, cotMsg);
        return JNI_FALSE;
    }
    
    const char *streamId = NULL;
    if (jstreamId) {
        streamId = env->GetStringUTFChars(jstreamId, NULL);
        if (!streamId) {
            env->ReleaseStringUTFChars(jcotMsg, cotMsg);
            env->ReleaseStringUTFChars(jmission, mission);
            return JNI_FALSE;
        }
    }
    
    CommoResult r = c->commo->sendCoTToServerMissionDest(streamId, mission, cotMsg);

    env->ReleaseStringUTFChars(jcotMsg, cotMsg);
    env->ReleaseStringUTFChars(jmission, mission);
    if (streamId)
        env->ReleaseStringUTFChars(jstreamId, streamId);
    return r == COMMO_SUCCESS;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_sendCoTTcpDirectNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
     jstring jhost, jint port, jstring jcotMsg)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *host = env->GetStringUTFChars(jhost, NULL);
    if (!host)
        return false;

    const char *cotMsg = env->GetStringUTFChars(jcotMsg, NULL);
    if (!cotMsg) {
        env->ReleaseStringUTFChars(jhost, host);
        return false;
    }
        
    CommoResult r = c->commo->sendCoTTcpDirect(host, port, cotMsg);
    env->ReleaseStringUTFChars(jcotMsg, cotMsg);
    env->ReleaseStringUTFChars(jhost, host);
    return r == COMMO_SUCCESS;
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_broadcastCoTNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jcotMsg, jint method)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *cotMsg = env->GetStringUTFChars(jcotMsg, NULL);
    if (!cotMsg)
        return false;
        
    CommoResult r = c->commo->broadcastCoT(cotMsg, (CoTSendMethod)method);
    env->ReleaseStringUTFChars(jcotMsg, cotMsg);
    return r == COMMO_SUCCESS;
}


JNIEXPORT jint JNICALL
Java_com_atakmap_commoncommo_Commo_simpleFileTransferInitNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, 
     jboolean jforUpload, jstring jremoteURI,
     jbyteArray jcacert, jint jcacertLen,
     jstring jcacertPassword,
     jstring juser,
     jstring jpassword,
     jstring jlocalFile)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    const char *localFile = env->GetStringUTFChars(jlocalFile, NULL);
    if (!localFile)
        return 0;

    const char *remoteURI = env->GetStringUTFChars(jremoteURI, NULL);
    if (!remoteURI)
        return 0;
    
    const jbyte *cacert = NULL;
    const char *cacertPassword = NULL;
    if (jcacert != NULL) {
        cacert = env->GetByteArrayElements(jcacert, NULL);
        if (!cacert)
            return 0;
        if (jcacertPassword) {
            cacertPassword = env->GetStringUTFChars(jcacertPassword, NULL);
            if (!cacertPassword)
                return 0;
        }
    }

    const char *user = NULL;    
    const char *password = NULL;    
    if (juser) {
        user = env->GetStringUTFChars(juser, NULL);
        if (!user)
            return 0;

        if (jpassword) {
            password = env->GetStringUTFChars(jpassword, NULL);
            if (!password)
                return 0;
        }
    }

    int xferId;
    CommoResult result = c->commo->simpleFileTransferInit(&xferId,
                                             jforUpload == JNI_TRUE,
                                             remoteURI,
                                             (const uint8_t *)cacert,
                                             jcacertLen,
                                             cacertPassword,
                                             user,
                                             password,
                                             localFile);

    const char *err;
    switch (result) {
    case COMMO_SUCCESS:
        err = NULL;
        break;
    case COMMO_ILLEGAL_ARGUMENT:
        err = "Unsupported protocol in URL";
        break;
    case COMMO_INVALID_CACERT:
        err = "Provide CA Certificate (truststore) is invalid";
        break;
    case COMMO_INVALID_CACERT_PASSWORD:
        err = "Invalid password for provided CA Certificate (truststore)";
        break;
    default:
        err = "An unknown error occurred";
        break;
    }
    
    if (!err)
        return xferId;
    else {
        env->ThrowNew(CommoJNI::jclass_commoexception, err);
        return 0;
    }
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_simpleFileTransferStartNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, 
     jint id)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    if (c->commo->simpleFileTransferStart(id) == COMMO_SUCCESS)
        return JNI_TRUE;
    else
        return JNI_FALSE;
}


JNIEXPORT jobject JNICALL Java_com_atakmap_commoncommo_Commo_createCloudClientNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jobject jio, 
   jint jproto, jstring jhost, jint jport, jstring jbasePath,
   jstring juser, jstring jpass, jbyteArray jcaCerts, jint jcaCertsLen,
   jstring jcaCertsPassword)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    CloudIOJNI *cloudiojni = NULL;
    const char *host = NULL;
    const char *basePath = NULL;
    const char *user = NULL;
    const char *pass = NULL;
    jbyte *caCerts = NULL;
    const char *caCertsPassword = NULL;
    CloudClient *newClient = NULL;
    
    try {
        cloudiojni = new CloudIOJNI(env, jio);
        host = env->GetStringUTFChars(jhost, NULL);
        if (!host) throw -1;

        basePath = env->GetStringUTFChars(jbasePath, NULL);
        if (!basePath) throw -1;

        if (juser) {
            user = env->GetStringUTFChars(juser, NULL);
            if (!user) throw -1;
        }
        if (jpass) {
            pass = env->GetStringUTFChars(jpass, NULL);
            if (!pass) throw -1;
        }
        if (jcaCertsPassword) {
            caCertsPassword = env->GetStringUTFChars(jcaCertsPassword, NULL);
            if (!caCertsPassword) throw -1;
        }
        if (jcaCertsLen > 0) {
            caCerts = env->GetByteArrayElements(jcaCerts, NULL);
            if (!caCerts) throw -1;
        }


        CommoResult r = c->commo->createCloudClient(&newClient, 
                         cloudiojni,
                         (CloudIOProtocol)jproto,
                         host,
                         jport,
                         basePath,
                         user,
                         pass,
                         (uint8_t *)caCerts,
                         jcaCertsLen,
                         caCertsPassword);

        const char *err = NULL;
        switch (r) {
        case COMMO_SUCCESS:
            break;
        case COMMO_ILLEGAL_ARGUMENT:
            err = "Invalid protocol or password w/out username";
            break;
        case COMMO_INVALID_CACERT:
            err = "Provided CA Certificate (truststore) is invalid";
            break;
        case COMMO_INVALID_CACERT_PASSWORD:
            err = "Invalid password for provided CA Certificate (truststore)";
            break;
        default:
            err = "An unknown error occurred";
            break;
        }
        if (err) {
            env->ThrowNew(CommoJNI::jclass_commoexception, err);
            throw -1;
        }
        
    } catch (int &) {
        newClient = NULL;
    }
    if (caCerts)
        env->ReleaseByteArrayElements(jcaCerts, caCerts, JNI_ABORT);
    if (caCertsPassword)
        env->ReleaseStringUTFChars(jcaCertsPassword, caCertsPassword);
    if (pass)
        env->ReleaseStringUTFChars(jpass, pass);
    if (user)
        env->ReleaseStringUTFChars(juser, user);
    if (basePath)
        env->ReleaseStringUTFChars(jbasePath, basePath);
    if (host)
        env->ReleaseStringUTFChars(jhost, host);
    if (!newClient && cloudiojni)
        CloudIOJNI::destroy(env, cloudiojni);

    if (!newClient)
        return NULL;

    jobject ret = c->wrapCloudClient(env, newClient, cloudiojni);
    if (!ret) {
        CloudIOJNI::destroy(env, cloudiojni);
    }
    return ret;
}


JNIEXPORT jboolean JNICALL Java_com_atakmap_commoncommo_Commo_destroyCloudClientNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jlong clientNativePtr,
                                                 jlong ioNativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    CloudClient *client = JLONG_TO_PTR(CloudClient, clientNativePtr);
    CloudIOJNI *io = JLONG_TO_PTR(CloudIOJNI, ioNativePtr);
    c->commo->destroyCloudClient(client);
    c->unwrapCloudClient(env, client);
    CloudIOJNI::destroy(env, io);
    
    return JNI_TRUE;
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_sendMPInitNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jobjectArray jcontactUIDs,
     jint ncontacts,
     jstring jfilePath, jstring jxferFileName, jstring jxferName)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *filePath = env->GetStringUTFChars(jfilePath, NULL);
    if (!filePath)
        return NULL;
    const char *xferFileName = env->GetStringUTFChars(jxferFileName, NULL);
    if (!xferFileName) {
        env->ReleaseStringUTFChars(jfilePath, filePath);
        return NULL;
    }
    const char *xferName = env->GetStringUTFChars(jxferName, NULL);
    if (!xferName) {
        env->ReleaseStringUTFChars(jfilePath, filePath);
        env->ReleaseStringUTFChars(jxferFileName, xferFileName);
        return NULL;
    }
    
    const ContactUID **contacts = new const ContactUID *[ncontacts];
    const char **contactsCopy = new const char *[ncontacts];
    memset(contactsCopy, 0, sizeof(const char *) * ncontacts);
    bool err = false;
    for (jint i = 0; i < ncontacts; ++i) {
        jstring jcontact = (jstring)env->GetObjectArrayElement(jcontactUIDs, i);
        const char *contactString = env->GetStringUTFChars(jcontact, NULL);
        if (!contactString) {
            err = true;
            break;
        }
        contactsCopy[i] = contactString;
        contacts[i] = new ContactUID(
                            (uint8_t *)contactString, strlen(contactString));
    }

    jobjectArray contactsRet = NULL;
    int xferId = 0;
    if (!err) {
        ContactList list(ncontacts, contacts);
        CommoResult result = c->commo->sendMissionPackageInit(&xferId,
                                             &list, filePath,
                                             xferFileName,
                                             xferName);
        
        if (result == COMMO_SUCCESS) {
            contactsRet = env->NewObjectArray(0, env->FindClass("java/lang/String"), NULL);
        } else if (result == COMMO_CONTACT_GONE) {
            contactsRet = env->NewObjectArray(list.nContacts,
                            env->FindClass("java/lang/String"), NULL);
            if (!contactsRet) {
                err = true;
            } else {
                for (size_t i = 0; i < list.nContacts; ++i) {
                    std::string str((const char *)list.contacts[i]->contactUID,
                                    list.contacts[i]->contactUIDLen);
                    jstring string = env->NewStringUTF(str.c_str());
                    if (!string) {
                        err = true;
                        break;
                    }
                    env->SetObjectArrayElement(contactsRet, i, string);
                }
            }
        } else {
            err = true;
        }
    }
    
    for (jint i = 0; i < ncontacts; ++i) {
        if (!contactsCopy[i])
            break;
        delete contacts[i];
        jstring jcontact = (jstring)env->GetObjectArrayElement(jcontactUIDs, i);
        env->ReleaseStringUTFChars(jcontact, contactsCopy[i]);
    }
    delete[] contactsCopy;
    delete[] contacts;
    env->ReleaseStringUTFChars(jfilePath, filePath);
    env->ReleaseStringUTFChars(jxferFileName, xferFileName);
    env->ReleaseStringUTFChars(jxferName, xferName);
    
    if (err) {
        return NULL;
    } else {
        return env->NewObject(CommoJNI::jclass_mpnativeresult,
                              CommoJNI::jmethod_mpnativeresultCtor,
                              NULL,
                              xferId,
                              contactsRet);
    }
}


JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_Commo_sendMPInitToServerNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jstreamId,
     jstring jfilePath, jstring jxferFileName)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *filePath = env->GetStringUTFChars(jfilePath, NULL);
    if (!filePath)
        return NULL;
    const char *xferFileName = env->GetStringUTFChars(jxferFileName, NULL);
    if (!xferFileName) {
        env->ReleaseStringUTFChars(jfilePath, filePath);
        return NULL;
    }
    const char *streamId = env->GetStringUTFChars(jstreamId, NULL);
    if (!streamId) {
        env->ReleaseStringUTFChars(jfilePath, filePath);
        env->ReleaseStringUTFChars(jxferFileName, xferFileName);
        return NULL;
    }
    
    bool err = false;

    int xferId = 0;
    std::string errMessage;
    CommoResult result = c->commo->sendMissionPackageInit(&xferId,
                                    streamId, filePath,
                                    xferFileName);

    if (result == COMMO_CONTACT_GONE) {
        errMessage = "Invalid server connection identifier";
        err = true;
    } else if (result != COMMO_SUCCESS) {
        err = true;
    }
    
    env->ReleaseStringUTFChars(jfilePath, filePath);
    env->ReleaseStringUTFChars(jxferFileName, xferFileName);
    env->ReleaseStringUTFChars(jstreamId, streamId);

    if (err) {
        if (errMessage.length() > 0) {
            jstring jmsg = env->NewStringUTF(errMessage.c_str());
            if (!jmsg)
                return NULL;
            else
                return env->NewObject(CommoJNI::jclass_mpnativeresult,
                                      CommoJNI::jmethod_mpnativeresultCtor,
                                      NULL,
                                      -1,
                                      NULL);
        }
        return NULL;
    } else {
        return env->NewObject(CommoJNI::jclass_mpnativeresult,
                              CommoJNI::jmethod_mpnativeresultCtor,
                              NULL,
                              xferId,
                              NULL);
    }
}


JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_startMPSendNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jint id)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    return (c->commo->sendMissionPackageStart(id) == COMMO_SUCCESS);
}


JNIEXPORT jobjectArray JNICALL
Java_com_atakmap_commoncommo_Commo_getContactsNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const ContactList *list = c->commo->getContactList();

    jobjectArray contactsRet = env->NewObjectArray(list->nContacts,
                                    env->FindClass("java/lang/String"), NULL);
    if (contactsRet) {

        for (size_t i = 0; i < list->nContacts; ++i) {
            std::string str((const char *)list->contacts[i]->contactUID,
                                          list->contacts[i]->contactUIDLen);
            jstring string = env->NewStringUTF(str.c_str());
            if (!string) {
                contactsRet = NULL;
                break;
            }
            env->SetObjectArrayElement(contactsRet, i, string);
        }
    
    }
    c->commo->freeContactList(list);

    return contactsRet;
}



JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_configKnownEndpointContactNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring juid,
     jstring jcallsign, jstring jipAddr, jint port)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    const char *callsign = NULL;
    const char *ipAddr = NULL;
    if (jcallsign && jipAddr) {
        callsign = env->GetStringUTFChars(jcallsign, NULL);
        if (!callsign)
            return JNI_FALSE;
        ipAddr = env->GetStringUTFChars(jipAddr, NULL);
        if (!ipAddr) {
            env->ReleaseStringUTFChars(jcallsign, callsign);
            return JNI_FALSE;
        }
    } else if (jcallsign || jipAddr) {
        // Specifying one or the other is not ok
        return JNI_FALSE;
    } // else both NULL which is ok to pass on
    
    const char *uid = env->GetStringUTFChars(juid, NULL);
    if (!uid) {
        if (jcallsign) {
            env->ReleaseStringUTFChars(jcallsign, callsign);
            env->ReleaseStringUTFChars(jipAddr, ipAddr);
        }
        return JNI_FALSE;
    }

    ContactUID uidobj((const uint8_t *)uid, env->GetStringUTFLength(juid));
    CommoResult r = c->commo->configKnownEndpointContact(&uidobj,
                                                         callsign,
                                                         ipAddr,
                                                         port);

    env->ReleaseStringUTFChars(juid, uid);
    if (jcallsign) {
        env->ReleaseStringUTFChars(jcallsign, callsign);
        env->ReleaseStringUTFChars(jipAddr, ipAddr);
    }
    
    return r == COMMO_SUCCESS ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jstring JNICALL Java_com_atakmap_commoncommo_Commo_generateKeyNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpw, jint keyLength)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    
    const char *pw = env->GetStringUTFChars(jpw, NULL);
    if (!pw)
        return NULL;

    char *result = c->commo->generateKeyCryptoString(pw, keyLength);

    env->ReleaseStringUTFChars(jpw, pw);

    jstring jresult = NULL;
    if (result) {
        jresult = env->NewStringUTF(result);
        c->commo->freeCryptoString(result);
    }
    return jresult;
}


JNIEXPORT jstring JNICALL Java_com_atakmap_commoncommo_Commo_generateCSRNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jobjectArray jkeys,
   jobjectArray jvalues, jint nEntries, jstring jpkeyPem, jstring jpw)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);

    const char *pkeyPem = env->GetStringUTFChars(jpkeyPem, NULL);
    if (!pkeyPem)
        return NULL;
    
    const char *pw = env->GetStringUTFChars(jpw, NULL);
    if (!pw) {
        env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
        return NULL;
    }
    
    const char **keys = new const char *[nEntries];
    const char **values = new const char *[nEntries];
    jstring *jkeyArr = new jstring[nEntries];
    jstring *jvalArr = new jstring[nEntries];
    jint i = 0;
    for (; i < nEntries; ++i) {
        jkeyArr[i] = (jstring)
                env->GetObjectArrayElement(jkeys, i);
        jvalArr[i] = (jstring)
                env->GetObjectArrayElement(jvalues, i);
        const char *key = env->GetStringUTFChars(jkeyArr[i], NULL);
        if (!key)
            break;
        const char *val = env->GetStringUTFChars(jvalArr[i], NULL);
        if (!val) {
            env->ReleaseStringUTFChars(jkeyArr[i], key);
            break;
        }
        keys[i] = key;
        values[i] = val;
    }
    if (i < nEntries) {
        for (jint j = 0; j < i; j++) {
            env->ReleaseStringUTFChars(jkeyArr[j], keys[j]);
            env->ReleaseStringUTFChars(jvalArr[j], values[j]);
        }
        delete[] keys;
        delete[] values;
        delete[] jkeyArr;
        delete[] jvalArr;
        env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
        env->ReleaseStringUTFChars(jpw, pw);
        return NULL;
    }


    char *result = c->commo->generateCSRCryptoString(keys, values, nEntries,
                                                    pkeyPem, pw);


    for (jint j = 0; j < nEntries; j++) {
        env->ReleaseStringUTFChars(jkeyArr[j], keys[j]);
        env->ReleaseStringUTFChars(jvalArr[j], values[j]);
    }
    delete[] keys;
    delete[] values;
    delete[] jkeyArr;
    delete[] jvalArr;
    env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
    env->ReleaseStringUTFChars(jpw, pw);

    jstring jresult = NULL;
    if (result) {
        jresult = env->NewStringUTF(result);
        c->commo->freeCryptoString(result);
    }
    return jresult;
}


JNIEXPORT jbyteArray JNICALL Java_com_atakmap_commoncommo_Commo_generateSelfSignedCertNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jpw)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);

    const char *pw = env->GetStringUTFChars(jpw, NULL);
    if (!pw)
        return NULL;
    
    uint8_t *cert = NULL;
    size_t n = c->commo->generateSelfSignedCert(&cert, pw);
    env->ReleaseStringUTFChars(jpw, pw);
    if (n == 0)
        return NULL;
    
    jbyteArray jresult = env->NewByteArray(n);
    if (!jresult) {
        c->commo->freeSelfSignedCert(cert);
        return NULL;
    }

    env->SetByteArrayRegion(jresult, 0, n, (const jbyte *)cert);    
    c->commo->freeSelfSignedCert(cert);
    
    return jresult;

}

JNIEXPORT jstring JNICALL Java_com_atakmap_commoncommo_Commo_generateKeystoreNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jcertPem,
   jobjectArray jcaPem, jint nCa, jstring jpkeyPem, jstring jpw,
   jstring jfriendlyName)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);

    const char *certPem = env->GetStringUTFChars(jcertPem, NULL);
    if (!certPem)
        return NULL;

    const char *pkeyPem = env->GetStringUTFChars(jpkeyPem, NULL);
    if (!pkeyPem) {
        env->ReleaseStringUTFChars(jcertPem, certPem);
        return NULL;
    }
    
    const char *pw = env->GetStringUTFChars(jpw, NULL);
    if (!pw) {
        env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
        env->ReleaseStringUTFChars(jcertPem, certPem);
        return NULL;
    }

    const char *fn = env->GetStringUTFChars(jfriendlyName, NULL);
    if (!pw) {
        env->ReleaseStringUTFChars(jpw, pw);
        env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
        env->ReleaseStringUTFChars(jcertPem, certPem);
        return NULL;
    }

    const char **caPem = new const char *[nCa];
    jstring *jcaStrings = new jstring[nCa];
    jint i = 0;
    for (; i < nCa; ++i) {
        jcaStrings[i] = (jstring)
                env->GetObjectArrayElement(jcaPem, i);
        const char *ca = env->GetStringUTFChars(jcaStrings[i], NULL);
        if (!ca)
            break;
        caPem[i] = ca;
    }
    if (i < nCa) {
        for (jint j = 0; j < i; j++) {
            env->ReleaseStringUTFChars(jcaStrings[j], caPem[j]);
        }
        delete[] jcaStrings;
        delete[] caPem;

        env->ReleaseStringUTFChars(jfriendlyName, fn);
        env->ReleaseStringUTFChars(jpw, pw);
        env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
        env->ReleaseStringUTFChars(jcertPem, certPem);

        return NULL;
    }

    char *result = c->commo->generateKeystoreCryptoString(
            certPem, caPem, nCa, pkeyPem, pw, fn);


    for (jint j = 0; j < nCa; j++)
        env->ReleaseStringUTFChars(jcaStrings[j], caPem[j]);
    delete[] jcaStrings;
    delete[] caPem;
    env->ReleaseStringUTFChars(jfriendlyName, fn);
    env->ReleaseStringUTFChars(jpw, pw);
    env->ReleaseStringUTFChars(jpkeyPem, pkeyPem);
    env->ReleaseStringUTFChars(jcertPem, certPem);

    jstring jresult = NULL;
    if (result) {
        jresult = env->NewStringUTF(result);
        c->commo->freeCryptoString(result);
    }
    return jresult;

}


JNIEXPORT jbyteArray JNICALL Java_com_atakmap_commoncommo_Commo_cotXmlToTakprotoNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jstring jcotxml,
   jint jversion)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);
    const char *cotxml = env->GetStringUTFChars(jcotxml, NULL);
    if (!cotxml) {
        env->ReleaseStringUTFChars(jcotxml, cotxml);
        return NULL;
    }
    
    char *protoData;
    size_t dataLen;
    CommoResult r = c->commo->cotXmlToTakproto(&protoData, &dataLen,
                        cotxml, jversion);

    env->ReleaseStringUTFChars(jcotxml, cotxml);

    if (r != COMMO_SUCCESS)
        return NULL;

    jbyteArray jresult = env->NewByteArray(dataLen);
    if (!jresult) {
        c->commo->takmessageFree(protoData);
        return NULL;
    }

    env->SetByteArrayRegion(jresult, 0, dataLen, (const jbyte *)protoData);    
    c->commo->takmessageFree(protoData);
    
    return jresult;
}


JNIEXPORT jstring JNICALL Java_com_atakmap_commoncommo_Commo_takprotoToCotXmlNative
  (JNIEnv *env, jclass selfCls, jlong nativePtr, jbyteArray jprotodata,
   jint jprotodataLength)
{
    CommoJNI *c = JLONG_TO_PTR(CommoJNI, nativePtr);

    jbyte *protodata = env->GetByteArrayElements(jprotodata, NULL);
    if (!protodata)
        return NULL;

    char *xml;
    CommoResult r = c->commo->takprotoToCotXml(&xml, (const char *)protodata,
                                               jprotodataLength);

    env->ReleaseByteArrayElements(jprotodata, protodata, JNI_ABORT);

    if (r != COMMO_SUCCESS)
        return NULL;
    
    jstring jresult = env->NewStringUTF(xml);
    c->commo->takmessageFree(xml);

    return jresult;
}



JNIEXPORT jboolean JNICALL
Java_com_atakmap_commoncommo_Commo_initNativeLibrariesNative
    (JNIEnv *env, jclass selfCls)
{
    xmlInitParser();
    OPENSSL_init_ssl(0, NULL);
    SSL_load_error_strings();
    OpenSSL_add_ssl_algorithms();
    curl_global_init(CURL_GLOBAL_NOTHING);
    return true;
}

