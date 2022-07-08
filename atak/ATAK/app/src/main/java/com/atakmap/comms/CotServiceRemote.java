
package com.atakmap.comms;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;

import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.cot.event.CotEvent;

/**
 */
public class CotServiceRemote {

    private final static List<CotServiceRemote> queued = new ArrayList<>();

    private CotEventListener cel;

    /**
     * the d variant of the variable is only to be used when deferring the registration of
     * the listener after it has been connected.
     */
    private ConnectionListener cl;
    private InputsChangedListener inListener, dInListener;
    private OutputsChangedListener outListener, dOutListener;

    private boolean connected = false;

    private static final Object lock = new Object();
    private static boolean alreadyReceivedStart;

    public static final String TAG = "CotServiceRemote";

    public final static NetConnectString ALL_STREAMING_OUTPUTS_CONN = new NetConnectString(
            "stcp",
            "*", -1);
    static {
        ALL_STREAMING_OUTPUTS_CONN.setCallsign("All Streaming");
    }

    public enum Proto {
        ssl,
        tcp,
        udp
    }

    public interface CotEventListener {
        /**
         * Callback for when a CoT event is received.
         * @param event the cot event.
         * @param extra the bundle which contains information such as where it was from.
         */
        void onCotEvent(CotEvent event, Bundle extra);
    }

    public interface InputsChangedListener {
        /**
         * Add an input.
         * @param descBundle
         * Keys based on usage in CotPortListActivity
         * CotPort.DESCRIPTION_KEY
         * CotPort.ENABLED_KEY
         * CotPort.CONNECTED_KEY
         */
        void onCotInputAdded(Bundle descBundle);

        /**
         * Remove an input.
         * @param descBundle the bundle that describes the input that was removed.
         */
        void onCotInputRemoved(Bundle descBundle);
    }

    public interface OutputsChangedListener {
        /**
         * Add or Update an output
         * Keys based on usage in CotPortListActivity
         * CotPort.DESCRIPTION_KEY
         * CotPort.ENABLED_KEY
         * CotPort.CONNECTED_KEY
         * @param descBundle the description bundle with the above keys
         */
        void onCotOutputUpdated(Bundle descBundle);

        /**
         * Remove an output
         * CotPort.DESCRIPTION_KEY
         * CotPort.ENABLED_KEY
         * CotPort.CONNECTED_KEY
         * @param descBundle the description bundle with the above keys
         */
        void onCotOutputRemoved(Bundle descBundle);
    }

    public interface ConnectionListener {
        /**
         *
         * @param fullServiceState
         * (Bundle[]) fullServiceState.getParcelableArray("streams") which appears to be and array of
         * all of the current TAK servers bundle made up of CotPort.DESCRIPTION_KEY,
         * CotPort.ENABLED_KEY, CotPort.CONNECTED_KEY
         * lifted from CotStreamListener
         *
         * fullServiceState can also contain
         * (Bundle[]) fullServiceState.getParcelableArray(getPortType()) which follows the same rules
         * above.
         * lifted from CotPortListActivity
         */
        void onCotServiceConnected(Bundle fullServiceState);

        void onCotServiceDisconnected();
    }

    /**
     * Sets the CotEventListener for the remote service.
     * @param cel the cot event listener to use.   If a previous one is set, then it is unregistered
     */
    public void setCotEventListener(final CotEventListener cel) {
        if (this.cel != null) {
            CommsMapComponent.getInstance().removeOnCotEventListener(this.cel);
        }

        this.cel = cel;

        if (cel != null)
            CommsMapComponent.getInstance().addOnCotEventListener(cel);
    }

    /**
     * Called by ATAKActivity to tell everyone that we are finally connected.   This simulates
     * the old way where connections would happen after the service started.
     * This iterates though a queue that contains all of the CotServiceRemote listening on 
     * a connection.
     * Any CotServiceRemote objects that are provided after this is been called will just 
     * immediately fire back the connected() callback.
     */
    static public void fireConnect() {
        final Thread t = new Thread() {
            public void run() {
                synchronized (lock) {
                    if (alreadyReceivedStart)
                        return;

                    alreadyReceivedStart = true;
                    for (final CotServiceRemote csr : queued) {
                        csr.notifyConnect();
                    }
                    queued.clear();
                }
            }
        };
        t.start();
    }

    synchronized private void notifyConnect() {
        if (!connected) {
            connected = true;
            Log.d(TAG, "connectlistener: " + cl.getClass() + " " + connected);

            final CommsMapComponent cmc = CommsMapComponent.getInstance();
            if (cmc != null)
                cl.onCotServiceConnected(cmc.getAllPortsBundle());

            // Deferred registration of the inputs/outputs listeners until after
            // a connection has occurred.
            if (dInListener != null) {
                setInputsChangedListener(dInListener);
                dInListener = null;
            }

            if (dOutListener != null) {
                setOutputsChangedListener(dOutListener);
                dOutListener = null;
            }

        }
    }

    /**
     * Enqueues all any ConnectionListener on the queue prior to fireConnected is called.
     *
     * Any CotServiceRemote objects that are provided after the fireConnected call will
     * immediately fire back the connected() callback.
     */
    public void connect(final ConnectionListener cl) {
        synchronized (lock) {
            if (connected)
                return;

            Log.d(TAG, "connectlistener: " + cl.getClass() + " "
                    + alreadyReceivedStart);
            this.cl = cl;
            if (alreadyReceivedStart) {
                notifyConnect();
            } else {
                queued.add(this);
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * .
     */
    public void disconnect() {
        synchronized (lock) {
            if (connected) {
                cl.onCotServiceDisconnected();
                CommsMapComponent.getInstance().removeOnCotEventListener(cel);
                connected = false;
            }
        }
    }

    /**
     * Gets the CoTService if it is valid otherwise returns null.
     * @return null if the CoTService is not valid.
     */
    private static CotService getCotService() {
        final CommsMapComponent cmc = CommsMapComponent.getInstance();
        if (cmc != null) {
            return cmc.getCotService();
        }
        return null;
    }

    /**
     * Request an input be added for the local network.
     * @param input this is the ConnectString as shown in CotOutputsListActivity
     * @param meta contained in CotPortListActivity.CotPort is all of the values that can be put in
     *             the bundle.
     */
    public void addInput(final String input, final Bundle meta) {
        CotService cs = getCotService();
        if (cs != null)
            cs.addInput(input, meta);
    }

    /**
     * Request an input be removed for the local network.
     * @param input the input as described by a string.
     */
    public void removeInput(final String input) {
        CotService cs = getCotService();
        if (cs != null)
            cs.removeInput(input);
    }

    /**
     * Request an output be added for the local network.
     * @param output this is the ConnectString as shown in CotOutputsListActivity
     * @param meta contained in CotPortListActivity.CotPort is all of the values that can be put in
     *             the bundle.
     */
    public void addOutput(final String output, final Bundle meta) {
        CotService cs = getCotService();
        if (cs != null)
            cs.addOutput(output, meta);
    }

    /**
     * Request an output be removed for the local network.
     * @param output the output as described by a string
     */
    public void removeOutput(final String output) {
        CotService cs = getCotService();
        if (cs != null)
            cs.removeOutput(output);
    }

    /**
     * Request a connection be removed for a TAK server.
     * @param connectString the stream defined by its connect string or
     *      * wild card "**" to remove all streams.
     */
    public final void removeStream(String connectString) {
        CotService cs = getCotService();
        if (cs != null)
            cs.removeStreaming(connectString, false);
    }

    /**
     * Request a connection be added for a TAK server.
     * @param connectString the stream defined by the connect string
     *
     */
    public final void addStream(final String connectString,
            final Bundle params) {
        CotService cs = getCotService();
        if (cs != null)
            cs.addStreaming(connectString, params);
    }

    public final void setCredentialsForStream(String connectString,
            String username, String password) {
        CotService cs = getCotService();
        if (cs != null)
            cs.setCredentialsForStream(connectString, username, password);
    }

    public final void setUseAuthForStream(String connectString,
            boolean useAuth) {
        CotService cs = getCotService();
        if (cs != null)
            cs.setUseAuthForStream(connectString, useAuth);
    }

    /**
     * Register a change listener with this instance of CotServiceRemote.
     *
     * @param listener called when the inputs are changed.
     */
    public void setInputsChangedListener(final InputsChangedListener listener) {
        synchronized (this) {
            if (connected) {
                if (inListener != null)
                    CommsMapComponent.getInstance()
                            .removeInputsChangedListener(inListener);
                CommsMapComponent.getInstance().addInputsChangedListener(
                        listener);
                inListener = listener;
            } else {
                dInListener = listener;
            }
        }
    }

    /**
     * Register a change listener with this instance of CotServiceRemote.
     *
     * @param listener called when the outputs are changed
     */
    public void setOutputsChangedListener(
            final OutputsChangedListener listener) {
        synchronized (this) {
            if (connected) {
                if (outListener != null)
                    CommsMapComponent.getInstance()
                            .removeOutputsChangedListener(outListener);
                CommsMapComponent.getInstance().addOutputsChangedListener(
                        listener);
                outListener = listener;
            } else {
                dOutListener = listener;
            }
        }
    }

}
