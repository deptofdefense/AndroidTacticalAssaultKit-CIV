
package com.atakmap.android.emergency.tool;

import java.util.Date;

public class EmergencyBeacon {

    private String uid;
    private EmergencyType type;
    private Date originalReceiptDate;
    private Date staleTime;
    private String callsign;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public EmergencyType getType() {
        return type;
    }

    public void setType(EmergencyType type) {
        this.type = type;
    }

    public Date getOriginalReceiptDate() {
        return originalReceiptDate;
    }

    public void setOriginalReceiptDate(Date originalReceiptDate) {
        this.originalReceiptDate = originalReceiptDate;
    }

    public Date getStaleTime() {
        return staleTime;
    }

    public void setStaleTime(Date staleTime) {
        this.staleTime = staleTime;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }
}
