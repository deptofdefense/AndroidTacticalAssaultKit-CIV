
package com.atakmap.android.lrf;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.atakmap.android.bluetooth.BtLowEnergyManager;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.app.R;

/**
 * Provides for the core Laser Range Finder Capability to include 
 * listening for either Bluetooth or Cabled Laser Range finder information
 * and rendering.
 */
public class LRFMapComponent extends AbstractMapComponent
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    final public static String TAG = "LRFMapComponent";

    private LocalRangeFinderInput input2;
    private Thread listenThread2;
    private PLRFBluetoothLEHandler plrfHandler;
    private AtakPreferences preferences;
    private MapView mapView;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {
        mapView = view;
        input2 = new LocalRangeFinderInput(mapView, mapView.getContext());
        preferences = new AtakPreferences(view);
        preferences.registerListener(this);

        plrfHandler = new PLRFBluetoothLEHandler(context,
                new GenericLRFCallback());
        plrfHandler.setConnectionListener(
                new BtLowEnergyManager.BluetoothLEConnectionStatus() {
                    @Override
                    public void pairingError(final BluetoothDevice device) {
                        if (device != null)
                            view.post(new Runnable() {
                                public void run() {
                                    Toast.makeText(view.getContext(),
                                            String.format(
                                                    view.getContext().getString(
                                                            R.string.bt_pair_error),
                                                    device.getName()),
                                            Toast.LENGTH_LONG).show();

                                }
                            });
                    }
                });
        BtLowEnergyManager.getInstance().addExternalBtleHandler(plrfHandler);

        onSharedPreferenceChanged(preferences.getSharedPrefs(),
                "nonBluetoothLaserRangeFinder");
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (input2 != null) {
            input2.dispose();
        }

        preferences.unregisterListener(this);
        // since it was not unregistered from the BtLowEnergyManager, that will take and call
        // dispose on shutdown.
        // plrfHandler.dispose();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key == null)
            return;

        if (key.equals("nonBluetoothLaserRangeFinder")) {
            boolean enabled = sharedPreferences
                    .getBoolean("nonBluetoothLaserRangeFinder", true);

            if (input2 != null)
                input2.cancel();

            if (enabled) {
                listenThread2 = new Thread(input2, "LocalRangeFinderThread");
                listenThread2.start();
            }
        }
    }
}
