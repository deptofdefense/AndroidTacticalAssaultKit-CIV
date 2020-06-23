
#ifndef simplefileio_objc_h
#define simplefileio_objc_h


typedef NS_ENUM(NSInteger, CommoSimpleFileIOStatus) {
    /**
     * The transfer is in progress
     */
    CommoSFIOInProgress,
    /**
     * The transfer completed successfully
     */
    CommoSFIOSuccess,
    /**
     */
    CommoSFIOHostResolutionFail,
    /**
     */
    CommoSFIOConnectFail,
    /**
     * URL syntax error
     */
    CommoSFIOUrlInvalid,
    /**
     */
    CommoSFIOUrlUnsupported,
    /**
     * Remote file/directory did not exist
     */
    CommoSFIOUrlNoResource,
    /**
     */
    CommoSFIOLocalFileOpenFailure,
    /**
     * Fatal error reading/writing local file
     */
    CommoSFIOLocalIOError,
    /**
     */
    CommoSFIOSslUntrustedServer,
    /**
     */
    CommoSFIOSslOtherError,
    /**
     * Login failed
     */
    CommoSFIOAuthError,
    /**
     * Lack of permission on server after login
     */
    CommoSFIOAccessDenied,
    /**
     */
    CommoSFIOTransferTimeout,
    /**
     */
    CommoSFIOOtherError
};


/**
 * @brief A bundle of information updating status of an ongoing simple file transfer.
 * See the CommoSimpleFileIO protocol and Commo's simpleFileTransferInit() function.
 */
@protocol CommoSimpleFileIOUpdate <NSObject>

@required

/**
 * @brief Identifier that uniquely identifies the transfer
 * @discussion matches the id returned from Commo's simpleFileTransferInit() function.
 */
@property (readonly) int transferId;

/**
 * @brief The current status of the transfer.
 * @discussion If the status is InProgress, future
 * updates will be forthcoming.  Any other status code indicates
 * (failed or successful) completion of the requested transfer.
 */
@property (readonly) CommoSimpleFileIOStatus status;

/**
 * @brief Additional information about the status
 * @discussion More detailed information suitable for logging
 * or detailed error display to the user. This may be nil if no
 * additional information is available.
 */
@property (readonly) NSString *additionalInfo;

/**
 * @brief Total number of bytes transferred so far.
 */
@property (readonly) int64_t totalBytesTransferred;

/**
 * @brief Total number of bytes expected over the course of the entire
 * transfer.
 * @discussion Note that this may be zero for download transfers if the server
 * does not provide this information or if otherwise unknown at the time
 * of this status update!!
 */
@property (readonly) int64_t totalBytesToTransfer;

@end




/**
 * @brief Protocol for applications to interact with simple file transfer system
 * @description Protocol to be implemented by an application using the Commo API
 * to do simple file transfers; it enables the application to receive status
 * and completion notifications of simple file transfers.
 * After implementing this interface, register it with a Commo implementation
 * and enable simple file transfers by calling Commo.enableSimpleFileIO().
 * See Commo's enableSimpleFileIO() and simpleFileTransferInit() calls.
 */
@protocol CommoSimpleFileIO <NSObject>

/**
 * @brief Provides an update on an active simple file transfer
 * @discussion Called to provide an update on an active simple
 * file transfer that was previously
 * initiated via call to Commo's simpleFileTransferInit() function.
 * The provided update includes the integer id to uniquely
 * identify the transfer.
 *
 * @param update the update on the transfer
 */
-(void) fileTransferUpdate:(id<CommoSimpleFileIOUpdate>) update;

@end


#endif

