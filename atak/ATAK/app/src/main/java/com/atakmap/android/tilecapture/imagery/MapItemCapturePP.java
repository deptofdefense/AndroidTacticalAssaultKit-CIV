
package com.atakmap.android.tilecapture.imagery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;

import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.imagecapture.Capturable;
import com.atakmap.android.imagecapture.CustomGrid;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.tilecapture.TileCapture;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capture post-processing for map items and imagery
 */
public class MapItemCapturePP extends ImageryCapturePP {

    protected static final Comparator<MapItem> MAP_ITEM_COMP = new Comparator<MapItem>() {
        @Override
        public int compare(MapItem item1, MapItem item2) {
            return Double.compare(item2.getZOrder(), item1.getZOrder());
        }
    };

    protected final CustomGrid _grid;
    protected final Map<String, Bundle> _pointData = new HashMap<>();
    protected final Map<String, Bitmap> _bitmapCache = new HashMap<>();
    protected List<MapItem> _items;
    protected FOVFilter _fovFilter;

    // Maximum number of features to query
    protected int _featureQueryLimit = 0;

    public MapItemCapturePP(MapView mapView, TileCapture tc,
            ImageryCaptureParams cp) {
        super(mapView, tc, cp);

        _fovFilter = new FOVFilter(_bounds);
        _grid = GridLinesMapComponent.getCustomGrid();
        init();
    }

    public void init() {
        _items = getItems(_bounds);
        Collections.sort(_items, MAP_ITEM_COMP);
        calcForwardPositions();
    }

    /**
     * Load icon bitmap from cache
     * Cache is released after window is closed
     * @param uri Icon uri as a string
     * @return Icon bitmap
     */
    @Override
    public Bitmap loadBitmap(String uri) {
        if (uri != null) {
            if (_bitmapCache.containsKey(uri))
                return _bitmapCache.get(uri);
            Bitmap b = ATAKUtilities.getUriBitmap(uri);
            if (b != null)
                _bitmapCache.put(uri, b);
            return b;
        }
        return null;
    }

    /**
     * Draw map items on top of the imagery
     * @param can Canvas to draw to
     * @return True if handled
     */
    @Override
    public boolean drawElements(Canvas can) {
        super.drawElements(can);

        // Draw grid
        if (_grid.isValid()) {
            if (snapToGrid())
                drawFittedGrid();
            else
                _grid.drawCanvas(this, _pointData.get(_grid.getName()));
        }

        // Draw all other map items
        for (MapItem mi : _items)
            drawMapItem(mi);

        return true;
    }

    /**
     * Draw a map item to the imagery bitmap
     * @param mi Map item
     */
    protected void drawMapItem(MapItem mi) {
        // Reset paint style
        resetPaint();
        if (mi instanceof Capturable) {
            Bundle pData = _pointData.get(mi.getUID());
            if (pData != null)
                ((Capturable) mi).drawCanvas(this, pData);
        }
    }

    /**
     * Draw fitted grid to canvas
     */
    protected void drawFittedGrid() {
        if (!_grid.isValid() || !_grid.isVisible())
            return;
        Canvas can = getCanvas();
        Paint paint = getPaint();
        Path path = getPath();
        float lineWeight = getLineWeight();

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getThemeColor(_grid.getColor()));
        paint.setStrokeWidth(_grid.getStrokeWeight() * lineWeight);

        // Draw grid lines
        double spacing = _grid.getSpacing();
        double rangeX = getHorizontalRange() / spacing;
        double rangeY = getVerticalRange() / spacing;
        int colCount = (int) Math.ceil(rangeX);
        int rowCount = (int) Math.ceil(rangeY);
        float celSize = (float) (getWidth() / rangeX);
        for (int i = 0; i <= colCount; i++) {
            float x = Math.min(i * celSize, getWidth());
            path.moveTo(x, 0);
            path.lineTo(x, getHeight());
        }
        for (int i = 0; i <= rowCount; i++) {
            float y = Math.min(i * celSize, getHeight());
            path.moveTo(0, y);
            path.lineTo(getWidth(), y);
        }
        can.drawPath(path, paint);
        path.reset();
    }

    public double getHorizontalRange() {
        return _grid.getNumColumns() * _grid.getSpacing();
    }

    public double getVerticalRange() {
        return _grid.getNumRows() * _grid.getSpacing();
    }

    /**
     * Needs to be called right when capture is started
     */
    public void calcForwardPositions() {
        _pointData.clear();

        if (_grid != null && _grid.isValid() && _grid.isVisible())
            _pointData.put(_grid.getName(), _grid.preDrawCanvas(this));

        // Save marker screen positions (only for markers within bounds
        for (int i = 0; i < _items.size(); i++) {
            MapItem mi = _items.get(i);
            if (mi == null || !mi.getVisible())
                continue;
            String uid = mi.getUID();
            if (mi instanceof Capturable) {
                if (_fovFilter.accept(mi)) {
                    Bundle data = ((Capturable) mi).preDrawCanvas(this);
                    if (data != null)
                        _pointData.put(uid, data);
                }
            }
        }
    }

    public Set<MapItem> getAllItems(MapGroup group, FOVFilter filter) {
        Set<MapItem> all = new HashSet<>();
        for (MapItem mi : group.getItems()) {
            if (mi.getVisible() && (filter == null || filter.accept(mi)))
                all.add(mi);
        }
        for (MapGroup mg : group.getChildGroups())
            all.addAll(getAllItems(mg, filter));

        return all;
    }

    public List<MapItem> getItems(GeoBounds bounds) {
        FOVFilter filter = new FOVFilter(bounds);
        Set<MapItem> ret = getAllItems(_mapView.getRootGroup(), filter);

        // Add features
        MapOverlay fo = _mapView.getMapOverlayManager()
                .getOverlay("fileoverlays");
        if (fo instanceof MapOverlayParent) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("visibleOnly", "true");
            if (_featureQueryLimit > 0)
                metadata.put("limit", String.valueOf(_featureQueryLimit));
            for (MapOverlay ov : ((MapOverlayParent) fo).getOverlays()) {
                DeepMapItemQuery query = ov.getQueryFunction();
                if (query == null)
                    continue;
                Collection<MapItem> items = query.deepFindItems(
                        bounds, metadata);
                ret.addAll(items);
            }
        }

        return new ArrayList<>(ret);
    }
}
