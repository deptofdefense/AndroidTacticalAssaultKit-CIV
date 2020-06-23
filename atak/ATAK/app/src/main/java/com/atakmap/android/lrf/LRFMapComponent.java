
package com.atakmap.android.lrf;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.bluetooth.BtLowEnergyManager;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * Provides for the core Laser Range Finder Capability to include 
 * listening for either Bluetooth or Cabled Laser Range finder information
 * and rendering.
 */
public class LRFMapComponent extends AbstractMapComponent {

    final public static String TAG = "LRFMapComponent";

    private LocalRangeFinderInput input2;
    private Thread listenThread2;
    private PLRFBluetoothLEHandler plrfHandler;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {
        input2 = new LocalRangeFinderInput(view, context);
        listenThread2 = new Thread(input2, "LocalRangeFinderThread");
        listenThread2.start();
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
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (input2 != null)
            input2.cancel();

        // since it was not unregistered from the BtLowEnergyManager, that will take and call
        // dispose on shutdown.
        // plrfHandler.dispose();
    }

}
