
package com.atakmap.android.features;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * View holder for feature buttons
 */
public class FeatureExtraHolder {

    public final View root;
    public final ImageButton pan, edit, send;

    private FeatureExtraHolder(View root) {
        this.root = root;
        this.pan = root.findViewById(R.id.panButton);
        this.edit = root.findViewById(R.id.editButton);
        this.send = root.findViewById(R.id.sendButton);
    }

    public static FeatureExtraHolder get(View row, ViewGroup parent) {

        // Get existing holder
        FeatureExtraHolder h = row != null
                && row.getTag() instanceof FeatureExtraHolder
                        ? (FeatureExtraHolder) row.getTag()
                        : null;

        // No existing holder - create and assign views
        if (h == null) {
            // Need map view to inflate view
            MapView mv = MapView.getMapView();
            if (mv == null)
                return null;
            if (parent == null)
                parent = mv;
            row = LayoutInflater.from(mv.getContext()).inflate(
                    R.layout.feature_set_extra, parent, false);
            h = new FeatureExtraHolder(row);
            row.setTag(h);
        }

        return h;
    }
}
