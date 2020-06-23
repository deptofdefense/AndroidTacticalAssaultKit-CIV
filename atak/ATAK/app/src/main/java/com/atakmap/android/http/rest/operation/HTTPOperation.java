
package com.atakmap.android.http.rest.operation;

import com.atakmap.comms.http.TakHttpClient;

/**
 * 
 */
public abstract class HTTPOperation extends NetworkOperation {
    private static final String TAG = "HTTPOperation";

    @Deprecated
    protected TakHttpClient getHttpClient(String url) {
        return TakHttpClient.GetHttpClient(url);
    }
}
