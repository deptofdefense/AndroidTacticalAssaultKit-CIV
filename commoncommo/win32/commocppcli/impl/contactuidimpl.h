#include "contactuid.h"
#include "contactuid_cli.h"

#include <msclr/marshal.h>

#ifndef CONTACTUIDIMPL_H
#define CONTACTUIDIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {
            class ContactPresenceListenerImpl : public atakmap::commoncommo::ContactPresenceListener
            {
            public:
                ContactPresenceListenerImpl(TAK::Commo::IContactPresenceListener ^listener);
                virtual ~ContactPresenceListenerImpl();

                virtual void contactAdded(const atakmap::commoncommo::ContactUID *c);
                virtual void contactRemoved(const atakmap::commoncommo::ContactUID *c);

            private:
                gcroot<TAK::Commo::IContactPresenceListener ^> contactlistenerCLI;
            };
        }
    }
}

#endif
