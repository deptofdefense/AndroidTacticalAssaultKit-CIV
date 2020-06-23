
package com.atakmap.android.dropdown;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.view.Menu;
import android.view.MenuItem;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DropDownMapComponent extends AbstractMapComponent {

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        for (DropDownReceiver r : _dropDownReceivers) {
            DropDownManager.getInstance()
                    .unregisterDropDownReceiver(r);
            r.dispose();
        }
        _dropDownReceivers.clear();

    }

    /**
     * Register a DropDownReceiver to be run in the main activity thread.
     * The receiver will be called with any broadcast Intent that matches filter, 
     * in the main application thread.
     * 
     * Any receiver registered will automatically be cleaned up when the MapComponent 
     * is destroyed. This includes both unregistering the receiver and calling 
     * dispose on it so it can no longer be used.
     * 
     * @param receiver The DropDownReceiver to handle the broadcast
     * @param filter Selects the Intent broadcasts to be received.
     */
    protected void registerDropDownReceiver(DropDownReceiver receiver,
            DocumentedIntentFilter filter) {
        if (receiver != null) {
            _dropDownReceivers.add(receiver);
            DropDownManager.getInstance().registerDropDownReceiver(
                    receiver, filter);
        }
    }

    private final ConcurrentLinkedQueue<DropDownReceiver> _dropDownReceivers = new ConcurrentLinkedQueue<>();

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

}
