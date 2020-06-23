
package com.atakmap.android.layers.overlay;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.app.R;
import com.atakmap.map.layer.control.TerrainBlendControl;
import com.atakmap.util.Visitor;

/**
 * Controls transparency of the base map imagery
 */
public class ImageryTransparencyListItem extends AbstractChildlessListItem {

    private final Context _context;
    private final View _view;
    private final SeekBar _seekBar;

    public ImageryTransparencyListItem(final MapView mapView) {
        _context = mapView.getContext();

        _view = LayoutInflater.from(_context).inflate(
                R.layout.imagery_transparency_list_item, mapView, false);
        _seekBar = _view.findViewById(R.id.transparency_bar);
        _seekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean user) {
                if (user) {
                    final double val = prog / 100.0;
                    final boolean enabled = prog < 100;
                    mapView.getRenderer().visitControl(null,
                            new Visitor<TerrainBlendControl>() {
                                @Override
                                public void visit(
                                        TerrainBlendControl blendControl) {
                                    blendControl.setBlendFactor(val);
                                    blendControl.setEnabled(enabled);
                                }
                            }, TerrainBlendControl.class);
                    mapView.getRenderer().requestRefresh();
                }
            }
        });
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.imagery_transparency);
    }

    @Override
    public String getUID() {
        return "imageryTransparency";
    }

    @Override
    public Drawable getIconDrawable() {
        return _context.getDrawable(R.drawable.ic_transparency);
    }

    @Override
    public Object getUserObject() {
        return this;
    }

    @Override
    public View getListItemView(View row, ViewGroup parent) {
        // TODO: Apply the current imagery transparency to the seekbar here
        //_seekBar.setProgress(mapTransparency);
        return _view;
    }
}
