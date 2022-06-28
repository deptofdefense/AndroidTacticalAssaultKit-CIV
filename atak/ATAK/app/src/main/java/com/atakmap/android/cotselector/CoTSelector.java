
package com.atakmap.android.cotselector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;

public class CoTSelector extends DropDownReceiver {

    public static final String TAG = "CoTSelector";

    interface TypeChangedListener {
        void onTypeChanged(String type);
    }

    private PointMapItem m;
    private TypeChangedListener tl;
    private final CustomListView clistview;
    private final View csuitmain;

    public CoTSelector(final MapView mapView) {
        super(mapView);

        // inflate the layout
        LayoutInflater inflater = LayoutInflater.from(mapView.getContext());
        csuitmain = inflater.inflate(R.layout.csuitmain, null);
        LinearLayout mainLL = csuitmain
                .findViewById(R.id.MainLL);

        FileIO fio = new FileIO(getMapView().getContext());
        clistview = new CustomListView(getMapView().getContext());
        clistview.init(
                csuitmain,
                fio,
                this);
        clistview.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mainLL.addView(clistview);

    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    /**
     * Given a PointMapItem, show the CoT selector that is used to select a new
     * CoT type.
     * @param m the map item
     * @param tl the type change listener
     */
    public void show(final PointMapItem m,
            final TypeChangedListener tl) {

        this.m = m;
        this.tl = tl;
        clistview.setType(m.getType());

        showDropDown(csuitmain, FULL_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT);

    }

    synchronized static public void changeType(final PointMapItem m,
            final String type,
            final MapView mapView,
            final TypeChangedListener tl) {
        final SharedPreferences _prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView
                        .getContext());

        m.setType(type);

        //if marker already has color, lets update it based on CoT Type
        //if (m.hasMetaValue("color")) {
        //int color = Marker.getAffiliationColor(type);
        // if (color != m.getMetaInteger("color", 0)) {
        //color has changed
        // m.setMetaInteger("color", color);
        //}
        // }

        //
        //   FOR LEGACY - COPY OF THE BROKEN CoTInfoBroadcastReceiver::saveTargetData
        //
        final MapGroup source = m.getGroup();
        MapGroup parent = null;

        if (source != null) {
            parent = source.getParentGroup();
        }

        // if the group is not null, as in this item already exists in a group then 

        if (parent != null) {
            for (MapGroup dest : parent.getChildGroups()) {
                String destGroupName = dest.getFriendlyName();
                if ((destGroupName != null)
                        &&
                        (type.charAt(2) == Character.toLowerCase(destGroupName
                                .charAt(0)))) {
                    if (dest != source) {
                        //Log.d(TAG, "marker: " + m.getUID() + " change group from: " + source + " to: " + dest);
                        //source.removeItem(m);
                        dest.addItem(m);

                        break;
                    }
                }
            }
        }

        // also persist the last selected CoT type 
        _prefs.edit().putString("lastCoTTypeSet", type)
                .remove("lastIconsetPath").apply();
        m.refresh(mapView.getMapEventDispatcher(), null, CoTSelector.class);
        m.persist(mapView.getMapEventDispatcher(), null, CoTSelector.class);
        if (tl != null)
            tl.onTypeChanged(type);

    }

    public void notifyChanged(final String type) {
        changeType(m, type, getMapView(), tl);

        closeDropDown();
    }

    @Override
    public void disposeImpl() {
    }

}
