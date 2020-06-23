
package com.atakmap.android.importfiles.sort;

import android.content.Context;

import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.app.R;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Sorts ATAK Tile sets
 * 
 * 
 */
public class ImportTilesetSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportTilesetSort";

    private final Context _context;

    public ImportTilesetSort(Context context, boolean validateExt,
            boolean copyFile) {
        super(".zip", "layers", validateExt, copyFile, context
                .getString(R.string.tileset));

        this._context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .zip, now lets see if it is a tile set
        try {
            return TilesetInfo.parse(file) != null;
        } catch (IOException e) {
            return false;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }
}
