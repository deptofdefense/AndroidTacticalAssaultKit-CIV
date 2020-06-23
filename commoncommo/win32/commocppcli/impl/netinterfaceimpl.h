#include "netinterface.h"
#include "netinterface_cli.h"

#include <msclr/marshal.h>

#ifndef NETINTERFACEIMPL_H
#define NETINTERFACEIMPL_H

namespace TAK {
    namespace Commo {
        namespace impl {

            class InterfaceStatusListenerImpl : public atakmap::commoncommo::InterfaceStatusListener
            {
            public:
                typedef System::Collections::Concurrent::ConcurrentDictionary<System::IntPtr, NetInterface ^> InterfaceRegistry;

                InterfaceStatusListenerImpl(InterfaceRegistry ^registry, 
                                            TAK::Commo::IInterfaceStatusListener ^listener);
                virtual ~InterfaceStatusListenerImpl();

                virtual void interfaceUp(atakmap::commoncommo::NetInterface *iface);
                virtual void interfaceDown(atakmap::commoncommo::NetInterface *iface);
                virtual void interfaceError(atakmap::commoncommo::NetInterface *iface,
                                            atakmap::commoncommo::netinterfaceenums::NetInterfaceErrorCode errCode);

            private:
                gcroot<InterfaceRegistry ^> ifaceRegistry;
                gcroot<TAK::Commo::IInterfaceStatusListener ^> interfacelistenerCLI;
            };
        }
    }
}

#endif
