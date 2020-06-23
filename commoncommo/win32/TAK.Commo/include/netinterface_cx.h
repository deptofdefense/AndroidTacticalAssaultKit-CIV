#pragma once

namespace atakmap {
    namespace commoncommo {
        class PhysicalNetInterface;
        class StreamingNetInterface;
    }
}

namespace TAK {
    namespace Commo {
    
        public interface class NetInterface
        {
        //public:
        //    virtual ~NetInterface();
        //internal:
        //    NetInterface();
        };
        
        public ref class PhysicalNetInterface sealed : public NetInterface
        {
        public:
            virtual ~PhysicalNetInterface();

            property Platform::Array<uint8>^ Addr { Platform::Array<uint8>^ get() { return _addr; }}
        internal:
            PhysicalNetInterface(atakmap::commoncommo::PhysicalNetInterface *impl, const Platform::Array<uint8> ^addr);

            atakmap::commoncommo::PhysicalNetInterface *impl;

        private:
            Platform::Array<uint8>^ _addr;
        };

        public ref class StreamingNetInterface sealed : public NetInterface
        {
        public:
            property Platform::String ^RemoteEndpointId { Platform::String^ get() { return _remoteEndpointId; }}
            virtual ~StreamingNetInterface();

        internal:
            StreamingNetInterface(atakmap::commoncommo::StreamingNetInterface *impl, Platform::String ^endpoint);

            atakmap::commoncommo::StreamingNetInterface *impl;

        private:
            Platform::String^ _remoteEndpointId;
        };
        
        public interface class IInterfaceStatusListener
        {
        public:
            virtual void interfaceUp(NetInterface ^iface);
            virtual void interfaceDown(NetInterface ^iface);
        };
    }
}
