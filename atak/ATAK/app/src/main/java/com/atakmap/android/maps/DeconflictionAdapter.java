
package com.atakmap.android.maps;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.toolbars.RangeAndBearingEndpoint;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;

/**
 * List adapter for filtered track menu.
 * <p/>
 *
 */
public class DeconflictionAdapter extends ArrayAdapter<MapItem> {

    public static final String TAG = "DeconflictionAdapter";

    protected final Context mContext;
    protected final MapView mMapView;
    private final SharedPreferences _prefs;
    private final CoordinateFormat _cFormat;
    private final DeconflictionType type;
    //private OnClickListener clickAction;
    protected final MapTouchController mtc;
    private final MotionEvent event;

    public enum DeconflictionType {
        RADIAL,
        MOVE,
        SET
    }

    public DeconflictionAdapter(Context context, ArrayList<MapItem> items,
            final DeconflictionType type,
            final MapTouchController mtc,
            final MotionEvent event) {
        super(context, R.layout.deconfliction_menu_row, items);
        this.mContext = context;
        MapActivity mapActivity = (MapActivity) context;
        mMapView = mapActivity.getMapView();

        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        _cFormat = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref",
                mMapView.getContext().getString(
                        R.string.coord_display_pref_default)));

        this.type = type;
        this.mtc = mtc;
        this.event = event;
    }

    public static class ViewHolder {
        public TextView callsign;
        public ImageView trackIcon;
        public TextView distance;
        public TextView degrees;
        public TextView position;
        public TextView elevation;
        public ImageButton action;
    }

    /**
     * Convienence Method for getting the GeoPoint that represents the 
     * map item.
     */
    private static GeoPointMetaData getMapItemPoint(final MapItem item) {
        GeoPointMetaData result = null;
        if (item != null) {
            if (item instanceof Marker) {
                result = ((Marker) item).getGeoPointMetaData();
            } else if (item instanceof MultiPolyline) {
                result = ((MultiPolyline) item).getCenter();
            } else if (item instanceof Shape) {
                result = ((Shape) item).getCenter();
            } else if (item instanceof PointMapItem) {
                result = ((PointMapItem) item).getGeoPointMetaData();
            }
        }
        return result;
    }

    /**
     * Given a Map Item, center the item on the map display.
     */
    protected void jumpTo(final MapItem pmi) {

        MapItem resolved = ShapeUtils.resolveShape(pmi);
        if (resolved != null) {
            ATAKUtilities.scaleToFit(mMapView,
                    resolved, false,
                    mMapView.getWidth(), mMapView.getHeight());
        }

    }

    /**
     * Builds the layout for the location block 
     *
     * @param context
     * @param mapView
     * @param holder
     * @param sourceItem
     * @param targetItem
     */
    private void buildLocationLayout(Context context, MapView mapView,
            ViewHolder holder,
            MapItem sourceItem, MapItem targetItem, Span displayUnits,
            boolean adjustUnits) {
        buildLocationLayout(context, mapView, holder, sourceItem,
                targetItem, displayUnits, adjustUnits, true);
    }

    private void buildLocationLayout(Context context, MapView mapView,
            ViewHolder holder,
            MapItem sourceItem,
            MapItem targetItem,
            Span displayUnits,
            boolean adjustUnits,
            boolean bearingToMag) {

        GeoPointMetaData sourceItemPoint = getMapItemPoint(sourceItem);
        GeoPointMetaData targetItemPoint = getMapItemPoint(targetItem);
        if (sourceItemPoint != null && targetItemPoint != null) {
            double distance = sourceItemPoint.get()
                    .distanceTo(targetItemPoint.get());
            String distanceDisplay = "";
            if (displayUnits == Span.METER) {
                distanceDisplay = SpanUtilities.formatType(
                        Span.METRIC, distance,
                        Span.METER);
            } else if (displayUnits == Span.FOOT) {
                distanceDisplay = SpanUtilities.formatType(
                        Span.ENGLISH, distance,
                        Span.METER);
            }
            holder.distance.setText(distanceDisplay);

            double bearing = DistanceCalculations.bearingFromSourceToTarget(
                    sourceItemPoint.get(), targetItemPoint.get());

            final String bearingString;
            if (bearingToMag) {
                bearing = ATAKUtilities.convertFromTrueToMagnetic(
                        targetItemPoint.get(), bearing);
                bearingString = AngleUtilities.format(bearing, Angle.DEGREE)
                        + "M";
            } else {
                bearingString = AngleUtilities.format(bearing, Angle.DEGREE)
                        + "T";
            }
            holder.degrees.setText(bearingString);

        } else {
            holder.degrees.setText(" ");
            holder.distance.setText(" ");
        }
        if (targetItemPoint != null) {
            final String p = CoordinateFormatUtilities.formatToString(
                    targetItemPoint.get(), _cFormat);
            final String a = AltitudeUtilities.format(targetItemPoint.get(),
                    _prefs);

            holder.elevation.setText(a);
            holder.position.setText(p);
        } else {
            holder.elevation.setText(R.string.ft_msl2);
            holder.position.setText(" ");
        }
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView,
            @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(R.layout.deconfliction_menu_row,
                    parent, false);
            holder = new ViewHolder();
            holder.callsign = convertView
                    .findViewById(R.id.track_callsign_label);
            holder.trackIcon = convertView
                    .findViewById(R.id.track_icon_imagebutton);
            holder.distance = convertView
                    .findViewById(R.id.tvDistance);
            holder.degrees = convertView
                    .findViewById(R.id.tvDegrees);
            holder.position = convertView
                    .findViewById(R.id.tvPosition);
            holder.elevation = convertView
                    .findViewById(R.id.tvElevation);
            holder.action = convertView
                    .findViewById(R.id.radial_imagebutton);
            holder.position.setPadding(0, 0, 60, 0);
            holder.trackIcon.setVisibility(View.VISIBLE);
            convertView.setTag(holder);

        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final MapItem item = getItem(position);
        if (item == null)
            return convertView;

        // Set Callsign
        String callsign = ATAKUtilities.getDisplayName(item);
        holder.callsign.setText(callsign);

        MapItem shp = ATAKUtilities.findAssocShape(item);
        if (shp instanceof RangeAndBearingMapItem
                && !(item instanceof RangeAndBearingEndpoint))
            // Don't use R&B icon for non-R&B markers
            shp = item;

        // Set icon
        ATAKUtilities.setIcon(holder.trackIcon, shp);

        holder.action.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpTo(item);
                mtc.focus(item);
            }
        });

        // Pan to marker
        holder.action.setImageResource(R.drawable.panto_normal);

        // Build position/alt layout
        PointMapItem self = ATAKUtilities.findSelf(mMapView);
        buildLocationLayout(mContext, mMapView, holder, self, item,
                Span.METER, true);

        return convertView;
    }
}
