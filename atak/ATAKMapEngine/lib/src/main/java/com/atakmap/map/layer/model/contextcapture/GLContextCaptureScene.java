package com.atakmap.map.layer.model.contextcapture;

import android.graphics.BitmapFactory;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.lang.Objects;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPoint;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.SceneObjectControl;
import com.atakmap.map.layer.model.opengl.GLSceneSpi;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.Tessellate;
import com.atakmap.util.ConfigOptions;

import com.atakmap.util.zip.IoUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

/** @deprecated PROTOTYPE CODE; SUBJECT TO REMOVAL AT ANY TIME; DO NOT CREATE DIRECT DEPENDENCIES */
@Deprecated
@DeprecatedApi(since = "4.1")
public final class GLContextCaptureScene implements GLMapRenderable2, Controls {
    public final static GLSceneSpi SPI = new GLSceneSpi() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLMapRenderable2 create(MapRenderer ctx, ModelInfo info, String cacheDir) {
            if(info == null)
                return null;
            if(!Objects.equals(info.type, "ContextCapture"))
                return null;
            return new GLContextCaptureScene(ctx, info, cacheDir);
        }
    };

    private final static String TAG = "GLContextCaptureScene";

    final MapRenderer context;
    final ModelInfo info;
    final String cacheDir;

    Envelope aabb;

    TileGrid tiles;

    Thread initializer;

    Collection<MapControl> controls;

    DatasetIndicator indicator = new DatasetIndicator();

    TileNodeLoader loader;

    public GLContextCaptureScene(MapRenderer ctx, ModelInfo info, String cacheDir) {
        this.context = ctx;
        this.info = info;
        this.cacheDir = cacheDir;

        this.controls = new ArrayList<>(2);
        this.controls.add(new SceneControlImpl());
        this.controls.add(new HitTestControlImpl());
    }

    @Override
    public void draw(GLMapView view, int renderPass) {


        // init tile grid, if necessary
        synchronized(this) {
            if(this.tiles == null) {
                if (this.initializer == null) {
                    this.initializer = new Thread(new Initializer());
                    this.initializer.setName("GLContextCaptureScene-initializer");
                    this.initializer.setPriority(Thread.NORM_PRIORITY);
                    this.initializer.start();
                }
                indicator.draw(view, renderPass);
                return;
            }
        }

        if(this.loader == null)
            this.loader = new TileNodeLoader(3);

        // XXX - can tile AOI be quickly computed???

        // compute and draw tiles in view
        for(Map<Long, GLContextCaptureTile> segment : tiles.tiles.values()) {
            for(GLTileNode tile : segment.values()) {
                // XXX - more efficient testing

                // test in view
                GLTileNode.RenderVisibility renderable = tile.isRenderable(view);
                if (renderable == GLTileNode.RenderVisibility.None) {
                    this.loader.cancel(tile);
                    if (tile.hasLODs())
                        tile.unloadLODs();
                    else
                        tile.release();
                } else {
                    final boolean prefetch = (renderable == GLTileNode.RenderVisibility.Prefetch);
                    if (!tile.isLoaded(view) && !loader.isQueued(tile, prefetch))
                        loader.enqueue(tile, tile.prepareLoadContext(view), prefetch);

                    // draw
                    if (!prefetch)
                        tile.draw(view, renderPass);
                }
            }
        }

        indicator.draw(view, renderPass);
    }

    @Override
    public void release() {
        initializer = null;
        // XXX - wait initializer join

        if(tiles != null) {
            if(tiles.mounted)
                ZipVirtualFile.unmountArchive(new File(info.uri));
            if(tiles.tiles != null) {
                for(Map<Long, GLContextCaptureTile> segment : tiles.tiles.values())
                    for(GLTileNode tile : segment.values())
                        tile.release();
            }
            tiles = null;
        }

        if(this.loader != null) {
            this.loader.cancelAll();
        }

        indicator.release();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        for(Object ctrl : this.controls)
            if(controlClazz.isAssignableFrom(ctrl.getClass()))
                return controlClazz.cast(ctrl);
        return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.addAll(this.controls);
    }

    static final class TileGrid {
        public boolean mounted;
        int minTileX;
        int maxTileX;
        int minTileY;
        int maxTileY;
        int minTileZoom;
        int maxTileZoom;
        /** AABB for tiles in model CS */
        Envelope aabb;
        boolean lod;

        Map<String, Map<Long, GLContextCaptureTile>> tiles = new HashMap<>();
    }

    class Initializer implements Runnable {

        @Override
        public void run() {
            final Thread initThread = Thread.currentThread();

            long si = System.currentTimeMillis();
            long sgi = 0L;
            long egi = 0L;
            long sbi = 0L;
            long ebi = 0L;

            ZipFile zip = null;
            try {
                TileGrid grid = null;
                try {
                    //grid = loadMetadata(new File(cacheDir, "metadata.json"), info);
                    if(grid != null) {
                        ZipVirtualFile.mountArchive(new File(info.uri));
                        grid.mounted = true;
                    }
                } catch(Throwable ignored) {}

                Log.i("GLContextCaptureScene", "Loaded metadata from cache [" + (grid != null) + "]");
                if(grid == null) {
                    sgi = System.currentTimeMillis();
                    zip = new ZipFile(info.uri);

                    Pattern lodTilePattern = Pattern.compile("(.*[\\\\/])?([\\w\\W]+)[\\\\/]Tile_[\\+\\-]\\d+_[\\+\\-]\\d+[\\\\\\/]Tile_([\\+\\-]\\d+)_([\\+\\-]\\d+)_L(\\d+)(_[0123]+)?\\.obj");
                    Pattern tilePattern = Pattern.compile("(.*[\\\\/])?([\\w\\W]+)[\\\\/]Tile_[\\+\\-]\\d+_[\\+\\-]\\d+[\\\\\\/]Tile_([\\+\\-]\\d+)_([\\+\\-]\\d+).obj");
                    Enumeration entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        if (initializer != initThread)
                            break;

                        ZipEntry entry = (ZipEntry) entries.nextElement();
                        final String entryName = entry.getName();
                        if(!entryName.endsWith(".obj"))
                            continue;

                        Matcher m;
                        if(grid == null || grid.lod) {
                            m = lodTilePattern.matcher(entry.getName());
                            if (m.matches()) {
                                final int x = Integer.parseInt(m.group(3));
                                final int y = Integer.parseInt(m.group(4));
                                final int z = Integer.parseInt(m.group(5));
                                final long key = ((long) y << 32L) | ((long) x & 0xFFFFFFFFL);

                                if (grid == null) {
                                    grid = new TileGrid();
                                    grid.minTileX = x;
                                    grid.minTileY = y;
                                    grid.maxTileX = x;
                                    grid.maxTileY = y;
                                    grid.minTileZoom = z;
                                    grid.maxTileZoom = z;
                                    grid.lod = true;
                                } else {
                                    if (x < grid.minTileX)
                                        grid.minTileX = x;
                                    else if (x > grid.maxTileX)
                                        grid.maxTileX = x;
                                    if (y < grid.minTileY)
                                        grid.minTileY = y;
                                    else if (y > grid.maxTileY)
                                        grid.maxTileY = y;
                                    if (z < grid.minTileZoom)
                                        grid.minTileZoom = z;
                                    else if (z > grid.maxTileZoom)
                                        grid.maxTileZoom = z;
                                }

                                Map<Long, GLContextCaptureTile> segment = grid.tiles.get(m.group(2));
                                if(segment == null) {
                                    segment = new HashMap<>();
                                    grid.tiles.put(m.group(2), segment);
                                }

                                if (!segment.containsKey(key)) {
                                    int lastBSlash = entry.getName().lastIndexOf('\\');
                                    int lastFSlash = entry.getName().lastIndexOf('/');
                                    final String lodBaseDir = entry.getName().substring(0, Math.max(lastFSlash, lastBSlash) + 1);
                                    segment.put(key, new GLContextCaptureTile(info, lodBaseDir, x, y));
                                }
                            }
                        }

                        if(grid == null || !grid.lod) {
                            m = tilePattern.matcher(entry.getName());
                            if (m.matches()) {
                                final int x = Integer.parseInt(m.group(3));
                                final int y = Integer.parseInt(m.group(4));
                                final long key = ((long) y << 32L) | ((long) x & 0xFFFFFFFFL);

                                if (grid == null) {
                                    grid = new TileGrid();
                                    grid.minTileX = x;
                                    grid.minTileY = y;
                                    grid.maxTileX = x;
                                    grid.maxTileY = y;
                                    grid.minTileZoom = -1;
                                    grid.maxTileZoom = -1;
                                    grid.lod = false;
                                } else {
                                    if (x < grid.minTileX)
                                        grid.minTileX = x;
                                    else if (x > grid.maxTileX)
                                        grid.maxTileX = x;
                                    if (y < grid.minTileY)
                                        grid.minTileY = y;
                                    else if (y > grid.maxTileY)
                                        grid.maxTileY = y;
                                }

                                Map<Long, GLContextCaptureTile> segment = grid.tiles.get(m.group(2));
                                if(segment == null) {
                                    segment = new HashMap<>();
                                    grid.tiles.put(m.group(2), segment);
                                }

                                if (!segment.containsKey(key)) {
                                    int lastBSlash = entry.getName().lastIndexOf('\\');
                                    int lastFSlash = entry.getName().lastIndexOf('/');
                                    final String lodBaseDir = entry.getName().substring(0, Math.max(lastFSlash, lastBSlash) + 1);
                                    segment.put(key, new GLContextCaptureTile(info, lodBaseDir, x, y));
                                }
                            }
                        }
                    }
egi = System.currentTimeMillis();

                    if(grid == null) {
                        Log.w("GLContextCaptureScene", "Failed to construct tile grid for " + grid);
                        return;
                    }

                    {
                        long s = System.currentTimeMillis();
                        ZipVirtualFile.mountArchive(new File(info.uri));
                        grid.mounted = true;
                        long e = System.currentTimeMillis();

                        Log.i("GLContextCaptureScene", "Archive mounted in " + (e - s) + "ms");
                    }

sbi = System.currentTimeMillis();

                    do {
                        // XXX - locate tile in center
                        final int centerTileX = grid.minTileX + ((grid.maxTileX - grid.minTileX + 1) / 2);
                        final int centerTileY = grid.minTileY + ((grid.maxTileY - grid.minTileY + 1) / 2);



                        Envelope centerMbb = null;

                        // compute MBB as nominal tile MBB
                        for(Map<Long, GLContextCaptureTile> segment : grid.tiles.values()) {
                            GLContextCaptureTile tile = segment.get(((long) centerTileY << 32L) | ((long) centerTileX & 0xFFFFFFFFL));
                            if(tile == null)
                                continue;

                            for (int i = grid.minTileZoom; i <= grid.maxTileZoom; i++) {
                                final StringBuilder entryPath = new StringBuilder(tile.baseTileDir);
                                entryPath.append(tile.tileNameSpec);
                                if (grid.lod) {
                                    entryPath.append("_L");
                                    entryPath.append(String.format("%02d", i));
                                }
                                entryPath.append(".obj");

                                ZipEntry e = zip.getEntry(entryPath.toString());
                                if (e == null || e.getSize() < 1L)
                                    continue;

                                try {
                                    ModelInfo tileInfo = new ModelInfo();
                                    tileInfo.uri = info.uri + "/" + entryPath.toString().replace('\\', '/');
                                    Model m = ModelFactory.create(tileInfo);
                                    if (m != null) {
                                        centerMbb = new Envelope(m.getAABB());

                                        // estimate resolution for non-LOD and populate grid info
                                        if (!grid.lod) {
                                            // decode texture bounds
                                            BitmapFactory.Options img = new BitmapFactory.Options();
                                            findLargestTexture(m, img);

                                            // compute GSD
                                            final double pixels = Math.sqrt(img.outWidth * img.outHeight);
                                            final double meters = Math.sqrt((centerMbb.maxX - centerMbb.minX) * (centerMbb.maxY - centerMbb.minY));

                                            if (pixels == 0d) {
                                                // XXX - choose some high default ?
                                                grid.minTileZoom = 23;
                                                grid.maxTileZoom = 23;
                                            } else {
                                                final double gsd = meters / pixels;

                                                grid.minTileZoom = (int) Math.ceil(GLContextCaptureTile.gsd2lod(gsd));
                                                grid.maxTileZoom = (int) Math.ceil(GLContextCaptureTile.gsd2lod(gsd));
                                            }
                                        }

                                        m.dispose();
                                        break;
                                    }
                                } catch (Throwable t) {
                                    Log.w(TAG, "Failed to load tile " + entryPath + " for bounds discovery", t);
                                }
                            }
                            if(centerMbb != null)
                                break;
                        }

                        // estimate scene MBB based on grid extents and nominal bounds
                        if(centerMbb != null) {
                            final double tileWidth = (centerMbb.maxX-centerMbb.minX);
                            final double tileHeight = (centerMbb.maxY-centerMbb.minY);

                            grid.aabb = new Envelope(
                                    centerMbb.minX - (tileWidth*(centerTileX-grid.minTileX)),
                                    centerMbb.minY - (tileHeight*(centerTileY-grid.minTileY)),
                                    centerMbb.minZ-1000d,
                                    centerMbb.maxX + (tileWidth*(grid.maxTileX-centerTileX)),
                                    centerMbb.maxY + (tileHeight*(grid.maxTileY-centerTileY)),
                                    centerMbb.maxZ+1000d);

                            Log.i(TAG, "Estimating scene bounds for " + (new File(info.uri)).getName() + " numEntries=" + grid.tiles.size() + " center AABB=" + centerMbb + " tw=" + tileWidth + " th=" + tileHeight + " grid min{" + grid.minTileX + "," + grid.minTileY + "} max{" + grid.maxTileX + "," + grid.maxTileY + "} center {" + centerTileX + "," + centerTileY + "} scene AABB=" + grid.aabb);

                            for(Map<Long, GLContextCaptureTile> seg : grid.tiles.values()) {
                                for (GLContextCaptureTile est : seg.values()) {
                                    est.minLod = grid.minTileZoom;
                                    est.maxLod = grid.maxTileZoom;
                                    est.lods = grid.lod;

                                    est.mbb = new Envelope(
                                            centerMbb.minX + (tileWidth * (est.tileX - centerTileX)),
                                            centerMbb.minY + (tileHeight * (est.tileY - centerTileY)),
                                            centerMbb.minZ - 1000d,
                                            centerMbb.maxX + (tileWidth * (est.tileX - centerTileX)),
                                            centerMbb.maxY + (tileHeight * (est.tileY - centerTileY)),
                                            centerMbb.maxZ + 1000d);
                                    ModelInfo wgs84 = new ModelInfo();
                                    wgs84.srid = 4326;
                                    Models.transform(est.mbb, info, est.mbb, wgs84);
                                }
                            }
                        }
                    } while(false);

ebi = System.currentTimeMillis();

                    try {
                        saveMetadata(new File(cacheDir, "metadata.json"), grid);
                    } catch(Throwable ignored) {}
                }

                // initialization is complete
                synchronized(GLContextCaptureScene.this) {
                    if(initializer != initThread || grid.aabb == null) {
                        ZipVirtualFile.unmountArchive(new File(info.uri));
                        return;
                    }

                    // convert scene MBB to WGS84 for dataset AABB
                    ModelInfo wgs84 = new ModelInfo();
                    wgs84.srid = 4326;
                    Envelope scenembb = new Envelope(0d, 0d, 0d, 0d, 0d, 0d);
                    Models.transform(grid.aabb, info, scenembb, wgs84);
                    aabb = new Envelope(scenembb);
                    

                    Log.i(TAG, "Completed scene initialization for " + (new File(info.uri)).getName() + " numEntries=" + grid.tiles.size() + " grid min{" + grid.minTileX + "," + grid.minTileY + "} max{" + grid.maxTileX + "," + grid.maxTileY + "}");
                    tiles = grid;
                    initializer = null;
                }

                // dispatch bounds update -- AABB is in scene space
                getControl(SceneControlImpl.class).dispatchUpdate(new Envelope(grid.aabb));

                context.requestRefresh();
            } catch(IOException e) {
                Log.w(TAG, "Failed to initialize model from " + info.uri + " [" + IOProviderFactory.exists(new File(info.uri)) + "]", e);
            } finally {
                if(zip != null)
                    try {
                        zip.close();
                    } catch(IOException ignored) {}
            }

            long ei = System.currentTimeMillis();

            Log.i(TAG, "Initialized " + info.name + " in " + (ei-si) + "ms [" + (egi-sgi) + "|" + (ebi-sbi) + "]");
        }
    }

    class SceneControlImpl implements SceneObjectControl {

        Set<OnBoundsChangedListener> listeners = Collections.newSetFromMap(new IdentityHashMap<OnBoundsChangedListener, Boolean>());

        @Override
        public boolean isModifyAllowed() {
            return false;
        }

        @Override
        public void setLocation(GeoPoint location) {

        }

        @Override
        public void setLocalFrame(Matrix localFrame) {

        }

        @Override
        public void setSRID(int srid) {

        }

        @Override
        public void setAltitudeMode(ModelInfo.AltitudeMode mode) {

        }

        @Override
        public GeoPoint getLocation() {
            return null;
        }

        @Override
        public int getSRID() {
            return info.srid;
        }

        @Override
        public Matrix getLocalFrame() {
            return info.localFrame;
        }

        @Override
        public ModelInfo.AltitudeMode getAltitudeMode() {
            return info.altitudeMode;
        }

        @Override
        public void addOnSceneBoundsChangedListener(OnBoundsChangedListener l) {
            synchronized(listeners) {
                listeners.add(l);
            }
        }

        @Override
        public void removeOnSceneBoundsChangedListener(OnBoundsChangedListener l) {
            synchronized(listeners) {
                listeners.remove(l);
            }
        }

        void dispatchUpdate(Envelope mbb) {
            final TileGrid grid = tiles;
            final double minGsd;
            final double maxGsd;
            if(grid != null) {
                minGsd = GLContextCaptureTile.lod2gsd(Math.max(tiles.minTileZoom-1, 0));
                maxGsd = 0d;
            } else {
                minGsd = info.minDisplayResolution;
                maxGsd = info.maxDisplayResolution;
            }
            synchronized(listeners) {
                for(OnBoundsChangedListener l : listeners)
                    l.onBoundsChanged(mbb, minGsd, maxGsd);
            }
        }
    }

    class HitTestControlImpl implements ModelHitTestControl {

        @Override
        public boolean hitTest(float screenX, float screenY, GeoPoint result) {
            final TileGrid t = tiles;
            if(t == null)
                return false;

            final MapSceneModel sm = ((GLMapSurface)((GLMapView)context).getRenderContext()).getMapView().getSceneModel();

            boolean retval = false;
            GeoPoint isect_geo = GeoPoint.createMutable();
            PointD isect_xyz = new PointD(0d, 0d, 0d);
            double dist = Double.NaN;
            for(Map<Long, GLContextCaptureTile> segment : t.tiles.values()) {
                for (GLTileNode n : segment.values()) {
                    if (!(n instanceof Controls))
                        continue;
                    ModelHitTestControl ctrl = ((Controls) n).getControl(ModelHitTestControl.class);
                    if (ctrl == null)
                        continue;

                    // XXX - need to find closest hit
                    if(!ctrl.hitTest(screenX, screenY, isect_geo))
                        continue;

                    sm.forward(isect_geo, isect_xyz);
                    if(!retval || isect_xyz.z < dist) {
                        dist = isect_xyz.z;
                        result.set(isect_geo);
                        retval = true;
                    }
                }
            }

            return retval;
        }
    }

    static TileGrid loadMetadata(File file, ModelInfo info) throws IOException, JSONException {
        if(!IOProviderFactory.exists(file))
            return null;

        TileGrid grid = new TileGrid();

        final String s = FileSystemUtils.copyStreamToString(file);
        if(s == null)
            return null;

        JSONObject metadata = new JSONObject(s);
        final int version = metadata.optInt("version", -1);
        if(version != 0)
            return null;

        JSONObject gridJson = metadata.optJSONObject("TileGrid");
        if(gridJson == null)
            return null;

        grid.minTileZoom = gridJson.getInt("minTileZoom");
        grid.maxTileZoom = gridJson.getInt("maxTileZoom");
        grid.minTileX = gridJson.getInt("minTileX");
        grid.maxTileX = gridJson.getInt("maxTileX");
        grid.minTileY = gridJson.getInt("minTileY");
        grid.maxTileY = gridJson.getInt("maxTileY");

        JSONObject gridAabb = gridJson.optJSONObject("aabb");
        if(gridAabb != null) {
            grid.aabb = new Envelope(gridAabb.getDouble("minX"),
                    gridAabb.getDouble("minY"),
                    gridAabb.getDouble("minZ"),
                    gridAabb.getDouble("maxX"),
                    gridAabb.getDouble("maxY"),
                    gridAabb.getDouble("maxZ"));
        }

        JSONArray tilesJson = gridJson.optJSONArray("tiles");
        if(tilesJson == null)
            return null;

        for(int i = 0; i < tilesJson.length(); i++) {
            JSONObject tileJson = tilesJson.getJSONObject(i);
            final int tileX = tileJson.getInt("tileX");
            final int tileY = tileJson.getInt("tileY");
            final String baseLodDir = tileJson.getString("baseLodDir");
            final String segment = tileJson.getString("segment");

            GLContextCaptureTile tile = new GLContextCaptureTile(info, baseLodDir, tileX, tileY);
            tile.minLod = grid.minTileZoom;
            tile.maxLod = grid.maxTileZoom;

            JSONObject mbb = tileJson.optJSONObject("mbb");
            if(mbb != null)
                tile.mbb = new Envelope(mbb.getDouble("minX"),
                                        mbb.getDouble("minY"),
                                        mbb.getDouble("minZ"),
                                        mbb.getDouble("maxX"),
                                        mbb.getDouble("maxY"),
                                        mbb.getDouble("maxZ"));

            final long key = ((long)tileX<<32L)|((long)tileY&0xFFFFFFFFL);
            Map<Long, GLContextCaptureTile> seg = grid.tiles.get(segment);
            if(seg == null) {
                seg = new HashMap<>();
                grid.tiles.put(segment, seg);
            }
            seg.put(key, tile);
        }

        return grid;
    }

    static void saveMetadata(File file, TileGrid grid) throws IOException, JSONException {
        if(!IOProviderFactory.exists(file.getParentFile()))
            if (!IOProviderFactory.mkdirs(file.getParentFile())) {
               Log.e(TAG, "unable to make the parent directory for: " + file);
            }

        JSONObject metadata = new JSONObject();
        metadata.put("version", 0);

        JSONObject gridJson = new JSONObject();

        gridJson.put("minTileZoom", grid.minTileZoom);
        gridJson.put("maxTileZoom", grid.maxTileZoom);
        gridJson.put("minTileX", grid.minTileX);
        gridJson.put("maxTileX", grid.maxTileX);
        gridJson.put("minTileY", grid.minTileY);
        gridJson.put("maxTileY", grid.maxTileY);

        if(grid.aabb != null) {
            JSONObject gridAabb = new JSONObject();
            gridAabb.put("minX", grid.aabb.minX);
            gridAabb.put("minY", grid.aabb.minY);
            gridAabb.put("minZ", grid.aabb.minZ);
            gridAabb.put("maxX", grid.aabb.maxX);
            gridAabb.put("maxY", grid.aabb.maxY);
            gridAabb.put("maxZ", grid.aabb.maxZ);

            gridJson.put("aabb", gridAabb);
        }

        JSONObject[] tileJson = new JSONObject[grid.tiles.size()];
        int idx = 0;
        for (Map.Entry<String, Map<Long, GLContextCaptureTile>> entry : grid.tiles.entrySet()) {
            final String segment = entry.getKey();
            for(GLContextCaptureTile tile : entry.getValue().values()) {
                tileJson[idx] = new JSONObject();
                tileJson[idx].put("tileX", tile.tileX);
                tileJson[idx].put("tileY", tile.tileY);
                tileJson[idx].put("baseLodDir", tile.baseTileDir);
                tileJson[idx].put("segment", segment);

                if (tile.mbb != null) {
                    JSONObject mbb = new JSONObject();
                    mbb.put("minX", tile.mbb.minX);
                    mbb.put("minY", tile.mbb.minY);
                    mbb.put("minZ", tile.mbb.minZ);
                    mbb.put("maxX", tile.mbb.maxX);
                    mbb.put("maxY", tile.mbb.maxY);
                    mbb.put("maxZ", tile.mbb.maxZ);

                    tileJson[idx].put("mbb", mbb);
                }

                idx++;
            }
        }

        gridJson.put("tiles", new JSONArray(tileJson));
        metadata.put("TileGrid", gridJson);

        try (FileOutputStream stream = IOProviderFactory.getOutputStream(file)) {
            FileSystemUtils.write(stream, metadata.toString());
        }
    }

    class DatasetIndicator implements GLMapRenderable2 {

        GLBatchPoint marker;
        GLBatchLineString[] boundingBox;

        @Override
        public void draw(GLMapView view, int renderPass) {
            if(!MathUtils.hasBits(renderPass, getRenderPass()))
                return;

            if(tiles != null && GLContextCaptureTile.gsd2lod(view.drawMapResolution) >= tiles.minTileZoom)
                return;

            if(boundingBox == null && aabb != null) {
                boundingBox = new GLBatchLineString[12];
                // top
                boundingBox[0] = construct(view, aabb.minX, aabb.minY, aabb.maxZ, aabb.minX, aabb.maxY, aabb.maxZ);
                boundingBox[1] = construct(view, aabb.minX, aabb.maxY, aabb.maxZ, aabb.maxX, aabb.maxY, aabb.maxZ);
                boundingBox[2] = construct(view, aabb.maxX, aabb.maxY, aabb.maxZ, aabb.maxX, aabb.minY, aabb.maxZ);
                boundingBox[3] = construct(view, aabb.maxX, aabb.minY, aabb.maxZ, aabb.minX, aabb.minY, aabb.maxZ);
                // bottom
                boundingBox[4] = construct(view, aabb.minX, aabb.minY, aabb.minZ, aabb.minX, aabb.maxY, aabb.minZ);
                boundingBox[5] = construct(view, aabb.minX, aabb.maxY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.minZ);
                boundingBox[6] = construct(view, aabb.maxX, aabb.maxY, aabb.minZ, aabb.maxX, aabb.minY, aabb.minZ);
                boundingBox[7] = construct(view, aabb.maxX, aabb.minY, aabb.minZ, aabb.minX, aabb.minY, aabb.minZ);
                // corners
                boundingBox[8] = construct(view, aabb.minX, aabb.minY, aabb.minZ, aabb.minX, aabb.minY, aabb.maxZ);
                boundingBox[9] = construct(view, aabb.minX, aabb.maxY, aabb.minZ, aabb.minX, aabb.maxY, aabb.maxZ);
                boundingBox[10] = construct(view, aabb.maxX, aabb.maxY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
                boundingBox[11] = construct(view, aabb.maxX, aabb.minY, aabb.minZ, aabb.maxX, aabb.minY, aabb.maxZ);
            }

            // draw the bounding box once it becomes larger than the icon on the map
            if(boundingBox != null &&
               (aabb.maxX-aabb.minX)*GeoCalculations.approximateMetersPerDegreeLongitude(info.location.getLatitude()) > 32*view.drawMapResolution &&
               (aabb.maxY-aabb.minY)*GeoCalculations.approximateMetersPerDegreeLatitude(info.location.getLatitude()) > 32*view.drawMapResolution) {

                for(GLBatchLineString l : boundingBox)
                    l.draw(view);
            }

            // draw the icon
            if(marker == null) {
                marker = new GLBatchPoint(view);
                marker.init(0L, info.name);
                marker.setGeometry(new Point(info.location.getLongitude(), info.location.getLatitude(), info.location.getAltitude()));
                marker.setStyle(new IconPointStyle(-1, ConfigOptions.getOption("TAK.Engine.Model.default-icon", "null")));
            }
            marker.draw(view);
        }

        private GLBatchLineString construct(GLMapView view, double x0, double y0, double z0, double x1, double y1, double z1) {
            GLBatchLineString retval = new GLBatchLineString(view);
            retval.init(0L, null);
            retval.setTessellationEnabled(false);
            retval.setTessellationMode(Tessellate.Mode.XYZ);
            retval.setAltitudeMode(Feature.AltitudeMode.Absolute);

            LineString ls = new LineString(3);
            ls.addPoint(x0, y0, z0);
            ls.addPoint(x1, y1, z1);
            retval.setGeometry(ls);
            retval.setStyle(new BasicStrokeStyle(0xFF00FF00, 2f));

            return retval;
        }

        @Override
        public void release() {
            if(marker != null) {
                marker.release();
                marker = null;
            }
            if(boundingBox != null) {
                for(GLBatchLineString l : boundingBox)
                    if(l != null)
                        l.release();
                boundingBox = null;
            }
        }

        @Override
        public int getRenderPass() {
            return GLContextCaptureScene.this.getRenderPass();
        }
    }

    static void findLargestTexture(Model m, BitmapFactory.Options opts) {
        opts.inJustDecodeBounds = true;
        int maxWidth = 0;
        int maxHeight = 0;
        for(int i = 0; i < m.getNumMeshes(); i++) {
            Mesh mesh = m.getMesh(i);
            for(int j = 0; j < mesh.getNumMaterials(); j++) {
                Material mat = mesh.getMaterial(j);
                final String textureUri = mat.getTextureUri();
                if(textureUri == null)
                    continue;
                InputStream stream = null;
                try {
                    if (FileSystemUtils.isZipPath(textureUri))
                        stream = (new ZipVirtualFile(textureUri)).openStream();
                    else
                        stream = IOProviderFactory.getInputStream(new File(textureUri));
                    BitmapFactory.decodeStream(stream, null, opts);
                    if(opts.outWidth > maxWidth)
                        maxWidth = opts.outWidth;
                    if(opts.outHeight > maxHeight)
                        maxHeight = opts.outHeight;
                } catch(Throwable ignored) {
                } finally {
                    IoUtils.close(stream);
                }
            }
        }

        opts.outWidth = maxWidth;
        opts.outHeight = maxHeight;
    }


}
