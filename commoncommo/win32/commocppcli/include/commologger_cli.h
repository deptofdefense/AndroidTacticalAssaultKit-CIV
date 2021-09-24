#ifndef COMMOLOGGER_CLI_H_
#define COMMOLOGGER_CLI_H_

namespace TAK {
    namespace Commo {

        /**
        * Interface used to log messages from the Commo library.
        */
        public interface class ICommoLogger {
        public:
            enum class Level {
                LevelVerbose,
                LevelDebug,
                LevelWarning,
                LevelInfo,
                LevelError,
            };

            enum class Type {
                General,
                Parsing,
                Network,
            };

            ref class ParsingDetail {
            public:
                ParsingDetail(array<System::Byte>^ message_data, System::String^ error_detail, System::String^ remote_endpoint) {
                    message_data_ = message_data;
                    error_detail_ = error_detail;
                    remote_endpoint_id_ = remote_endpoint;
                }

                // Binary message data received. Never NULL, but may be 0 length.
                property array<System::Byte>^ MessageData { array<System::Byte>^ get() { return message_data_; } }
                // Human-readable detail of why the parse failed
                property System::String^ ErrorDetail { System::String^ get() { return error_detail_; }}
                // RemoteEndpointId is identifier of NetworkInterface upon which the message was received or null if unknown
                property System::String^ RemoteEndpointId { System::String^ get() { return remote_endpoint_id_; }}

            private:
                array<System::Byte>^ message_data_;
                System::String^ error_detail_;
                System::String^ remote_endpoint_id_;
            };

            ref class NetworkDetail {
            public:
                NetworkDetail(int port) {
                    port_ = port;
                }

                property int Port { int get() { return port_; } }

            private:
                int port_;
            };

            /**
                * log a message of the specified severity level.
                * This callback will be invoked from multiple different threads;
                * an implementation must be able to handle this properly.
                */
            virtual void Log(Level level, Type type, System::String^ message, Object^ data);
        };


    }
}


#endif /* COMMOLOGGER_H_ */
