
package com.atakmap.android.overlay;

interface OverlayListener {
    void onOverlayRegistered(String overlayId);

    void onOverlayUnregistered(String overlayId);

    void onOverlayBooleanChanged(String overlayId, String property,
            boolean value);

    void onOverlayIntChanged(String overlayId, String property, int value);

    void onOverlayStringChanged(String overlayId, String property,
            String value);
}
