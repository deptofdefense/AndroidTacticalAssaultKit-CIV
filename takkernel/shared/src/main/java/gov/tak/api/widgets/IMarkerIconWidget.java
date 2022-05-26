package gov.tak.api.widgets;

import gov.tak.api.commons.graphics.IIcon;

public interface IMarkerIconWidget extends IMapWidget {
    interface OnMarkerWidgetIconChangedListener {
        void onMarkerWidgetIconChanged(IMarkerIconWidget widget);
    }

    interface OnMarkerWidgetIconStateChangedListener {
        void onMarkerWidgetStateChanged(IMarkerIconWidget widget);
    }

    interface OnMarkerWidgetIconRotationChangedListener {
        void onMarkerWidgetIconRotationChanged(IMarkerIconWidget widget);
    }
    void addOnMarkerWidgetIconStateChangedListener(OnMarkerWidgetIconStateChangedListener listener);
    void removeOnMarkerWidgetIconStateChangedListener(OnMarkerWidgetIconStateChangedListener listener);

    void addOnMarkerWidgetIconChangedListener(OnMarkerWidgetIconChangedListener listener);
    void removeOnMarkerWidgetIconChangedListener(OnMarkerWidgetIconChangedListener listener);

    void addOnMarkerWidgetIconRotationChangedListener(OnMarkerWidgetIconRotationChangedListener listener);
    void removeOnMarkerWidgetIconRotationChangedListener(OnMarkerWidgetIconRotationChangedListener listener);
    IIcon getWidgetIcon();
    int getState();
    float getRotation();
}