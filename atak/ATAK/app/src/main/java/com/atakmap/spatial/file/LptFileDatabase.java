
package com.atakmap.spatial.file;

import android.content.Context;
import android.content.res.AssetManager;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

public class LptFileDatabase extends FileDatabase {
    private static final String TAG = "LptFileDatabase";

    public static final String LPT_FILE_MIME_TYPE = "application/x-msaccess";
    public static final File LPT_DIRECTORY = FileSystemUtils
            .getItem(FileSystemUtils.OVERLAYS_DIRECTORY);
    public static final String LPT_CONTENT_TYPE = "LPT";
    private final static String EXTENSION = ".lpt";

    final private static String ICON_PATH = "asset://icons/geojson.png";
    final private static Map<String, String> iconmap = new HashMap<>();

    private static final String DEFAULT_URI_ICON = "asset://lpticons/lpt_blue_local.ico";

    public LptFileDatabase(Context context, MapView view) {
        super(DATABASE_FILE, context, view);
    }

    @Override
    public boolean accept(File file) {
        String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
        return IOProviderFactory.isFile(file) && lc.endsWith(EXTENSION);
    }

    @Override
    public File getFileDirectory() {
        return LPT_DIRECTORY;
    }

    @Override
    protected String getFileMimeType() {
        return LPT_FILE_MIME_TYPE;
    }

    @Override
    protected String getIconPath() {
        return ICON_PATH;
    }

    @Override
    public String getContentType() {
        return LPT_CONTENT_TYPE;
    }

    @Override
    protected void processFile(File file, MapGroup fileGrp) {
        insertFromLptFile(file, fileGrp);
    }

    private void insertFromLptFile(final File lptFile, final MapGroup fileGrp) {
        if (!FileSystemUtils.isFile(lptFile))
            return;
        Envelope.Builder bounds = new Envelope.Builder();
        Database msaccessDb = null;
        try(FileChannel channel = IOProviderFactory.getChannel(lptFile, "r")) {
            DatabaseBuilder db = new DatabaseBuilder();
            db.setChannel(channel);
            db.setReadOnly(true);
            msaccessDb = db.open();

            Table msaccessTable = msaccessDb.getTable("Points");
            if (msaccessTable != null) {
                // read each row
                for (Map<String, Object> row : msaccessTable) {
                    String name = (String) row.get("ID");
                    Double lat = (Double) row.get("Latitude");
                    Double lng = (Double) row.get("Longitude");
                    Short alt = (Short) row.get("Elevation");
                    String link = (String) row.get("Link_Name");
                    String icon = (String) row.get("Icon_Name");
                    // String grpName = (String) row.get("Group_Name");

                    //Log.d(TAG, name + " has Icon URI: ##" + icon
                    //        + "##, from link: ##" + link + "##");

                    if (link == null || link.trim().isEmpty()) {
                        link = icon;
                        if (icon == null || icon.trim().isEmpty()) {
                            link = "blue_local";
                        }
                    }

                    link = link.replaceAll("[\\p{Punct} ]", "_")
                            .toLowerCase(
                                    LocaleUtil.getCurrent());

                    final String iconUri = mapUri("asset://lpticons/lpt_"
                            + link + ".ico");
                    //Log.d(TAG, name + " has Icon URI: " + iconUri
                    //        + ", from link: " + link);

                    //Log.d(TAG, "Got LPT point: (" + lat + ", " + lng + ")");
                    if (lat == null || lng == null)
                        continue;
                    if (alt == null)
                        alt = Short.MIN_VALUE;

                    insertLptPoint(lptFile, name, iconUri, lat, lng, alt,
                            fileGrp);
                    bounds.add(lng, lat);
                }
            }
        } catch (Exception e) {
            Log.e(TAG,
                    "Error parsing/reading MS Access table or LPT file: "
                            + lptFile,
                    e);
            SpatialDbContentSource.notifyUserOfError(lptFile,
                    "LPT file invalid", "LPT file invalid");
            return;
        } finally {
            if (msaccessDb != null) {
                try {
                    msaccessDb.close();
                } catch (Exception ignored) {
                }
            }

        }

        // Add to content handler
        this.contentResolver.addHandler(new FileDatabaseContentHandler(view,
                lptFile, fileGrp, bounds.build(), getContentType(),
                getFileMimeType()));
    }

    /** 
     * Verifies the lpt icon specified by the iconUri exists.   If the icon uri is invalid
     * then a default icon is provided.
     */
    private String mapUri(final String iconUri) {
        String uri = iconmap.get(iconUri);
        if (uri != null) {
            return uri;
        }

        InputStream is = null;
        try {
            AssetManager am = context.getAssets();
            is = am.open(iconUri.replace("asset://", ""));
            iconmap.put(iconUri, iconUri);
            return iconUri;
        } catch (IOException fnfe) {
            iconmap.put(iconUri, DEFAULT_URI_ICON);
            return DEFAULT_URI_ICON;
        } finally {
            if (is != null)
                try {
                    is.close();
                } catch (Exception ignored) {
                }
        }
    }

    private void insertLptPoint(File lptFile,
            String name, String iconUri, double lat, double lng,
            double alt, MapGroup fileGrp) {

        if (iconUri != null) {
            String ogrStyle = "SYMBOL(id:\"" + iconUri +
                    "\")";

            String wktGeom = "POINT(" + lng + " " + lat + ")";

            FeatureDataSource.FeatureDefinition feature = new FeatureDataSource.FeatureDefinition();
            feature.name = name;
            feature.rawGeom = wktGeom;
            feature.geomCoding = FeatureDataSource.FeatureDefinition.GEOM_WKT;
            feature.rawStyle = ogrStyle;
            feature.styleCoding = FeatureDataSource.FeatureDefinition.STYLE_OGR;

            createMapItem(feature, fileGrp);
        }
    }
}
