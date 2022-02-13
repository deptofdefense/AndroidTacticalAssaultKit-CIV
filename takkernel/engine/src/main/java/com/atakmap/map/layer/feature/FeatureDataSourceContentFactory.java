
package com.atakmap.map.layer.feature;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.atakmap.interop.Pointer;

public final class FeatureDataSourceContentFactory {

    public static final String TAG = "FeatureDataSourceContentFactory";

    private static final Map<FeatureDataSource, Pointer> spis = new HashMap<>();

    private FeatureDataSourceContentFactory() {
    }

    public static FeatureDataSource.Content parse(File file, String typeHint) {
        final String path = file.getAbsolutePath();
        final Pointer result = NativeFeatureDataSource.FeatureDataSourceContentFactory_parse(path, typeHint);
        if(result == null)
            return null;
        return new NativeFeatureDataSource.NativeContent(result);
    }

    public static void register(FeatureDataSource spi) {
        register(spi, 0);
    }

    public static void register(FeatureDataSource spi, int priority) {
        Pointer pointer;
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            if(spi instanceof NativeFeatureDataSource)
                pointer = ((NativeFeatureDataSource)spi).pointer;
            else
                pointer = NativeFeatureDataSource.wrap(spi);
            spis.put(spi, pointer);
        }

        NativeFeatureDataSource.FeatureDataSourceContentFactory_register(pointer, priority);
    }


    public static void unregister(FeatureDataSource spi) {
        Pointer pointer;
        synchronized(spis) {
            pointer = spis.remove(spi);
            if(pointer == null)
                return;
        }
        NativeFeatureDataSource.FeatureDataSourceContentFactory_unregister(pointer);

        // if the SPI is not a NativeFeatureDataSource, we allocated the native pointer
        if(!(spi instanceof NativeFeatureDataSource))
            NativeFeatureDataSource.FeatureDataSource_destruct(pointer);
    }
    
    public static FeatureDataSource getProvider(String name) {
        final Pointer pointer = NativeFeatureDataSource.FeatureDataSourceContentFactory_getProvider(name);
        if(pointer == null)
            return null;
        synchronized(spis) {
            for(Map.Entry<FeatureDataSource, Pointer> entry : spis.entrySet()) {
                if(entry.getValue().equals(pointer)) {
                    // delete the pointer container allocated by JNI
                    NativeFeatureDataSource.FeatureDataSource_destruct(pointer);

                    return entry.getKey();
                }
            }

            NativeFeatureDataSource retval = new NativeFeatureDataSource(pointer);
            spis.put(retval, pointer);
            return retval;
        }
    }
}
