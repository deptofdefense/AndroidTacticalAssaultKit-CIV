#pragma once

#include "netinterface.h"
#include "netinterface_cx.h"

#include <collection.h>

namespace TAK {
    namespace Commo {
        namespace impl {

            class InterfaceStatusListenerImpl : public atakmap::commoncommo::InterfaceStatusListener
            {
            public:
                //typedef Platform::Collections::Map<Platform::IntPtr, NetInterface ^> InterfaceRegistry;
                typedef std::map < atakmap::commoncommo::NetInterface*, Platform::Agile<NetInterface>> InterfaceRegistry;

                InterfaceStatusListenerImpl(InterfaceRegistry* registry,
                    TAK::Commo::IInterfaceStatusListener ^listener);
                virtual ~InterfaceStatusListenerImpl();

                virtual void interfaceUp(atakmap::commoncommo::NetInterface *iface);
                virtual void interfaceDown(atakmap::commoncommo::NetInterface *iface);

            private:
                InterfaceRegistry* ifaceRegistry;
                TAK::Commo::IInterfaceStatusListener ^ interfacelistenerCx;
            };
        }
    }
}
