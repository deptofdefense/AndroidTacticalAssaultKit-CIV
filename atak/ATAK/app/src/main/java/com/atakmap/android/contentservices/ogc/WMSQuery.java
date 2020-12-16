
package com.atakmap.android.contentservices.ogc;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.atakmap.android.contentservices.Service;
import com.atakmap.android.contentservices.ServiceListing;
import com.atakmap.android.contentservices.ServiceQuery;
import com.atakmap.android.maps.tilesets.mobac.QueryLayers;
import com.atakmap.android.maps.tilesets.mobac.WMSQueryLayers;
import com.atakmap.android.maps.tilesets.mobac.WebMapLayer;
import com.atakmap.android.maps.tilesets.mobac.WebMapLayerService;

public class WMSQuery implements ServiceQuery {

    @Override
    public String getName() {
        return "WMS";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public ServiceListing queryServices(String url) {
        try {
            QueryLayers legacy = new WMSQueryLayers(url);
            legacy.process();
            List<WebMapLayer> results = legacy.getLayers();
            if (results == null || results.isEmpty())
                return null;

            ServiceListing retval = new ServiceListing();
            retval.serverType = "WMS";
            retval.title = legacy.getServiceTitle();
            retval.services = new HashSet<>();

            createServices(results, retval.services);
            return retval;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void createServices(Collection<WebMapLayer> results,
            Collection<Service> services) {
        for (WebMapLayer result : results) {
            if (result.isDisplayable())
                services.add(new WebMapLayerService(result));
            if (result.getChildren() != null)
                createServices(result.getChildren(), services);
        }
    }
}
