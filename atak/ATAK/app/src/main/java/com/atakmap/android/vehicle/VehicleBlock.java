
package com.atakmap.android.vehicle;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Environment;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.EnterLocationDropDownReceiver;
import com.atakmap.android.user.icon.VehiclePallet;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opencsv.CSVReader;

public class VehicleBlock {
    public static final String TAG = "VehicleBlock";
    public static final String ASSET_PATH = "vehicle_blocks";
    public static final String TOOL_DIR = Environment
            .getExternalStorageDirectory() + File.separator
            + FileSystemUtils.ATAK_ROOT_DIRECTORY + File.separator
            + FileSystemUtils.TOOL_DATA_DIRECTORY;

    public static final int TYPE_OTHER = -1;
    public static final int TYPE_HELO = 0;
    public static final int TYPE_FWAC = 2;

    private static final int ICON_SIZE = 128;

    private static final Comparator<String> ALPHA_COMP = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return lhs.compareTo(rhs);
        }
    };

    private static final HashMap<String, VehicleBlock> _blockCache = new HashMap<>();
    private static VehiclePallet _blockPallet;

    // Instance fields
    private final String _name;
    private File _file;
    private Bitmap _icon;
    private List<PointF> _points = new ArrayList<>();
    private double _length, _width, _height;
    private int _type = TYPE_OTHER;
    private boolean _valid = false;

    // Helo aircraft only
    private double _contingency, _training, _brownOut, _rotorClearance;

    // Fixed-wing aircraft only
    private double _clearanceAC, _clearanceTX, _setbackPA, _setbackTX;

    private VehicleBlock(File blockFile) {
        String name = blockFile.getName();
        _name = name.substring(0, name.lastIndexOf("."));
        if (_blockCache.containsKey(name))
            loadFromCache();
        else {
            _file = blockFile;
            reload();
            _blockCache.put(_name, this);
        }
        initPallet();
    }

    /**
     * Get vehicle dimensions as array
     * @return Length, width, and height in meters
     */
    public double[] getDimensions() {
        return new double[] {
                _length, _width, _height
        };
    }

    public String getName() {
        return _name;
    }

    public File getFile() {
        return _file;
    }

    /**
     * Vehicle block validity
     * @return True if vehicle block was loaded successfully
     */
    public boolean isValid() {
        return _valid;
    }

    /**
     * Get vehicle radials as array
     * @return Associated vehicle radii in meters
     */
    public double[] getRadials() {
        if (_type == TYPE_HELO)
            return new double[] {
                    _rotorClearance, _contingency, _training, _brownOut
            };
        else if (_type == TYPE_FWAC)
            return new double[] {
                    _clearanceAC, _clearanceTX, _setbackPA, _setbackTX
            };
        return new double[0];
    }

    public int getType() {
        return _type;
    }

    /**
     * Generates an icon for this vehicle block
     * Icon is fully opaque, white, and at most 128x128 in size
     * @return Vehicle block icon
     */
    public Bitmap getIcon() {
        if (_icon != null || FileSystemUtils.isEmpty(_points))
            return _icon;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (PointF p : _points) {
            minX = Math.min(p.x, minX);
            minY = Math.min(p.y, minY);
            maxX = Math.max(p.x, maxX);
            maxY = Math.max(p.y, maxY);
        }
        float width = maxX - minX, length = maxY - minY;
        float sizeX, sizeY;
        if (width > length) {
            sizeX = ICON_SIZE;
            sizeY = ICON_SIZE * (length / width);
        } else {
            sizeX = ICON_SIZE * (width / length);
            sizeY = ICON_SIZE;
        }
        _icon = Bitmap.createBitmap(Math.round(sizeX),
                Math.round(sizeY), Bitmap.Config.ARGB_8888);
        Path path = new Path();
        for (int i = 0; i < _points.size(); i++) {
            PointF p = _points.get(i);
            float x = ((p.x - minX) / width) * sizeX;
            float y = ((p.y - minY) / length) * sizeY;
            if (i == 0)
                path.moveTo(x, y);
            else
                path.lineTo(x, y);
        }
        path.close();
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        new Canvas(_icon).drawPath(path, paint);
        return _icon;
    }

    private void loadFromCache() {
        VehicleBlock existing = _blockCache.get(_name);
        if (existing != null) {
            _file = existing._file;
            _valid = existing._valid;
            _points = existing._points;
            _length = existing._length;
            _width = existing._width;
            _height = existing._height;
            _type = existing._type;
            if (_type == TYPE_HELO) {
                _rotorClearance = existing._rotorClearance;
                _contingency = existing._contingency;
                _training = existing._training;
                _brownOut = existing._brownOut;
            } else if (_type == TYPE_FWAC) {
                _clearanceAC = existing._clearanceAC;
                _clearanceTX = existing._clearanceTX;
                _setbackPA = existing._setbackPA;
                _setbackTX = existing._setbackTX;
            }
        }
    }

    /**
     * Reload vehicle block information from file
     */
    private void reload() {
        if (_file == null)
            return;
        if (!IOProviderFactory.exists(_file)) {
            Log.e(TAG, "Vehicle block " + _file + " does not exist.");
            return;
        }
        _type = TYPE_OTHER;
        _points.clear();
        PointF lp = null;
        double la = Double.NaN;
        CSVReader csv = null;
        InputStream is = null;
        try {
            is = IOProviderFactory.getInputStream(_file);
            InputStreamReader isr = new InputStreamReader(is);
            csv = new CSVReader(isr);
            //Skip the header
            csv.readNext();
            String[] currentRow;
            while ((currentRow = csv.readNext()) != null) {
                if (currentRow[0].startsWith("#"))
                    continue;
                // Type is defined 1 line below the header
                if (currentRow[0].equals("HELO"))
                    _type = TYPE_HELO;
                else if (currentRow[0].equals("FWAC"))
                    _type = TYPE_FWAC;
                switch (currentRow.length) {
                    case 2:
                        // XY point offsets
                        PointF point = new PointF(
                                Float.parseFloat(currentRow[0]),
                                Float.parseFloat(currentRow[1]));
                        if (lp != null) {
                            double ang = CanvasHelper.angleTo(lp, point);
                            double dist = CanvasHelper.length(lp, point);
                            if (dist < 0.01) {
                                //Log.d(TAG, _name + ": ignoring dist < 1cm " + lp + " -> " + point);
                                break;
                            } else if (!Double.isNaN(la) && dist < 10
                                    && Math.abs(ang - la) < 1) {
                                //Log.d(TAG, _name + ": ignoring straight line " + lp + " -> " + point);
                                _points.remove(lp);
                                ang = CanvasHelper.angleTo(_points.get(
                                        _points.size() - 1), point);
                            }
                            la = ang;
                        }
                        _points.add(point);
                        lp = point;
                        break;
                    case 3:
                        // Dimensions
                        _length = Double.parseDouble(currentRow[0]);
                        _width = Double.parseDouble(currentRow[1]);
                        _height = Double.parseDouble(currentRow[2]);
                        break;
                    case 4:
                        if (_type == TYPE_HELO) {
                            _training = Double.parseDouble(currentRow[0]);
                            _contingency = Double.parseDouble(currentRow[1]);
                            _brownOut = Double.parseDouble(currentRow[2]);
                            _rotorClearance = Double.parseDouble(currentRow[3]);
                        } else if (_type == TYPE_FWAC) {
                            _clearanceAC = Double.parseDouble(currentRow[0]);
                            _clearanceTX = Double.parseDouble(currentRow[1]);
                            _setbackPA = Double.parseDouble(currentRow[2]);
                            _setbackTX = Double.parseDouble(currentRow[3]);
                        }
                        break;
                }
            }
            _valid = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read block " + _file.getAbsolutePath(), e);
        } finally {
            try {
                if (csv != null)
                    csv.close();
            } catch (IOException ignored) {
            }
            try {
                if (is != null)
                    is.close();
            } catch (IOException ignored) {
            }
        }
    }

    /* STATIC FIELDS */
    public static File getBlockDir() {
        return new File(TOOL_DIR, ASSET_PATH);
    }

    /**
     * Migrate old vehicle blocks over to new director, scan for any vehicles,
     * and create the pallet if needed
     */
    public static void init() {

        File blocksDir = getBlockDir();
        if (!IOProviderFactory.exists(blocksDir))
            IOProviderFactory.mkdirs(blocksDir);

        // Move legacy directory and remove old default vehicle blocks
        File legacyDir = new File(TOOL_DIR, "vehicles");
        if (IOProviderFactory.exists(legacyDir)) {

            // Remove contents that are default
            MapView mv = MapView.getMapView();
            AssetManager assets = mv.getContext().getAssets();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(assets.open(ASSET_PATH
                            + "/defaults.txt")))) {
                String line;
                while (!FileSystemUtils.isEmpty(line = reader.readLine())) {
                    File f = new File(legacyDir, line + ".block");
                    if (FileSystemUtils.isFile(f))
                        FileSystemUtils.deleteFile(f);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read defaults.txt", e);
            }

            // Move remaining blocks if any
            File[] files = IOProviderFactory.listFiles(legacyDir);
            if (files != null) {
                for (File f : files) {
                    // Skip empty directories
                    if (IOProviderFactory.isDirectory(f)
                            && FileSystemUtils
                                    .isEmpty(IOProviderFactory.list(f)))
                        continue;
                    FileSystemUtils.renameTo(f,
                            new File(blocksDir, f.getName()));
                }
            }

            // Delete old directory
            FileSystemUtils.deleteDirectory(legacyDir, false);
        }

        String[] blocks = getBlocks();
        if (!FileSystemUtils.isEmpty(blocks))
            initPallet();
    }

    /**
     * Get block file based on name
     * @param name Block name
     * @return Block file (note that blocks in sub-directories will
     *          be returned over blocks in the parent directories)
     */
    private static File getBlockFile(String name) {
        name = FileSystemUtils.sanitizeWithSpacesAndSlashes(name);

        if (_blockCache.containsKey(name))
            return _blockCache.get(name)._file;
        File f = getBlockFile(name, getBlockDir());
        if (f == null)
            f = new File(getBlockDir(), name + ".block");
        return f;
    }

    private static File getBlockFile(String name, File dir) {
        name = FileSystemUtils.sanitizeWithSpacesAndSlashes(name);

        if (!IOProviderFactory.exists(dir)
                || !IOProviderFactory.isDirectory(dir))
            return null;
        File[] files = IOProviderFactory.listFiles(dir);
        if (FileSystemUtils.isEmpty(files))
            return null;
        List<File> subDirs = new ArrayList<>();
        String fileName = name + ".block";
        File ret = null;
        for (File f : files) {
            if (ret == null && IOProviderFactory.isFile(f)
                    && f.getName().equals(fileName))
                ret = f;
            else if (IOProviderFactory.isDirectory(f))
                subDirs.add(f);
        }
        for (File subDir : subDirs) {
            File f = getBlockFile(name, subDir);
            if (f != null)
                ret = f;
        }
        return ret;
    }

    /**
     * Get block name based on file
     * @param block Block file
     * @return Block name
     */
    private static String getBlockName(File block) {
        String name = block.getName();
        return name.substring(0, name.lastIndexOf("."));
    }

    /**
     * Return array of block names within default block directory
     * @param incSubDirs True to include sub-directory blocks
     *                   False to only include blocks in the immediate dir
     * @return Array of block names
     */
    public static String[] getBlocks(File dir, boolean incSubDirs) {
        if (IOProviderFactory.exists(dir)
                && IOProviderFactory.isDirectory(dir)) {
            File[] blockFiles = IOProviderFactory.listFiles(dir);
            Set<String> blocks = new HashSet<>();
            if (!FileSystemUtils.isEmpty(blockFiles)) {
                for (File f : blockFiles) {
                    if (IOProviderFactory.isFile(f)) {
                        String name = f.getName();
                        if (name.endsWith(".block"))
                            blocks.add(getBlockName(f));
                    } else if (incSubDirs && IOProviderFactory.isDirectory(f))
                        blocks.addAll(Arrays.asList(getBlocks(f, true)));
                }
                return blocks.toArray(new String[0]);
            }
        }
        return new String[0];
    }

    /**
     * Get all blocks in the entire directory structure
     * @return Array of block names
     */
    public static String[] getBlocks() {
        String[] blocks = getBlocks(getBlockDir(), true);
        Arrays.sort(blocks, ALPHA_COMP);
        return blocks;
    }

    public static String[] getBlocks(String group) {
        String[] ret;
        if (FileSystemUtils.isEmpty(group)
                || group.equalsIgnoreCase("default"))
            ret = getBlocks(getBlockDir(), false);
        else {
            File subDir = new File(getBlockDir(), group);
            if (!IOProviderFactory.exists(subDir)
                    || !IOProviderFactory.isDirectory(subDir))
                return new String[0];
            ret = getBlocks(subDir, false);
        }
        Arrays.sort(ret, ALPHA_COMP);
        return ret;
    }

    public static String[] getGroups() {
        List<String> groups = new ArrayList<>();
        groups.add("default");
        File[] files = IOProviderFactory.listFiles(getBlockDir());
        if (!FileSystemUtils.isEmpty(files)) {
            for (File f : files) {
                if (IOProviderFactory.isDirectory(f))
                    groups.add(f.getName());
            }
        }
        Collections.sort(groups, ALPHA_COMP);
        return groups.toArray(new String[0]);
    }

    /**
     * Get vehicle block associated with name
     * @param name Block name
     * @return Vehicle block object
     */
    public static VehicleBlock getBlock(String name) {
        if (_blockCache.containsKey(name))
            return _blockCache.get(name);
        return new VehicleBlock(getBlockFile(name));
    }

    public static List<PointF> getBlockPoints(String name) {
        return getBlock(name)._points;
    }

    public static GeoPointMetaData[] buildPolyline(String blockName,
            GeoPoint center, double angle) {
        List<GeoPointMetaData> ret = new ArrayList<>();
        List<PointF> points = getBlockPoints(blockName);

        boolean closed = true;
        if (points.size() > 1) {
            PointF first = points.get(0), last = points.get(points.size() - 1);
            closed = first.equals(last.x, last.y);
        }

        // Offset points by average
        PointF avg = new PointF(0, 0);
        int pSize = points.size();
        if (closed)
            pSize--;
        double div_size = 1d / pSize;
        for (int i = 0; i < pSize; i++) {
            avg.x += points.get(i).x * div_size;
            avg.y += points.get(i).y * div_size;
        }

        // Convert to geopoints
        for (PointF p : points) {
            PointF nextPoint = new PointF(p.x - avg.x, p.y - avg.y);
            GeoPoint partial = GeoCalculations.pointAtDistance(center,
                    angle + 180, nextPoint.y);
            ret.add(new GeoPointMetaData(GeoCalculations
                    .pointAtDistance(partial, angle + 270, nextPoint.x)));
        }

        // Close shape if we need to
        if (!closed)
            ret.add(new GeoPointMetaData(ret.get(0)));

        return ret.toArray(new GeoPointMetaData[0]);
    }

    static double[] getBlockDimensions(String name) {
        return getBlock(name).getDimensions();
    }

    // For helicopters: { contingency, training, brown-out }
    // For planes/jets: { ac clearance, apron clearance,
    //          taxiway clearance, parking setback, taxiway setback }
    public static double[] getBlockRadials(String name) {
        return getBlock(name).getRadials();
    }

    private static void initPallet() {
        // Create and register vehicle outline pallet if we haven't done so
        if (_blockPallet == null) {
            MapView mv = MapView.getMapView();
            if (mv != null) {
                EnterLocationDropDownReceiver.getInstance(mv).addPallet(
                        _blockPallet = new VehiclePallet(), 4);
            }
        }
    }
}
