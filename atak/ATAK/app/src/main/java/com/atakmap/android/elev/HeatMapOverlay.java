
package com.atakmap.android.elev;

import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Actions;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.app.R;
import com.atakmap.map.layer.Layer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HeatMapOverlay extends AbstractHierarchyListItem implements Layer,
        MapOverlay {

    private boolean visible;
    private float _saturation = 0.1f; // .5
    private float _value = 0.1f; // .5
    private float _alpha = 0.1f; // .5
    private int _xSampleResolution = 14 * 7;
    private int _ySampleResolution = 10 * 7;

    private final Set<OnLayerVisibleChangedListener> visibleChangedListeners;
    private final Set<OnHeatMapResolutionChangedListener> resolutionChangedListeners;
    private final Set<OnHeatMapColorChangedListener> colorChangedListeners;

    private final Visibility visibility = new Visibility() {

        @Override
        public boolean setVisible(boolean visible) {
            HeatMapOverlay.this.setVisible(visible);
            return true;
        }

        @Override
        public boolean isVisible() {
            return HeatMapOverlay.this.isVisible();
        }

    };

    public HeatMapOverlay() {
        this.visibleChangedListeners = new HashSet<>();
        this.resolutionChangedListeners = new HashSet<>();
        this.colorChangedListeners = new HashSet<>();

        this.visible = false;
    }

    @Override
    public void addOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
        synchronized (visibleChangedListeners) {
            this.visibleChangedListeners.add(l);
        }
    }

    @Override
    public void removeOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
        synchronized (visibleChangedListeners) {
            this.visibleChangedListeners.remove(l);
        }
    }

    public void addOnHeatMapResolutionChangedListener(
            OnHeatMapResolutionChangedListener l) {
        synchronized (resolutionChangedListeners) {
            this.resolutionChangedListeners.add(l);
        }
    }

    public void removeOnHeatMapResolutionChangedListener(
            OnHeatMapResolutionChangedListener l) {
        synchronized (resolutionChangedListeners) {
            this.resolutionChangedListeners.remove(l);
        }
    }

    public void addOnHeatMapColorChangedListener(
            OnHeatMapColorChangedListener l) {
        synchronized (colorChangedListeners) {
            this.colorChangedListeners.add(l);
        }
    }

    public void removeOnHeatMapColorChangedListener(
            OnHeatMapColorChangedListener l) {
        synchronized (colorChangedListeners) {
            this.colorChangedListeners.remove(l);
        }
    }

    private void dispatchOnHeatMapVisibleChanged() {
        // Read from a copy of the listeners instead of synchronizing the loop
        List<OnLayerVisibleChangedListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(
                    this.visibleChangedListeners);
        }
        for (OnLayerVisibleChangedListener l : listeners)
            l.onLayerVisibleChanged(this);
    }

    private void dispatchOnHeatMapColorChanged() {
        synchronized (colorChangedListeners) {
            for (OnHeatMapColorChangedListener l : this.colorChangedListeners)
                l.onHeatMapColorChanged(this);
        }
    }

    private void dispatchOnHeatMapResolutionChanged() {
        synchronized (resolutionChangedListeners) {
            for (OnHeatMapResolutionChangedListener l : this.resolutionChangedListeners)
                l.onHeatMapResolutionChanged(this);
        }
    }

    public float getSaturation() {
        return _saturation;
    }

    public float getValue() {
        return _value;
    }

    public float getAlpha() {
        return _alpha;
    }

    public void setColorComponents(float saturation, float value, float alpha) {
        _saturation = saturation;
        _value = value;
        _alpha = alpha;

        this.dispatchOnHeatMapColorChanged();
    }

    public int getSampleResolutionX() {
        return _xSampleResolution;
    }

    public int getSampleResolutionY() {
        return _ySampleResolution;
    }

    public void setResolution(int xSamples, int ySamples) {
        _xSampleResolution = xSamples;
        _ySampleResolution = ySamples;

        this.dispatchOnHeatMapResolutionChanged();
    }

    /**************************************************************************/
    // Map Overlay

    @Override
    public String getIdentifier() {
        return this.getClass().getName();
    }

    @Override
    public String getName() {
        return "Elevation Heat Map";
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, Sort preferredSort) {
        if ((capabilities
                & Actions.ACTION_VISIBILITY) != Actions.ACTION_VISIBILITY)
            return null;
        return this;
    }

    /**************************************************************************/

    @Override
    public String getTitle() {
        return this.getName();
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext().getPackageName() + "/" +
                R.drawable.ic_overlay_dted;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Visibility.class))
            return clazz.cast(this.visibility);
        else
            return null;
    }

    @Override
    public Object getUserObject() {
        return this;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        return null;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    public Sort refresh(Sort sortHint) {
        return sortHint;
    }

    /**************************************************************************/
    // Layer

    @Override
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            this.dispatchOnHeatMapVisibleChanged();
        }
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    /**************************************************************************/

    public interface OnHeatMapResolutionChangedListener {
        void onHeatMapResolutionChanged(HeatMapOverlay overlay);
    }

    public interface OnHeatMapColorChangedListener {
        void onHeatMapColorChanged(HeatMapOverlay overlay);
    }
}
