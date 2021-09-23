#ifndef TAKENGINEJNI_INTEROP_UTIL_MANAGEDDATAOUTPUT2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_UTIL_MANAGEDDATAOUTPUT2_H_INCLUDED

#include <jni.h>

#include <util/DataOutput2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Util {
            class ManagedDataOutput2 : public TAK::Engine::Util::DataOutput2
            {
            public:
                ManagedDataOutput2() NOTHROWS;
                ~ManagedDataOutput2() NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr open(JNIEnv &env, jobject mimpl) NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr close() NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr writeByte(const uint8_t value) NOTHROWS override;
            private:
                jobject impl;
            };
        }
    }
}

#endif
