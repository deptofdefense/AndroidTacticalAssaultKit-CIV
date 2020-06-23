#pragma once

#include "contactuid.h"
#include "contactuid_cx.h"

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
            TAK::Commo::IContactPresenceListener ^ _contactlistenerCx;
        };
        }
    }
}
