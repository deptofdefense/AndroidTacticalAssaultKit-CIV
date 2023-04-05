package com.atakmap.android.helloworld;


import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

public class JoystickListener implements View.OnGenericMotionListener, View.OnKeyListener {
    public static final String TAG = "JoystickListener";

    public JoystickListener() {
        Log.d(TAG, "Starting Joystick Listener");
        MapView.getMapView().addOnGenericMotionListener(this);
        MapView.getMapView().addOnKeyListener(this);
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        Log.d(TAG, "onGenericMotion: " + event.toString());
        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp: " + event.toString());
        return false;
    }

    public void dispose() {
        Log.d(TAG, "dispose");
        MapView.getMapView().removeOnGenericMotionListener(this);
        MapView.getMapView().removeOnKeyListener(this);
    }
}
