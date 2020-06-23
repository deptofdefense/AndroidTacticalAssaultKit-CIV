#include "cotmessageio.h"
#include "cotmessageio_cx.h"

namespace TAK {
    namespace Commo {
        namespace impl {
            class CoTMessageListenerImpl : public atakmap::commoncommo::CoTMessageListener
            {
            public:
                CoTMessageListenerImpl(TAK::Commo::ICoTMessageListener ^listener);
                virtual ~CoTMessageListenerImpl();

                virtual void cotMessageReceived(const char *cotMessage);

            private:
                TAK::Commo::ICoTMessageListener ^ _cotlistenerCx;
            };
        }
    }
}
