#ifndef ATAK_MANAGEDSTATEMENT_H
#define ATAK_MANAGEDSTATEMENT_H

#include <jni.h>
#include <db/Database2.h>
#include <db/Statement2.h>
#include <util/Error.h>

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

class ManagedStatement : public TAK::Engine::DB::Statement2
{
public:
    ManagedStatement(JNIEnv &env, jobject instance) NOTHROWS;

    virtual ~ManagedStatement() NOTHROWS;
public :
    /**
     * Executes the statement.
     */
    virtual TAK::Engine::Util::TAKErr execute() NOTHROWS;

    /**
    * Binds the specified blob on the specified index.
    *
    * @param idx   The index (one based)
    * @param value The blob
    */
    virtual TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS;

    /**
    * Binds the specified <code>int</code> on the specified index.
    *
    * @param idx   The index (one based)
    * @param value The value
    */
    virtual TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS;

    /**
    * Binds the specified <code>long</code> on the specified index.
    *
    * @param idx   The index (one based)
    * @param value The value
    */
    virtual TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS;

    /**
    * Binds the specified <code>double</code> on the specified index.
    *
    * @param idx   The index (one based)
    * @param value The value
    */
    virtual TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS;

    /**
    * Binds the specified {@link String} on the specified index.
    *
    * @param idx   The index (one based)
    * @param value The value
    */
    virtual TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS;

    /**
    * Binds the specified <code>null</code> on the specified index.
    *
    * @param idx   The index (one based)
    */
    virtual TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS;

    /**
    * Clears all bindings.
    */
    virtual TAK::Engine::Util::TAKErr clearBindings() NOTHROWS;

private:
    /**
     * The instance of the Java JniFileIOProvider JNI object.
     */
    jobject m_instance;
};

#endif //ATAK_MANAGEDSTATEMENT_H
