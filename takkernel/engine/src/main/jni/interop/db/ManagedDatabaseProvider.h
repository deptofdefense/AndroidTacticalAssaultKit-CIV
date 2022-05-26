#ifndef ATAK_MANAGEDDATABASEPROVIDER_H
#define ATAK_MANAGEDDATABASEPROVIDER_H

#include <db/DatabaseProvider.h>
#include <db/Database2.h>
#include "util/Error.h"
#include "db/DatabaseInformation.h"

#include <jni.h>

using namespace TAK::Engine::DB;

class ManagedDatabaseProvider : public DatabaseProvider
{
public:
    ManagedDatabaseProvider(JNIEnv &env, jobject instance);
    virtual ~ManagedDatabaseProvider();

public :
    virtual TAK::Engine::Util::TAKErr create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS;
    virtual TAK::Engine::Util::TAKErr getType(const char** value) NOTHROWS;

private:
    /**
     * The instance of the Java JniFileIOProvider JNI object.
     */
    jobject m_instance;
    char *providerType;
};
#endif //ATAK_MANAGEDDATABASEPROVIDER_H
