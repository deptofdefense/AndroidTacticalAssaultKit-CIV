
package com.atakmap.android.maps.tilesets.mobac;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.contentservices.Service;
import com.atakmap.android.contentservices.ServiceType;
import com.atakmap.coremap.filesystem.FileSystemUtils;

// XXX - this class should be newly reimplemented. legacy implementations leave
//       much to be desired

public class WebMapLayerService implements Service {

    private final static String TAG = "WebMapLayerService";

    private final WebMapLayer impl;

    public WebMapLayerService(WebMapLayer impl) {
        this.impl = impl;
    }

    @Override
    public ServiceType getType() {
        return ServiceType.Imagery;
    }

    @Override
    public String getName() {
        return impl.getTitle();
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void generateConfigFile(OutputStream sink) throws IOException {
        // XXX - this is stupid..

        // XXX - select style
        QueryLayers.Style style;
        Iterator<QueryLayers.Style> styles = impl.getStyles().iterator();
        if (styles.hasNext())
            style = styles.next();
        else
            style = null;

        File f = null;
        try {
            f = impl.writeMobacXML(style);
            try (InputStream is = IOProviderFactory.getInputStream(f)) {
                FileSystemUtils.copyStream(is, true, sink, false);
            }
        } finally {
            if (f != null)
                FileSystemUtils.delete(f);
        }
    }

    /**
     * Provided a list of layers, determine if an agregate layer can be constructed, 
     * with the provided name being optional (can be null or empty).
     * @param name of the layer
     * @param layers to be aggregated
     * 
     */
    public static WebMapLayerService constructAggregate(final String name,
            final List<Service> layers) {
        if (layers.size() == 0) {
            // do nothing
        } else if (layers.size() == 1) {
            Object o = layers.get(0);
            if (o instanceof WebMapLayerService)
                return (WebMapLayerService) o;
        } else {
            List layerImpls = new ArrayList();
            for (Service layer : layers) {
                if (layer instanceof WebMapLayerService) {
                    layerImpls.add(((WebMapLayerService) layer).impl);
                }
            }
            com.atakmap.android.maps.tilesets.mobac.WebMapLayer wml = com.atakmap.android.maps.tilesets.mobac.WMSQueryLayers
                    .constructAggregate(name, layerImpls);
            if (wml != null) {
                return new WebMapLayerService(wml);
            } else {
                Log.d(TAG, "error agregating layers");
                return null;
            }
        }
        return null;
    }
}
