//
// Created by GeoDev on 12/13/2019.
//

#ifndef TAKENGINEJNI_INTEROP_DB_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_DB_INTEROP_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace DB {
            TAK::Engine::Util::TAKErr RowIterator_moveToNext(JNIEnv *env, jobject jrowIterator) NOTHROWS;
            TAK::Engine::Util::TAKErr RowIterator_close(JNIEnv *env, jobject jrowIterator) NOTHROWS;
            TAK::Engine::Util::TAKErr RowIterator_isClosed(bool *value, JNIEnv *env, jobject jrowIterator) NOTHROWS;

            TAK::Engine::Util::TAKErr Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const int32_t value) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const int64_t value) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const double value) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const char *value) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_bindNull(JNIEnv &env, jobject mbindable, const std::size_t idx) NOTHROWS;
            TAK::Engine::Util::TAKErr Bindable_clearBindings(JNIEnv &env, jobject mbindable) NOTHROWS;
        }
    }
}

#endif //TAKENGINEJNI_INTEROP_DB_INTEROP_H_INCLUDED
