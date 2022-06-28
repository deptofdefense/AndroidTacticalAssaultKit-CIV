
package com.atakmap.android.items;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;

import java.util.ArrayList;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import android.text.Html;

public class MapItemDetailsView extends RelativeLayout implements
        OnSharedPreferenceChangeListener {

    public static final String TAG = "MapItemDetailsView";

    private DropDownReceiver dropDown = null;
    private String _prevName, _prevRemarks;
    private CoordinateFormat _cFormat = CoordinateFormat.MGRS;
    private TextView _nameEdit, _remarksEdit;
    private CheckBox _rawCb;
    private View _noGps;
    private RangeAndBearingTableHandler rabtable;
    private Button _centerButton;
    private ImageButton _galleryButton;
    private SharedPreferences _prefs;
    private boolean _galleryAsAttachments;

    private MapItem item;
    private MapView _mapView;
    private View _sendExportView;
    private boolean initialized;

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sp,
            final String key) {

        if (key == null)
            return;

        if (key.equals("coord_display_pref")) {
            _cFormat = CoordinateFormat
                    .find(sp.getString(
                            key,
                            dropDown.getMapView()
                                    .getContext()
                                    .getString(
                                            R.string.coord_display_pref_default)));
        }
    }

    /****************************** CONSTRUCTOR ****************************/

    public MapItemDetailsView(final Context context) {
        super(context);
        this.initImpl(context);
    }

    public MapItemDetailsView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        this.initImpl(context);
    }

    private void initImpl(Context context) {
        _prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (!isInEditMode()) {
            _prefs.registerOnSharedPreferenceChangeListener(this);
            _cFormat = CoordinateFormat.find(_prefs.getString(
                    "coord_display_pref",
                    context.getString(R.string.coord_display_pref_default)));
        }

        _galleryAsAttachments = false;
        this.initialized = false;
    }

    /****************************** PUBLIC METHODS ****************************/

    public void setGalleryAsAttachments(boolean b) {
        _galleryAsAttachments = b;
    }

    public void setDropDown(DropDownReceiver ddr) {
        this.dropDown = ddr;
    }

    protected void sendSelected(final String uid) {

        // Make sure the object is shared since the user hit "Send".
        MapItem item = dropDown.getMapView().getRootGroup()
                .deepFindItem("uid", uid);
        if (item != null) {
            item.setMetaBoolean("shared", true);
        } else {
            Log.d(TAG, "cannot send item that is missing: " + uid);
            return;
        }

        Intent contactList = new Intent();
        contactList.setAction(ContactPresenceDropdown.SEND_LIST);
        contactList.putExtra("targetUID", uid);
        AtakBroadcast.getInstance().sendBroadcast(contactList);
    }

    public void setItem(MapView mapView, MapItem item) {
        _mapView = mapView;
        this.item = item;
        _init();
    }

    /**
     * *************************** PRIVATE METHODS ***************************
     */

    private void _init() {
        if (!this.initialized) {
            _nameEdit = this.findViewById(R.id.mapItemNameEdit);
            _remarksEdit = this
                    .findViewById(R.id.mapItemRemarksEdit);

            _rawCb = this.findViewById(R.id.mapItemRawRemarks);
            _rawCb.setChecked(_prefs.getBoolean("mapItemRawRemarks", false));

            _rawCb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView,
                        boolean isChecked) {
                    _prefs.edit()
                            .putBoolean("mapItemRawRemarks", isChecked).apply();
                    if (_prevRemarks != null) {
                        if (!isChecked) {
                            _remarksEdit.setText(Html
                                    .fromHtml(sanitize(_prevRemarks)));
                        } else {
                            _remarksEdit.setText(_prevRemarks);
                        }
                    }
                }
            });

            _noGps = this.findViewById(R.id.mapItemRangeBearingNoGps);
            rabtable = new RangeAndBearingTableHandler(this);
            _centerButton = this
                    .findViewById(R.id.mapItemCenterButton);
            _centerButton.setEnabled(false);
            _galleryButton = findViewById(
                    R.id.mapItemGalleryButton);
            _galleryButton.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Context context = v.getContext();

                    Toast.makeText(context,
                            context.getString(R.string.gallery_tip),
                            Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
            });
            _galleryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    _onGallerySelected();
                }
            });

            /*
            *************************** PRIVATE FIELDS ***************************
            */
            ImageButton _sendButton = this
                    .findViewById(R.id.mapItemSendButton);
            _sendExportView = this
                    .findViewById(R.id.mapItemSendExportView);
            _sendExportView.setVisibility(View.GONE);

            _sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    _onSendSelected();
                }
            });

            this.initialized = true;
        }

        // Save an instance of the name, so we know if it changed when the dropdown closes
        _prevName = ATAKUtilities.getDisplayName(this.item);
        _nameEdit.setText(_prevName);

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        _prevRemarks = this.item.getMetaString("remarks", "");
        if (!_rawCb.isChecked()) {
            _remarksEdit.setText(Html.fromHtml(sanitize(_prevRemarks)));
        } else {
            _remarksEdit.setText(_prevRemarks);
        }

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);
            rabtable.setVisibility(View.VISIBLE);
            rabtable.update(device, getLocation(item).get());
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        // keep the calculations and processes in the detail page, but disable the 
        // view for 3.2 as per JS.   Maybe this becomes a preference.
        _noGps.setVisibility(View.GONE);
        rabtable.setVisibility(View.GONE);

        final String p = CoordinateFormatUtilities.formatToString(
                getLocation(this.item).get(), _cFormat);
        final String a = AltitudeUtilities.format(getLocation(item).get(),
                _prefs);
        _centerButton.setText(p + "\n" + a);

        _galleryButton.setVisibility(item.hasMetaValue("attachments")
                ? View.VISIBLE
                : View.GONE);
    }

    /**
     * Remove the style tag that does not properly render in html.
     */
    private String sanitize(String gigo) {
        if (gigo == null)
            return null;

        int styleStart = gigo.indexOf("<style>");
        int styleEnd = gigo.indexOf("</style>");
        if (styleStart < 0 || styleEnd < 0) {
            return gigo;
        }

        return gigo.substring(0, styleStart) + gigo.substring(styleEnd + 8);
    }

    private void _onSendSelected() {
        sendSelected(this.item.getUID());
    }

    private void _onGallerySelected() {
        if (_galleryAsAttachments) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ImageGalleryReceiver.VIEW_ATTACHMENTS)
                            .putExtra("uid", item.getUID()));
        } else {
            ArrayList<String> attachments = item
                    .getMetaStringArrayList("attachments");

            if (attachments == null)
                attachments = new ArrayList<>();

            Intent viewAttachments = new Intent(
                    ImageGalleryReceiver.IMAGE_GALLERY)
                            .putExtra("uid", item.getUID())
                            .putExtra("title", _prevName + " Attachments")
                            .putExtra("uris",
                                    attachments.toArray(new String[] {}));

            AtakBroadcast.getInstance().sendBroadcast(viewAttachments);
        }
    }

    /**************************************************************************/

    private static GeoPointMetaData getLocation(MapItem item) {
        if (item instanceof PointMapItem) {
            return ((PointMapItem) item).getGeoPointMetaData();
        } else if (item instanceof AnchoredMapItem) {
            return ((AnchoredMapItem) item).getAnchorItem()
                    .getGeoPointMetaData();
        } else if (item instanceof com.atakmap.android.maps.Shape) {
            return ((com.atakmap.android.maps.Shape) item).getCenter();
        } else {
            // XXX - 
            return GeoPointMetaData.wrap(GeoPoint.ZERO_POINT);
        }
    }
}
