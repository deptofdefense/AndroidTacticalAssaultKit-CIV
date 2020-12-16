
package com.atakmap.android.metrics;

import android.content.Intent;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// used just for registering local broadcast reciever
import android.content.BroadcastReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import java.util.List;
import java.util.ArrayList;

/**
 * Simple interface for tools and plugins to log metrics records.  Implementation for metrics
 * collection and handling should be done as a plugin so that it not adversely effect system
 * performance.
 */
public class MetricsApi {

    // note - this uses local broadcasts to implement the record methods 
    // because they will occur asyncronously instead of a add/remove listener 
    // paradigm.   This class also cannot call AtakBroadcast because AtakBroadcast
    // directly calls into this class when firing intents.

    private static final String METRIC_INTENT = "com.atakmap.metric";

    private static LocalBroadcastManager lbm;

    private static final List<BroadcastReceiver> registered = new ArrayList<>();

    /**
     * Must be called only once by the AtakBroadcast class. 
     * Prevent future reinitialization.
     * @param lbm the local broadcast manager in use by the metrics api.
     */
    synchronized public static void init(final LocalBroadcastManager lbm) {
        if (MetricsApi.lbm == null) {
            MetricsApi.lbm = lbm;
        }

    }

    /**
     * Call to register a BroadcastReceiver that will obtain metric information.
     */
    public static void register(BroadcastReceiver receiver) {
        // note - does not require synchronization because it is only used for 
        // book keeping and not iteration.
        registered.add(receiver);
        try {
            AtakBroadcast.getInstance().registerReceiver(receiver,
                    new DocumentedIntentFilter(METRIC_INTENT));
        } catch (Exception ignored) {
        }
    }

    /**
     * Call to unregister a BroadcastReceiver that will obtain metric information.
     */
    public static void unregister(BroadcastReceiver receiver) {
        // note - does not require synchronization because it is only used for 
        // book keeping and not iteration.
        registered.remove(receiver);
        try {
            AtakBroadcast.getInstance().unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }

    /**
     * Should be called prior to calling record metric in order to make sure 
     * that the recording will be used.
     */
    public static boolean shouldRecordMetric() {
        return registered.size() > 0;
    }

    /**
     * Used to record an nonnull intent event.   This call will accurately 
     * capture all attributes related to the intent and pass them onto 
     * passive listeners that implement MetricApi.METRIC_INTENT broadcast 
     * receiver.
     * @param intent either a local or system intent.
     */
    public static void record(final Intent intent) {
        if (intent != null) {
            Intent metricIntent = new Intent(MetricsApi.METRIC_INTENT);
            metricIntent.putExtra("intent", intent);
            lbm.sendBroadcast(metricIntent);
        }
    }

    /**
     * Used to record a category and a bundle, this call will pass the bundle onto 
     * passive listeners that implement the MetricApi.METRIC_INTENT broadcast
     * receiver.
     * @param category is the category used to bin the bundle
     * @param b the name value pairs passed in as a bundle
     */
    public static void record(final String category, final Bundle b) {
        if (b != null) {
            Intent metricIntent = new Intent(MetricsApi.METRIC_INTENT);
            if (!FileSystemUtils.isEmpty(category))
                metricIntent.putExtra("category", category);
            metricIntent.putExtra("bundle", b);
            lbm.sendBroadcast(metricIntent);
        }
    }
}
