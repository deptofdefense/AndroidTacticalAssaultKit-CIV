#ifndef SIMPLEFILEIO_CLI_H_
#define SIMPLEFILEIO_CLI_H_


namespace TAK {
    namespace Commo {


        public enum class SimpleFileIOStatus {
            /**
             * The transfer is in progress
             */
            InProgress,
            /**
             * The transfer completed successfully
             */
            Success,
            /**
             */
            HostResolutionFail,
            /**
             */
            ConnectFail,
            /**
             * URL syntax error
             */
            UrlInvalid,
            /**
             */
            UrlUnsupported,
            /**
             * Remote file/directory did not exist
             */
            UrlNoResource,
            /**
             */
            LocalFileOpenFailure,
            /**
             * Fatal error reading/writing local file
             */
            LocalIOError,
            /**
             */
            SslUntrustedServer,
            /**
             */
            SslOtherError,
            /**
             * Login failed
             */
            AuthError,
            /**
             * Lack of permission on server after login
             *
             * NOTE: With FTP downloads, some servers will (oddly) return this error
             *       instead of URL_NO_RESOURCE. The additional error text
             *       will help to differentiate in most cases, but does not have
             *       consistent format from server to server.
             */
            AccessDenied,
            /**
             */
            TransferTimeout,
            /**
             */
            OtherError
        };
        
        
        /**
         * <summary>
         * A bundle of information updating status of an ongoing simple file transfer.
         * See the SimpleFileIO interface and Commo's SimpleFileTransferInit() function.
         * </summary>
         */
        public ref class SimpleFileIOUpdate
        {
        public:
            /**
             * <summary>
             * identifier that uniquely identifies the transfer - matches
             * the id returned from Commo's SimpleFileTransferInit() function.
             * </summary>
             */
            initonly int transferId;

            /**
             * <summary>
             * The current status of the transfer.
             * If the status is InProgress, future
             * updates will be forthcoming.  Any other status code indicates
             * (failed or successful) completion of the requested transfer.
             * </summary>
             */
            initonly SimpleFileIOStatus status;

            /**
             * <summary>
             * Additional information about the status, suitable for logging
             * or detailed error display to the user. This may be null if no
             * additional information is available.
             * </summary>
             */
            initonly System::String ^additionalInfo;

            /**
             * <summary>
             * Total number of bytes transferred so far.
             * </summary>
             */
            initonly System::Int64 totalBytesTransferred;

            /**
             * <summary>
             * Total number of bytes expected over the course of the entire
             * transfer. NOTE: May be zero for download transfers if the server
             * does not provide this information or if otherwise unknown at the time
             * of this status update!!
             * </summary>
             */
            initonly System::Int64 totalBytesToTransfer;
            



        internal:
            SimpleFileIOUpdate(int transferId, SimpleFileIOStatus status,
                               System::String ^info,
                               System::Int64 bytesTransferred,
                               System::Int64 expectedBytes);
            virtual ~SimpleFileIOUpdate();
        };


        /**
         * <summary>
         * Implemented by the application using the Commo API to receive status
         * and completion notifications of simple file transfers.
         * After implementing this interface, register it with a Commo implementation
         * and enable simple file transfers by calling Commo.EnableSimpleFileIO().
         * See Commo's EnableSimpleFileIO() and SimpleFileTransferInit() calls.
         * </summary>
         */
        public interface class ISimpleFileIO
        {
        public:
            /**
             * <summary>
             * Provides an update on an active simple file transfer that was
             * initiated via call to Commo's SimpleFileTransferInit() function.
             * The provided update includes the integer id to uniquely
             * identify the transfer.
             * </summary>
             *
             * <param name="update">
             * the update on the transfer
             * </param>
             */
            virtual void FileTransferUpdate(SimpleFileIOUpdate ^update);
        };

    }
}


#endif
