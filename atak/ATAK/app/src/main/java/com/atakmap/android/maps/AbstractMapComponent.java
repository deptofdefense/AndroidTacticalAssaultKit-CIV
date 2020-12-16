
package com.atakmap.android.maps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.Collections2;

import java.util.Set;

public abstract class AbstractMapComponent implements MapComponent {

    private final Set<BroadcastReceiver> registeredReceivers;
    private final Set<MapOverlay> registeredOverlays;
    private OnAllComponentsCreatedCallback allComponentsCreatedCallback;
    private final boolean removeReceiversOnDestroy;

    public static final String TAG = "AbstractMapComponent";

    protected AbstractMapComponent() {
        this(false);
    }

    protected AbstractMapComponent(boolean removeReceiversOnDestroy) {
        this.removeReceiversOnDestroy = removeReceiversOnDestroy;
        this.registeredReceivers = Collections2.newIdentityHashSet();
        this.registeredOverlays = Collections2.newIdentityHashSet();
        this.allComponentsCreatedCallback = null;
    }

    /**************************************************************************/

    protected final synchronized void setOnAllComponentsCreatedCallback(
            Context context,
            OnAllComponentsCreatedCallback callback) {
        if (this.allComponentsCreatedCallback != null)
            throw new IllegalStateException();
        this.allComponentsCreatedCallback = callback;

        this.registerReceiver(
                context,
                new ComponentsCreatedReceiver(),
                new DocumentedIntentFilter(
                        "com.atakmap.app.COMPONENTS_CREATED"));
    }

    protected final synchronized void registerReceiver(Context ignored,
            BroadcastReceiver receiver,
            DocumentedIntentFilter filter) {
        AtakBroadcast.getInstance().registerReceiver(receiver, filter);
        this.registeredReceivers.add(receiver);
    }

    protected final synchronized void unregisterReceiver(Context ignored,
            BroadcastReceiver receiver) {
        this.unregisterReceiverImpl(receiver, true);
    }

    private void unregisterReceiverImpl(BroadcastReceiver receiver,
            boolean remove) {
        if (!this.registeredReceivers.contains(receiver))
            return;

        try {
            AtakBroadcast.getInstance().unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.d(TAG, "unregistering a receiver: " + receiver
                    + " that was never registered (this might be a problem).");
        }

        if (remove)
            this.registeredReceivers.remove(receiver);
    }

    protected final synchronized void addOverlay(MapView view, MapOverlay o) {
        if (this.registeredOverlays.add(o))
            view.getMapOverlayManager().addOverlay(o);
    }

    protected final synchronized void removeOverlay(MapView view,
            MapOverlay o) {
        if (this.registeredOverlays.remove(o))
            view.getMapOverlayManager().removeOverlay(o);
    }

    /**
     * This method is invoked as a subset of the onDestroy call as part of the
     * MapComponent lifecycle.
     */
    protected abstract void onDestroyImpl(Context context, MapView view);

    /**
     * This method is called during MapComponent destruction and additionally calls the subclass
     * implementation of onDestroyImpl
     */
    @Override
    public final void onDestroy(Context context, MapView view) {
        this.onDestroyImpl(context, view);

        synchronized (this) {
            for (BroadcastReceiver registeredReceiver : this.registeredReceivers)
                this.unregisterReceiverImpl(registeredReceiver, false);
            if (this.removeReceiversOnDestroy)
                this.registeredReceivers.clear();
            for (MapOverlay o : this.registeredOverlays)
                this.removeOverlay(view, o);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(Context context, MenuItem item) {
        return false;
    }

    @Override
    public void onOptionsMenuClosed(Context context, Menu menu) {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
    }

    /**************************************************************************/

    public interface OnAllComponentsCreatedCallback {
        void onAllComponentsCreated(Bundle extras);
    }

    private class ComponentsCreatedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction() == null)
                return;

            if (intent.getAction().equals("com.atakmap.app.COMPONENTS_CREATED")
                    &&
                    AbstractMapComponent.this.allComponentsCreatedCallback != null) {

                AbstractMapComponent.this.allComponentsCreatedCallback
                        .onAllComponentsCreated(intent.getExtras());

                AbstractMapComponent.this.unregisterReceiver(context, this);
            }
        }
    }

    /**
     * Default implementation for an abstract map component which does not respond to the onStart
     * state transition.
     */
    @Override
    public void onStart(Context context, MapView view) {
    }

    /**
     * Default implementation for an abstract map component which does not respond to the onStop
     * state transition.
     */
    @Override
    public void onStop(Context context, MapView view) {
    }

    /**
     * Default implementation for an abstract map component which does not respond to the onPause
     * state transition.
     */
    @Override
    public void onPause(Context context, MapView view) {
    }

    /**
     * Default implementation for an abstract map component which does not respond to the onResume
     * state transition.
     */
    @Override
    public void onResume(Context context, MapView view) {
    }
}
