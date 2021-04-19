package com.atakmap.android.maps;

public class MapTouchControllerCompat {
    public static MapTouchController getInstance(MapView view){
        return new MapTouchController(view);
    }
}
