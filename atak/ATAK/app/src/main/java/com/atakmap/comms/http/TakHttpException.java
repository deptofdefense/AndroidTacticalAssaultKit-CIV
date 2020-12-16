
package com.atakmap.comms.http;

import java.io.IOException;

public class TakHttpException extends IOException {
    private final int statusCode;

    public TakHttpException(String detailMessage, int statusCode) {
        super(detailMessage);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
