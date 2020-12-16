
package com.atakmap.android.maps.tilesets.mobac;

import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceLayerInfoSpi;

import java.io.File;

public class MobacLayerScanner extends GenericLayerScanner {

    public static final String TAG = "MobacLayerScanner";

    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new MobacLayerScanner();
        }

    };

    public MobacLayerScanner() {
        super("mobac");
    }

    @Override
    protected File[] getScanDirs() {
        return getDefaultScanDirs("mobac/mapsources", true);
    }

    @Override
    protected int checkFile(int depth, File f) {
        if (IOProviderFactory.isDirectory(f))
            return DELAY;
        else if (f.getName().endsWith(".bsh") || f.getName().endsWith(".xml")
                || f.getName().endsWith(".xmle"))
            return ACCEPT;
        else
            return REJECT;
    }

    @Override
    protected String getProviderHint(int depth, File f) {
        return MobacMapSourceLayerInfoSpi.INSTANCE.getType();
    }

    @Override
    public void reset() {
    }
}
