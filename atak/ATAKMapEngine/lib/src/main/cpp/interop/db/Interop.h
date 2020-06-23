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
        }
    }
}

#endif //TAKENGINEJNI_INTEROP_DB_INTEROP_H_INCLUDED
