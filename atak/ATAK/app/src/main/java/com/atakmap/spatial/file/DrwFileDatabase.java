
package com.atakmap.spatial.file;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;

import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.elev.dt2.Dt2ElevationModel;
import com.atakmap.android.maps.AxisOfAdvance;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.SimpleRectangle;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.util.ArrayList;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DrwFileDatabase extends
        FileDatabase {
    private static final String TAG = "DrwFileDatabase";

    public static final String DRW_FILE_MIME_TYPE = "application/x-msaccess";
    private static final File DRW_DIRECTORY = FileSystemUtils
            .getItem(FileSystemUtils.OVERLAYS_DIRECTORY);
    public static final String DRW_CONTENT_TYPE = "DRW";
    private static final String ICON_PATH = "asset://icons/geojson.png";
    private final static String EXTENSION = ".drw";

    private static final int DARK_RED = Color.rgb(128, 0, 0);
    private static final int DARK_GREEN = Color.rgb(0, 128, 0);
    private static final int DARK_YELLOW = Color.rgb(128, 128, 0);
    private static final int DARK_BLUE = Color.rgb(0, 0, 128);
    private static final int DARK_MAGENTA = Color.rgb(128, 0, 128);
    private static final int DARK_CYAN = Color.rgb(0, 128, 128);
    private static final int MONEY_GREEN = Color.rgb(180, 220, 180);
    private static final int SKY_BLUE = Color.rgb(160, 200, 255);
    private static final int CREAM = Color.rgb(255, 240, 240);
    private static final Dt2ElevationModel _dem = Dt2ElevationModel
            .getInstance();

    public DrwFileDatabase(Context context, MapView view) {
        super(DATABASE_FILE, context, view);

    }

    @Override
    public boolean accept(File file) {
        String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
        return IOProviderFactory.isFile(file) && lc.endsWith(EXTENSION);
    }

    @Override
    public File getFileDirectory() {
        return DRW_DIRECTORY;
    }

    @Override
    protected String getFileMimeType() {
        return DRW_FILE_MIME_TYPE;
    }

    @Override
    protected String getIconPath() {
        return ICON_PATH;
    }

    @Override
    public String getContentType() {
        return DRW_CONTENT_TYPE;
    }

    @Override
    protected void processFile(File file, MapGroup fileGrp) {
        insertFromDrwFile(file, fileGrp);
    }

    private void insertFromDrwFile(File drwFile, MapGroup fileGrp) {
        if (!FileSystemUtils.isFile(drwFile))
            return;
        Envelope.Builder bounds = new Envelope.Builder();
        Database msaccessDb = null;
        try {
            DatabaseBuilder db = new DatabaseBuilder();
            db.setChannel(IOProviderFactory.getChannel(drwFile, "r"));
            db.setReadOnly(true);
            msaccessDb = db.open();
            Table msaccessTable = msaccessDb.getTable("Main");

            int currentItemNum = -1;
            String currentShpType = "";
            ArrayList<Map<String, Object>> currentShapeRows = null;

            if (msaccessTable != null) {
                // read each row and build shapes from groups of rows
                for (Map<String, Object> row : msaccessTable) {
                    Integer itemNum = (Integer) row.get("ItemNum");
                    if (itemNum == null)
                        continue;

                    // if new shape number
                    if (!itemNum.equals(currentItemNum)) {
                        // process previous shape
                        if (currentItemNum != -1) {
                            MapItem pmi = processShape(drwFile,
                                    currentShapeRows,
                                    currentShpType);
                            if (pmi != null) {
                                addMapItem(pmi, fileGrp, bounds);
                                addText(pmi);
                            }
                        }

                        // init next shape
                        currentShpType = "";
                        currentItemNum = itemNum;
                        currentShapeRows = new ArrayList<>();
                    }

                    // update shape type if not set yet
                    if (currentShpType.equals("")) {
                        String dataString = (String) row.get("Data");
                        if (dataString != null) { // can be null, so keep trying
                            currentShpType = dataString.substring(0, 2);
                        }
                    }

                    // add current row
                    if (currentShapeRows != null)
                        currentShapeRows.add(row);
                }
            }

            final MapItem item = processShape(drwFile, currentShapeRows,
                    currentShpType);
            // process last shape
            if (item != null) {
                item.setClickable(true);
                addMapItem(item, fileGrp, bounds);
                addText(item);
            }

        } catch (Exception e) {
            Log.e(TAG,
                    "Error parsing/reading MS Access table or DRW file: "
                            + drwFile,
                    e);
            SpatialDbContentSource.notifyUserOfError(drwFile,
                    "DRW file invalid", "DRW file invalid");
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
                drwFile, fileGrp, bounds.build(), getContentType(),
                getFileMimeType()));
    }

    private void addMapItem(MapItem item, MapGroup grp, Envelope.Builder b) {
        addMapItem(item, grp);
        if (item instanceof Shape) {
            GeoBounds bounds = ((Shape) item).getBounds(null);
            b.add(bounds.getWest(), bounds.getSouth());
            b.add(bounds.getEast(), bounds.getNorth());
        } else if (item instanceof PointMapItem) {
            GeoPoint point = ((PointMapItem) item).getPoint();
            b.add(point.getLongitude(), point.getLatitude());
        }
    }

    private MapItem processShape(File file,
            ArrayList<Map<String, Object>> shapeRows,
            String currentShpType) {
        switch (currentShpType) {
            case "08": // axis of advance
                return handleShpAxis(shapeRows);
            case "01": // line
                return handleShpLine(file, shapeRows);
            case "03": // oval
                return handleShpEllipse(file, shapeRows);
            case "07": // rectangle
                return handleShpRect(file, shapeRows);
            case "04": // text
                return handleShpText(file, shapeRows);
            default: // currentShpType never set or unrecognized
                Log.w(TAG, "Unrecognized DRW shape '" + currentShpType + "'");
                return null;
        }
    }

    private static Shape handleShpAxis(List<Map<String, Object>> shapeRows) {
        final AxisOfAdvance shp = new AxisOfAdvance(UUID.randomUUID()
                .toString());

        for (int i = 0; i < shapeRows.size(); i++) {
            Short dataType = (Short) shapeRows.get(i).get("DataType");
            String data = (String) shapeRows.get(i).get("Data");
            if (dataType == null || data == null)
                continue;

            if (dataType == 1) { // type
                shp.setStrokeWeight(Double.parseDouble(data.substring(2, 4)));
                shp.setFillStyle(Integer.parseInt(data.substring(5, 6)));// .setStyle(shp.getStyle()|Shape.STYLE_FILLED_MASK);
                shp.setWidthRatio(Double.parseDouble(data.substring(6, 11)));
                if (data.startsWith("Y", 11)) {
                    shp.setCrossed(true);
                }

            } else if (dataType == 12) { // line color
                int c = getColorFromFVColor(data.substring(0, 3));
                shp.setStrokeColor(c);
                shp.setFillColor(getFill(c));

            } else if (dataType == 3) { // moveto
                shp.setNock(convDrwPtToGeoPt(data));

            } else if (dataType == 4) { // lineto
                shp.setHead(convDrwPtToGeoPt(data));
            }
        }

        return shp;
    }

    private static Shape handleShpLine(File file,
            ArrayList<Map<String, Object>> shapeRows) {
        final EditablePolyline shp = new EditablePolyline(MapView.getMapView(),
                UUID.randomUUID().toString()) {
            protected String getShapeMenu() {
                return "";
            }

            protected String getLineMenu() {
                return "";
            }
        };
        shp.setMetaBoolean("removable", false);
        shp.setTouchable(true);
        shp.setClickable(true);
        ArrayList<GeoPointMetaData> pts = new ArrayList<>(2);

        String title = "";
        String colorString = "255";

        for (int i = 0; i < shapeRows.size(); i++) {
            Short dataType = (Short) shapeRows.get(i).get("DataType");
            String data = (String) shapeRows.get(i).get("Data");

            if (dataType == null || data == null)
                continue;

            if (dataType == 1) { // type
                if (data.startsWith("Y", 11)) { // if it's a polygon
                    shp.setStyle(shp.getStyle() | Polyline.STYLE_CLOSED_MASK);
                }

                if ((!data.startsWith("0", 7))
                        && ((shp.getStyle()
                                & Polyline.STYLE_CLOSED_MASK) > 0)) {
                    // if it's filled in some way and is marked as a polygon
                    shp.setStyle(shp.getStyle() | Shape.STYLE_FILLED_MASK);

                }
                shp.setStrokeWeight(Double.parseDouble(data.substring(2, 4)));
            } else if (dataType == 3) { // moveto
                pts.add(convDrwPtToGeoPt(data));

            } else if (dataType == 4) { // lineto
                pts.add(convDrwPtToGeoPt(data));

            } else if (dataType == 12) { // line/fill color
                colorString = getColorStringFromFVColor(data.substring(0, 3));

                shp.setStrokeColor(getColorFromFVColor(data.substring(0, 3)));
                shp.setFillColor(getFill(getColorFromFVColor(data.substring(3,
                        6))));

            } else if (dataType == 15) { // line param (isFixed, lineWidth)
                // if !fixed, use lineWidth
                if (data.startsWith("N")) {
                    // shp.setStrokeWeight(Double.parseDouble(data.substring(1, 9)));
                    // //line width in decimal meters!!!
                }
            } else if (dataType == 24) { // text
                if (title.length() == 0) {
                    title = data;
                    shp.setTitle(data);
                }
            }
        }

        // The DRW lied to use, this is not closed.
        if (pts.size() == 1) {
            shp.setStyle(EditablePolyline.STYLE_STROKE_MASK);
        } else if (!pts.get(0).get().equals(pts.get(pts.size() - 1).get())) {
            shp.setStyle(EditablePolyline.STYLE_STROKE_MASK);
        }

        int s = pts.size();

        GeoPointMetaData[] ptsAr = new GeoPointMetaData[s];
        for (int i = 0; i < s; i++) {
            ptsAr[i] = pts.get(i);
        }

        shp.setPoints(ptsAr);

        if (FileSystemUtils.isEmpty(shp.getTitle())) {
            Log.w(TAG, "No title for line");
            title = getName(file, colorString, "line");
            shp.setTitle(title);
            shp.setMetaBoolean("skipDrwTxt", true);
        }

        //Log.d(TAG, "Line created: " + title);
        return shp;
    }

    private static Shape handleShpEllipse(File file,
            ArrayList<Map<String, Object>> shapeRows) {
        final Ellipse shp = new Ellipse(UUID.randomUUID().toString());
        String title = "";
        String colorString = "255";

        for (int i = 0; i < shapeRows.size(); i++) {
            Short dataType = (Short) shapeRows.get(i).get("DataType");
            String data = (String) shapeRows.get(i).get("Data");

            if (dataType == null || data == null)
                continue;

            if (dataType == 1) { // type
                // 030300009.269260
                //Log.d(TAG, "debug info: " + data);
                //Log.d(TAG, "debug info: " + data.length());
                //Log.d(TAG, "debug info: " + data.substring(6,9));
                //Log.d(TAG, "debug info: " + data.substring(9,14));
                //Log.d(TAG, "debug info: " + data.substring(14,19));
                //Log.d(TAG, "debug info: " + data.substring(2,4));
                //Log.d(TAG, "debug info: " + data.substring(5,6));
                shp.setAngle(Double.parseDouble(data.substring(6, 9)));
                final double h = Double.parseDouble(data.substring(9, 14));
                final double w = Double.parseDouble(data.substring(14, 19));
                if (h > 10000 || w > 10000) {
                    Log.d(TAG, "debug info: height width > 10000 (bad data): "
                            + h + " " + w);
                    return null;
                }

                shp.setMinorRadius(1000.0 * h);
                shp.setMajorRadius(1000.0 * w);

                shp.setStrokeWeight(Double.parseDouble(data.substring(2, 4)));
                shp.setFillStyle(Integer.parseInt(data.substring(5, 6)));// .setStyle(shp.getStyle()|Shape.STYLE_FILLED_MASK);

            } else if (dataType == 12) { // line/fill color
                colorString = getColorStringFromFVColor(data.substring(0, 3));
                shp.setStrokeColor(getColorFromFVColor(data.substring(0, 3)));
                shp.setFillColor(getFill(getColorFromFVColor(data.substring(3,
                        6))));

            } else if (dataType == 5) { // center
                shp.setCenter(convDrwPtToGeoPt(data));
            } else if (dataType == 24) { // text
                if (title.length() == 0) {
                    title = data;
                    shp.setTitle(data);
                }
            }
        }
        if (FileSystemUtils.isEmpty(shp.getTitle())) {
            Log.w(TAG, "No title for ellipse");
            title = getName(file, colorString, "ellipse");
            shp.setTitle(title);
            shp.setMetaBoolean("skipDrwTxt", true);
        }

        return shp;
    }

    private static String getName(File file, String color, String type) {
        if (file == null || FileSystemUtils.isEmpty(type))
            return "";

        return FileSystemUtils.stripExtension(file.getName()) + " " + color
                + " " + type;
    }

    private static Shape handleShpRect(File file,
            ArrayList<Map<String, Object>> shapeRows) {
        final SimpleRectangle shp = new SimpleRectangle(
                UUID.randomUUID().toString());
        shp.setMetaString("menu", "menus/immutable_shape.xml");
        shp.setClickable(true);
        String title = "";

        String colorString = "255";
        for (int i = 0; i < shapeRows.size(); i++) {
            Short dataType = (Short) shapeRows.get(i).get("DataType");
            String data = (String) shapeRows.get(i).get("Data");

            if (dataType == null || data == null)
                continue;

            if (dataType == 1) { // type
                shp.setAngle(Double.parseDouble(data.substring(6, 9)));

                shp.setHeight(
                        1000.0 * Double.parseDouble(data.substring(9, 14)));
                shp.setWidth(
                        1000.0 * Double.parseDouble(data.substring(14, 19)));

                shp.setStrokeWeight(Double.parseDouble(data.substring(2, 4)));
                shp.setFillStyle(Integer.parseInt(data.substring(5, 6)));// .setStyle(shp.getStyle()|Shape.STYLE_FILLED_MASK);

            } else if (dataType == 12) { // line/fill color
                colorString = getColorStringFromFVColor(data.substring(0, 3));
                shp.setStrokeColor(getColorFromFVColor(data.substring(0, 3)));
                shp.setFillColor(getFill(getColorFromFVColor(data.substring(3,
                        6))));

            } else if (dataType == 5) { // center
                shp.setCenter(convDrwPtToGeoPt(data));
            } else if (dataType == 24) { // text
                if (title.length() == 0) {
                    title = data;
                    shp.setTitle(data);
                }
            }
        }
        if (FileSystemUtils.isEmpty(shp.getTitle())) {
            Log.w(TAG, "No title for rectangle");
            title = getName(file, colorString, "rectangle");
            shp.setTitle(title);
            shp.setMetaBoolean("skipDrwTxt", true);
        }

        return shp;
    }

    private static void addText(MapItem shp) {
        String name = shp.getTitle();
        if (shp.getMetaBoolean("skipDrwTxt", false))
            return;

        //Log.d(TAG, "name: " + name);

        if (name != null && shp instanceof Shape) {
            //Log.d(TAG, "shape: " + name);
            final Marker txt = new Marker(UUID.randomUUID().toString());
            txt.setTitle(name.replaceAll("\n", " "));
            txt.setPoint(((Shape) shp).getCenter());
            txt.setMetaBoolean("addToObjList", false);
            shp.addOnVisibleChangedListener(
                    new MapItem.OnVisibleChangedListener() {
                        @Override
                        public void onVisibleChanged(MapItem item) {
                            txt.setVisible(item.getVisible());
                        }
                    });

            //Log.d(TAG, "center: " + txt.getPoint());
            shp.getGroup().addItem(txt);
        }
    }

    private static Marker handleShpText(File file,
            ArrayList<Map<String, Object>> shapeRows) {
        final Marker shp = new Marker(UUID.randomUUID().toString());
        shp.setMetaString("menu", "menus/immutable_point.xml");
        shp.setMarkerHitBounds(new Rect(-32, -32, 32, 32));
        shp.setClickable(true);
        String colorString = "255";

        for (int i = 0; i < shapeRows.size(); i++) {
            Short dataType = (Short) shapeRows.get(i).get("DataType");
            String data = (String) shapeRows.get(i).get("Data");

            if (dataType == null || data == null)
                continue;

            if (dataType == 6 && !data.equals("null")) { // title
                shp.setTitle(shp.getTitle() + data);
            } else if (dataType == 5) { // center
                shp.setPoint(convDrwPtToGeoPt(data));
            }
        }

        if (shp.getPoint() == null) {
            Log.w(TAG, "Failed to load shp Text");
            return null;
        }

        if (FileSystemUtils.isEmpty(shp.getTitle())) {
            Log.w(TAG, "No title for pt");
            String title = getName(file, colorString, "point");
            shp.setTitle(title);
            shp.setMetaBoolean("skipDrwTxt", true);
        }

        //Log.d(TAG, "Point created: " + shp.getTitle());
        return shp;
    }

    private static GeoPointMetaData convDrwPtToGeoPt(final String drwPoint) {
        double lat = Double.parseDouble(drwPoint.substring(1, 10));
        double lon = Double.parseDouble(drwPoint.substring(11, 21));

        if (drwPoint.charAt(0) == 'S') {
            lat *= -1;
        }

        if (drwPoint.charAt(10) == 'W') {
            lon *= -1;
        }

        try {
            return _dem.queryPoint(lat, lon);
        } catch (Exception e) {
            return GeoPointMetaData.wrap(new GeoPoint(lat, lon));
        }
    }

    private static int getFill(int color) {
        return Color.argb(50,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int getColorFromFVColor(String fvcolor) {
        switch (fvcolor) {
            case "000": // black
                return Color.BLACK;
            case "001": // dark red
                return DARK_RED;
            case "002": // dark green
                return DARK_GREEN;
            case "003": // dark yellow
                return DARK_YELLOW;
            case "004": // dark blue
                return DARK_BLUE;
            case "005": // dark magenta
                return DARK_MAGENTA;
            case "006": // dark cyan
                return DARK_CYAN;
            case "007": // light gray
                return Color.LTGRAY;
            case "008": // money green
                return MONEY_GREEN;
            case "009": // sky blue
                return SKY_BLUE;
            case "246": // cream
                return CREAM;
            case "247": // medium gray
                return Color.GRAY;
            case "248": // dark gray
                return Color.DKGRAY;
            case "249": // red
                return Color.RED;
            case "250": // green
                return Color.GREEN;
            case "251": // yellow
                return Color.YELLOW;
            case "252": // blue
                return Color.BLUE;
            case "253": // magenta
                return Color.MAGENTA;
            case "254": // cyan
                return Color.CYAN;
            case "255": // white
                return Color.WHITE;
        }
        return -1;
    }

    private static String getColorStringFromFVColor(String fvcolor) {
        switch (fvcolor) {
            case "000":
                return "black";
            case "001":
                return "dark red";
            case "002":
                return "dark green";
            case "003":
                return "dark yellow";
            case "004":
                return "dark blue";
            case "005":
                return "dark magenta";
            case "006":
                return "dark cyan";
            case "007":
                return "light gray";
            case "008":
                return "money green";
            case "009":
                return "sky blue";
            case "246":
                return "cream";
            case "247":
                return "medium gray";
            case "248":
                return "dark gray";
            case "249":
                return "red";
            case "250":
                return "green";
            case "251":
                return "yellow";
            case "252":
                return "blue";
            case "253":
                return "magenta";
            case "254":
                return "cyan";
            case "255":
                return "white";
        }
        return "white";
    }
}
