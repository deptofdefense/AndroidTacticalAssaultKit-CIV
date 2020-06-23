package com.atakmap.commoncommo;

import java.io.File;

/**
 * Implemented by the application using Commo's CloudClient API
 * to receive status and completion notifications of cloud operations.
 * After implementing this interface, register it when creating
 * a cloud client.  See CloudClient.
 */
public interface CloudIO {

    /**
     * Provides an update on an cloud operation that was
     * initiated via call to a CloudClient's xyzInit() calls.
     * The provided update includes the integer id to uniquely
     * identify the operation as well as the completion status,
     * type of operation and various statistics.
     * NOTE: It is not safe to call back to the originating client
     * from the thread providing these updates.
     */
    public void cloudOperationUpdate(CloudIOUpdate update);

}
