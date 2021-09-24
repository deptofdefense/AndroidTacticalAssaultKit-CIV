package com.atakmap.commoncommo;

import java.io.File;

/**
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
 */
public class CloudClient {

    // NOTE: native initialization not required as `CloudClient` may only be
    // externally instantiated through `Commo`

    private long nativePtr;
    private long nativeIOPtr;
    
    CloudClient(long nativePtr, long nativeIOPtr)
    {
        this.nativePtr = nativePtr;
        this.nativeIOPtr = nativeIOPtr;
    }
    
    long getNativePtr() {
        return nativePtr;
    }

    long getNativeIOPtr() {
        return nativeIOPtr;
    }

    void nativeClear()
    {
        nativePtr = 0;
        nativeIOPtr = 0;
    }
    
    private void checkNull(String name, Object o)
    {
        if (o == null)
            throw new IllegalArgumentException(name + " cannot be null");
    }
    
    private void checkPtr() throws CommoException
    {
        if (nativePtr == 0)
            throw new CommoException("Object invalidated");
    }

    /**
     * Create operation to test connectivity to the server connection info. 
     * This tests info provided
     * at client creation (validity of certificates, username,
     * password for example) as well as network connectivity.
     * On a successful operation, the completion event will include
     * the cloud server's version information.
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int testServerInit() throws CommoException
    {
        checkPtr();
        return testServerInitNative(nativePtr);
    }

    /**
     * Create operation to list a remote collection at remotePath  
     * 
     * @param remotePath the path to the remote collection
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int listCollectionInit(String remotePath) throws CommoException
    {
        checkPtr();
        checkNull("remotePath", remotePath);
        return listCollectionInitNative(nativePtr, remotePath);
    }
    
    /**
     * Create operation to download a remote file to a local file.  The
     * local file will be overwritten if it already exists, and must be a valid
     * path with write permissions.  
     * 
     * @localFile where to write the file as it is downloaded
     * @param remotePath the path to the remote file
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int getFileInit(File localFile, String remotePath) throws CommoException
    {
        checkPtr();
        checkNull("remotePath", remotePath);
        return getFileInitNative(nativePtr, localFile.getAbsolutePath(),
                remotePath);
    }
    
    /**
     * Create operation to upload a file to a remote cloud resource.  The
     * local file must be readable and the remote path must include the desired
     * remote file name.  The remote resource will be overwritten if it already
     * exists.
     * 
     * @param remotePath the path to the remote file
     * @localFile the file to be uploaded
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int putFileInit(String remotePath, File localFile) throws CommoException
    {
        checkPtr();
        checkNull("remotePath", remotePath);
        return putFileInitNative(nativePtr, remotePath,
                localFile.getAbsolutePath());
    }
    
    /**
     * Create operation to move/rename a remote cloud resource.
     * 
     * @param fromPath the path to the remote resource to rename
     * @param toPath the path to new remote location
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int moveResourceInit(String fromPath, String toPath) throws CommoException
    {
        checkPtr();
        checkNull("fromPath", fromPath);
        checkNull("toPath", toPath);
        return moveResourceInitNative(nativePtr, fromPath,
                toPath);
    }
    
    /**
     * Create operation to delete a remote cloud resource.
     * 
     * @param remotePath the path to the remote location to delete
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int deleteResourceInit(String remotePath) throws CommoException
    {
        checkPtr();
        checkNull("remotePath", remotePath);
        return deleteResourceInitNative(nativePtr, remotePath);
    }
    
    /**
     * Create operation to create a new (empty) collection.
     * 
     * @param remotePath the path at which to create the new collection
     * @return the unique operation id of the created operation
     * @throws CommoException if the operation cannot be created
     */
    public int createCollectionInit(String remotePath) throws CommoException
    {
        checkPtr();
        checkNull("remotePath", remotePath);
        return createCollectionInitNative(nativePtr, remotePath);
    }
    
    /**
     * Start an already created operation. The operation will be started
     * as soon as possible and progress may be reported at any point.
     * 
     * @param cloudOpId operation identifier from an xxxInit() call.
     * @throws CommoException if the operation id is invalid
     */
    public void startOperation(int cloudOpId) throws CommoException
    {
        checkPtr();
        startOperationNative(nativePtr, cloudOpId);
    }

    /**
     * Cancel an already created operation. The operation can be one that
     * was previously started or simply just created and not yet started.
     * Once cancelled it may not be restarted and no further progress updates
     * will be passed back to the CloudIO callback interface for that operation.
     * 
     * @param cloudOpId operation identifier from an xxxInit() call to cancel.
     * @throws CommoException if an error occurs
     */
    public void cancelOperation(int cloudOpId) throws CommoException
    {
        checkPtr();
        cancelOperationNative(nativePtr, cloudOpId);
    }
    
    private static native int testServerInitNative(long nativePtr) 
            throws CommoException;
    private static native int listCollectionInitNative(long nativePtr,
            String path) throws CommoException;
    private static native int getFileInitNative(long nativePtr, String file,
            String path) throws CommoException;
    private static native int putFileInitNative(long nativePtr, String path, 
            String file) throws CommoException;
    private static native int moveResourceInitNative(long nativePtr, 
            String fromPath, String toPath) throws CommoException;
    private static native int deleteResourceInitNative(long nativePtr, 
            String remotePath) throws CommoException;
    private static native int createCollectionInitNative(long nativePtr, 
            String path) throws CommoException;
    private static native void startOperationNative(long nativePtr, 
            int operationId) throws CommoException;
    private static native void cancelOperationNative(long nativePtr, 
            int operationId) throws CommoException;
    
}
