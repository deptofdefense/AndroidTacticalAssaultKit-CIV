
package com.atakmap.android.tools;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import com.atakmap.android.importfiles.sort.ImportInternalSDResolver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Set;

/**
 * Sorter for toolbar configuration XML
 */
public class ImportActionBarSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportActionBarSort";

    public static final String CONTENT_TYPE = "Toolbar Config";
    public static final String MIME_TYPE = "application/xml";

    private final AtakPreferences _prefs;

    public ImportActionBarSort(Context context, boolean validateExt,
            boolean copyFile) {
        super(".xml", FileSystemUtils.CONFIG_DIRECTORY + "/actionbars",
                validateExt, copyFile, CONTENT_TYPE,
                context.getDrawable(R.drawable.ic_menu_tools));
        _prefs = new AtakPreferences(MapView.getMapView());
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;
        try {
            byte[] b = FileSystemUtils.read(IOProviderFactory
                    .getInputStream(file), 1024, true);
            String content = new String(b, FileSystemUtils.UTF8_CHARSET);
            return content.contains("<AtakActionBar");
        } catch (Exception e) {
            Log.d(TAG, "Failed to match action bar XML", e);
            return false;
        }
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);

        _prefs.set(AtakActionBarListData.getOrientationPrefName(
                AtakActionBarMenuData.Orientation.landscape),
                AtakActionBarListData.DEFAULT_LABEL);

        _prefs.set(AtakActionBarListData.getOrientationPrefName(
                AtakActionBarMenuData.Orientation.portrait),
                AtakActionBarListData.DEFAULT_LABEL);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.RELOAD_ACTION_BAR));
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, MIME_TYPE);
    }
}
