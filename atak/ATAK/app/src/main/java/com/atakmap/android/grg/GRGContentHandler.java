
package com.atakmap.android.grg;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.app.R;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.spatial.file.FileOverlayContentHandler;

import java.io.File;

public class GRGContentHandler extends FileOverlayContentHandler
        implements Visibility {

    private final DatasetDescriptor _desc;
    private final DatasetRasterLayer2 _layer;

    private String _uid;
    private int _color;

    GRGContentHandler(MapView mv, File file, DatasetDescriptor desc,
            DatasetRasterLayer2 layer) {
        super(mv, file, desc.getMinimumBoundingBox());
        _desc = desc;
        _layer = layer;
        _layer.initDataset(desc);
    }

    void setUID(String uid) {
        _uid = uid;
    }

    void setColor(int color) {
        _color = color;
    }

    @Override
    public String getContentType() {
        return GRGMapComponent.IMPORTER_CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return GRGMapComponent.IMPORTER_DEFAULT_MIME_TYPE;
    }

    @Override
    public String getTitle() {
        return _desc.getName();
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_overlay_gridlines);
    }

    @Override
    public int getIconColor() {
        return _color;
    }

    @Override
    public boolean goTo(boolean select) {
        if (select && _uid != null) {
            Intent showMenu = new Intent(MapMenuReceiver.SHOW_MENU);
            showMenu.putExtra("uid", _uid);
            AtakBroadcast.getInstance().sendBroadcast(showMenu);
        }
        return super.goTo(select);
    }

    @Override
    public boolean isVisible() {
        return isConditionVisible() && _layer.isVisible(_desc.getName());
    }

    @Override
    public boolean setVisibleImpl(boolean visible) {
        _layer.setVisible(_desc.getName(), visible);
        return true;
    }
}
