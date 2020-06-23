#ifndef NETINTERFACE_CLI_H_
#define NETINTERFACE_CLI_H_



namespace atakmap {
    namespace commoncommo {
        class PhysicalNetInterface;
        class StreamingNetInterface;
        class TcpInboundNetInterface;
    }
}

namespace TAK {
        namespace Commo {


        public enum class NetInterfaceErrorCode {
            ErrConnNameResFailed,
            ErrConnRefused,
            ErrConnTimeout,
            ErrConnHostUnreachable,
            ErrConnSSLNoPeerCert,
            ErrConnSSLPeerCertNotTrusted,
            ErrConnSSLHandshake,
            ErrConnOther,
            ErrIORxDataTimeout,
            ErrIO,
            ErrInternal,
            ErrOther
        };
        
            
        public ref class NetInterface
        {
        protected:
            NetInterface();
            virtual ~NetInterface();
        };



        public ref class PhysicalNetInterface : public NetInterface
        {
        public:
            initonly System::String ^ifaceName;

        internal:
            PhysicalNetInterface(atakmap::commoncommo::PhysicalNetInterface *impl, System::String ^ifaceName);
            virtual ~PhysicalNetInterface();

            atakmap::commoncommo::PhysicalNetInterface *impl;
        };

        public ref class TcpInboundNetInterface : public NetInterface
        {
        public:
            const int port;

        internal:
            TcpInboundNetInterface(atakmap::commoncommo::TcpInboundNetInterface *impl, const int port);
            virtual ~TcpInboundNetInterface();

            atakmap::commoncommo::TcpInboundNetInterface *impl;
        };


        public ref class StreamingNetInterface : public NetInterface
        {
        public:
            initonly System::String ^remoteEndpointId;

        internal:
            StreamingNetInterface(atakmap::commoncommo::StreamingNetInterface *impl, System::String ^endpoint);
            virtual ~StreamingNetInterface();

            atakmap::commoncommo::StreamingNetInterface *impl;
        };




        public interface class IInterfaceStatusListener
        {
        public:
            virtual void InterfaceUp(NetInterface ^iface);
            virtual void InterfaceDown(NetInterface ^iface);

            /**
            * <summary>
            * Invoked when a NetInterface makes an attempt to come online
            * but fails for any reason, or when an interface that is up is forced
            * UNEXPECTEDLY into a down state. The error code gives the reason
            * for the error.
            * A callback to this does not imply a permanent error; attempts
            * will continue to be made to bring up the interface unless it is
            * removed.
            * </summary>
            * <param name="iface">the NetInterface on which the error occurred</param>
            * <param name="errorCode">code indication of the error that occurred</param>
            */
            virtual void InterfaceError(NetInterface ^iface, NetInterfaceErrorCode errorCode);
        };
    }
}


#endif
