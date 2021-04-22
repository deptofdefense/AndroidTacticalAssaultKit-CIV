
package com.atakmap.android.tools;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Handles action bar data
 */
public class ActionBarContentHandler extends FileContentHandler
        implements GoTo {

    private final Context _context;
    private final AtakPreferences _prefs;
    private final AtakActionBarMenuData _data;

    public ActionBarContentHandler(MapView mapView,
            AtakActionBarMenuData data) {
        super(data.getFile());
        _context = mapView.getContext();
        _prefs = new AtakPreferences(mapView);
        _data = data;
    }

    @Override
    public String getContentType() {
        return ImportActionBarSort.CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return ImportActionBarSort.MIME_TYPE;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_tools);
    }

    @Override
    public void deleteContent() {
        // Delete config file
        FileSystemUtils.delete(_data.getFile());

        // Reset to default action bar
        _prefs.set(AtakActionBarListData.getOrientationPrefName(
                _data.getOrientation()), AtakActionBarListData.DEFAULT_LABEL);
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.RELOAD_ACTION_BAR)
                        .putExtra("label",
                                AtakActionBarListData.DEFAULT_LABEL));
    }

    @Override
    public boolean goTo(boolean select) {
        _prefs.set(AtakActionBarListData.getOrientationPrefName(
                _data.getOrientation()), _data.getLabel());

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.RELOAD_ACTION_BAR)
                        .putExtra("label", _data.getLabel()));

        return false;
    }
}
