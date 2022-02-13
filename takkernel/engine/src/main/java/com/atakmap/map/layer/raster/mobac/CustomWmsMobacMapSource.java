
package com.atakmap.map.layer.raster.mobac;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.PointD;
import com.atakmap.coremap.log.Log;

public class CustomWmsMobacMapSource extends CustomMobacMapSource {

    private final static Map<String, String> TILE_FORMAT_TO_WMS_FORMAT_MIME = new HashMap<String, String>();
    static {
        TILE_FORMAT_TO_WMS_FORMAT_MIME.put("JPG", "image/jpeg");
        TILE_FORMAT_TO_WMS_FORMAT_MIME.put("PNG", "image/png");
    }

    private final String layers;
    private final String style;
    private final String formatMime;
    private final String additionalParameters;
    private final String version;
    private final GeoBounds bounds;
    private final Projection proj;
    
    private final int gridWidth;
    private final int gridHeight;

    public CustomWmsMobacMapSource(String name, int srid, int tileSize, int minZoom,
            int maxZoom, String type, String url, String layers, String style, String version,
            String additionalParameters,
            int backgroundColor,
            GeoBounds bounds) {
        this(name, srid, tileSize, minZoom, maxZoom, type, url, layers, style, version, additionalParameters, backgroundColor, bounds, 0L);
    }

    public CustomWmsMobacMapSource(String name, int srid, int tileSize, int minZoom,
        int maxZoom, String type, String url, String layers, String style, String version,
                String additionalParameters,
        int backgroundColor,
        GeoBounds bounds, long refreshInterval) {
        super(name, srid, tileSize, minZoom, maxZoom, type, url, null,
                backgroundColor, false, refreshInterval);

        this.layers = layers;
        this.style = style;
        this.formatMime = TILE_FORMAT_TO_WMS_FORMAT_MIME.get(this.tileType);
        this.additionalParameters = additionalParameters;
        this.version = version;
        
        Projection p = MobileImageryRasterLayer2.getProjection(this.getSRID());
        if(p == null)
            p = EquirectangularMapProjection.INSTANCE;
        this.proj = p;

        // tiles are normalized into a uniform grid for purposes of aggregation;
        // the internal bounds will always be the full extents of the
        // projection
        this.bounds = new GeoBounds(this.proj.getMaxLatitude(),
                                    this.proj.getMinLongitude(),
                                    this.proj.getMinLatitude(),
                                    this.proj.getMaxLongitude());
        
        this.gridWidth = (this.proj.getSpatialReferenceID() == 4326) ? 2 : 1;
        this.gridHeight = 1;

        fixupURL();
    }

    /**
     * getUrl() simply appends query string arguments to the end of the URL. This function makes
     * sure the user-provided URL ends with ? or & (as appropriate) so that this appending works
     * properly.
     */
    private void fixupURL() {
        try {
            URL u = new URL(url);
            String queryString = u.getQuery();
            if (queryString == null)
                url = url + "?";
            else if (!queryString.isEmpty() && !queryString.endsWith("&"))
                url = url + "&";
        } catch (MalformedURLException mfe) {
            Log.d("CustomWmsMobacMapSource", "Malformed GetMap URL (" + url + "): "
                    + mfe.getMessage());
        }
    }

    @Override
    public GeoBounds getBounds() {
        return bounds;
    }

    @Override
    protected String getUrl(int zoom, int x, int y) {
        final PointD projUL = this.proj.forward(new GeoPoint(this.bounds.getNorth(), this.bounds.getWest()), null);
        final PointD projLR = this.proj.forward(new GeoPoint(this.bounds.getSouth(), this.bounds.getEast()), null);
        
        if(projUL == null || projLR == null)
            throw new IllegalStateException();
        
        final double dx = (projLR.x-projUL.x) / this.gridWidth;
        final double dy = (projUL.y-projLR.y) / this.gridHeight;
        
        final double south = projUL.y - ((dy / (1 << zoom)) * (y + 1));
        final double west = projUL.x + ((dx / (1 << zoom)) * x);
        final double north = projUL.y - ((dy / (1 << zoom)) * y);
        final double east = projUL.x + ((dx / (1 << zoom)) * (x + 1));

        boolean isVersionx3 =  version != null && (version.equals("1.3.1") || version.equals("1.3.0"));
        
        StringBuilder retval = new StringBuilder(url);
        retval.append("service=WMS&request=GetMap&layers=" + this.layers + "&" +
                (isVersionx3 ? "crs" : "srs") +"="
                + (isVersionx3 && srid == 4326 ?  "CRS:84" : "EPSG:" +String.valueOf(this.srid)));
        retval.append("&format=" + this.formatMime + "&width=" + String.valueOf(this.tileSize)
                + "&height=" + String.valueOf(this.tileSize));
        if (this.version != null)
            retval.append("&version=" + this.version);
        else
            // version is required according to WMS spec
            retval.append("&version=1.1.1");

        if (this.style != null)
            retval.append("&styles=" + this.style);
        else
            // style is required according to WMS spec; "default" is default
            retval.append("&styles=");

        if (this.additionalParameters != null)
            retval.append(additionalParameters);
        retval.append("&bbox=" + String.valueOf(west) + "," + String.valueOf(south) + ","
                + String.valueOf(east) + "," + String.valueOf(north));

        return retval.toString();
    }
}
