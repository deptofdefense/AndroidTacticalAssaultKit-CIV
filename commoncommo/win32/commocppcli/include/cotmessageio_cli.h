#ifndef COTMESSAGEIO_CLI_H_
#define COTMESSAGEIO_CLI_H_

namespace TAK {
        namespace Commo {

        public enum class CoTSendMethod {
            SendTAKServer,
            SendPointToPoint,
            SendAny
        };


        public value class CoTPointData {
        public:
            // Can be used for hae, ce, or le to indicate a lack of value
            literal double NO_VALUE = 9999999.0;

            CoTPointData(double lat, double lon, double hae,
                            double ce, double le) : lat(lat), lon(lon),
                                                    hae(hae), ce(ce), le(le)
            {
            };

            initonly double lat;
            initonly double lon;
            initonly double hae;
            initonly double ce;
            initonly double le;
        };


        public enum class CoTMessageType {
            SituationalAwareness,
            Chat,
        };


        public interface class ICoTMessageListener
        {
        public:
            // rxEndpointId is identifier of NetworkInterface upon which
            // the message was received, if known, or nullptr
            // if not known.
            virtual void CotMessageReceived(System::String ^cotMessage, System::String ^rxEndpointId);
        };

        public interface class IGenericDataListener
        {
        public:
            // rxEndpointId is identifier of NetworkInterface upon which
            // the message was received, if known, or nullptr
            // if not known.
            virtual void GenericDataReceived(array<System::Byte> ^data, System::String ^rxEndpointId);
        };

        public interface class ICoTSendFailureListener
        {
        public:
            virtual void SendCoTFailure(System::String ^host, int port, System::String ^errorReason);
        };
    }
}


#endif /* COTMESSAGE_H_ */
