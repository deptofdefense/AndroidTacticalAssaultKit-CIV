package com.atakmap.commoncommo;

/**
 * A CommoException derivative that indicates an exceptional status
 * during a mission package transfer.
 */
public class MissionPackageTransferException extends CommoException {
    public final MissionPackageTransferStatus status;

    public MissionPackageTransferException(MissionPackageTransferStatus status) {
        this.status = status;
    }
    
}
