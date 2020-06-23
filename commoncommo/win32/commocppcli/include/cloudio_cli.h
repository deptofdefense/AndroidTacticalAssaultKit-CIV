#ifndef CLOUDIO_CLI_H_
#define CLOUDIO_CLI_H_

#include "simplefileio_cli.h"
#include "commoresult_cli.h"
#include "cloudio.h"


namespace TAK {
    namespace Commo {


        /**
        * <summary>
        * Enum representing the protocols by which
        * cloud interactions may be performed
        * </summary>
        */
        public enum class CloudIOProtocol
        {
            /** http, no SSL */
            Http,
            /** http with SSL */
            Https,
            /** ftp, no SSL */
            Ftp,
            /** ftp with SSL */
            Ftps
        };

        /**
        * <summary>
        * Enum representing the various cloud
        * operations that may be performed
        * </summary>
        */
        public enum class CloudIOOperation
        {
            /** Test server authorization, paths, and connectivity */
            TestServer,
            /** List contents of a collection */
            ListCollection,
            /** Get a file */
            Get,
            /** Upload/send a file */
            Put,
            /** Rename a collection or file */
            Move,
            /** Create a new, empty collection */
            MakeCollection
        };

        /**
        * <summary>
        * An entry in a cloud collection listing.
        * </summary>
        */
        public ref class CloudCollectionEntry
        {
        public:
            /**
            * <summary>
            * The type of entry
            * </summary>
            */
            enum class Type {
                /**
                * A remote file resource
                */
                File,
                /**
                * A remote collection (folder)
                */
                Collection
            };

            /**
            * <summary>
            * The full path to the listed entry, from the
            * root of the cloud resource.  It will be URL encoded if needed.
            * </summary>
            */
            initonly System::String ^path;
            /**
            * <summary>
            * The type of the listed entry
            * </summary>
            */
            initonly Type type;
            /**
            * <summary>
            * The size of the resource, in bytes, if known.
            * Only valid if > 0, negative if unknown
            * </summary>
            */
            initonly System::Int64 size;

        internal:
            CloudCollectionEntry(Type t, System::String ^path, System::Int64 size);
            ~CloudCollectionEntry();
        };


        /**
        * <summary>
        * A bundle of information updating status of an ongoing cloud IO operation.
        * See the CloudIO interface and CloudClient.
        *
        * The info in the superclass applies here-in as well, of course.
        * Particularly, the notion of progress updates and final status delivery
        * carries through here as well.  See in particular the documentation of super.status!
        *
        * This extension is to provide the type of transfer, as well as additional
        * information on some cloud operations, namely
        * getEntries() for ListCollection operations.
        *
        * Superclass's additionalInfo will be filled with the remote server version string
        * on a TestServer operation's successful completion (if known).
        *
        * For get operations that fail, it is possibly that the local file was
        * partially written and the client may wish to clean up the file.
        * </summary>
        */
        public ref class CloudIOUpdate : public SimpleFileIOUpdate
        {
        public:
            /**
            * <summary>
            * The type of operation that this is updating status on.
            * This is here for convenience; it will always match the
            * original type of transfer (identified by transferId).
            * </summary>
            */
            initonly CloudIOOperation operation;

            /**
            * <summary>
            * Entries of a collection listing.  The returned values
            * will only make sense for update events that indicate successful
            * completion status *and* are for a ListCollection operation.
            * Will be null or a nonsense value otherwise.
            * </summary>
            */
            initonly array<CloudCollectionEntry ^> ^entries;

        internal:
            CloudIOUpdate(CloudIOOperation op,
                int transferId, SimpleFileIOStatus status,
                System::String ^info,
                System::Int64 bytesTransferred,
                System::Int64 totalBytes,
                array<CloudCollectionEntry ^> ^entries);

            virtual ~CloudIOUpdate();

        };


        /**
        * <summary>
        * Implemented by the application using Commo's CloudClient API
        * to receive status and completion notifications of cloud operations.
        * After implementing this interface, register it when creating
        * a cloud client.  See CloudClient.
        * </summary>
        */
        public interface class ICloudIO
        {
        public:

            /**
            * <summary>
            * Provides an update on an cloud operation that was
            * initiated via call to a CloudClient's xyzInit() calls.
            * The provided update includes the integer id to uniquely
            * identify the operation as well as the completion status,
            * type of operation and various statistics.
            * NOTE: It is not safe to call back to the originating client
            * from the thread providing these updates.
            * </summary>
            */
            virtual void cloudOperationUpdate(CloudIOUpdate ^update);

        };


        /**
        * <summary>
        * Client for interacting with a cloud server.
        * Objects of this class must be created through an active instance of
        * the Commo class.  When no longer needed, the client **must** be
        * actively destroyed using the corresponding method in the Commo object
        * that created it. Not doing so will cause memory leaks on the native heap.
        *
        * Operations are created via the xxxInit() methods.
        * Operations are then held until the startOperation() method is invoked
        * using the provided operation identifier.  Operations occur
        * asynchronously, with progress being reported on the CloudIO callback
        * interface provided when the Client was created.  Any number of operations
        * may occur simultaneously.
        *
        * Remote paths provided to the various operations should be absolute from the
        * root of the cloud server path provided at client creation.  Paths may
        * include or omit a leading "/"; the client will adjust for either case as
        * needed.  Paths MUST be URL encoded!
        * </summary>
        */
        public ref class CloudClient
        {
        public:
            /**
            * <summary>
            * Create operation to test connectivity to the server connection info.
            * This tests info provided
            * at client creation (validity of certificates, username,
            * password for example) as well as network connectivity.
            * On a successful operation, the completion event will include
            * the cloud server's version information.
            * </summary>
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult testServerInit(int %cloudOpId);

            /**
            * <summary>
            * Create operation to list a remote collection at remotePath
            * </summary>
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <param name="remotePath">
            * the path to the remote collection
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult listCollectionInit(int %cloudOpId, System::String ^remotePath);

            /**
            * Create operation to download a remote file to a local file.  The
            * local file will be overwritten if it already exists, and must be a valid
            * path with write permissions.
            *
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <param name="localFile">
            * where to write the file as it is downloaded
            * </param>
            * <param name="remotePath">
            * the path to the remote file
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult getFileInit(int %cloudOpId, System::String ^localFile, System::String ^remotePath);

            /**
            * Create operation to upload a file to a remote cloud resource.  The
            * local file must be readable and the remote path must include the desired
            * remote file name.  The remote resource will be overwritten if it already
            * exists.
            *
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <param name="localFile">
            * the local file to upload
            * </param>
            * <param name="remotePath">
            * the path to the remote file
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult putFileInit(int %cloudOpId, System::String ^remotePath, System::String ^localFile);

            /**
            * Create operation to move/rename a remote cloud resource.
            *
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <param name="fromPath">
            * the path to the remote resource to rename
            * </param>
            * <param name="toPath">
            * the path to new remote location
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult moveResourceInit(int %cloudOpId, System::String ^fromPath, System::String ^toPath);

            /**
            * Create operation to create a new (empty) collection.
            *
            * <param name="cloudOpId">
            * set to the unique operation id of the created operation
            * on a Success return. Unchanged otherwise.
            * </param>
            * <param name="remotePath">
            * the path at which to create the new collection
            * </param>
            * <returns>
            * Success if the operation is created
            * </returns>
            */
            CommoResult createCollectionInit(int %cloudOpId, System::String ^remotePath);

            /**
            * Start an already created operation. The operation will be started
            * as soon as possible and progress may be reported at any point.
            *
            * <param name="cloudOpId">
            * operation identifier from an xxxInit() call
            * </param>
            * <returns>
            * Success if the operation id is valid, IllegalArgument otherwise
            * </returns>
            */
            CommoResult startOperation(int cloudOpId);

            /**
            * Cancel an already created operation. The operation can be one that
            * was previously started or simply just created and not yet started.
            * Once cancelled it may not be restarted and no further progress updates
            * will be passed back to the CloudIO callback interface for that operation.
            *
            * <param name="cloudOpId">
            * operation identifier from an xxxInit() call
            * </param>
            * <returns>
            * Success if the operation is cancelled or not running
            * </returns>
            */
            CommoResult cancelOperation(int cloudOpId);


        internal:
            atakmap::commoncommo::CloudClient *impl;

            CloudClient(atakmap::commoncommo::CloudClient *impl);
            ~CloudClient();
        };



    }
}


#endif
