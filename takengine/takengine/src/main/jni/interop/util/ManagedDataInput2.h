#ifndef TAKENGINEJNI_INTEROP_UTIL_MANGEDDATAINPUT2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_UTIL_MANGEDDATAINPUT2_H_INCLUDED

#include <jni.h>

#include <util/DataInput2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Util {
            class ManagedDataInput2 : public TAK::Engine::Util::DataInput2
            {
            public:
                ManagedDataInput2() NOTHROWS;
                virtual ~ManagedDataInput2() NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr open(JNIEnv &env, jobject mobject) NOTHROWS;
            public:
                virtual TAK::Engine::Util::TAKErr close() NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS override;
                virtual TAK::Engine::Util::TAKErr readByte(uint8_t *value) NOTHROWS override;
                virtual int64_t length() const NOTHROWS override;
            private:
                jobject impl;
            };
        }
    }
}
#endif
