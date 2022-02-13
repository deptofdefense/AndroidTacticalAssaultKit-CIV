
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputFilter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.PointOfInterest;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.elevation.ElevationManager;

public class FastMGRS {

    private final static String TAG = "FastMGRS";
    final private View v;
    final private Context context;
    final private MapView mapView;
    final private AlertDialog ad;
    final private EditText gs;
    final private EditTextWithKeyPadDismissEvent en;
    final private CheckBox cb;
    final private DialogInterface.OnClickListener docl;
    final private OnEnterListener _listener;

    static private String removeSpace(String text) {
        return text.replaceAll(" ", "");
    }

    static private String addSpace(String text) {
        text = text.replaceAll(" ", "");
        final int mid = text.length() / 2;
        return text.substring(0, mid) + " " + text.substring(mid);
    }

    public FastMGRS(final MapView mapView, boolean spi,
            OnEnterListener listener) {
        this.context = mapView.getContext();
        this.mapView = mapView;
        LayoutInflater inflater = LayoutInflater.from(context);
        v = inflater.inflate(R.layout.fast_mgrs_coord, null);
        gs = v.findViewById(R.id.gridSquare);
        en = v
                .findViewById(R.id.eastingNorthing);
        cb = v.findViewById(R.id.dropSpi);
        cb.setText(ResourceUtil.getResource(R.string.civ_place_local_spi,
                R.string.place_local_spi));
        cb.setChecked(spi);
        cb.setVisibility(spi ? View.VISIBLE : View.GONE);

        _listener = listener;

        en.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    setMaxLength(en, 11);
                    en.setText(addSpace(en.getText().toString()));
                    InputMethodManager imm = (InputMethodManager) context
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null)
                        imm.hideSoftInputFromWindow(en.getWindowToken(), 0);
                    en.clearFocus();
                    if (docl != null) {
                        docl.onClick(ad, 0);
                    }
                    return true;
                } else {
                    return false;
                }

            }
        });

        en.set_keyPadDismissListener(
                new EditTextWithKeyPadDismissEvent.KeyPadDismissListener() {
                    @Override
                    public void onKeyPadDismissed(EditText who) {
                        setMaxLength(en, 11);
                        en.setText(addSpace(en.getText().toString()));
                        en.clearFocus();
                    }
                });

        en.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setMaxLength(en, 11);
                    en.setText(addSpace(en.getText().toString()));
                } else {
                    setMaxLength(en, 10);
                    en.setText(removeSpace(en.getText().toString()));
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(
                        (spi) ? context.getString(R.string.mgrs_goto)
                                : context.getString(R.string.mgrs_location))
                .setCancelable(false)
                .setView(v)
                .setNegativeButton(context.getString(R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.ok),
                        docl = new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {

                                GeoPoint gp = null;

                                final String ctext = removeSpace(en.getText()
                                        .toString());
                                if (ctext.length() > 1) {
                                    try {
                                        gp = CoordinateFormatUtilities
                                                .convert(
                                                        gs.getText()
                                                                .toString()
                                                                .toUpperCase(
                                                                        LocaleUtil
                                                                                .getCurrent())
                                                                + ctext,
                                                        CoordinateFormat.MGRS);
                                    } catch (Exception e) {
                                        Log.e(TAG, "error creating a geopoint",
                                                e);
                                    }
                                }

                                if (gp != null) {
                                    CameraController.Programmatic.panTo(
                                            mapView.getRenderer3(),
                                            gp, false);
                                }

                                if (cb.isChecked() && gp != null) {
                                    PointOfInterest poi = PointOfInterest
                                            .getInstance();
                                    // Include DTED elevation
                                    poi.setPoint(ElevationManager
                                            .getElevationMetadata(gp));
                                }

                                if (_listener != null && gp != null)
                                    _listener.onEnter(GeoPointMetaData.wrap(gp,
                                            GeoPointMetaData.USER,
                                            GeoPointMetaData.USER));
                                dialog.dismiss();
                            }
                        });
        ad = builder.create();
    }

    /**
     * Construct a FastMGRS ui with the sole purpose of dropping a visual SPI at that 
     * location.
     */
    public FastMGRS(MapView mapView, boolean spi) {
        this(mapView, spi, null);
    }

    private void setMaxLength(EditText et, int length) {
        et.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(length)
        });
    }

    public void show() {
        final GeoPointMetaData center = mapView.getPoint();
        String[] c = CoordinateFormatUtilities.formatToStrings(center.get(),
                CoordinateFormat.MGRS);
        gs.setText(String.format(LocaleUtil.US, "%s%s", c[0], c[1])
                .toUpperCase(LocaleUtil.getCurrent()));
        en.post(new Runnable() {
            @Override
            public void run() {
                en.requestFocusFromTouch();
                en.selectAll();
                InputMethodManager imm = (InputMethodManager) context
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.showSoftInput(en, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        ad.show();
    }

    // Listener called when point has been entered
    public interface OnEnterListener {
        void onEnter(GeoPointMetaData gp);
    }
}
