
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.atakmap.android.cot.detail.AddressDetailHandler;
import com.atakmap.android.cotdetails.CoTInfoView;
import com.atakmap.android.gui.ColorButton;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.CameraController;

public class GenericPointDetailsView extends GenericDetailsView implements
        PointMapItem.OnPointChangedListener {

    private static final String TAG = "GenericPointDetailsView";

    /*************************** PRIVATE FIELDS *****************************/
    private Button _coordButton;
    protected Marker _point;
    protected ImageButton _sendButton;
    private Boolean isChanged = false;

    protected View _addressLayout;
    protected TextView _addressText;
    protected TextView _addressInfoText;

    private TextView _authorText;
    private TextView _productionTimeText;
    private View cotAuthorLayout;
    private ImageView cotAuthorIconButton;
    private ImageView cotAuthorPanButton;

    /*************************** CONSTRUCTORS *****************************/

    public GenericPointDetailsView(Context context) {
        super(context);
    }

    public GenericPointDetailsView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    @Override
    public boolean setItem(MapView mapView, MapItem item) {
        if (!(item instanceof Marker))
            return false;
        setPoint(mapView, (Marker) item);
        return true;
    }

    public void setPoint(MapView mapView, Marker point) {
        super.setItem(mapView, point);
        _point = point;
        _init();
        formatAddress();
        point.addOnPointChangedListener(this);
    }

    /**
     * Callback for when the Dropdown closes. Save the point's meta data if something has
     * changed.
     */
    @Override
    public void onClose() {
        super.onClose();
        save();
    }

    public void save() {

        // nothing to save 
        if (_point == null)
            return;

        // Update the name if the user changed it.
        String name = _nameEdit.getText().toString();
        if (!name.equals(_prevName)) {
            _point.setMetaString("callsign", name);
            _point.setTitle(name);
            isChanged = true;
        }

        // Update the remarks if the user changed them.
        String remarks = _remarksLayout.getText();
        if (!remarks.equals(_prevRemarks)) {
            _point.setMetaString("remarks", remarks);
            isChanged = true;
        }
        if (isChanged) {
            _point.persist(MapView.getMapView().getMapEventDispatcher(), null,
                    Marker.class);
        }
        isChanged = false;
    }

    /**
     **************************** PRIVATE METHODS ***************************
     */

    protected void _init() {
        GenericDetailsView.addEditTextPrompts(this);
        _nameEdit = this.findViewById(R.id.drawingGenPointNameEdit);
        _remarksLayout = this.findViewById(R.id.remarksLayout);
        _noGps = this.findViewById(R.id.drawingGenPointRangeBearingNoGps);
        rabtable = new RangeAndBearingTableHandler(this);
        _coordButton = this
                .findViewById(R.id.drawingGenPointCoordButton);
        _heightButton = this
                .findViewById(R.id.drawingGenPointHeightButton);
        _colorButton = this
                .findViewById(R.id.drawingGenPointColorButton);
        _sendButton = this
                .findViewById(R.id.drawingGenPointSendButton);

        _authorText = this.findViewById(R.id.dgInfoAuthor);
        _productionTimeText = this
                .findViewById(R.id.dgInfoProductionTime);
        cotAuthorLayout = this.findViewById(R.id.dgAuthorLayout);
        cotAuthorIconButton = this
                .findViewById(R.id.dgAuthorIconButton);
        cotAuthorPanButton = this
                .findViewById(R.id.dgAuthorPanButton);

        CoTInfoView.refreshAuthorLayout(_mapView, _point, _authorText,
                _productionTimeText, cotAuthorLayout, cotAuthorIconButton,
                cotAuthorPanButton);

        _addressLayout = this.findViewById(R.id.dgAddressLayout);
        _addressText = this.findViewById(R.id.dgInfoAddress);
        _addressInfoText = this.findViewById(R.id.dgInfoAddressInfo);

        // Save an instance of the name, so we know if it changed when the dropdown closes
        if (_point != null)
            _prevName = _point.getTitle();
        else
            _prevName = "";

        _nameEdit.setText(_prevName);

        _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_point != null) {
                    _updateName();
                    _point.refresh(MapView.getMapView()
                            .getMapEventDispatcher(), null, this.getClass());
                }
            }
        });

        // Save an instance of the remarks, so we know if they changed when the dropdown closes
        if (_point != null) {
            _prevRemarks = _point.getMetaString("remarks", "");
        } else {
            _prevRemarks = "";
        }
        _remarksLayout.setText(_prevRemarks);

        _remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_point != null)
                    _updateRemarks();
            }
        });

        // Update the R & B text
        PointMapItem device = ATAKUtilities.findSelf(_mapView);
        // It's possible that we don't have GPS and therefore don't have a controller point
        if (device != null) {
            _noGps.setVisibility(View.GONE);
            rabtable.setVisibility(View.VISIBLE);
            rabtable.update(device, _point);
        } else {
            _noGps.setVisibility(View.VISIBLE);
            rabtable.setVisibility(View.GONE);
        }

        ImageButton _panButton = this
                .findViewById(R.id.drawingGenPanButton);
        if (_panButton != null) {
            _panButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (_point != null) {
                        GeoPoint gp = _point.getPoint();
                        CameraController.Programmatic.panTo(
                                _mapView.getRenderer3(), gp, false);
                    }
                }
            });
        }

        if (_point != null) {
            _coordButton.setText(_unitPrefs.formatPoint(
                    _point.getGeoPointMetaData(), true));
            _coordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    _onCoordSelected();
                }
            });
            double height = _point.getHeight();
            Span unit = _unitPrefs.getAltitudeUnits();
            if (!Double.isNaN(height)) {
                _heightButton.setText(SpanUtilities.format(height, Span.METER,
                        unit));
            } else {
                _heightButton.setText("-- " + unit.getAbbrev());
            }
        }

        _heightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _onHeightSelected();
            }
        });

        _updateColorButtonDrawable();

        _colorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _onColorSelected();
            }
        });

        _sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSelected(_point.getUID());
            }
        });
    }

    protected void _updateColorButtonDrawable() {
        int color = _point != null ? _point.getMetaInteger("color", 0)
                : Color.WHITE;
        if (_colorButton instanceof ColorButton)
            ((ColorButton) _colorButton).setColor(color);
        else
            _colorButton.setColorFilter(color);
    }

    private void _updateName() {

        String name = _nameEdit.getText().toString().trim();

        //Log.d(TAG, "update name called: " + name + " where the previous name was " + _prevName);

        // do not allow the callsign or the title to be set to the UID value
        if (_point != null && !name.equals(_prevName)
                && !name.equals(_point.getUID())) {
            // If the name changed then update it
            _point.setMetaString("callsign", name);
            _point.setTitle(name);
            _prevName = name;
        }
    }

    private void _updateRemarks() {

        String remarks = _remarksLayout.getText().trim();
        if (!remarks.equals(_prevRemarks)) {
            _prevRemarks = remarks;
            _point.setMetaString("remarks", remarks);
        }
    }

    @Override
    protected void _onColorSelected(int color, String label) {
        if (_point != null) {
            _point.setMetaInteger("color", color);
            _point.refresh(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
            _updateColorButtonDrawable();
        }
    }

    // show dialog box to enter the altitude
    private void _onCoordSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // custom view that allows entry of geo via mgrs, dms, and decimal degrees
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle(R.string.point_dropper_text19);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(_point.getGeoPointMetaData(),
                _mapView.getPoint(),
                _cFormat);

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = coordView.getPoint();
                        CoordinateFormat cf = coordView.getCoordFormat();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_UNCHANGED
                                && cf != _cFormat) {
                            // The coordinate format was changed but not the point itself
                            _coordButton
                                    .setText(coordView.getFormattedString());
                        }
                        _cFormat = cf;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {

                            setAddress(coordView, _point, _nameEdit);

                            _point.setPoint(p);
                            _coordButton
                                    .setText(coordView.getFormattedString());

                            CameraController.Programmatic.panTo(
                                    dropDown.getMapView().getRenderer3(),
                                    _point.getPoint(), true);
                        }
                        locDialog.dismiss();
                    }
                });

    }

    /**
     * Helper function used to set the address properly for the PointMapItem passed in.
     * used by rhe subclasses.   Also used by CotInfoView
     * @param coordView the coordinate view so that the human address can be retrieved.
     * @param _point the point map item to use
     * @param _nameEdit the name field that might be set to the address depending on the checkbox.
     */
    public static void setAddress(CoordDialogView coordView,
            PointMapItem _point, EditText _nameEdit) {
        if (coordView.isAddressPointChecked()) {
            String addr = coordView.getHumanAddress();
            if (addr != null && addr.length() > 0) {
                _point.setMetaString("callsign", addr);
                if (_point instanceof Marker)
                    _point.setTitle(addr);

                if (_nameEdit != null)
                    _nameEdit.setText(addr);
            }
        }
        String addr = coordView.getHumanAddress();
        if (addr != null && addr.length() > 0) {
            final GeoPointMetaData p = coordView.getPoint();

            // geopoint location must be equal to the current point
            // on the map for the address to be valid
            _point.setMetaString("address_geopoint",
                    AddressDetailHandler.locationHash(p.get()));
            _point.setMetaString("address_text", addr);

            String src = coordView.getAddressLookupSource();
            if (src == null)
                src = "unknown";
            _point.setMetaString("address_geocoder", src);
            _point.setMetaString("address_lookuptime",
                    new CoordinatedTime().toString());
        }
    }

    /** 
     * Helper function used to set the address properly for the PointMapItem passed in.
     * used by rhe subclasses.   Also used by CotInfoView
     * @param m the point map item to get the address from.
     * @param _addressText the actual textview used to display the address
     * @param _addressLayout the layout that contains the address and the address title.
     */
    public static void controlAddressUI(final PointMapItem m,
            final TextView _addressText,
            final TextView _addressInfoText,
            final View _addressLayout) {
        if (m == null)
            return;

        if (_addressLayout == null || _addressText == null) {
            Log.e(TAG, "error with layout initialization", new Exception());
            return;
        }

        final String address_geopoint = m.getMetaString("address_geopoint",
                null);
        final String address_text = m.getMetaString("address_text", null);
        final String address_geocoder = m.getMetaString("address_geocoder",
                null);
        final String address_lookuptime = m.getMetaString("address_lookuptime",
                null);

        MapView.getMapView().post(new Runnable() {
            public void run() {
                if (address_geopoint != null && address_text != null) {
                    if (address_geopoint.equals(
                            AddressDetailHandler.locationHash(m.getPoint()))) {
                        _addressText.setText(address_text);

                        if (_addressInfoText != null) {
                            if (address_geocoder != null
                                    && address_lookuptime != null) {
                                _addressInfoText.setText(address_geocoder + " ("
                                        + address_lookuptime + ")");
                                _addressInfoText.setVisibility(View.VISIBLE);
                            } else if (address_geocoder != null) {
                                _addressInfoText.setText(address_geocoder);
                                _addressInfoText.setVisibility(View.VISIBLE);
                            } else if (address_lookuptime != null) {
                                _addressInfoText
                                        .setText(
                                                "(" + address_lookuptime + ")");
                                _addressInfoText.setVisibility(View.VISIBLE);
                            } else {
                                _addressInfoText.setVisibility(View.GONE);
                            }
                        }
                        _addressLayout.setVisibility(View.VISIBLE);
                        return;
                    }
                }
                _addressLayout.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void refresh() {
    }

    @Override
    protected void _onHeightSelected() {
        createHeightDialog(_point, R.string.enter_point_height, null);
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (item != _point) {
            item.removeOnPointChangedListener(this);
        } else {
            post(new Runnable() {
                @Override
                public void run() {
                    String c = CoordinateFormatUtilities.formatToString(
                            _point.getPoint(), _cFormat);
                    String a = EGM96.formatMSL(_point.getPoint());
                    _coordButton.setText(c + "\n" + a);
                    formatAddress();
                }
            });
        }
    }

    protected final void formatAddress() {
        controlAddressUI(_point, _addressText, _addressInfoText,
                _addressLayout);
    }

}
