
package com.atakmap.comms;

public interface CommsFileTransferListener {
    /**
     *  
     * @param totalBytesTransferred total number of bytes transferred, 
     *                              or 0 if unknown/not started yet
     * @param streamSize expected total size of the transfer, or 0 if unknown
     */
    void bytesTransferred(long totalBytesTransferred, long streamSize);
}
