
package com.atakmap.android.maps;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Pair;

import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.conversions.ConversionFactors;

import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.imagecapture.Capturable;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.imagecapture.TextRect;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import org.gdal.ogr.ogr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A geo-space map icon with optional text and style settings
 * <p>
 * Marker has the following properties:
 * <li><b>icon</b> defines the icon image states and pixel that maps to the marker GeoPoint (anchor)
 * </li>
 * <li><b>title</b> defines the text drawn below, center the icon image</li>
 * <li><b>state</b> affects the stylized view state or touch event interaction</li>
 * <li><b>hitBounds</b> defines the pixel area where the Marker may be touched offset from the icon
 * anchor</li>
 * </p>
 * <p>
 * Notable <i>UserControlComponent</i> (touch) behavior:
 * <li><b>state</b> will be updated for <i>STATE_PRESSED_MASK</i></li>
 * <li><b>hitBounds</b> affects hit testing when handling touch events</li>
 * </p>
 * <p>
 * Notable <i>GLMapComponent</i> (graphics) behavior:
 * <li>when <b>state</b> contains <i>STATE_PRESSED_MASK</i>, a highlight is drawn around
 * <b>hitBounts</b></li>
 * <li><b>title</b> text will always be drawn centered on the current <b>icon</b></li>
 * </p>
 * 
 * 
 * FAIR WARNING - DO NOT EXTEND ME
 */
public class Marker extends PointMapItem implements Exportable, Capturable {

    private static final String TAG = "Marker";

    /**
     * The specific purpose is to allow for an existing marker within the system
     * that has been created as part of the CotMapAdapter (unfortunately CotMapAdapter
     * cannot create anything better than a Marker) to be able to express itself as a
     * more robust Kml or Kmz structure.
     * Note:  Currently only used to decouple the Marker from JumpMaster.
     */
    public interface AugmentedKeyholeInfo {
        Folder toKml(Folder folder, Marker m);

        KMZFolder toKmz(KMZFolder folder, Marker m);
    }

    private AugmentedKeyholeInfo aki;

    private final ConcurrentLinkedQueue<OnIconChangedListener> _onIconChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTitleChangedListener> _onTitleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnSummaryChangedListener> _onSummaryChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStateChangedListener> _onStateChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnMarkerHitBoundsChangedListener> _onMarkerHitBoundsChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTrackChangedListener> _onTrackChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStyleChangedListener> _onStyleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLabelTextSizeChangedListener> _onLabelSizeChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLabelPriorityChangedListener> _onLabelPriorityChanged = new ConcurrentLinkedQueue<>();

    public enum LabelPriority {
        Low,
        Standard,
        High
    }

    private int _style = 0;
    private Icon _icon;
    private String _title = "";
    private String _summary = "";
    private int _state;
    private double _heading = Double.NaN, _speed = Double.NaN; // track property
    private final Rect _hitBounds = new Rect(0, 0, 0, 0);
    private int _textColor = Color.WHITE;
    private int _labelTextSize = (MapView.getDefaultTextFormat() == null) ? 14
            : MapView.getDefaultTextFormat().getFontSize();
    private Typeface _labelTypeface = Typeface.DEFAULT;

    private LabelPriority _labelPriority = LabelPriority.Standard;
    private LabelPriority _userSetLabelPriority = null;

    // Icon visibility states - similar to View visibility
    public static final int ICON_VISIBLE = 0;
    public static final int ICON_INVISIBLE = 1;
    public static final int ICON_GONE = 2;

    private int _iconVisibility = ICON_VISIBLE;

    public static final int TEXT_STATE_DEFAULT = 0;
    public static final int TEXT_STATE_ALWAYS_SHOW = 1;
    public static final int TEXT_STATE_NEVER_SHOW = 2;

    private volatile int textRenderFlag = TEXT_STATE_DEFAULT;

    /**
     * Minimum label render resolution for a polyline that is inside the render window.
     */
    public final static double DEFAULT_MIN_LABEL_RENDER_RESOLUTION = 0d;

    /**
     * Maximum label render resolution for a polyline that is inside the render window.
     */
    public final static double DEFAULT_MAX_LABEL_RENDER_RESOLUTION = 10d;

    /**
     * Summary sub label
     */
    private boolean _summFlag = false;

    /**
     * The marker should appear and behave as selected
     */
    public static final int STATE_SELECTED_MASK = 1;

    /**
     * The marker should appear and behave as focused
     */
    public static final int STATE_FOCUSED_MASK = 1 << 1;

    /**
     * The marker should appear and behave as pressed
     */
    public static final int STATE_PRESSED_MASK = 1 << 2;

    /**
     * The marker should appear and behave as canceled
     */
    public static final int STATE_CANCELED_MASK = 1 << 3;

    /**
     * The marker should appear and behave as disabled
     */
    public static final int STATE_DISABLED_MASK = 1 << 4;

    /**
     * Indicates the icon should be rotated according to the track heading. The icon is assumed to
     * be naturally at heading 0 degrees and will be rotated counter clockwise from there. This is a
     * handy feature for implementing markers that are rotating arrows.
     */
    public static final int STYLE_ROTATE_HEADING_MASK = 1;

    /**
     * If Rotating arrows are not desired and full rotation is desired, set the following flag.
     */
    public static final int STYLE_ROTATE_HEADING_NOARROW_MASK = 32;

    /**
     * The marker will smoothly interpolate between rotation changes
     */
    public static final int STYLE_SMOOTH_ROTATION_MASK = 2;

    /**
     * The marker will smoothly interpolate between point changes
     */
    public static final int STYLE_SMOOTH_MOVEMENT_MASK = 4;

    /**
     * Shorten long titles by marquee the text
     */
    public static final int STYLE_MARQUEE_TITLE_MASK = 8;

    public static final int STYLE_ALERT_MASK = 16;

    public static final int STATE_DEFAULT = 0;

    private static final String POINT_ICON_SUFFIX = "icons/reference_point.png";

    /* Listener interfaces for marker events */

    public interface OnLabelPriorityChanged {
        void onLabelPriorityChanged(Marker marker);
    }

    public interface OnIconChangedListener {
        void onIconChanged(Marker marker);
    }

    public interface OnTitleChangedListener {
        void onTitleChanged(Marker marker);
    }

    public interface OnLabelPriorityChangedListener {
        void onLabelPriorityChanged(Marker marker);
    }

    public interface OnLabelTextSizeChangedListener {
        void onLabelSizeChanged(Marker marker);
    }

    public interface OnSummaryChangedListener {
        void onSummaryChanged(Marker marker);
    }

    public interface OnStateChangedListener {
        void onStateChanged(Marker marker);
    }

    /**
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public interface OnMarkerHitBoundsChangedListener {
        void onMarkerHitBoundsChanged(Marker marker);
    }

    public interface OnTrackChangedListener {
        void onTrackChanged(Marker marker);
    }

    public interface OnStyleChangedListener {
        void onStyleChanged(Marker marker);
    }

    /* Constructors */

    public Marker(final String uid) {
        this(MapItem.createSerialId(), uid);
    }

    public Marker(final long serialId,
            final String uid) {
        this(serialId, new DefaultMetaDataHolder(), uid);
    }

    public Marker(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, GeoPoint.ZERO_POINT, uid);
    }

    /**
     * Create a Marker at a given point and altitude
     *
     * @param point the map point of the Marker
     */
    public Marker(GeoPoint point, String uid) {
        this(MapItem.createSerialId(), point, uid);
    }

    /**
     * Initializer for a marker from an existing GeoPoint with additional metadata.
     * @param point the GeoMetaData point to construct the Marker with the appropriate metadata.
     * @param uid the uid to provide the Marker.
     */
    public Marker(GeoPointMetaData point, String uid) {
        this(MapItem.createSerialId(), point.get(), uid);
        setPoint(point);
    }

    public Marker(final long serialId,
            final GeoPoint point,
            final String uid) {
        this(serialId, new DefaultMetaDataHolder(), point, uid);
    }

    private Marker(final long serialId,
            final MetaDataHolder metadata,
            final GeoPoint point,
            final String uid) {
        super(serialId, metadata, point, uid);
        this.setZOrder(-100);
    }

    /**
     * Called when any of the following changes: the type, team or summary
     */
    private void calculateLabelPriority() {
        if (_userSetLabelPriority != null)
            return;

        LabelPriority calculatedPriority = LabelPriority.Standard;

        // User/team markers
        if (hasMetaValue("team"))
            calculatedPriority = LabelPriority.High;

        // Hostile markers
        final String type = getType();
        if (type != null && type.startsWith("a-h-"))
            calculatedPriority = LabelPriority.High;

        // Marker with a summary defined
        if (!FileSystemUtils.isEmpty(_summary))
            calculatedPriority = LabelPriority.High;

        if (_labelPriority != calculatedPriority) {
            _labelPriority = calculatedPriority;
            onLabelPriorityChanged();
        }
    }

    /**
     * Set the icon property
     * 
     * @param icon the icon
     */
    public void setIcon(Icon icon) {
        if (icon != _icon) {
            _icon = icon;
            calculateLabelPriority();
            onIconChanged();
        }
    }

    /**
     * Get the icon property
     * 
     * @return the icon that for the marker
     */
    public Icon getIcon() {
        return _icon;
    }

    /**
     * Set the title property
     * 
     * @param title the title
     */
    @Override
    public void setTitle(String title) {
        if (title == null) {
            title = "";
        }
        if (!FileSystemUtils.isEquals(_title, title)) {
            // XXX - Yuck. Stupid exception for CASEVAC markers
            if (FileSystemUtils.isEquals(_title, getMetaString("callsign", "")))
                setMetaString("callsign", title);

            _title = title;
            // XXX - title should be more benign than callsign -- this should
            //       really just be a metadata property and not a member
            super.setTitle(title);
            onTitleChanged();
        }
    }

    /**
     * Get the title property
     * 
     * @return Marker title
     */
    @Override
    public String getTitle() {
        return _title;
    }

    /**
     * Set the summary property
     * @param summary - The string
     */
    public void setSummary(String summary) {
        if (summary == null)
            summary = "";
        if (!FileSystemUtils.isEquals(_summary, summary)) {
            _summary = summary;
            this.setMetaString("summary", _summary);
            calculateLabelPriority();
            onSummaryChanged();
        }
    }

    /**
     * Set the label priority on a case by case basis given a label priority.
     * @param priority the label priority, null if a default priority calculation is expected.
     */
    public void setLabelPriority(LabelPriority priority) {

        // the user/plugin has set the priority so it should be used above all others.
        _userSetLabelPriority = priority;

        // User-set label priority has been turned off - fallback to default
        if (_userSetLabelPriority == null) {
            calculateLabelPriority();
            return;
        }

        if (_labelPriority != priority) {
            _labelPriority = priority;
            onLabelPriorityChanged();
        }
    }

    public LabelPriority getLabelPriority() {
        return _labelPriority;
    }

    @Override
    protected String getRemarksKey() {
        // Another very special exception for CASEVAC markers
        return getType().equals("b-r-f-h-c") ? "medline_remarks"
                : super.getRemarksKey();
    }

    /**
     * sets the text size to use for the rendering label
     * convenience method for setting label size meta
     * @param size the int value size to use on the labels for this marker
     * If not set the default MapView format size is used
     */
    public void setLabelTextSize(int size) {
        _labelTextSize = size;
        onLabelTextSizeChanged();
    }

    /**gets the text size to use for the rendering label/labels
     * return default MapView font size if not modified
     */
    public int getLabelTextSize() {
        return _labelTextSize;
    }

    /**sets the text size to use for the rendering label as well as the Typeface(font)
     * this is a convenience method for setting size and font for a rendering label
     * @param size the int value size to use on the labels for this marker
     * @param typeface the font typeface.java for the rendering label
     */
    public void setLabelTextSize(int size, Typeface typeface) {
        _labelTextSize = size;
        _labelTypeface = typeface;
        onLabelTextSizeChanged();
    }

    /**Sets the label typeface graphic(font)
     * @param _labelTypeface the graphic typeface class to use as the font
     */
    public void setLabelTypeface(Typeface _labelTypeface) {
        this._labelTypeface = _labelTypeface;
        onLabelTextSizeChanged();
    }

    /**Returns the current label typeface(font)
     * @return the current typeface font for the text inside the drawn labels
     */
    public Typeface getLabelTypeface() {
        return _labelTypeface;
    }

    /**
     * Get the summary property
     * @return - The summary string
     */
    public String getSummary() {
        return _summary;
    }

    public void setSummaryFlag(boolean b) {
        this._summFlag = b;
    }

    public boolean getSummaryFlag() {
        return _summFlag;
    }

    /**
     * @deprecated Implementation moved to
     * {@link AbstractGLMapItem2#hitTest(com.atakmap.map.MapRenderer3, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected void updateTextBounds() {
    }

    /**
     * Get the state property
     * 
     * @return a flag indicating the state of the marker described by setState
     */
    public int getState() {
        return _state;
    }

    /**
     * Set the state property based on the STATE values defined in the IMarker interface. Once
     * called the state listeners are notified.
     * 
     * @param state any of the STATE_*_MAKS flags or'd together
     */
    public void setState(int state) {
        if (_state != state) {
            _state = state;
            onStateChanged();
        }
    }

    /**
     * Sets the style of the Marker as defined by the STYLE values in the IMarker interface. After
     * this has been invoked the style listeners are notified.
     * 
     * @param style a STYLE flag from the IMarker interface.
     */
    public void setStyle(int style) {
        if (_style != style) {
            _style = style;
            onStyleChanged();
        }
    }

    /**
     * Set the text rendering underneath the marker.   If set to default
     * the text will render when the min zoom level is reached which is
     * the original behavior.   If set to ALWAYS_SHOW, then the text will
     * always show, if set to NEVER_SHOW, the text will never render.
     */
    public void setTextRenderFlag(int flag) {
        if (this.textRenderFlag != flag) {
            // temp unlock to set the initial state
            this.textRenderFlag = flag;
            onStateChanged();
        }
    }

    public int getTextRenderFlag() {
        return this.textRenderFlag;
    }

    public void setAlwaysShowText(boolean state) {
        setTextRenderFlag(state ? TEXT_STATE_ALWAYS_SHOW : TEXT_STATE_DEFAULT);
    }

    /**
     * Convienence method for setting the minimum label render resolution.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMinLabelRenderResolution(final double d) {
        this.setMetaDouble("minLabelRenderResolution", d);
    }

    /**
     * Convienence method for setting the maximum label render resolution.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMaxLabelRenderResolution(final double d) {
        this.setMetaDouble("maxLabelRenderResolution", d);
    }

    /**
     * Set whether the marker label is displayed on the map
     *
     * @param showLabel True to show the label, false to hide it
     */
    public void setShowLabel(boolean showLabel) {
        if (showLabel) {
            removeMetaData("hideLabel");
            setTextRenderFlag(TEXT_STATE_DEFAULT);
        } else {
            setMetaBoolean("hideLabel", true);
            setTextRenderFlag(TEXT_STATE_NEVER_SHOW);
        }
    }

    /**
     * Set the visibility state of the marker icon shown on the map
     *
     * States include:
     * {@link Marker#ICON_VISIBLE} - Icon is visible
     * {@link Marker#ICON_INVISIBLE} - Icon is invisible but takes up space
     * {@link Marker#ICON_GONE} - Icon is invisible and does not take up space
     *
     * @param visibility Visibility state
     */
    public void setIconVisibility(int visibility) {
        if (_iconVisibility != visibility) {
            _iconVisibility = visibility;
            onIconChanged();
        }
    }

    /**
     * Get the icon visibility state
     * See {@link #setIconVisibility(int)} for more info
     *
     * @return Icon visibility state (ICON_VISIBLE, ICON_INVISIBLE, or ICON_GONE)
     */
    public int getIconVisibility() {
        return _iconVisibility;
    }

    public int getStyle() {
        return _style;
    }

    public void addOnStyleChangedListener(OnStyleChangedListener l) {
        _onStyleChanged.add(l);
    }

    public void removeOnStyleChangedListener(OnStyleChangedListener l) {
        _onStyleChanged.remove(l);
    }

    /**
     * Add a icon property listener
     *
     * @param listener the listener
     */
    public void addOnIconChangedListener(OnIconChangedListener listener) {
        _onIconChanged.add(listener);
    }

    /**
     * Remove a icon property listener
     *
     * @param listener the listener
     */
    public void removeOnIconChangedListener(OnIconChangedListener listener) {
        _onIconChanged.remove(listener);
    }

    /**
     * Add a title property listener
     *
     * @param listener the listener
     */
    public void addOnTitleChangedListener(OnTitleChangedListener listener) {
        _onTitleChanged.add(listener);
    }

    public void addOnLabelSizeChangedListener(
            OnLabelTextSizeChangedListener listener) {
        _onLabelSizeChanged.add(listener);
    }

    public void removeOnLabelSizeChangedListner(
            OnLabelTextSizeChangedListener listener) {
        _onLabelSizeChanged.remove(listener);
    }

    /**
     * Remove a title property listener
     *
     * @param listener the listener
     */
    public void removeOnTitleChangedListener(OnTitleChangedListener listener) {
        _onTitleChanged.remove(listener);
    }

    /**
     * Add a summary changed listener
     *
     * @param listener the listener
     */
    public void addOnSummaryChangedListener(OnSummaryChangedListener listener) {
        _onSummaryChanged.add(listener);
    }

    /**
     * Remove a summary changed listener
     *
     * @param listener the listener
     */
    public void removeOnSummaryChangedListener(
            OnSummaryChangedListener listener) {
        _onSummaryChanged.remove(listener);
    }

    /**
     * Add a summary changed listener
     *
     * @param listener the listener
     */
    public void addOnLabelPriorityChangedListener(
            OnLabelPriorityChangedListener listener) {
        _onLabelPriorityChanged.add(listener);
    }

    /**
     * Remove a summary changed listener
     *
     * @param listener the listener
     */
    public void removeOnLabelPriorityChangedListener(
            OnLabelPriorityChangedListener listener) {
        _onLabelPriorityChanged.remove(listener);
    }

    /**
     * Add a state property listener
     *
     * @param listener the listener
     */
    public void addOnStateChangedListener(OnStateChangedListener listener) {
        _onStateChanged.add(listener);
    }

    /**
     * Remove a state property listener
     *
     * @param listener the listener
     */
    public void removeOnStateChangedListener(OnStateChangedListener listener) {
        _onStateChanged.remove(listener);
    }

    /**
     * Add a hitBounds property listener
     *
     * @param listener the listener
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void addOnMarkerHitBoundsChangedListener(
            OnMarkerHitBoundsChangedListener listener) {
        _onMarkerHitBoundsChanged.add(listener);
    }

    /**
     * Remove a hitBounds property listener
     *
     * @param listener the listener
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void removeOnMarkerHitBoundsChangedListener(
            OnMarkerHitBoundsChangedListener listener) {
        _onMarkerHitBoundsChanged.remove(listener);
    }

    public void addOnTrackChangedListener(OnTrackChangedListener listener) {
        if (listener != null)
            _onTrackChanged.add(listener);
    }

    public void removeOnTrackChangedListener(OnTrackChangedListener listener) {
        _onTrackChanged.remove(listener);
    }

    /**
     * Set the hitBounds property.
     *
     * @param left offset left of MapIcon anchor
     * @param top offset above MapIcon anchor
     * @param right offset right of MapIcon anchor
     * @param bottom offset below MapIcon anchor
     *
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setMarkerHitBounds(int left, int top, int right, int bottom) {
        _hitBounds.left = Math.round(left * MapView.DENSITY);
        _hitBounds.right = Math.round(right * MapView.DENSITY);
        _hitBounds.top = Math.round(top * MapView.DENSITY);
        _hitBounds.bottom = Math.round(bottom * MapView.DENSITY);
        onMarkerHitBoundsChanged();
    }

    /**
     * Set the hitBounds property.
     *
     * @param hitBounds offset values from MapIcon anchor
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setMarkerHitBounds(Rect hitBounds) {
        setMarkerHitBounds(hitBounds.left, hitBounds.top, hitBounds.right,
                hitBounds.bottom);
    }

    /**
     * Get the hitBounds property
     *
     * @return offset values from MapIcon anchor
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public Rect getMarkerHitBounds() {
        return getMarkerHitBounds(null);
    }

    /**
     * Get the hitBounds property
     *
     * @param out the Rect to use (may be null)
     * @return offset values from MapIcon anchor
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public Rect getMarkerHitBounds(Rect out) {
        if (out == null) {
            out = new Rect();
        }
        out.set(_hitBounds);
        // TODO: convert back to dp?
        return out;
    }

    /**
     * The current color of the marker text.
     * @return color of the marker text in #ARGB format.
     */
    public int getTextColor() {
        return _textColor;
    }

    /**
     * Set the color of the text being rendered when the Marker text is visible.
     * @param textColor the color in #ARGB format.
     */
    public void setTextColor(final int textColor) {
        if (_textColor != textColor) {
            _textColor = textColor;
            onStateChanged();
        }
    }

    /**
     * Sets the heading and speed for this Marker.
     *
     * @param heading the track heading in degrees True [-360..360]
     * @param speed the track speed in meters per second
     */
    public void setTrack(double heading, double speed) {
        if (Double.compare(_heading, heading) != 0
                || Double.compare(_speed, speed) != 0) {

            // further protection against bad values the lessons learned from r34875
            // and the S8 Active / PR response.

            if (Double.isNaN(heading) || Math.abs(heading) > 3600)
                heading = 0.0;

            _heading = heading;

            // normalize the value between 0..359
            while (_heading < 0) {
                _heading += 360;
            }
            _heading %= 360;

            _speed = speed;
            onTrackChanged();
        }
    }

    /**
     * Provides the track heading
     *
     * @return a double value indicating the track heading in true degrees
     */
    public double getTrackHeading() {
        return _heading;
    }

    /**
     * Provides the track speed
     *
     * @return a double value indicating the track speed in meters per second
     */
    public double getTrackSpeed() {
        return _speed;
    }

    /**
     * Invoked when hitBounds property changes
     * @deprecated No longer used for hit-testing
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected void onMarkerHitBoundsChanged() {
        for (OnMarkerHitBoundsChangedListener l : _onMarkerHitBoundsChanged) {
            l.onMarkerHitBoundsChanged(this);
        }
    }

    /**
     * Invoked when icon property changes
     */
    protected void onIconChanged() {
        for (OnIconChangedListener l : _onIconChanged) {
            l.onIconChanged(this);
        }
    }

    /**
     * Invoked when title property changes
     */
    protected void onTitleChanged() {
        for (OnTitleChangedListener l : _onTitleChanged) {
            l.onTitleChanged(this);
        }
    }

    protected void onLabelTextSizeChanged() {
        for (OnLabelTextSizeChangedListener l : _onLabelSizeChanged) {
            l.onLabelSizeChanged(this);
        }
    }

    protected void onLabelPriorityChanged() {
        for (OnLabelPriorityChangedListener l : _onLabelPriorityChanged) {
            l.onLabelPriorityChanged(this);
        }
    }

    /**
     * Invoked when the summary property changes
     */
    protected void onSummaryChanged() {
        for (OnSummaryChangedListener l : _onSummaryChanged) {
            l.onSummaryChanged(this);
        }
    }

    /**
     * Invoked when state property changes
     */
    protected void onStateChanged() {
        for (OnStateChangedListener l : _onStateChanged) {
            l.onStateChanged(this);
        }
    }

    protected void onTrackChanged() {
        for (OnTrackChangedListener l : _onTrackChanged) {
            l.onTrackChanged(this);
        }
    }

    protected void onStyleChanged() {
        for (OnStyleChangedListener l : _onStyleChanged) {
            l.onStyleChanged(this);
        }
    }

    /**
     * Given a marker, this will search throug all of the know locations to find out the markers affiliation
     * For example, red = hostile, blue = friendly, gold team = yello.
     */
    public static int getAffiliationColor(Marker t) {
        if (t == null)
            return Color.WHITE;

        if (t.hasMetaValue("team")) {
            return Icon2525cIconAdapter.teamToColor(t
                    .getMetaString("team", "white"));
        }

        if (t.hasMetaValue("color"))
            return t.getColor();

        return getAffiliationColor(t.getType());
    }

    /**
     * Uses the type to determine the affiliation color: red = hostile, blue = friendly for example.
     */
    public static int getAffiliationColor(String type) {
        if (type == null)
            return Color.WHITE;

        if (type.startsWith("a-f")) {
            return Color.argb(255, 128, 224, 255);
        } else if (type.startsWith("a-h")) {
            return Color.argb(255, 255, 128, 128);
        } else if (type.startsWith("a-n")) {
            return Color.argb(255, 170, 255, 170);
        } else if (type.startsWith("a-u")) {
            return Color.argb(255, 225, 255, 128);
        }

        return Color.WHITE;
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (filters != null && filters.filter(this))
            return null;

        if (!getMetaBoolean("addToObjList", true)) {
            return null;
        }

        if (Folder.class.equals(target)) {
            return toKml();
        } else if (KMZFolder.class.equals(target)) {
            return toKmz();
        } else if (MissionPackageExportWrapper.class.equals(target)) {
            return toMissionPackage(this);
        } else if (GPXExportWrapper.class.equals(target)) {
            return toGPX(this);
        } else if (OGRFeatureExportWrapper.class.equals(target)) {
            return toOgrGeomtry(this);
        }

        return null;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    public void setAugmentedKeyholeInfo(AugmentedKeyholeInfo aki) {
        this.aki = aki;
    }

    protected Folder toKml() {
        try {
            // style element
            Style style = new Style();
            IconStyle istyle = new IconStyle();
            if (_icon != null) {
                if (getIconVisibility() == ICON_VISIBLE) {
                    int color = getAffiliationColor(this);
                    istyle.setColor(KMLUtil.convertKmlColor(color));
                    style.setIconStyle(istyle);

                    //set white pushpin and Google Earth will tint based on color above
                    com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
                    String whtpushpin = MapView.getMapView().getContext()
                            .getString(R.string.whtpushpin);
                    icon.setHref(whtpushpin);
                    istyle.setIcon(icon);

                }
            }

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Folder element containing styles, shape and label
            Folder folder = new Folder();
            if (getGroup() != null
                    && !FileSystemUtils.isEmpty(getGroup().getFriendlyName()))
                folder.setName(getGroup().getFriendlyName());
            else
                folder.setName(getTitle());
            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            // Placemark element
            String uid = getUID();
            Placemark pointPlacemark = new Placemark();
            pointPlacemark.setId(uid);
            pointPlacemark.setName(getTitle());
            pointPlacemark.setStyleUrl("#" + styleId);
            pointPlacemark.setVisibility(getVisible() ? 1 : 0);

            Coordinate coord = KMLUtil
                    .convertKmlCoord(this.getGeoPointMetaData(), false);
            if (coord == null) {
                Log.w(TAG, "No marker location set");
                return null;
            }

            Point centerPoint = new Point();
            centerPoint.setCoordinates(coord);
            centerPoint.setAltitudeMode(
                    KMLUtil.convertAltitudeMode(getAltitudeMode()));

            List<Geometry> pointGeomtries = new ArrayList<>();
            pointGeomtries.add(centerPoint);
            pointPlacemark.setGeometryList(pointGeomtries);
            folderFeatures.add(pointPlacemark);

            //set an HTML description (e.g. for the Google Earth balloon)
            String desc = getKMLDescription(null);
            if (!FileSystemUtils.isEmpty(desc)) {
                pointPlacemark.setDescription(desc);
            }

            if (aki != null) {
                return aki.toKml(folder, this);
            }

            return folder;
        } catch (Exception e) {
            Log.e(TAG, "Export of Marker to KML failed with Exception", e);
        }

        return null;
    }

    protected KMZFolder toKmz() {
        try {
            // Folder element containing styles, shape and label
            KMZFolder folder = new KMZFolder();
            if (getGroup() != null
                    && !FileSystemUtils.isEmpty(getGroup().getFriendlyName()))
                folder.setName(getGroup().getFriendlyName());
            else
                folder.setName(getTitle());

            // style element
            Style style = new Style();
            IconStyle istyle = new IconStyle();
            if (_icon != null) {
                style.setIconStyle(istyle);

                if (getIconVisibility() == ICON_VISIBLE) {
                    int color = getAffiliationColor(this);
                    istyle.setColor(KMLUtil.convertKmlColor(color));

                    //set white pushpin and Google Earth will tint based on color above
                    com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
                    icon.setHref(
                            "http://maps.google.com/mapfiles/kml/pushpin/wht-pushpin.png");
                    istyle.setIcon(icon);

                    //see if we can set the actual icon for this marker
                    String type = getType();
                    String imageUri = getIcon().getImageUri(Icon.STATE_DEFAULT);
                    if (!FileSystemUtils.isEmpty(imageUri)) {
                        String kmzIconPath = null;
                        if (imageUri.startsWith("sqlite")) {
                            //query sqlite to get iconset UID and icon filename
                            UserIcon userIcon = UserIcon.GetIcon(imageUri,
                                    false,
                                    MapView.getMapView().getContext());
                            if (userIcon != null && userIcon.isValid()) {
                                kmzIconPath = "icons" + File.separatorChar
                                        + userIcon.getIconsetUid()
                                        + "_" + userIcon.getFileName();
                            }
                        } else {
                            File f = new File(imageUri);
                            kmzIconPath = "icons" + File.separatorChar
                                    + type + "_" + f.getName();
                        }

                        if (!FileSystemUtils.isEmpty(kmzIconPath)) {
                            icon.setHref(kmzIconPath);

                            if (!imageUri.endsWith(POINT_ICON_SUFFIX)
                                    && !hasMetaValue("color")) {
                                //if we are including the icon, and it's not the white dot, then
                                //don't set color as GE will paint/taint it.
                                // shb: unless the marker is a team member //
                                if (!hasMetaValue("team")) {
                                    istyle.setColor(null);
                                }
                            }
                        }

                        Pair<String, String> pair = new Pair<>(
                                imageUri, kmzIconPath);
                        if (!folder.getFiles().contains(pair)) {
                            folder.getFiles().add(pair);
                        }
                    }
                }
            }

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Placemark element
            String uid = getUID();
            Placemark pointPlacemark = new Placemark();
            pointPlacemark.setId(uid);
            pointPlacemark.setName(getTitle());
            pointPlacemark.setStyleUrl("#" + styleId);
            pointPlacemark.setVisibility(getVisible() ? 1 : 0);

            Coordinate coord = KMLUtil
                    .convertKmlCoord(this.getGeoPointMetaData(), false);
            if (coord == null) {
                Log.w(TAG, "No marker location set");
                return null;
            }

            Point centerPoint = new Point();
            centerPoint.setCoordinates(coord);
            centerPoint.setAltitudeMode(
                    KMLUtil.convertAltitudeMode(getAltitudeMode()));

            List<Geometry> geometryList = new ArrayList<>();
            geometryList.add(centerPoint);
            pointPlacemark.setGeometryList(geometryList);

            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);
            folderFeatures.add(pointPlacemark);

            //now gather attachments
            List<File> attachments = AttachmentManager.getAttachments(uid);
            for (File file : attachments) {
                if (ImageDropDownReceiver.ImageFileFilter.accept(
                        file.getParentFile(), file.getName())) {
                    String kmzAttachmentsPath = "attachments"
                            + File.separatorChar
                            + uid + File.separatorChar + file.getName();

                    Pair<String, String> pair = new Pair<>(
                            file.getAbsolutePath(), kmzAttachmentsPath);
                    if (!folder.getFiles().contains(pair))
                        folder.getFiles().add(pair);
                }
            } //end attachment loop

            //set an HTML description (e.g. for the Google Earth balloon)
            String desc = getKMLDescription(attachments.toArray(new File[0]));
            if (!FileSystemUtils.isEmpty(desc)) {
                pointPlacemark.setDescription(desc);
            }

            if (aki != null) {
                return aki.toKmz(folder, this);
            }

            return folder;

        } catch (Exception e) {
            Log.e(TAG, "Export of Marker to KML failed with Exception", e);
        }

        return null;
    }

    /**
     * Display image thumbnails
     * 
     * @param marker
     * @param attachments
     * @return
     */
    public static String getKMLDescription(final PointMapItem marker,
            String title,
            File[] attachments) {

        String kmzAttachmentsPath = null;
        StringBuilder sb = new StringBuilder();

        sb.append("<html>");
        sb.append("<body>");
        sb.append("<table>");
        sb.append("<tr><td>Name</td><td>")
                .append(marker.getMetaString("callsign", ""))
                .append("</td></tr>");
        sb.append("<tr><td>Production Time</td><td>")
                .append(marker.getMetaString("production_time", "n/a"))
                .append("</td></tr>");
        sb.append("<tr><td>Remarks</td><td>")
                .append(marker.getMetaString("remarks", ""))
                .append("</td></tr>");

        if (marker.hasMetaValue("phoneNumber"))
            sb.append("<tr><td>Phone Number</td><td>")
                    .append(marker.getMetaString("phoneNumber", "n/a"))
                    .append("</td></tr>");

        GeoPoint gp = marker.getPoint();
        sb.append("<tr><td>MGRS</td><td>")
                .append(CoordinateFormatUtilities.formatToString(gp,
                        CoordinateFormat.MGRS))
                .append("</td></tr>");
        sb.append("<tr><td>Lat/Lon DD</td><td>")
                .append(CoordinateFormatUtilities.formatToString(gp,
                        CoordinateFormat.DD))
                .append("</td></tr>");
        sb.append("<tr><td>Lat/Lon DMS</td><td>")
                .append(CoordinateFormatUtilities.formatToString(gp,
                        CoordinateFormat.DMS))
                .append("</td></tr>");
        sb.append("<tr><td>UTM</td><td>")
                .append(CoordinateFormatUtilities.formatToString(gp,
                        CoordinateFormat.UTM))
                .append("</td></tr>");
        String alt = EGM96.formatMSL(gp,
                Span.FOOT);
        sb.append("<tr><td>Elevation</td><td>").append(alt)
                .append("</td></tr>");
        if (marker instanceof Marker) {
            Marker m = (Marker) marker;
            sb.append("<tr><td>Course</td><td>")
                    .append((int) Math.round(m.getTrackHeading())).append("T")
                    .append("</td></tr>");
            sb.append("<tr><td>Speed</td><td>")
                    .append((int) Math.round(m.getTrackSpeed()
                            * ConversionFactors.METERS_PER_S_TO_MILES_PER_H))
                    .append("</td></tr>");
        }
        sb.append("</table>");

        String base = sb.toString();

        base = base.replaceAll(Character.toString('\u200E'), "");
        base = base.replaceAll(Character.toString('\u00B0'), "&#176;");
        sb = new StringBuilder();

        if (attachments == null || attachments.length < 1)
            return base;

        //now handle attachments
        sb.append("<h3>");
        sb.append("<b>Images:</b>");
        sb.append("</h3></br>");
        for (File file : attachments) {
            if (ImageDropDownReceiver.ImageFileFilter.accept(
                    file.getParentFile(), file.getName())) {
                kmzAttachmentsPath = "attachments" + File.separatorChar
                        + marker.getUID() + File.separatorChar + file.getName();

                sb.append("<hr><h3>");
                sb.append(file.getName());
                sb.append("</h3></br>");
                sb.append("<img src=\"");
                sb.append(kmzAttachmentsPath);
                sb.append("\" width=\"1280\" />");
                sb.append("</br>");
            }
        }

        //be sure at least one image
        if (!FileSystemUtils.isEmpty(kmzAttachmentsPath))
            base = base + sb;

        base = base + "</body>";
        base = base + "</html>";
        return base;
    }

    protected String getKMLDescription(File[] attachments) {
        return getKMLDescription(this, getTitle(), attachments);
    }

    public static MissionPackageExportWrapper toMissionPackage(MapItem item) {
        String uid = item.getUID();
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Skipping null Mission Package item");
            return null;
        }

        return new MissionPackageExportWrapper(true, uid);
    }

    public static OGRFeatureExportWrapper toOgrGeomtry(PointMapItem item) {
        org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                org.gdal.ogr.ogrConstants.wkbPoint);
        GeoPoint point = item.getPoint();
        geometry.SetPoint(0, point.getLongitude(), point.getLatitude());
        //geometry.AddPoint(point.getLatitude(), point.getLongitude());

        String name = item.getMetaString("title", "");
        String groupName = name;
        if (item.getGroup() != null) {
            groupName = item.getGroup().getFriendlyName();
        }
        return new OGRFeatureExportWrapper(groupName, ogr.wkbPoint,
                new OGRFeatureExportWrapper.NamedGeometry(geometry, name));
    }

    public static GPXExportWrapper toGPX(PointMapItem item) {
        GeoPoint point = item.getPoint();

        GpxWaypoint wp = new GpxWaypoint();
        wp.setLat(point.getLatitude());
        wp.setLon(point.getLongitude());
        // TODO any elevation conversion?

        if (point.isAltitudeValid()) {
            // This seems like it should be MSL.   Not documented in the spec
            // https://productforums.google.com/forum/#!topic/maps/ThUvVBoHAvk
            final double alt = EGM96.getMSL(point);
            wp.setEle(alt);
        }

        wp.setName(item.getMetaString("title", ""));
        String description = item.getUID();
        String remarks = item.getMetaString("remarks", null);
        if (!FileSystemUtils.isEmpty(remarks))
            description += (" " + remarks);
        wp.setDesc(description);
        return new GPXExportWrapper(wp);
    }

    public static void addToGroup(String[] path, Marker m) {
        MapGroup mg = MapView.getMapView().getRootGroup();
        if (path == null || path.length == 0) {
            Log.d(TAG,
                    "error finding group, group path is null or zero length");
            return;
        }

        for (String aPath : path) {
            if (mg != null) {
                mg = mg.findMapGroup(aPath);
                if (mg == null)
                    Log.d(TAG, "could not find group: " + aPath);
            }
        }

        if (mg != null) {
            //Log.d(TAG, "adding: " + m.getUID() + " to: " + mg.getFriendlyName());
            mg.addItem(m);
        } else {
            Log.d(TAG, "error finding group: " + path[path.length - 1]);
        }
    }

    public String[] getGroupPath() {
        List<String> path = new ArrayList<>();

        MapGroup m = getGroup();
        while (m != null) {
            path.add(0, m.getFriendlyName());
            m = m.getParentGroup();
        }

        if (!path.isEmpty()) { // We can't assume the marker will belong to a map group
            // remove the "Root" friendly name //
            path.remove(0);
        }

        String[] parray = new String[path.size()];
        path.toArray(parray);
        return parray;
    }

    /**
     * Set the color of the marker
     * @param color Marker color
     */
    public void setColor(int color) {
        if (color != getColor()) {
            setMetaInteger("color", color);
            MapView mv = MapView.getMapView();
            if (mv != null && getGroup() != null)
                refresh(mv.getMapEventDispatcher(), null, getClass());
        }
    }

    public int getColor() {
        return getMetaInteger("color", Color.WHITE);
    }

    @Override
    public int getIconColor() {
        int color = Color.WHITE;

        if (_icon != null) {
            color = _icon.getColor(getState());
        }
        return color;
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle data = new Bundle();
        data.putParcelable("point", new PointA(
                cap.forward(getPoint()),
                (float) getTrackHeading()));
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        MapView mapView = MapView.getMapView();
        if (mapView == null)
            return;
        PointA point = data.getParcelable("point");
        if (point == null)
            return;
        float density = mapView.getResources().getDisplayMetrics().density;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        float dr = cap.getResolution();
        float iconSize = cap.getIconSize();
        // Draw icon
        PointF iconScaled = new PointF(0, 0);
        Icon ico = getIcon();
        if (ico != null && _iconVisibility != ICON_GONE) {
            String icoUri = ico.getImageUri(getState());
            Bitmap icoBmp = cap.loadBitmap(icoUri);
            if (icoBmp != null) {
                // Calculate scaled size
                // bW/bH is bitmap (native) size
                // iW/iH is set icon size (-1 = inherit native size)
                float bW = icoBmp.getWidth(), bH = icoBmp.getHeight(), iW = ico
                        .getWidth(), iH = ico.getHeight();
                float bwDp = bW, bhDp = bH;
                if (icoUri.startsWith("resource")
                        || icoUri.startsWith("android.resource")) {
                    // Resource bitmaps are dpi scaled
                    bwDp = bW / density;
                    bhDp = bH / density;
                }
                if (iW <= 0)
                    iW = bwDp;
                if (iH <= 0)
                    iH = bhDp;
                // Scale defines 32x32 as the default size
                // So if the user inputs 64 all icons are doubled in size
                // 16 means half in size, etc...
                iconScaled.x = iconSize * (iW / 32f);
                iconScaled.y = iconSize * (iH / 32f);
                if (_iconVisibility == ICON_VISIBLE) {
                    int icoColor = ico.getColor(Icon.STATE_DEFAULT);
                    paint.setColor(Color.WHITE);
                    if (icoColor != -1) {
                        paint.setColorFilter(new PorterDuffColorFilter(
                                icoColor, PorterDuff.Mode.MULTIPLY));
                        paint.setAlpha(Color.alpha(icoColor));
                    }
                    int style = getStyle();
                    Matrix icoMat = new Matrix();
                    icoMat.postTranslate(-bW / 2, -bH / 2);
                    icoMat.postScale(iconScaled.x / bW, iconScaled.y / bH);
                    if ((style & Marker.STYLE_ROTATE_HEADING_MASK) != 0
                            && !Double.isNaN(point.angle)
                            && !Double.isInfinite(point.angle))
                        icoMat.postRotate(point.angle);
                    icoMat.postTranslate(dr * point.x, dr * point.y);
                    can.drawBitmap(icoBmp, icoMat, paint);
                    paint.setColorFilter(null);
                    paint.setAlpha(255);
                }
            }
        }

        // Draw label
        String title = getTitle();
        if (FileSystemUtils.isEmpty(title))
            return;
        String summary = getSummary();
        if (!FileSystemUtils.isEmpty(summary))
            title += "\n" + summary;
        if (getTextRenderFlag() == TEXT_STATE_NEVER_SHOW) {
            // do nothing
        } else if (getTextRenderFlag() == TEXT_STATE_ALWAYS_SHOW ||
                cap.shouldDrawLabel(this, title)) {
            float yOffset;
            int align;
            if (iconScaled.y > 0) {
                yOffset = iconScaled.y / 2;
                align = TextRect.ALIGN_TOP;
            } else {
                yOffset = 0;
                align = TextRect.ALIGN_Y_CENTER;
            }
            yOffset /= dr;
            cap.drawLabel(title, new PointF(point.x, point.y + yOffset),
                    align, getTextColor(), true);
        }
    }
}
