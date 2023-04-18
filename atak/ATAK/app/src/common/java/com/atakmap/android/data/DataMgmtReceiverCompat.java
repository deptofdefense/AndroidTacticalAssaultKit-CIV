
package com.atakmap.android.data;

import com.atakmap.android.maps.MapView;

public class DataMgmtReceiverCompat {
    public static DataMgmtReceiver getInstance(MapView view) {
        return new DataMgmtReceiver(view);
    }
}
