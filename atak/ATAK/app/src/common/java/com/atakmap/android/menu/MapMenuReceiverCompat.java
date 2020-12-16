
package com.atakmap.android.menu;

import android.content.BroadcastReceiver;

public class MapMenuReceiverCompat {
    public static boolean isNotNull() {
        return MapMenuReceiver.getInstance() != null;
    }

    public static void hideMenu() {
        MapMenuReceiver.getInstance().hideMenu();
    }

    public static void addEventListener(MapMenuEventListener listener) {
        MapMenuReceiver.getInstance().addEventListener(listener);
    }

    public static void removeEventListener(MapMenuEventListener listener) {
        MapMenuReceiver.getInstance().removeEventListener(listener);
    }
}
