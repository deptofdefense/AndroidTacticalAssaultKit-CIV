
package com.atakmap.comms.missionpackage;

public interface MPSendListener {

    enum UploadStatus {
        PENDING,
        IN_PROGRESS,
        FILE_ALREADY_ON_SERVER,
        FAILED,
        COMPLETE
    }

    /**
     * Called by the transfer engine with the list of valid 
     * recipients for which the MP send was initialized.
     * This is certain to have at least one element, and could be
     * as many as were in the original send request. Contact's
     * whose UIDs are not present here were not available to send
     * to and will receive no further update callbacks.
     * It is certain that this will be invoked exactly once prior
     * to the transfer starting and prior to calls to any other
     * progress function in this interface. This gives the client
     * a chance to set up progress tracking based on the actual
     * recipients prior to the transfers commencing.
     * When setting up the transfer, if all given uids were considered
     * invalid, then this will not be invoked; instead the call
     * setting up the send will fail. 
     * This is not invoked for direct transfers to TAK servers.
     * See CommsMapComponent.sendMissionPackage()
     *   
     * @param contactUids list of accepted uids for which the transfer
     *                    will be handling
     */
    void mpSendRecipients(String[] contactUids);

    /**
     * Called by the transfer engine when an acknowledgement
     * is received for an outstanding MP transfer signalling the remote
     * end has received the MP.  Note
     * that for a multi-recipient transfer, this may be invoked multiple times
     * (with contactUid indicating the relevant contact).
     * This is not invoked for direct sends to TAK servers.
     * 
     * @param contactUid uid of the contact which sent the ack
     * @param ackDetail detail info sent with the ack
     * @param byteCount number of bytes received, as reported in the ack
     */
    void mpAckReceived(
            String contactUid,
            String ackDetail,
            long byteCount);

    /**
     * Called by the transfer engine when an negative
     * acknowledgement is received for an outstanding MP transfer,
     * or another local error occurs that is ending the send of the MP
     * to the identified recipient. Note
     * that for a multi-recipient transfer, this may be invoked multiple times
     * (with contactUid indicating the relevant contact).
     * If a send to a TAK server, the contactUid will be null
     * and the nackDetail will be null.
     * 
     * @param contactUid uid of the contact for whom the transfer has failed
     * @param nackDetail any detail supplied with the nack message from the
     *                   remote receiver
     * @param byteCount number of bytes received, as reported in the nack
     */
    void mpSendFailed(
            String contactUid,
            String nackDetail,
            long byteCount);

    /**
     * Called by the transfer engine when an MP send successfully notifies
     * the specified contact the transfer is available for them to download.
     * The transfer is suspended until the recipient sends us an ack message.
     * This is not invoked for direct sends to TAK servers.
     *
     * @param contactUid uid of the destination contact that we have asked to
     *                   fetch our MP and are waiting to hear results from
     */
    void mpSendInProgress(String contactUid);

    /**
     * Called by the transfer engine when an MP send requires transit via a
     * TAK server. If a TAK server must be involved with a transfer,
     * this is invoked one or more times with the various status codes. Note
     * that for a multi-recipient transfer, this may be invoked multiple times
     * (with contactUid indicating the relevant contact).
     * 
     * If COMPLETE or ALREADY_ON_SERVER status, detail contains
     * URL to MP on server after upload.
     * If a FAILED status is reported, detail may contain additional
     * information on the failure.
     * It will be null if no detail is available
     * or for other statuses.
     * 
     * If a send to a TAK server, the contactUid will be null. 
     * 
     * 
     * @param contactUid uid of the contact for whom the TAK server upload is
     *                   necessary. null if this is for a direct send
     *                   to a TAK server
     * @param status the status of the upload
     * @param detail if FAILED, may contain additional info on failure. 
     *               If COMPLETE or ALREADY_ON_SERVER will contain URL
     *               MP on server after upload
     * @param byteCount the number of bytes uploaded thus far, if known, else 0
     */
    void mpUploadProgress(
            String contactUid,
            UploadStatus status,
            String detail,
            long byteCount);

}
