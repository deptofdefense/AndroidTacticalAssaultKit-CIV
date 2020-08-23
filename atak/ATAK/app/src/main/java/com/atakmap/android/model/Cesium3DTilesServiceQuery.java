
package com.atakmap.android.model;

import android.net.Uri;

import com.atakmap.android.contentservices.Service;
import com.atakmap.android.contentservices.ServiceListing;
import com.atakmap.android.contentservices.ServiceQuery;
import com.atakmap.android.contentservices.ServiceType;
import com.atakmap.map.formats.c3dt.Cesium3DTilesModelInfoSpi;
import com.atakmap.map.layer.model.ModelInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Set;

public class Cesium3DTilesServiceQuery implements ServiceQuery {
    @Override
    public String getName() {
        return "Cesium 3D Tiles";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public ServiceListing queryServices(String url) {
        try {
            Set<ModelInfo> datasets = Cesium3DTilesModelInfoSpi.INSTANCE
                    .create(url);
            if (datasets == null)
                return null;
            ServiceListing result = new ServiceListing();
            try {
                result.title = (Uri.parse(url)).getHost();
            } catch (Throwable t) {
                result.title = url;
            }
            result.description = "Cesium 3D Tiles";
            result.serverType = "Cesium 3D Tiles";
            result.services = new ArrayList<>(datasets.size());
            for (final ModelInfo info : datasets)
                result.services.add(new Service() {
                    @Override
                    public ServiceType getType() {
                        return ServiceType.SurfaceMesh;
                    }

                    @Override
                    public String getName() {
                        return info.name;
                    }

                    @Override
                    public String getDescription() {
                        return info.name;
                    }

                    @Override
                    public void generateConfigFile(OutputStream sink)
                            throws IOException {
                        try {
                            JSONObject json = new JSONObject();
                            JSONObject i = new JSONObject();
                            i.put("uri", info.uri);
                            i.put("name", info.name);
                            i.put("type", info.type);
                            json.put("ModelInfo", i);

                            PrintStream ps = new PrintStream(sink);
                            ps.print(json.toString(2));
                        } catch (JSONException e) {
                            throw new IOException(e);
                        }
                    }
                });

            return result;
        } catch (Throwable t) {
            return null;
        }
    }
}
