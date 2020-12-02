
package com.atakmap.android.http.rest.operation;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.comms.http.TakHttpClient;

/**
 * 
 */
public abstract class HTTPOperation extends NetworkOperation {
    private static final String TAG = "HTTPOperation";

    /**
     * @deprecated Use {@link TakHttpClient} directly
     * @param url
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    protected TakHttpClient getHttpClient(String url) {
        return TakHttpClient.GetHttpClient(url);
    }
}
