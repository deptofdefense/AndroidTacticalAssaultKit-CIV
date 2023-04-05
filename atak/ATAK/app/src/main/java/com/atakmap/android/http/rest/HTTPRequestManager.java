
package com.atakmap.android.http.rest;

import android.content.Context;

import com.foxykeep.datadroid.requestmanager.RequestManager;

/**
 * This class is a singleton that specifies which {@link HTTPRequestService} to use. It also uses
 * DataDroid base class as a proxy to call the Service. It provides easy-to-use methods to call the
 * service and manages the Intent creation. It also assures that a request will not be sent again if
 * an exactly identical one is already in progress.
 *
 * Take care that the 'listener' is referenced until the callback is delivered to the listener. If
 * the listener is garbage collected, then the listener callback will not execute. For calls to:
 * void execute(Request request, RequestManager.RequestListener listener) {

 * 
 * 
 */
public final class HTTPRequestManager extends RequestManager {

    // Singleton management
    private static HTTPRequestManager sInstance;

    public static HTTPRequestManager from(Context context) {
        if (sInstance == null) {
            sInstance = new HTTPRequestManager(context);
        }

        return sInstance;
    }

    // Note Service used in super constructor, should match our RequestService subclass
    private HTTPRequestManager(Context context) {
        super(context, HTTPRequestService.class);
    }
}
