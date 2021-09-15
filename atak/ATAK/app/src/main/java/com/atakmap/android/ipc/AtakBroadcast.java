
package com.atakmap.android.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.MetricsApi;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/** 
 * All use of Broadcasts within ATAK should utilize this class.   If this 
 * class is not used within ATAK or the associated plugins, undefined 
 * actions could occur.
 */
public final class AtakBroadcast {

    public static final String TAG = "AtakBroadcast";

    /**
     * Instead of using IntentFilters, ATAK makes use of
     * DocumentedIntentFilter to provide for automatic extraction of
     * documentation supplied by the developer.
     */
    static public class DocumentedIntentFilter extends IntentFilter {
        private final Map<String, DocumentedAction> doc = new HashMap<>();

        /**
         * Build an intent filter without any associated actions.
         */
        public DocumentedIntentFilter() {
            super();
        }

        /**
         * Build an intent filter with a single associated action.
         */
        public DocumentedIntentFilter(final String action) {
            super(action);
        }

        /**
         * Build an intent filter with a single associated action and
         * developer supplied documentation.
         * @param action the action the filter listens on.
         * @param description the documentation that describes the action
         * @param extras the description of the extras used.
         *
         */
        public DocumentedIntentFilter(final String action,
                final String description, DocumentedExtra[] extras) {
            super(action);
            doc.put(action, new DocumentedAction(action, description, extras));
        }

        /**
         * Build an intent filter with a single associated action and
         * developer supplied documentation.
         * @param action the action the filter listens on.
         * @param description the documentation that describes the action
         */
        public DocumentedIntentFilter(final String action,
                final String description) {
            this(action, description, null);
        }

        /**
         * Build an intent filter with a single associated action and
         * developer supplied documentation.
         * @param action the action the filter listens on.
         * @param description the documentation that describes the action
         * @param extras the domented extras that can be passed.
         */
        public DocumentedIntentFilter addAction(final String action,
                final String description, DocumentedExtra[] extras) {
            super.addAction(action);
            doc.put(action, new DocumentedAction(action, description, extras));
            return this;
        }

        /**
         * Add an action to an intent filter with appropriately
         * supplied documentation.
         */
        public DocumentedIntentFilter addAction(final String action,
                final String description) {
            return this.addAction(action, description, null);
        }

        /**
         * Extract the developer supplied documentation for a specific
         * action.   This is used during the intent documentation generation.
         */
        public DocumentedAction getDocumentation(final String action) {
            final DocumentedAction retval = doc.get(action);
            return retval;
        }

    }

    private final LocalBroadcastManager lbm;
    private final Context context;

    private static final Map<String, DocumentedIntentFilter> localFilters = new ConcurrentHashMap<>();
    private static final Map<String, DocumentedIntentFilter> systemFilters = new ConcurrentHashMap<>();

    private static AtakBroadcast _instance;

    private java.io.BufferedWriter bw = null;
    final private static boolean DOC_INTENTS = false;

    private AtakBroadcast(final Context context) {
        lbm = LocalBroadcastManager.getInstance(context);
        this.context = context;

        if (DOC_INTENTS) {
            try {
                bw = new java.io.BufferedWriter(IOProviderFactory.getFileWriter(
                        FileSystemUtils.getItem("intents.txt")));
            } catch (java.io.IOException ioe) {
                Log.e(TAG, "error occurred writing out the intents.");
                bw = null;
            }
        }
    }

    public static synchronized void init(Context context) {
        if (_instance == null)
            _instance = new AtakBroadcast(context);
        MetricsApi.init(_instance.lbm);
    }

    public static synchronized AtakBroadcast getInstance() {
        if (_instance == null) {
            final MapView mv = MapView.getMapView();
            if (mv == null)
                throw new IllegalStateException("application not running");

            final Context context = mv.getContext();
            if (context == null)
                throw new IllegalStateException("application not running");

            _instance = new AtakBroadcast(context);
        }
        return _instance;
    }

    public void dispose() {
        IoUtils.close(bw);
    }

    /**
     * Register a receive for any ATAK broadcasts that match the given DocumentedIntentFilter.
     *
     * @param receiver The BroadcastReceiver to handle the broadcast.
     * @param filter Selects the Intent broadcasts to be received.
     *
     * @see #unregisterReceiver
     */
    public void registerReceiver(BroadcastReceiver receiver,
            DocumentedIntentFilter filter) {

        // For the purposes of documentation generation.
        if (DOC_INTENTS) {
            printFormatted("localbroadcast", receiver, filter);
        }

        localFilters.put(receiver.getClass().getName(), filter);

        lbm.registerReceiver(receiver, filter);
    }

    /**
     * Register a system wide receiver.   Do not use unless you are specifically 
     * looking for external intents.   To unregister, use unregisterSystemReceiver.
     * Do not use for intents local to ATAK or any of the plugins.
     * 
     * Using this will flag your code in security reports.
     *
     * @param receiver The BroadcastReceiver to handle the broadcast.
     * @param filter Selects the Intent broadcasts to be received.
     *
     * @see #unregisterReceiver
     */
    public void registerSystemReceiver(BroadcastReceiver receiver,
            DocumentedIntentFilter filter) {
        // For the purposes of documentation generation.
        if (DOC_INTENTS) {
            printFormatted("systembroadcast", receiver, filter);
        }

        systemFilters.put(receiver.getClass().getName(), filter);

        context.registerReceiver(receiver, filter);
    }

    /**
     * Unregister a previously registered BroadcastReceiver.  <em>All</em>
     * filters that have been registered for this BroadcastReceiver will be
     * removed.
     *
     * @param receiver The BroadcastReceiver to unregister.
     *
     * @see #registerReceiver
     */
    public void unregisterReceiver(BroadcastReceiver receiver) {

        localFilters.remove(receiver.getClass().getName());

        lbm.unregisterReceiver(receiver);
    }

    /**
     * Unregister a previously registered BroadcastReceiver.  <em>All</em>
     * filters that have been registered for this BroadcastReceiver will be
     * removed.  Only use with receivers that were registered using 
     * registerSystemReceiver.   For all others use unregisterReceiver(BroadcastReceiver).
     *
     *
     * @param receiver The BroadcastReceiver to unregister.
     *
     * @see #registerSystemReceiver
     */
    public void unregisterSystemReceiver(BroadcastReceiver receiver) {

        systemFilters.remove(receiver.getClass().getName());

        context.unregisterReceiver(receiver);
    }

    /**
     * Broadcast the given intent to all interested BroadcastReceivers.  This
     * call is asynchronous; it returns immediately, and you will continue
     * executing while the receivers are run.
     *
     * @param intent The Intent to broadcast; all receivers matching this
     *     Intent will receive the broadcast.
     *
     * @see #registerReceiver
     */
    public boolean sendBroadcast(Intent intent) {
        if (MetricsApi.shouldRecordMetric()) {
            MetricsApi.record(intent);
        }
        return lbm.sendBroadcast(intent);
    }

    /**
     * Broadcast the given intent to all interested BroadcastReceivers (to include those outside of the 
     * system).    This call is asynchronous; it returns immediately, and you will continue
     * executing while the receivers are run.   Unless required for external communication, 
     * use sendBroadcast(android.content.Intent)
     *
     * @param intent The Intent to broadcast; all receivers matching this
     *     Intent will receive the broadcast.
     *
     * @see #registerSystemReceiver
     */
    public boolean sendSystemBroadcast(Intent intent) {
        if (MetricsApi.shouldRecordMetric()) {
            MetricsApi.record(intent);
        }
        context.sendBroadcast(intent);
        return true;
    }

    /**
     * Broadcast the given intent to all interested BroadcastReceivers (to include those outside of the 
     * system).    This call is asynchronous; it returns immediately, and you will continue
     * executing while the receivers are run.   Unless required for external communication, 
     * use sendBroadcast(android.content.Intent)
     *
     * @param intent The Intent to broadcast; all receivers matching this
     *     Intent will receive the broadcast.
     * @param permission the permission to be used when sending a system broadcast
     * @see #registerSystemReceiver
     */
    public boolean sendSystemBroadcast(final Intent intent,
            final String permission) {
        if (MetricsApi.shouldRecordMetric()) {
            MetricsApi.record(intent);
        }
        context.sendBroadcast(intent, permission);
        return true;
    }

    /**
     * Method for iterating over an intent list and sending out the intents.
     * Replaces IntentDispatchReceiver.
     * @param intents list of intents to send
     */
    public void sendIntents(List<Intent> intents) {
        if (intents != null) {
            for (int i = 0; i < intents.size(); i++) {
                AtakBroadcast.getInstance().sendBroadcast(intents.get(i));
            }
        }
    }

    /**
     * Method still in progress
     * @return returns all of the currently locally registered intent filters.
     */
    public List<DocumentedIntentFilter> getLocalDocumentedFilters() {
        return new ArrayList<>(localFilters.values());
    }

    /**
     * Method still in progress
     * @return returns all of the currently systemwide registered intent filters available.
     */
    public List<DocumentedIntentFilter> getSystemDocumentedFilters() {
        return new ArrayList<>(systemFilters.values());
    }

    private void printFormatted(String type, BroadcastReceiver receiver,
            DocumentedIntentFilter filter) {
        try {
            if (bw != null) {
                bw.write("/**\n");
                bw.write(" * Type: " + type + "\n");
                bw.write(" * Class: " + receiver.getClass() + "\n");
                for (int i = 0; i < filter.countActions(); ++i) {
                    final String action = filter.getAction(i);
                    bw.write(" * Action: " + action + "\n");
                    DocumentedAction da = filter.getDocumentation(action);
                    if (da == null)
                        bw.write(" * Description/Constraints: none supplied\n");
                    else {
                        bw.write(" * Description/Constraints: "
                                + da.description + "\n");
                        for (DocumentedExtra de : da.extras)
                            bw.write(" *           Extra: "
                                    + de.name + " " + de.description + "\n");
                    }
                    bw.write(" * \n");
                }
                bw.write(" **/\n");
                bw.flush();
            }
        } catch (java.io.IOException ignore) {
        }
    }
}
