
package com.atakmap.android.maps.tilesets;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import android.database.sqlite.SQLiteException;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.spi.InteractiveServiceProvider;

public class TilesetLayerInfoSpi extends AbstractDatasetDescriptorSpi {

    static {
        TilesetInfo.staticInit();
    }

    public static final String TAG = "TilesetLayerInfoSpi";

    public final static DatasetDescriptorSpi INSTANCE = new TilesetLayerInfoSpi();

    private TilesetLayerInfoSpi() {
        super("tileset", 1);
    }

    @Override
    public Set<DatasetDescriptor> create(File file, File workingDir, InteractiveServiceProvider.Callback callback) {
        DatasetDescriptor retval;
        try {
            retval = TilesetInfo.parse(file);
        } catch (IOException e) {
            retval = null;
            Log.e(TAG, "IO error creating LayerInfo", e);
            if(callback != null)
                callback.errorOccurred("IO Error parsing " + file.getName(), e);
        } catch(SQLiteException e) {
            retval = null;
            Log.e(TAG, "SQLite error creating LayerInfo", e);
            if(callback != null)
                callback.errorOccurred("SQLite Error parsing " + file.getName(), e);
        } catch(Throwable e) {
            retval = null;
            Log.e(TAG, "General error creating LayerInfo", e);
            if(callback != null)
                callback.errorOccurred("General Error parsing " + file.getName(), e);
        } 
        if (retval == null)
            return null;
        return Collections.singleton(retval);
    }

    @Override
    public boolean probe(File file, InteractiveServiceProvider.Callback callback) {
        if(IOProviderFactory.isDirectory(file))
            return false;

        // Since tileset files are either an XML or database,
        // and you can't really tell if they are valid without trying
        // to parse the data, we are going  to call create here to test
        // if they are made. It shouldn't be too expensive, since only
        // a single file is being parsed, and data isn't actually being pulled
        // out, only boundaries, etc.

        // Pass Null for the ResourceSpi and CreateLayersCallback since they
        // aren't used in this call to create.
        Set<DatasetDescriptor> layers = create(file, null, null);

        if(layers != null && layers.size() > 0){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public int parseVersion() {
        return 10;
    }
}
