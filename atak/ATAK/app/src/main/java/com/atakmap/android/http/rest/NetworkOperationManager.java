
package com.atakmap.android.http.rest;

import android.util.Pair;

import com.atakmap.android.http.rest.operation.GetClientListOperation;
import com.atakmap.android.http.rest.operation.GetCotEventOperation;
import com.atakmap.android.http.rest.operation.GetCotHistoryOperation;
import com.atakmap.android.http.rest.operation.GetServerVersionOperation;
import com.atakmap.android.http.rest.operation.SimpleHttpOperation;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.service.RequestService.Operation;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows components & plugins to leverage the <code>HTTPRequestService</code> to perform
 * asynchronous operations (including but not limited to HTTP, FTP, etc network operations).
 * Request types should be registered with this class. ATAK Core tools can use Parcelable to pass
 * data to/from its <code>Operation</code> instances. See Mission Package Tool for sample code.
 * Due to plugin class loading constraints, plugins should use something like JSON to to pass data
 * to/from its <code>Operation</code> instances. See Enterprise Sync Plugin for sample code. Both
 * tools and plugins can receive callbacks upon completion. Use <code>HTTPRequestManager</code> to
 * initiate operations.
 *
 */
public class NetworkOperationManager {
    private static final String TAG = "NetworkOperationManager";

    /**
     * This code provides a few simple Network Operation Types which may be used by other
     * tools which don't require special logic
     */
    public final static int REQUEST_TYPE_GET_FILE;
    public final static int REQUEST_TYPE_GET_FILES;
    public final static int REQUEST_TYPE_FTP_UPLOAD;
    public final static int REQUEST_TYPE_GET_CLIENT_LIST;
    public final static int REQUEST_TYPE_GET_SERVER_VERSION;
    public final static int REQUEST_TYPE_GET_COT_EVENT;
    public final static int REQUEST_TYPE_GET_COT_HISTORY;
    public final static int REQUEST_TYPE_GET_SERVER_GROUPS;

    static {
        REQUEST_TYPE_GET_FILE = NetworkOperationManager.register(
                "com.atakmap.android.http.rest.operation.GetFileOperation",
                new com.atakmap.android.http.rest.operation.GetFileOperation());

        REQUEST_TYPE_GET_FILES = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.GetFilesOperation",
                        new com.atakmap.android.http.rest.operation.GetFilesOperation());

        REQUEST_TYPE_FTP_UPLOAD = NetworkOperationManager
                .register(
                        "com.atakmap.android.ftp.operation.FtpStoreFileOperation",
                        new com.atakmap.android.ftp.operation.FtpStoreFileOperation());

        REQUEST_TYPE_GET_CLIENT_LIST = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.GetClientListOperation",
                        new GetClientListOperation());

        REQUEST_TYPE_GET_SERVER_VERSION = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.GetServerVersionOperation",
                        new GetServerVersionOperation());

        REQUEST_TYPE_GET_COT_EVENT = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.GetCotEventOperation",
                        new GetCotEventOperation());

        REQUEST_TYPE_GET_COT_HISTORY = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.GetCotHistoryOperation",
                        new GetCotHistoryOperation());

        REQUEST_TYPE_GET_SERVER_GROUPS = NetworkOperationManager
                .register(
                        "com.atakmap.android.http.rest.operation.SimpleHttpOperation",
                        new SimpleHttpOperation());
    }

    /**
     * Map a unique requestType/int to the classname and/or a cached operation instance
     */
    static private Map<Integer, Pair<String, Operation>> operations;

    /**
     * Register handler, return unique requestType.
     * Registering a handler instance may help when class names/paths are mangled by proguard
     * and may provide a slight performance bump for not recreating operations for each request
     * This uses an empty salt when generating the identifier which could end up duplicate identifiers
     * @param clazz the class to register
     * @param handler the operation handler
     * @return returns a unique rest type identifier as an integer
     */
    static synchronized public Integer register(String clazz,
            Operation handler) {
        return register(clazz, handler, null);
    }

    /**
     * Register handler, return unique requestType.
     * Optionally salt the clazz hash. e.g. this could be used to allow a single Operation implementation
     * to be used for multiple request types.   This is most evident when the same request handler is registered
     * across multiple plugins.
     *
     * @param clazz the class to register
     * @param handler the operation handler
     * @param salt the salt to be used to generate a unique id
     * @return returns a unique rest type identifier as an integer
     */
    static synchronized public Integer register(String clazz,
            Operation handler, String salt) {
        ClassLoader cl = handler.getClass().getClassLoader();

        //  Class. Implementations are free to return null for classes that were loaded by the
        //  bootstrap class loader. The Android reference implementation, though, always returns
        //  a reference to an actual class loader.
        if (cl == null)
            cl = ClassLoader.getSystemClassLoader();

        final String id = cl.hashCode() + ":"
                + clazz + ":" +
                (salt == null ? 0 : salt.hashCode());
        final Integer uid = id.hashCode();
        get().put(uid, new Pair<>(clazz, handler));
        //Log.d(TAG, "Registered: " + uid + (clazz == null ? "," : ", class=" + clazz) +
        //        (handler == null ? "" : ",handler=" + handler.getClass().getName()));
        return uid;
    }

    private synchronized static Map<Integer, Pair<String, Operation>> get() {
        if (operations == null) {
            operations = new HashMap<>();
        }

        return operations;
    }

    static synchronized private Pair<String, Operation> get(
            Integer requestType) {
        if (requestType == null) {
            Log.w(TAG, "Invalid uid");
            return null;
        }

        if (!get().containsKey(requestType)) {
            Log.w(TAG, "Unable to find uid: " + requestType);
            return null;
        }

        return get().get(requestType);
    }

    static synchronized public Operation getOperation(int requestType) {
        Pair<String, Operation> p = get(requestType);
        if (p == null) {
            Log.w(TAG, "Unable to find: " + requestType);
            return null;
        }

        return p.second;
    }

    public synchronized static String getClass(int requestType) {
        Pair<String, Operation> p = get(requestType);
        if (p == null) {
            Log.w(TAG, "Unable to find: " + requestType);
            return null;
        }

        return p.first;
    }
}
