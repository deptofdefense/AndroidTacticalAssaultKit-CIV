
package com.atakmap.android.http.rest;

import android.content.Intent;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.service.RequestService;

import java.lang.reflect.Constructor;

/**
 * This class is called by the {@link HTTPRequestManager} through the {@link Intent} system. It
 * simply creates an {@link Operation} based on the type of {@link Request}, and then leverages
 * DataDriod to work the request. Currently allows 3 concurrent requests. Uses
 * <code>OperationManager</code> to create an operation of the correct type so <code>Operation</code>
 * implementations may reside in tools/plugins where they are used
 * 
 * 
 */
public final class HTTPRequestService extends RequestService {

    public static final String TAG = "HTTPRequestService";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service starting.");
        return super.onStartCommand(intent, flags, startId);

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    protected int getMaximumNumberOfThreads() {
        return 3;
    }

    @Override
    public Operation getOperationForType(int requestType) {
        //look for cached operation instance
        Operation op = NetworkOperationManager.getOperation(requestType);
        if (op != null) {
            Log.d(TAG, "Using cached operation with request type: "
                    + requestType);
            return op;
        } else {
            String className = NetworkOperationManager.getClass(requestType);
            if (FileSystemUtils.isEmpty(className)) {
                Log.w(TAG, "No class name cached for requestType: "
                        + requestType);
                return null;
            }

            try {
                Log.d(TAG, "Creating Operation of type: " + className);

                Class<?> clazz = Class.forName(className);
                Constructor<?> ctor = clazz.getConstructor();
                Object object = ctor.newInstance();
                if (object instanceof Operation)
                    return (Operation) object;
                else
                    Log.e(TAG, "Class in not an Operation: " + className);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Operation of of type: "
                        + className + ", requestType: " + requestType, e);
            }
        }

        return null;
    }
}
