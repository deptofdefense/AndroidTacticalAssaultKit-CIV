
package com.atakmap.android.data;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Adds control for viewing and deleting data 
 * e.g. Zeroize and User Profile
 * 
 */
public class DataMgmtMapComponent extends AbstractMapComponent {

    final public static String TAG = "DataMgmtMapComponent";

    private DataMgmtReceiver _dataMgmtReceiver;
    private URIContentManager _uriContentManager;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        _dataMgmtReceiver = new DataMgmtReceiver(view);
        _uriContentManager = URIContentManager.getInstance();

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(DataMgmtReceiver.CLEAR_CONTENT_ACTION);
        AtakBroadcast.getInstance().registerReceiver(_dataMgmtReceiver,
                intentFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_dataMgmtReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_dataMgmtReceiver);
            _dataMgmtReceiver.dispose();
            _dataMgmtReceiver = null;
        }
        if (_uriContentManager != null) {
            _uriContentManager.dispose();
            _uriContentManager = null;
        }
    }

}
