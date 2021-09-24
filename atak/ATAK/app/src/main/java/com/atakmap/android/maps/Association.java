
package com.atakmap.android.maps;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Visible link between two PointMapItems
 * <p>
 * Association has the following properties:
 * <li><b>link</b> defines type of primitive to draw between points</li>
 * <li><b>style</b> defines the style of the drawn link (solid, dotted, dashed)</li>
 * <li><b>firstItem</b> provides the first point in the link</li>
 * <li><b>secondItem</b> provides the second point in the link</li>
 * <li><b>color</b> defines the link draw color</li>
 * <li><b>strokeWidth</b> defines the link primitive line segment width</li>
 * </p>
 * <p>
 * Notable <i>GLMapComponent</i> (touch) behavior:
 * <li>if <b>firstItem</b> or <b>secondItem</b> is <i>null</i>, the link is not drawn</li>
 * </p>
 * 
 * 
 */
public class Association extends Shape implements AnchoredMapItem {

    /**
     * Show now visible link between the two objects
     */
    public static final int LINK_NONE = 0;

    /**
     * Show a line-segment link between the two objects
     */
    public static final int LINK_LINE = 1;

    /**
     * The link will be solid throughout
     */
    public static final int STYLE_SOLID = BASIC_LINE_STYLE_SOLID;

    /**
     * The link will be dotted
     */
    public static final int STYLE_DOTTED = BASIC_LINE_STYLE_DOTTED;

    /**
     * The link will be dashed
     */
    public static final int STYLE_DASHED = BASIC_LINE_STYLE_DASHED;

    public static final int STYLE_OUTLINED = BASIC_LINE_STYLE_OUTLINED;

    private static final String TAG = "Association";

    /**
     * Association <i>link property</i> listener
     * 
     * 
     */
    public interface OnLinkChangedListener {
        void onAssociationLinkChanged(Association association);
    }

    /**
     * Association style property listener
     * 
     * 
     */
    public interface OnStyleChangedListener {
        void onAssociationStyleChanged(Association association);
    }

    /**
     * Association firstItem property listener
     * 
     * 
     */
    public interface OnFirstItemChangedListener {
        void onFirstAssociationItemChanged(Association association,
                PointMapItem first);
    }

    /**
     * Association secondItem property listener
     * 
     * 
     */
    public interface OnSecondItemChangedListener {
        void onSecondAssociationItemChanged(Association association,
                PointMapItem second);
    }

    /**
     * Association color property listener
     * 
     * 
     */
    public interface OnColorChangedListener {
        void onAssociationColorChanged(Association association);
    }

    /**
     * Association strokeWeight property listener
     * 
     * 
     */
    public interface OnStrokeWeightChangedListener {
        void onAssociationStrokeWeightChanged(Association association);
    }

    public interface OnTextChangedListener {
        void onAssociationTextChanged(Association assoc);
    }

    public interface OnClampToGroundChangedListener {
        void onAssociationClampToGroundChanged(Association assoc);
    }

    public interface OnParentChangedListener {
        void onParentChanged(Association assoc, AssociationSet parent);
    }

    private final ConcurrentLinkedQueue<OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLinkChangedListener> _onLinkChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStyleChangedListener> _onStyleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFirstItemChangedListener> _onFirstItemChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnSecondItemChangedListener> _onSecondItemChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnColorChangedListener> _onColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStrokeWeightChangedListener> _onStrokeWeightChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnClampToGroundChangedListener> _onClampToGroundChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnParentChangedListener> _onParentChanged = new ConcurrentLinkedQueue<>();

    private Marker _marker = null;
    private PointMapItem _firstItem;
    private PointMapItem _secondItem;
    private int _style;
    private int _link;
    private int _color;
    private double _strokeWeight = 1d;
    private String _text = "";
    private boolean _clampToGround;
    private AssociationSet _parent;

    /**
     * Create an Association with no point items
     */
    public Association(final String uid) {
        this(null, null, uid);
    }

    public Association(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, null, null, uid);
    }

    /**
     * Create an Association given two point items
     * 
     * @param firstItem the first point item
     * @param secondItem the second point item
     */
    public Association(final PointMapItem firstItem,
            final PointMapItem secondItem,
            final String uid) {
        this(MapItem.createSerialId(),
                new DefaultMetaDataHolder(),
                firstItem,
                secondItem, uid);
    }

    public Association(final long serialId,
            final MetaDataHolder metadata,
            final PointMapItem firstItem,
            final PointMapItem secondItem,
            final String uid) {
        super(serialId, metadata, uid);

        _firstItem = firstItem;
        _secondItem = secondItem;
        _link = LINK_LINE;
        _style = STYLE_SOLID;

        // start out virtually under everything else.
        setZOrder(2000d);

        this.setMetaBoolean("addToObjList", false);

        _clampToGround = false;
    }

    /**
     * Get the link color
     * 
     * @return an argb packed color (@see Color)
     */
    public int getColor() {
        return _color;
    }

    /**
     * Get the first PointMapItem in the Assocaition
     * 
     * @return the first item
     */
    public PointMapItem getFirstItem() {
        return _firstItem;
    }

    /**
     * Get the second PointMapItem in the Association
     * 
     * @return the second item
     */
    public PointMapItem getSecondItem() {
        return _secondItem;
    }

    /**
     * Get the link type
     * 
     * @return LINK_NONE or LINK_LINE
     */
    public int getLink() {
        return _link;
    }

    /**
     * Get the link primitive style
     * 
     * @return STYLE_SOLID, STYLE_DOTTED, or STYLE_DASHED
     */
    @Override
    public int getStyle() {
        return _style;
    }

    /**
     * Add a 'link' property listener
     * 
     * @param l the listener
     */
    public void addOnLinkChangedListener(OnLinkChangedListener l) {
        _onLinkChanged.add(l);
    }

    /**
     * Remove a 'link' property listener
     * 
     * @param l the listener
     */
    public void removeOnLinkChangedListener(OnLinkChangedListener l) {
        _onLinkChanged.remove(l);
    }

    /**
     * Add a 'firstItem' property listener
     * 
     * @param l the listener
     */
    public void addOnFirstItemChangedListener(OnFirstItemChangedListener l) {
        _onFirstItemChanged.add(l);
    }

    /**
     * Remove a 'firstItem' property listener
     * 
     * @param l the listener
     */
    public void removeOnFirstItemChangedListner(OnFirstItemChangedListener l) {
        _onFirstItemChanged.remove(l);
    }

    /**
     * Add a 'secondItem' property listener
     * 
     * @param l the listener
     */
    public void addOnSecondItemChangedListener(OnSecondItemChangedListener l) {
        _onSecondItemChanged.add(l);
    }

    /**
     * Remove a 'secondItem' property listener
     * 
     * @param l the listener
     */
    public void removeOnSecondItemChangedListner(
            OnSecondItemChangedListener l) {
        _onSecondItemChanged.remove(l);
    }

    public void addOnParentChangedListener(OnParentChangedListener l) {
        _onParentChanged.add(l);
    }

    public void removeOnParentChangedListener(OnParentChangedListener l) {
        _onParentChanged.remove(l);
    }

    /**
     * Add a 'style' property listener
     * 
     * @param l the listener
     */
    public void addOnStyleChangedListener(OnStyleChangedListener l) {
        _onStyleChanged.add(l);
    }

    /**
     * Remove a 'style' property listener
     * 
     * @param l the listener
     */
    public void removeOnStyleChangedListener(OnStyleChangedListener l) {
        _onStyleChanged.remove(l);
    }

    /**
     * Add a 'color' property listener
     * 
     * @param l the listener
     */
    public void addOnColorChangedListener(OnColorChangedListener l) {
        _onColorChanged.add(l);
    }

    /**
     * Remove a 'color' property listener
     * 
     * @param l the listener
     */
    public void removeOnColorChangedListener(OnColorChangedListener l) {
        _onColorChanged.remove(l);
    }

    /**
     * Add a 'strokeWeight' property listener
     * 
     * @param l the listener
     */
    public void addOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener l) {
        _onStrokeWeightChanged.add(l);
    }

    /**
     * Remove a 'strokeWeight' property listener
     * 
     * @param l the listener
     */
    public void removeOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener l) {
        _onStrokeWeightChanged.remove(l);
    }

    /**
     * Set the link color
     * 
     * @param color the argb packed color of the link (@see Color)
     */
    public void setColor(int color) {
        if (_color != color) {
            _color = color;
            onColorChanged();
        }
    }

    /**
     * Set the link style
     * 
     * @param style STYLE_SOLID, STYLE_DOTTED, STYLE_DASHED, or STYLE_OUTLINED
     */
    @Override
    public void setStyle(int style) {
        if (_style != style) {
            _style = style;
            onStyleChanged();
        }
    }

    /**
     * Set the link style
     *
     * @param basicLineStyle STYLE_SOLID, STYLE_DOTTED, or STYLE_DASHED
     */
    @Override
    public void setBasicLineStyle(int basicLineStyle) {
        super.setBasicLineStyle(basicLineStyle);
        setStyle(basicLineStyle);
    }

    @Override
    public int getBasicLineStyle() {
        return getStyle();
    }

    /**
     * Set the link type
     * 
     * @param link LINK_NONE, or LINK_LINE
     */
    public void setLink(int link) {
        if (_link != link) {
            _link = link;
            onLinkChanged();
        }
    }

    /**
     * Set the first PointMapItem
     * 
     * @param firstItem the first item (may be null)
     */
    public void setFirstItem(PointMapItem firstItem) {
        if (_firstItem != firstItem) {
            PointMapItem prevItem = _firstItem;
            _firstItem = firstItem;
            onFirstItemChanged(prevItem);
            this.onPointsChanged();
        }
    }

    /**
     * Set the second PointMapItem
     * 
     * @param secondItem the second item (may be null)
     */
    public void setSecondItem(PointMapItem secondItem) {
        if (_secondItem != secondItem) {
            PointMapItem prevItem = _secondItem;
            _secondItem = secondItem;
            onSecondItemChanged(prevItem);
            this.onPointsChanged();
        }
    }

    /**
     * Set the marker so the user can click on an association
     * 
     * @param marker the marker to the user can click on an association.
     */
    public void setMarker(final Marker marker) {
        _marker = marker;
    }

    /*
     * Get the marker connected to this association
     */
    public Marker getMarker() {
        return _marker;
    }

    @Override
    public PointMapItem getAnchorItem() {
        return getMarker();// added by John Thompson - this was driving me nuts!
                           // I kept using this method instead of getMarker() and getting a
                           // nullpointerexception!
    }

    /**
     * Set the stroke weight of the link primitive
     * 
     * @param strokeWeight the stroke weight [0.0, 10.0]
     */
    @Override
    public void setStrokeWeight(double strokeWeight) {
        // this does not call super.setStrokeWeight intentionally

        if (Double.compare(_strokeWeight, strokeWeight) != 0) {
            _strokeWeight = strokeWeight * MapView.DENSITY;
            onStrokeWeightChanged();
        }
    }

    /**
     * Get the stroke weight
     * 
     * @return the stroke weight [0.0. 10.0]
     */
    @Override
    public double getStrokeWeight() {
        return _strokeWeight / MapView.DENSITY;
    }

    public String getText() {
        return _text;
    }

    public void setText(String text) {
        if (!_text.equals(text)) {
            _text = text;
            onTextChanged();
        }
    }

    public boolean getClampToGround() {
        return this._clampToGround;
    }

    public void setClampToGround(boolean value) {
        if (this._clampToGround != value) {
            this._clampToGround = value;
            onClampToGroundChanged();
        }
    }

    /**
     * Set the parent set for this association
     * @param parent Parent set (null to unset)
     */
    public void setParent(AssociationSet parent) {
        if (_parent != parent) {
            _parent = parent;
            onParentChanged();
        }
    }

    public AssociationSet getParent() {
        return _parent;
    }

    /**
     * Gets the center point of this association
     * 
     * @return the center point of the assocation with metadata.
     */
    @Override
    public GeoPointMetaData getCenter() {
        MapView mv = MapView.getMapView();
        return GeoPointMetaData.wrap(
                GeoCalculations.midPointCartesian(_firstItem.getPoint(),
                        _secondItem.getPoint(),
                        mv != null && mv.isContinuousScrollEnabled()),
                GeoPointMetaData.CALCULATED, GeoPointMetaData.CALCULATED);
    }

    public void addOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.add(l);
    }

    public void removeOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    protected void onTextChanged() {
        for (OnTextChangedListener l : _onTextChanged) {
            l.onAssociationTextChanged(this);
        }
    }

    public void addOnClampToGroundChangedListener(
            OnClampToGroundChangedListener l) {
        _onClampToGroundChanged.add(l);
    }

    public void removeOnClampToGroundChangedListener(
            OnClampToGroundChangedListener l) {
        _onClampToGroundChanged.remove(l);
    }

    protected void onClampToGroundChanged() {
        for (OnClampToGroundChangedListener l : _onClampToGroundChanged) {
            l.onAssociationClampToGroundChanged(this);
        }
    }

    /**
     * Invoked when the link property changes
     */
    protected void onLinkChanged() {
        for (OnLinkChangedListener l : _onLinkChanged) {
            l.onAssociationLinkChanged(this);
        }
    }

    /**
     * Invoked when the style property changes
     */
    @Override
    protected void onStyleChanged() {
        for (OnStyleChangedListener l : _onStyleChanged) {
            l.onAssociationStyleChanged(this);
        }
    }

    /**
     * Invoked when the firstItem property changes
     * 
     * @param prevItem the previous firstItem
     */
    protected void onFirstItemChanged(PointMapItem prevItem) {
        for (OnFirstItemChangedListener l : _onFirstItemChanged) {
            l.onFirstAssociationItemChanged(this, prevItem);
        }
    }

    /**
     * Invoked when the secondItem property changes
     * 
     * @param prevItem the previous secondItem
     */
    protected void onSecondItemChanged(PointMapItem prevItem) {
        for (OnSecondItemChangedListener l : _onSecondItemChanged) {
            l.onSecondAssociationItemChanged(this, prevItem);
        }
    }

    /**
     * Invoked when the parent set changes
     */
    protected void onParentChanged() {
        for (OnParentChangedListener l : _onParentChanged) {
            l.onParentChanged(this, _parent);
        }
    }

    /**
     * Invoked when the color property changes
     */
    protected void onColorChanged() {
        for (OnColorChangedListener l : _onColorChanged) {
            l.onAssociationColorChanged(this);
        }
    }

    /**
     * Invoked when the stroke weight changes
     */
    @Override
    protected void onStrokeWeightChanged() {
        for (OnStrokeWeightChangedListener l : _onStrokeWeightChanged) {
            l.onAssociationStrokeWeightChanged(this);
        }
    }

    @Override
    public GeoPoint[] getPoints() {
        if (_firstItem != null && _secondItem != null) {
            return new GeoPoint[] {
                    _firstItem.getPoint(),
                    _secondItem.getPoint()
            };
        } else if (_firstItem != null) {
            return new GeoPoint[] {
                    _firstItem.getPoint()
            };
        } else if (_secondItem != null) {
            return new GeoPoint[] {
                    _secondItem.getPoint()
            };
        } else {
            return new GeoPoint[] {};
        }
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        if (_firstItem != null && _secondItem != null) {
            return new GeoPointMetaData[] {
                    _firstItem.getGeoPointMetaData(),
                    _secondItem.getGeoPointMetaData()
            };
        } else if (_firstItem != null) {
            return new GeoPointMetaData[] {
                    _firstItem.getGeoPointMetaData()
            };
        } else if (_secondItem != null) {
            return new GeoPointMetaData[] {
                    _secondItem.getGeoPointMetaData()
            };
        } else {
            return new GeoPointMetaData[] {};
        }
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        GeoPoint a = GeoPoint.ZERO_POINT;
        GeoPoint b = GeoPoint.ZERO_POINT;

        if (_firstItem != null && _secondItem != null) {
            a = _firstItem.getPoint();
            b = _secondItem.getPoint();
        } else if (_firstItem != null) {
            a = _firstItem.getPoint();
            b = a;
        } else if (_secondItem != null) {
            a = _secondItem.getPoint();
            b = a;
        }
        if (bounds != null) {
            bounds.set(a, b);
            return bounds;
        } else {
            return new GeoBounds(a, b);
        }
    }

    /**
     * Note, Association does not implement Exportable since it is typically exported
     * by parent objects like other shapes. However this method may be used to 
     * get the KML representation
     * 
     * @return the KML that represents the association.
     */
    public Folder toKml() {
        if (_firstItem == null || _secondItem == null) {
            Log.w(TAG, "Unable to create KML Folder without 2 points");
            return null;
        }

        try {
            // style inner ring
            Style style = new Style();
            LineStyle lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getColor()));
            lstyle.setWidth(2F);
            style.setLineStyle(lstyle);
            IconStyle istyle = new IconStyle();
            istyle.setColor(KMLUtil.convertKmlColor(getColor()));
            style.setIconStyle(istyle);

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Folder element containing styles, shape and label
            Folder folder = new Folder();
            folder.setName(getText());
            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            // line between the two points
            Placemark linePlacemark = new Placemark();
            linePlacemark.setName(getText());
            linePlacemark.setDescription(getText());
            linePlacemark.setId(getUID() + getText());
            linePlacemark.setStyleUrl("#" + styleId);
            linePlacemark.setVisibility(getVisible() ? 1 : 0);

            GeoPoint[] pts = {
                    _firstItem.getPoint(), _secondItem.getPoint()
            };
            MapView mv = MapView.getMapView();
            Coordinates coordinates = new Coordinates(KMLUtil.convertKmlCoords(
                    GeoPointMetaData.wrap(pts), false, mv != null
                            && mv.isContinuousScrollEnabled()
                            && GeoCalculations.crossesIDL(pts, 0, pts.length)));
            LineString lineString = new LineString();
            lineString.setCoordinates(coordinates);
            lineString.setAltitudeMode("absolute");

            List<Geometry> geomtries = new ArrayList<>();
            geomtries.add(lineString);
            linePlacemark.setGeometryList(geomtries);
            folderFeatures.add(linePlacemark);

            return folder;
        } catch (Exception e) {
            Log.e(TAG, "Export of DrawingCircle to KML failed", e);
        }

        return null;
    }

    /**
     * Note, Association does not implement Exportable since it is typically exported
     * by parent objects like other shapes. However this method may be used to 
     * get the KMZ representation
     * 
     * @return the KMZ folder representation of the association.
     */
    public KMZFolder toKmz() {
        Folder f = toKml();
        if (f == null)
            return null;
        return new KMZFolder(f);
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle data = super.preDrawCanvas(cap);
        // Include label
        GeoPointMetaData[] points = getMetaDataPoints();
        if (points != null && points.length == 2) {
            float deg = CanvasHelper.angleTo(cap.forward(points[0].get()),
                    cap.forward(points[1].get())) + 90;
            data.putParcelable("labelPoint", new PointA(
                    cap.forward(getCenter().get()), deg));
        }
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointF[] p = (PointF[]) data.getSerializable("points");
        if (p == null || p.length < 2)
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float lineWeight = cap.getLineWeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(_color);
        paint.setStrokeWidth((float) getStrokeWeight() * lineWeight);

        if ((_style & STYLE_DASHED) > 0)
            paint.setPathEffect(cap.getDashed());
        else if ((_style & STYLE_DOTTED) > 0)
            paint.setPathEffect(cap.getDotted());

        // Draw line
        path.moveTo(dr * p[0].x, dr * p[0].y);
        path.lineTo(dr * p[1].x, dr * p[1].y);
        path.close();
        can.drawPath(path, paint);
        path.reset();

        // Draw label
        PointA labelPoint = data.getParcelable("labelPoint");
        if (labelPoint != null && cap.shouldDrawLabel(_text, p))
            cap.drawLabel(_text, labelPoint);
    }
}
