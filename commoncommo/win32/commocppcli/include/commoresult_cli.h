#ifndef COMMORESULT_CLI_H_
#define COMMORESULT_CLI_H_

namespace TAK {
    namespace Commo {
        public enum class CommoResult {
            CommoSuccess,
            CommoIllegalArgument,
            CommoContactGone,
            CommoInvalidCert,
            CommoInvalidTruststore,
            CommoInvalidCertPassword,
            CommoInvalidTruststorePassword,
        };
    }
}


#endif
