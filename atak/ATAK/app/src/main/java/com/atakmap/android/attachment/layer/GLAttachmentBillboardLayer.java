
package com.atakmap.android.attachment.layer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Pair;

import com.atakmap.android.image.ExifHelper;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.AttachmentWatcher;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws attachment thumbnails as "billboards" above map items
 */
public class GLAttachmentBillboardLayer implements GLLayer3,
        AttachmentWatcher.Listener,
        MapItem.OnGroupChangedListener,
        MapItem.OnVisibleChangedListener,
        MapItem.OnHeightChangedListener,
        PointMapItem.OnPointChangedListener,
        MapEventDispatcher.MapEventDispatchListener,
        Layer.OnLayerVisibleChangedListener {

    private static final String TAG = "GLAttachmentBillboardLayer";

    private static final int LABEL_HEIGHT = 36;
    private static final int ICON_HEIGHT_DEFAULT = 32;

    public static final GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (object.second instanceof AttachmentBillboardLayer)
                return new GLAttachmentBillboardLayer(object.first,
                        (AttachmentBillboardLayer) object.second);
            return null;
        }
    };

    // Attachments root directory
    private static final File ATT_DIR = FileSystemUtils.getItem("attachments");

    // Sorts files by last modified time (oldest to newest)
    private static final Comparator<File> SORT_MOD = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
            return Long.compare(o1.lastModified(), o2.lastModified());
        }
    };

    // Sorts thumbnails by their distance from the camera
    public static final Comparator<AttachmentThumb> SORT_Z = new Comparator<AttachmentThumb>() {
        @Override
        public int compare(AttachmentThumb o1, AttachmentThumb o2) {
            return Double.compare(o2.point.z, o1.point.z);
        }
    };

    // Attachment thumbnail width and height in pixels
    private static final int THUMB_SIZE = 512;

    // Attachment thumbnail struct
    private static class AttachmentThumb {
        MapItem item;
        GLTexture texture;
        int width, height;
        PointD point = new PointD(0, 0, 0);
        float scale;
    }

    private final MapView _mapView;
    private final MapRenderer _renderer;
    private final AttachmentBillboardLayer _layer;
    private final Map<String, AttachmentThumb> _textures = new HashMap<>();
    private final List<AttachmentThumb> _drawList = new ArrayList<>();
    private final MutableGeoBounds _mapBounds = new MutableGeoBounds();

    private GLImage _attImage = null;
    private double _distanceMeters = Double.NaN;
    private float _displayScale = 1;
    private boolean _visible;
    private double _terrainValue;
    private int _terrainVersion = 0;

    public GLAttachmentBillboardLayer(MapRenderer renderer,
            AttachmentBillboardLayer layer) {
        _renderer = renderer;
        _layer = layer;
        _mapView = MapView.getMapView();
        _visible = _layer.isVisible();
        _layer.setGLSubject(this);
    }

    /**
     * Set the distance from the self marker billboards should be rendered
     * @param distanceMeters Distance in meters (NaN to always render)
     */
    void setSelfMarkerDistance(double distanceMeters) {
        if (Double.compare(distanceMeters, _distanceMeters) != 0) {
            _distanceMeters = distanceMeters;
            _renderer.requestRefresh();
        }
    }

    /**
     * Set the relative scaling used for markers and labels
     * @param scale Relative scaling
     */
    void setRelativeScaling(float scale) {
        if (Float.compare(scale, _displayScale) != 0) {
            _displayScale = scale;
            _renderer.requestRefresh();
        }
    }

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        _visible = layer.isVisible();
        _renderer.requestRefresh();
    }

    @Override
    public void start() {
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);
        AttachmentWatcher.getInstance().addListener(this);
        _layer.addOnLayerVisibleChangedListener(this);
    }

    @Override
    public void stop() {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, this);
        AttachmentWatcher.getInstance().removeListener(this);
        _layer.removeOnLayerVisibleChangedListener(this);

        for (Map.Entry<String, AttachmentThumb> e : _textures.entrySet()) {
            AttachmentThumb thumb = e.getValue();
            thumb.texture.release();
        }
        _textures.clear();
    }

    @Override
    public Layer getSubject() {
        return _layer;
    }

    @Override
    public void draw(GLMapView view) {
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!_visible || !MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES))
            return;

        _mapBounds.set(view.currentPass.southBound,
                view.currentPass.westBound,
                view.currentPass.northBound,
                view.currentPass.eastBound);
        Marker self = _mapView.getSelfMarker();
        boolean checkDistance = self != null && self.getGroup() != null
                && !Double.isNaN(_distanceMeters);

        PointD camLoc = view.currentPass.scene.camera.location;

        // Establish list of thumbnails to draw
        _drawList.clear();
        for (AttachmentThumb thumb : _textures.values()) {
            MapItem item = thumb.item;

            if (item instanceof AnchoredMapItem)
                item = ((AnchoredMapItem) item).getAnchorItem();

            GeoPoint point = null;
            if (item instanceof PointMapItem)
                point = ((PointMapItem) item).getPoint();
            else if (item instanceof Shape)
                point = ((Shape) item).getGeoPointMetaData().get();

            // No point found or point not in PVS
            if (point == null || !_mapBounds.contains(point))
                continue;

            // Distance check (if applicable)
            if (checkDistance) {
                // Marker needs to be within a certain distance threshold of the
                // self marker
                GeoPoint selfPoint = self.getPoint();
                double distance = GeoCalculations.distanceTo(selfPoint, point);
                if (distance > _distanceMeters)
                    continue;

                // Marker also needs to be in front of the marker direction-wise
                double bearing = GeoCalculations.bearingTo(selfPoint, point);
                double heading = self.getTrackHeading();
                double diff = Math.abs(bearing - heading);
                if (diff > 90 && diff < 270)
                    continue;
            }

            boolean tilted = Double.compare(view.currentScene.drawTilt, 0) != 0;

            // Get icon and label height for y-offset
            int iconSize = 0;
            int labelSize = 0;
            if (item instanceof Marker) {
                Marker marker = (Marker) item;

                // Marker icon height offset
                if (marker.getIconVisibility() != Marker.ICON_GONE) {
                    Icon icon = marker.getIcon();
                    iconSize = icon.getHeight();
                    if (iconSize < 0)
                        iconSize = ICON_HEIGHT_DEFAULT;
                }

                // Label height offset
                if (tilted && marker
                        .getTextRenderFlag() != Marker.TEXT_STATE_NEVER_SHOW) {
                    labelSize += LABEL_HEIGHT;
                    if (!FileSystemUtils.isEmpty(marker.getSummary()))
                        labelSize += LABEL_HEIGHT;
                    if (iconSize > 0)
                        labelSize += LABEL_HEIGHT;
                }

                iconSize *= _displayScale;
                labelSize *= _displayScale;
            }

            view.scratch.geo.set(point);

            // Get terrain value
            final int renderTerrainVersion = view.getTerrainVersion();
            if (_terrainVersion != renderTerrainVersion) {
                _terrainValue = view.getTerrainMeshElevation(
                        point.getLatitude(), point.getLongitude());
                _terrainVersion = renderTerrainVersion;
            }

            // Calculate altitude based on various parameters
            // derived from GLMarker2 implementation
            double alt = point.getAltitude();
            boolean nadirClamp = _layer.getClampToGroundAtNadir() && !tilted;
            Feature.AltitudeMode altMode = nadirClamp
                    ? AltitudeMode.ClampToGround
                    : item.getAltitudeMode();
            if (!GeoPoint.isAltitudeValid(alt)
                    || altMode == Feature.AltitudeMode.ClampToGround)
                alt = _terrainValue;
            else if (altMode == Feature.AltitudeMode.Relative)
                alt += _terrainValue;

            double height = item.getHeight();
            if (!nadirClamp && !Double.isNaN(height))
                alt += height;

            view.scratch.geo.set(alt);

            // Get the thumbnail point on the screen
            AbstractGLMapItem2.forward(view, view.scratch.geo, thumb.point,
                    0d, _terrainValue);

            // Calculate thumbnail size based on the distance from the camera
            view.currentPass.scene.mapProjection.forward(view.scratch.geo,
                    view.scratch.pointD);
            thumb.scale = (float) Math.max(100, 1000 - Math.abs(
                    view.scratch.pointD.z - camLoc.z)) / 1000f;

            float yOffset = (thumb.height / 2f) * thumb.scale + iconSize
                    + labelSize;
            thumb.point.y += yOffset;

            _drawList.add(thumb);
        }

        // Sort thumbnails by distance from camera
        Collections.sort(_drawList, SORT_Z);

        // Draw thumbnails in correct order
        for (AttachmentThumb thumb : _drawList) {
            if (_attImage == null) {
                _attImage = new GLImage(0, THUMB_SIZE, THUMB_SIZE,
                        0, 0,
                        THUMB_SIZE, THUMB_SIZE,
                        -THUMB_SIZE / 2f, -THUMB_SIZE / 2f,
                        THUMB_SIZE, THUMB_SIZE);
            }

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef((float) thumb.point.x,
                    (float) thumb.point.y, (float) thumb.point.z);
            GLES20FixedPipeline.glScalef(thumb.scale, -thumb.scale, 1f);
            _attImage.setTexId(thumb.texture.getTexId());
            _attImage.draw();
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public void release() {
    }

    @Override
    public void onAttachmentAdded(File attFile) {
        File parent = attFile.getParentFile();
        if (parent == null)
            return;

        String uid = parent.getName();
        MapItem item = _mapView.getMapItem(uid);
        if (item == null)
            return;

        rebuildThumbnail(item);
    }

    @Override
    public void onAttachmentRemoved(File attFile) {
        onAttachmentAdded(attFile);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String eventType = event.getType();
        if (eventType.equals(MapEvent.ITEM_ADDED)) {
            MapItem item = event.getItem();
            File attDir = new File(ATT_DIR, item.getUID());
            if (attDir.exists())
                rebuildThumbnail(item);
        }
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(final MapItem item, MapGroup group) {
        _renderer.queueEvent(new Runnable() {
            @Override
            public void run() {
                stopObserving(item);
            }
        });
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        _renderer.requestRefresh();
    }

    @Override
    public void onHeightChanged(MapItem item) {
        _renderer.requestRefresh();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        _renderer.requestRefresh();
    }

    private void startObserving(MapItem item) {
        item.addOnGroupChangedListener(this);
        item.addOnVisibleChangedListener(this);
        item.addOnHeightChangedListener(this);
        if (item instanceof AnchoredMapItem)
            item = ((AnchoredMapItem) item).getAnchorItem();
        if (item instanceof PointMapItem)
            ((PointMapItem) item).addOnPointChangedListener(this);
    }

    private void stopObserving(MapItem item) {
        String uid = item.getUID();
        AttachmentThumb thumb = _textures.remove(uid);
        if (thumb != null)
            thumb.texture.release();
        item.removeOnGroupChangedListener(this);
        item.removeOnVisibleChangedListener(this);
        item.removeOnHeightChangedListener(this);
        if (item instanceof AnchoredMapItem)
            item = ((AnchoredMapItem) item).getAnchorItem();
        if (item instanceof PointMapItem)
            ((PointMapItem) item).removeOnPointChangedListener(this);
    }

    /**
     * Request a rebuild of an attachment thumbnail for a given map item
     * @param item Map item
     */
    private void rebuildThumbnail(final MapItem item) {
        _renderer.queueEvent(new Runnable() {
            @Override
            public void run() {
                String uid = item.getUID();
                AttachmentThumb existing = _textures.get(uid);
                AttachmentThumb thumb = generateThumbnail(uid, existing);
                if (thumb == null) {
                    stopObserving(item);
                } else if (existing == null) {
                    thumb.item = item;
                    _textures.put(uid, thumb);
                    startObserving(item);
                }
            }
        });
    }

    private AttachmentThumb generateThumbnail(String uid,
            AttachmentThumb thumb) {
        List<File> atts = AttachmentManager.getAttachments(uid);
        if (FileSystemUtils.isEmpty(atts))
            return null;

        Collections.sort(atts, SORT_MOD);

        // Determine if all the images can be fit into a square tile
        boolean crop = false;
        int totalW = 0, totalH = 0;
        float lastAR = -1;
        Matrix mat = new Matrix();
        List<Bitmap> images = new ArrayList<>();
        for (File file : atts) {
            String path = file.getAbsolutePath();

            // Import smallest appropriate version of bitmap
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0)
                continue;

            if (w > THUMB_SIZE && h > THUMB_SIZE)
                opts.inSampleSize = 1 << (int) (com.atakmap.android.math.MathUtils
                        .log2(Math.min(w, h))
                        - com.atakmap.android.math.MathUtils.log2(THUMB_SIZE));
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null)
                continue;

            // Rotate bitmap if we need to
            int rot = ExifHelper.getImageOrientation(file);
            if (rot != 0) {
                mat.reset();
                mat.postRotate(rot);
                bmp = Bitmap.createBitmap(bmp, 0, 0,
                        bmp.getWidth(), bmp.getHeight(),
                        mat, false);
            }

            images.add(bmp);

            // Compare current aspect ratio to last to check if size has changed
            // If images do not have matching aspect ratios or there are more
            // than 2 images then use cropping
            w = bmp.getWidth();
            h = bmp.getHeight();
            float ar = (float) w / h;
            int size = images.size();
            if (size > 2 || size > 1 && Float.compare(ar, lastAR) != 0) {
                totalW = THUMB_SIZE;
                totalH = THUMB_SIZE;
                crop = true;
            } else {
                if (ar > 1) {
                    totalW = Math.max(totalW, w);
                    totalH += h;
                } else {
                    totalW += w;
                    totalH = Math.max(totalH, h);
                }
            }
            lastAR = ar;

            // Max out at 4 images
            if (size >= 4)
                break;
        }

        // No images found
        if (images.isEmpty())
            return null;

        // Organize images in canvas
        Bitmap texBmp = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(texBmp);

        // Setup crop canvas
        Bitmap cropBmp = null;
        Canvas cropCan = null;
        if (crop) {
            cropBmp = Bitmap.createBitmap(THUMB_SIZE / 2, THUMB_SIZE / 2,
                    Bitmap.Config.ARGB_8888);
            cropCan = new Canvas(cropBmp);
        }

        float totalS = 1f;
        if (totalW > texBmp.getWidth() || totalH > texBmp.getHeight()) {
            if (totalW > totalH)
                totalS = (float) texBmp.getWidth() / totalW;
            else
                totalS = (float) texBmp.getHeight() / totalH;
            totalW *= totalS;
            totalH *= totalS;
        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setStrokeWidth(2);
        float leftMargin = (texBmp.getWidth() - totalW) / 2f;
        float left = leftMargin, top = (texBmp.getHeight() - totalH) / 2f;
        for (int i = 0; i < images.size(); i++) {
            Bitmap bmp = images.get(i);
            int w = bmp.getWidth(), h = bmp.getHeight();

            w *= totalS;
            h *= totalS;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);

            // Need to crop the image to a square
            float ar = (float) w / h;
            mat.reset();
            if (crop && cropBmp != null) {
                if (ar > 1) {
                    float scale = (float) cropBmp.getHeight() / h;
                    mat.postScale(scale, scale);
                    mat.postTranslate((cropBmp.getWidth() - (scale * w)) / 2,
                            0);
                } else {
                    float scale = (float) cropBmp.getWidth() / w;
                    mat.postScale(scale, scale);
                    mat.postTranslate(0,
                            (cropBmp.getHeight() - (scale * h)) / 2);
                }
                cropCan.drawBitmap(bmp, mat, paint);
                bmp = cropBmp;
                w = bmp.getWidth();
                h = bmp.getHeight();
                mat.reset();
            }

            mat.postScale(totalS, totalS);
            mat.postTranslate(left, top);

            // Draw image
            can.drawBitmap(bmp, mat, paint);

            // Draw outline over image
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.BLACK);
            can.drawRect(left, top, left + w, top + h, paint);

            if (i == 1) {
                left = leftMargin;
                top += h;
            } else {
                if (w > h)
                    top += h;
                else
                    left += w;
            }
        }

        // Update attachment thumbnail
        if (thumb == null) {
            thumb = new AttachmentThumb();
            thumb.texture = new GLTexture(texBmp.getWidth(), texBmp.getHeight(),
                    Bitmap.Config.ARGB_8888);
        }
        thumb.width = totalW;
        thumb.height = totalH;
        thumb.texture.load(texBmp);

        return thumb;
    }
}
