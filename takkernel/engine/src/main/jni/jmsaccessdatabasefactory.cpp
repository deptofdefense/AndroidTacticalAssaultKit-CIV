#include "jmsaccessdatabasefactory.h"

#include <db/DatabaseFactory.h>
#include <db/DatabaseProvider.h>
#include <formats/msaccess/MsAccessDatabaseFactory.h>
#include <formats/pfps/FalconViewFeatureDataSource.h>
#include <util/Error.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"
#include "interop/db/ManagedDatabase2.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::MsAccess;
using namespace TAK::Engine::Formats::PFPS;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace {

    struct
    {
        jclass id;
        jmethodID createDatabase;
    } FalconViewFeatureDataSource_class;

    struct
    {
        jclass id;
        jmethodID ctor;
    } File_class;

    class FalconViewProviderFactoryProxy : public DatabaseProvider {
        public :
            FalconViewProviderFactoryProxy(JNIEnv &env) NOTHROWS;
        public : // DatabaseProvider
            virtual TAKErr create(DatabasePtr &result, const DatabaseInformation &information) NOTHROWS override;
            virtual TAKErr getType(const char **value) NOTHROWS override;
    };
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_falconview_FalconViewFeatureDataSource_parse(
        JNIEnv *env, jclass clazz, jstring jpath) {

    FalconViewFeatureDataSource dataSource;
    JNIStringUTF path(*env, jpath);
    FeatureDataSource2::ContentPtr content(nullptr, nullptr);
    TAKErr code = dataSource.parse(content, path);

    if(code == TE_InvalidArg)
        return NULL;
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(content));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_falconview_FalconViewFeatureDataSource_registerProvider(
        JNIEnv *env, jclass clazz) {

    //TAKErr code(TE_Ok);
    auto wrappedProvider = std::make_shared<FalconViewProviderFactoryProxy>(*env);
    MsAccessDatabaseFactory_registerProvider(wrappedProvider);
    //TE_CHECKRETURN_CODE(code);
}

namespace {
    FalconViewProviderFactoryProxy::FalconViewProviderFactoryProxy(JNIEnv &env) NOTHROWS
    {
        FalconViewFeatureDataSource_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/feature/falconview/FalconViewFeatureDataSource");
        FalconViewFeatureDataSource_class.createDatabase = env.GetStaticMethodID(FalconViewFeatureDataSource_class.id, "createDatabase", "(Ljava/io/File;)Lcom/atakmap/database/DatabaseIface;");

        File_class.id = ATAKMapEngineJNI_findClass(&env, "java/io/File");
        File_class.ctor = env.GetMethodID(File_class.id, "<init>", "(Ljava/lang/String;)V");
    }

    // DatabaseProvider
    TAKErr FalconViewProviderFactoryProxy::create(DatabasePtr& value, const DatabaseInformation& information) NOTHROWS
    {
        LocalJNIEnv env;
        TAKErr code(TE_Ok);
        const char *curi;
        int *options;
        code = information.getUri(&curi);
        TE_CHECKRETURN_CODE(code);
        code = information.getOptions(options);
        TE_CHECKRETURN_CODE(code);

        JNILocalRef mpath(*env, env->NewStringUTF(curi));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        JNILocalRef mdatabase(*env, env->CallStaticObjectMethod(FalconViewFeatureDataSource_class.id, FalconViewFeatureDataSource_class.createDatabase, mfile.get(), 0));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        if(!mdatabase)
            return TE_Err;
        value = DatabasePtr(new ManagedDatabase2(*env, mdatabase), Memory_deleter_const<Database2, ManagedDatabase2>);
        return TE_Ok;
    }

    TAKErr FalconViewProviderFactoryProxy::getType(const char** value) NOTHROWS
    {
        *value = "com.atakmap.map.layer.feature.falconview.FalconViewDatabaseFactory";
        return TE_Ok;
    }
}