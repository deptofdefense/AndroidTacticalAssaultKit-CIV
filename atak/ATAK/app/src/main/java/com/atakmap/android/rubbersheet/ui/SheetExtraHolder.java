
package com.atakmap.android.rubbersheet.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class SheetExtraHolder {

    public View root;
    public ImageButton export, delete;
    public ImageView failed;
    public ProgressBar loader;

    public SheetExtraHolder(MapView mapView, ViewGroup parent) {
        this.root = LayoutInflater.from(mapView.getContext()).inflate(
                R.layout.rs_extra_view, parent, false);
        this.export = this.root.findViewById(R.id.export);
        this.delete = this.root.findViewById(R.id.delete);
        this.loader = this.root.findViewById(R.id.loader);
        this.failed = this.root.findViewById(R.id.failed);
        this.root.setTag(this);
    }
}
