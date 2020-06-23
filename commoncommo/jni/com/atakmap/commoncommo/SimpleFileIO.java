package com.atakmap.commoncommo;

import java.io.File;

/**
 * Implemented by the application using the Commo API to receive status
 * and completion notifications of simple file transfers.
 * After implementing this interface, register it with a Commo implementation
 * and enable simple file transfers by calling Commo.enableSimpleFileIO().
 * See Commo's enableSimpleFileIO() and simpleFileTransferInit() calls.
 */
public interface SimpleFileIO {

    /**
     * Provides an update on an active simple file transfer that was
     * initiated via call to Commo's simpleFileTransferInit() function.
     * The provided update includes the integer id to uniquely
     * identify the transfer.
     */
    public void fileTransferUpdate(SimpleFileIOUpdate update);

}
