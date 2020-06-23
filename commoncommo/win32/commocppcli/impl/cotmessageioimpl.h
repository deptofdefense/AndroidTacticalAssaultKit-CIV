#include "cotmessageio.h"
#include "cotmessageio_cli.h"

#include <msclr/marshal.h>

#ifndef COTMESSAGEIOIMPL_H
#define COTMESSAGEIOIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {
            class CoTMessageListenerImpl : public atakmap::commoncommo::CoTMessageListener
            {
            public:
                CoTMessageListenerImpl(TAK::Commo::ICoTMessageListener ^listener);
                virtual ~CoTMessageListenerImpl();

                virtual void cotMessageReceived(const char *cotMessage, const char *rxEndpointId);

            private:
                gcroot<TAK::Commo::ICoTMessageListener ^> cotlistenerCLI;
            };

            class GenericDataListenerImpl : public atakmap::commoncommo::GenericDataListener
            {
            public:
                GenericDataListenerImpl(TAK::Commo::IGenericDataListener ^listener);
                virtual ~GenericDataListenerImpl();

                virtual void genericDataReceived(const uint8_t *cotMessage, size_t len, const char *rxEndpointId);

            private:
                gcroot<TAK::Commo::IGenericDataListener ^> genericListenerCLI;
            };

            class CoTSendFailureListenerImpl : public atakmap::commoncommo::CoTSendFailureListener
            {
            public:
                CoTSendFailureListenerImpl(TAK::Commo::ICoTSendFailureListener ^listener);
                virtual ~CoTSendFailureListenerImpl();

                virtual void sendCoTFailure(const char *host, int port, const char *errorReason);

            private:
                gcroot<TAK::Commo::ICoTSendFailureListener ^> cotlistenerCLI;
            };
        }
    }
}

#endif
