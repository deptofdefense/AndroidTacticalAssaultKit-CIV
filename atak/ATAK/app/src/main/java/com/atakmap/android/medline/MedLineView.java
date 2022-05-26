
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import com.atakmap.android.gui.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.AdapterView;
import android.widget.Toast;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.gui.ActionButton;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.PluginSpinner;
import com.atakmap.android.gui.PlusMinusWidget;
import com.atakmap.android.gui.Selector;
import com.atakmap.android.gui.Selector.OnSelectionChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.DialogConstructor;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.util.AttachmentManager;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

public class MedLineView implements PointMapItem.OnPointChangedListener {

    public static final String TAG = "MedLineView";

    private static final String[] locationTypeArray = new String[4];

    private TextView m_Title;
    private ActionButton m_BtnLineOne;
    private ActionButton m_BtnLineTwo_callsign;
    private ActionButton m_BtnLineTwo_freq;
    private ActionButton m_BtnLineThree;
    private ActionButton m_BtnLineFour;
    private EditText m_TxtLineFour;
    private ActionButton m_BtnLineFive;
    private Selector m_BtnLineSix;
    private Selector m_BtnLineSeven;
    private EditText m_TxtLineSeven;
    private ActionButton m_BtnLineEight;
    private ActionButton m_BtnLineNine;
    private EditText m_TxtLineNine;
    private RemarksLayout remarksLayout;
    private PointMapItem marker;
    private LayoutInflater inflater;
    private ImageButton _attachmentsButton;
    private ImageButton send;

    private AttachmentManager attachmentManager;

    private View view;

    private ZMistView zMistView;
    private HLZView hlzView;

    private final MapView _mapView;
    private final Context _context;
    protected static MedLineView _instance;
    private boolean init = true;
    private boolean reopening = false;
    private CoordinateFormat _cFormat = CoordinateFormat.MGRS;
    private final SharedPreferences _prefs;
    public static final String PREF_MEDLINE_FREQ = "com.atakmap.android.medline.pref_medline_freq";
    public static final String PREF_MEDLINE_CALLSIGN = "com.atakmap.android.medline.pref_medline_callsign";

    private static final ThreadLocal<SimpleDateFormat> d_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("d", LocaleUtil.getCurrent());
        }
    };
    private static final ThreadLocal<SimpleDateFormat> HHmm_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HHmmss", LocaleUtil.getCurrent());
        }
    };

    // for the choices to send
    final TileButtonDialog tileButtonDialog;

    /**
     * When a plugin wants to send a Casevac/Medevac digitally using a transport method other than CoT
     * (such as VMF), the plugin should register a runnable with this class.
     * In the future we will be deprecating this feature in favor of using the contact list.
     */
    public interface ExternalMedevacProcessor {
        boolean processMedevac(CotEvent ce);
    }

    private final Map<ExternalMedevacProcessor, TileButtonDialog.TileButton> eflpList = new HashMap<>();

    /**
     * Installs and external Medevac Processor.
     * @param icon the icon used when the selection dialog is shown.
     * @param txt the text that appears under the icon.
     * @param eflp the External Medevac Processor implementation.
     */
    synchronized public void addExternalMedevacProcessor(final Drawable icon,
            String txt, final ExternalMedevacProcessor eflp) {
        TileButtonDialog.TileButton tb = tileButtonDialog.createButton(icon,
                txt);
        tb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMedevacEvent(eflp);
            }
        });
        tileButtonDialog.addButton(tb);
        eflpList.put(eflp, tb);
    }

    /**
     * Returns true if the number of external Medevac processors is greater than 0.
     */
    synchronized public boolean hasExternalMedevacProcessors() {
        return eflpList.size() > 0;
    }

    /**
     * Removes an External Medevac Processor.
     * @param eflp the External Medevac Processor.
     * processor.
     */
    synchronized public void removeExternalMedevacProcessor(
            final ExternalMedevacProcessor eflp) {
        TileButtonDialog.TileButton tb = eflpList.get(eflp);
        if (tb != null)
            tileButtonDialog.removeButton(tb);
        eflpList.remove(eflp);
    }

    private void sendMedevacEvent(final ExternalMedevacProcessor eflp) {

        boolean success = false;

        try {
            if (eflp != null) {
                success = eflp.processMedevac(CotEventFactory
                        .createCotEvent(marker));
            }
        } catch (Exception e) {
            // guarding against null above reveals a NPE in generateFriendlyEvent
            // since at this point the information might be invalid, show the
            // error in logcat and do not allow ATAK to crash.
            // please revisit.
            Log.e(TAG, "error: ", e);
        }
        if (!success) {
            Toast.makeText(_mapView.getContext(),
                    "Medevac failed to send",
                    Toast.LENGTH_SHORT).show();
        }
    }

    synchronized public static MedLineView getInstance(final MapView mapView) {
        if (_instance == null) {
            _instance = new MedLineView(mapView);
        }
        return _instance;
    }

    protected MedLineView(final MapView mapView) {
        this._mapView = mapView;
        _context = _mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        try {
            init();
        } catch (Exception e) {
            Log.d(TAG, "catch against bad call to init()", e);
        }

        tileButtonDialog = new TileButtonDialog(mapView);
        TileButtonDialog.TileButton tb = tileButtonDialog
                .createButton(
                        mapView.getContext()
                                .getResources()
                                .getDrawable(
                                        com.atakmap.app.R.drawable.send_square),
                        "Share");
        tb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachmentManager.send();
            }
        });
        tileButtonDialog.addButton(tb);

    }

    public boolean getReopening() {
        return reopening;
    }

    public void toggleReopening() {
        reopening = !reopening;
    }

    public PointMapItem getMarker() {
        return marker;
    }

    public boolean setMarker(final PointMapItem pmi) {
        attachmentManager.cleanup();
        if (marker != null) {
            shutdown(true);
            reopening = true;
        }
        marker = pmi;
        attachmentManager.setMapItem(marker);

        marker.addOnPointChangedListener(this);
        updateLocation();

        zMistView.setMarker(marker);
        hlzView.setMarker(marker);

        loadData();

        return true;
    }

    private void init() {

        inflater = (LayoutInflater) _mapView.getContext().getSystemService(
                Service.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.medic_9line, null, false);

        hlzView = view.findViewById(R.id.hlz_field);
        hlzView.init(_mapView, this);

        //Title
        m_Title = view.findViewById(R.id.med_title);

        m_Title.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldTitle = m_Title.getText().toString();
                input.setText(oldTitle);
                input.setSelection(oldTitle.length());
                input.setSelectAllOnFocus(true);
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                AlertDialog.Builder ad = new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getResources().getString(
                                        R.string.enter_title_dialogue))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            m_Title.setText(input
                                                    .getText().toString());
                                            saveData();
                                        } else
                                            m_Title.setText(oldTitle);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, // implemented
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        m_Title.setText(oldTitle);
                                    }
                                });
                AlertDialog dialog = ad.create();
                dialog.getWindow()
                        .setSoftInputMode(
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                        |
                                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                dialog.show();
            }
        });

        //Line 1 Location
        m_BtnLineOne = new ActionButton(view.findViewById(R.id.med_lineOne));
        m_BtnLineOne.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                editLocation();
            }
        });
        updateLocation();

        //Center on button
        final ImageButton centerOn = view
                .findViewById(R.id.centerOnButton);

        centerOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (marker != null) {
                    GeoPoint gp = marker.getPoint();
                    CameraController.Programmatic.panTo(
                            _mapView.getRenderer3(),
                            gp, false);
                }
            }
        });

        //Line 2
        m_BtnLineTwo_callsign = new ActionButton(
                view.findViewById(R.id.med_lineTwo_callsign)); // callsign of sender
        m_BtnLineTwo_callsign
                .setText(_prefs.getString(PREF_MEDLINE_CALLSIGN, "Med9Line"));

        m_BtnLineTwo_callsign.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldCallsign = m_BtnLineTwo_callsign.getText();
                input.setText(oldCallsign);
                input.setSelection(oldCallsign.length());
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                AlertDialog.Builder ad = new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(
                                        R.string.callsign_enter_dialogue))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            String medlineCallsign = input
                                                    .getText().toString();
                                            m_BtnLineTwo_callsign
                                                    .setText(medlineCallsign);
                                            _prefs.edit().putString(
                                                    PREF_MEDLINE_CALLSIGN,
                                                    medlineCallsign).apply();
                                            saveData();
                                        } else
                                            m_BtnLineTwo_callsign
                                                    .setText(oldCallsign);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, // implemented
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        m_BtnLineTwo_callsign
                                                .setText(oldCallsign);
                                    }
                                });
                AlertDialog dialog = ad.create();
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                dialog.show();

            }
        });

        m_BtnLineTwo_freq = new ActionButton(
                view.findViewById(R.id.med_lineTwo_freq));
        m_BtnLineTwo_freq.setText(_prefs.getString(PREF_MEDLINE_FREQ, "0.0"));

        m_BtnLineTwo_freq.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                input.setSelectAllOnFocus(true);
                final String oldFreq = m_BtnLineTwo_freq.getText();
                input.setText(oldFreq);

                AlertDialog.Builder ad = new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.frequency_dialogue))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            String medlineFreq = input.getText()
                                                    .toString();
                                            m_BtnLineTwo_freq
                                                    .setText(medlineFreq);
                                            _prefs.edit()
                                                    .putString(
                                                            PREF_MEDLINE_FREQ,
                                                            medlineFreq)
                                                    .apply();
                                            saveData();
                                        } else
                                            m_BtnLineTwo_freq.setText(oldFreq);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, // implemented
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        m_BtnLineTwo_freq.setText(oldFreq);
                                    }
                                });

                AlertDialog dialog = ad.create();
                Window w = dialog.getWindow();
                if (w != null)
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                dialog.show();
            }
        });

        //Line 3
        m_BtnLineThree = new ActionButton(
                view.findViewById(R.id.med_lineThree)); // number of patients

        // number of patients
        final String[] precedenceArray = {
                "urgent", "priority", "routine"
        };
        final String[] precedenceLetters = {
                "A", "B", "C", "D", "E", "F"
        };
        m_BtnLineThree.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                final View input = inflater.inflate(
                        R.layout.medic_patient_precedence, null, false);
                if (marker.getMetaString("urgent", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_urgent_edit))
                            .setText(marker.getMetaString("urgent", null));

                if (marker.getMetaString("priority", null) != null)
                    ((PlusMinusWidget) input
                            .findViewById(R.id.med_priority_edit))
                                    .setText(marker.getMetaString("priority",
                                            null));

                if (marker.getMetaString("routine", null) != null)
                    ((PlusMinusWidget) input
                            .findViewById(R.id.med_routine_edit))
                                    .setText(marker.getMetaString("routine",
                                            null));

                new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.precedence))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {

                                        final RelativeLayout item = input
                                                .findViewById(
                                                        R.id.med_patient_precedence_rl);

                                        int pointer = 0;
                                        boolean first = true;

                                        StringBuilder sBuilder = new StringBuilder();
                                        for (int i = 0; i < item
                                                .getChildCount(); i++) {
                                            View v = item.getChildAt(i);
                                            if (v.getClass() == PlusMinusWidget.class) {

                                                String result = ((PlusMinusWidget) input
                                                        .findViewById(
                                                                v.getId()))
                                                                        .getText();
                                                if (result.trim().length() > 0
                                                        && !result.trim()
                                                                .equals("0")) {
                                                    if (!first)
                                                        sBuilder.append(", ");

                                                    if (first)
                                                        first = false;

                                                    sBuilder.append(result);
                                                    sBuilder.append("x");
                                                    sBuilder.append(
                                                            precedenceLetters[pointer]);

                                                    marker.setMetaString(
                                                            precedenceArray[pointer],
                                                            result);
                                                }
                                                pointer++;
                                            }
                                        }
                                        if (sBuilder.toString().trim()
                                                .length() > 0) {
                                            m_BtnLineThree.setText(sBuilder
                                                    .toString().trim());
                                        } else {
                                            m_BtnLineThree
                                                    .setText(view
                                                            .getResources()
                                                            .getString(
                                                                    R.string.precedence));
                                        }
                                        saveData();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        //Line Four
        m_BtnLineFour = new ActionButton(
                view.findViewById(R.id.med_lineFour));
        m_BtnLineFour.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.medic_special_equipment, null, false);

                ((CheckBox) input.findViewById(R.id.med_none))
                        .setChecked(
                                marker.getMetaBoolean("equipment_none", false));

                ((CheckBox) input.findViewById(R.id.med_hoist))
                        .setChecked(marker.getMetaBoolean("hoist", false));

                ((CheckBox) input.findViewById(R.id.med_extraction_equipment))
                        .setChecked(marker.getMetaBoolean(
                                "extraction_equipment", false));

                ((CheckBox) input.findViewById(R.id.med_ventilator))
                        .setChecked(marker.getMetaBoolean("ventilator", false));

                ((CheckBox) input.findViewById(R.id.med_equipment_other))
                        .setChecked(marker.getMetaBoolean("equipment_other",
                                false));

                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.equipment_dialogue))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        RelativeLayout item = input
                                                .findViewById(
                                                        R.id.med_special_equipment_r1);
                                        StringBuilder sBuilder = new StringBuilder();
                                        for (int i = 1; i < item
                                                .getChildCount(); i++) {
                                            View v = item.getChildAt(i);
                                            if (v.getClass() != LinearLayout.class) {
                                                if (((CheckBox) input
                                                        .findViewById(
                                                                v.getId()))
                                                                        .isChecked()) {
                                                    sBuilder.append(
                                                            ((CheckBox) v)
                                                                    .getText()
                                                                    .toString());
                                                    sBuilder.append("\n");
                                                }
                                            }
                                        }
                                        if (sBuilder.toString().trim()
                                                .length() > 0) {
                                            m_BtnLineFour.setText(sBuilder
                                                    .toString().trim());
                                        } else {
                                            m_BtnLineFour
                                                    .setText(_context.getString(
                                                            R.string.equipment_none_choice));
                                        }
                                        if (sBuilder.toString()
                                                .contains(_context.getString(
                                                        R.string.equipment_other_choice))) {
                                            m_TxtLineFour.setVisibility(
                                                    View.VISIBLE);
                                        } else {
                                            m_TxtLineFour
                                                    .setVisibility(View.GONE);
                                        }
                                        saveData();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        this.m_TxtLineFour = view.findViewById(R.id.txtLineFour);
        m_TxtLineFour.setFocusable(false);
        m_TxtLineFour.setFocusableInTouchMode(false);
        m_TxtLineFour.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogConstructor.buildDialog(_context,
                        m_TxtLineFour,
                        "equipment_detail",
                        _context.getString(R.string.equipment_other_choice),
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        true,
                        marker);
            }
        });

        //Line 5
        m_BtnLineFive = new ActionButton(
                view.findViewById(R.id.med_lineFive)); // number of patients by type

        // number of patients by type
        m_BtnLineFive.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                final View input = inflater.inflate(
                        R.layout.medic_patient_type, null, false);

                if (marker.getMetaString("litter", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_litter_edit))
                            .setText(marker.getMetaString("litter", null));

                if (marker.getMetaString("ambulatory", null) != null)
                    ((PlusMinusWidget) input
                            .findViewById(R.id.med_ambulatory_edit))
                                    .setText(marker.getMetaString("ambulatory",
                                            null));

                new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.patientType))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        boolean first = true;
                                        RelativeLayout item = input
                                                .findViewById(
                                                        R.id.patienttype_rl);
                                        String[] title = {
                                                "Litter", "Ambulatory"
                                        };
                                        int pointer = 0;

                                        StringBuilder sBuilder = new StringBuilder();
                                        for (int i = 0; i < item
                                                .getChildCount(); i++) {
                                            View v = item.getChildAt(i);
                                            if (v.getClass() == PlusMinusWidget.class) {
                                                String result = ((PlusMinusWidget) input
                                                        .findViewById(
                                                                v.getId()))
                                                                        .getText();
                                                if (result.trim().length() > 0
                                                        && !result.trim()
                                                                .equals("0")) {
                                                    if (!first)
                                                        sBuilder.append(", ");
                                                    if (first)
                                                        first = false;

                                                    sBuilder.append(
                                                            title[pointer]);
                                                    sBuilder.append(" x ");
                                                    sBuilder.append(result);

                                                    marker.setMetaString(
                                                            title[pointer]
                                                                    .toLowerCase(
                                                                            LocaleUtil
                                                                                    .getCurrent()),
                                                            result);
                                                }
                                                pointer++;
                                            }
                                        }
                                        if (sBuilder.toString().trim()
                                                .length() > 0) {
                                            m_BtnLineFive.setText(sBuilder
                                                    .toString().trim());
                                        } else {
                                            m_BtnLineFive
                                                    .setText(_context
                                                            .getResources()
                                                            .getString(
                                                                    R.string.patientType));
                                        }

                                        saveData();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();

            }
        });

        final OnSelectionChangedListener oscl = new OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(String selectionText,
                    int selectedIndex) {
                // if the selection change was a result of initialization, ignore.
                if (!init) {
                    saveData();
                    hlzView.updateEnemy(selectionText);
                }
            }
        };

        //Line 6
        m_BtnLineSix = new Selector(_context,
                view.findViewById(R.id.med_lineSix),
                view.getResources().getStringArray(R.array.security)); // security at pick up site

        m_BtnLineSix.setTitle("Security at Pick-up Site");
        m_BtnLineSix.setTitleVisible(false);
        m_BtnLineSix.setOnSelectionChangedListener(oscl);

        //Line 7
        m_BtnLineSeven = new Selector(_context,
                view.findViewById(R.id.med_lineSeven),
                view.getResources().getStringArray(R.array.hlz_marking));
        m_BtnLineSeven.setTitle("Method of Marking Pick-up Site");
        m_BtnLineSeven.setTitleVisible(false);
        m_BtnLineSeven.setOnSelectionChangedListener(
                new OnSelectionChangedListener() {
                    @Override
                    public void onSelectionChanged(String selectionText,
                            int selectedIndex) {
                        if (selectionText.equals(_context
                                .getString(
                                        R.string.hlz_marking_other_choice))) {
                            m_TxtLineSeven.setVisibility(View.VISIBLE);
                        } else {
                            m_TxtLineSeven.setVisibility(View.GONE);
                        }
                        if (!init) {
                            hlzView.updateMarkedBy(getMarkedBy());
                            saveData();
                        }
                    }
                });

        this.m_TxtLineSeven = view.findViewById(R.id.txtLineSeven);
        m_TxtLineSeven.setFocusable(false);
        m_TxtLineSeven.setFocusableInTouchMode(false);
        m_TxtLineSeven.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogConstructor.buildDialog(_context,
                        m_TxtLineSeven,
                        "hlz_other",
                        _context.getString(R.string.hlz_marking_other_choice),
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        true,
                        marker);
            }
        });
        // still need code that updates hlzView if the m_TxtLineSeven changes

        //Line 8
        m_BtnLineEight = new ActionButton(
                view.findViewById(R.id.med_lineEight)); // patient nationality
        m_BtnLineEight.setText("Patient by Nationality");

        remarksLayout = view.findViewById(R.id.remarksLayout);
        remarksLayout.setHint(_context.getString(R.string.remarks_hint));

        // nationality
        final String[] title = {
                "us_military", "us_civilian", "nonus_military",
                "nonus_civilian", "epw", "child"
        };
        m_BtnLineEight.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.medic_patient_nationality, null, false);

                if (marker.getMetaString("us_military", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_us_mil_edit))
                            .setText(marker.getMetaString("us_military", null));

                if (marker.getMetaString("us_civilian", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_us_civ_edit))
                            .setText(marker.getMetaString("us_civilian", null));

                if (marker.getMetaString("nonus_civilian", null) != null)
                    ((PlusMinusWidget) input
                            .findViewById(R.id.med_non_us_civ_edit))
                                    .setText(marker.getMetaString(
                                            "nonus_civilian",
                                            null));

                if (marker.getMetaString("nonus_military", null) != null)
                    ((PlusMinusWidget) input
                            .findViewById(R.id.med_non_us_mil_edit))
                                    .setText(marker.getMetaString(
                                            "nonus_military",
                                            null));

                if (marker.getMetaString("epw", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_epw_edit))
                            .setText(marker.getMetaString("epw", null));

                if (marker.getMetaString("child", null) != null)
                    ((PlusMinusWidget) input.findViewById(R.id.med_child_edit))
                            .setText(marker.getMetaString("child", null));

                new AlertDialog.Builder(_context)
                        .setMessage(R.string.line8_dialogue)
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {

                                        RelativeLayout item = input
                                                .findViewById(
                                                        R.id.med_patient_national_rl);
                                        int pointer = 0;
                                        boolean first = true;

                                        StringBuilder sBuilder = new StringBuilder();
                                        for (int i = 0; i < item
                                                .getChildCount(); i++) {
                                            View v = item.getChildAt(i);
                                            if (v.getClass() == PlusMinusWidget.class) {
                                                String result = ((PlusMinusWidget) input
                                                        .findViewById(
                                                                v.getId()))
                                                                        .getText();
                                                if (result.trim().length() > 0
                                                        && !result.trim()
                                                                .equals("0")) {
                                                    // temp[0].toLowerCase(LocaleUtil.getCurrent()).replaceAll("-,", "") + "_" +
                                                    // temp[1].toLowerCase(LocaleUtil.getCurrent())
                                                    if (!first)
                                                        sBuilder.append(", ");

                                                    if (first)
                                                        first = false;

                                                    sBuilder.append(result);
                                                    sBuilder.append("x");
                                                    sBuilder.append(
                                                            precedenceLetters[pointer]);

                                                    marker.setMetaString(
                                                            title[pointer],
                                                            result);
                                                }
                                                pointer++;
                                            }
                                        }
                                        if (sBuilder.toString().trim()
                                                .length() > 0) {
                                            m_BtnLineEight.setText(sBuilder
                                                    .toString().trim());
                                        } else {
                                            m_BtnLineEight.setText(view
                                                    .getResources().getString(
                                                            R.string.nation));
                                        }

                                        saveData();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();

            }
        });

        //Line 9
        m_BtnLineNine = new ActionButton(
                view.findViewById(R.id.med_lineNine));
        m_BtnLineNine.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.medic_hlz_terrain, null, false);

                ((CheckBox) input.findViewById(R.id.hlz_none))
                        .setChecked(
                                marker.getMetaBoolean("terrain_none", false));

                ((CheckBox) input.findViewById(R.id.hlz_slope))
                        .setChecked(
                                marker.getMetaBoolean("terrain_slope", false));
                final ArrayAdapter<CharSequence> directionAdapter = ArrayAdapter
                        .createFromResource(_context,
                                R.array.cardinal_direction,
                                android.R.layout.simple_spinner_dropdown_item);
                final PluginSpinner slopeDirection = input
                        .findViewById(R.id.slope_direction);

                slopeDirection.setOnItemSelectedListener(
                        new SimpleItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> arg0,
                                    View v,
                                    int pos, long id) {
                                if (v instanceof TextView)
                                    ((TextView) v).setTextColor(Color.WHITE);
                            }
                        });

                slopeDirection.setAdapter(directionAdapter);
                String currentDirection = marker
                        .getMetaString("terrain_slope_dir", "N");
                int pos = directionAdapter.getPosition(currentDirection);
                slopeDirection.setSelection(pos);

                ((CheckBox) input.findViewById(R.id.hlz_rough))
                        .setChecked(
                                marker.getMetaBoolean("terrain_rough", false));

                ((CheckBox) input.findViewById(R.id.hlz_loose_sand))
                        .setChecked(
                                marker.getMetaBoolean("terrain_loose", false));

                ((CheckBox) input.findViewById(R.id.hlz_terrain_other))
                        .setChecked(
                                marker.getMetaBoolean("terrain_other", false));

                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.line9_dialogue))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        RelativeLayout item = input
                                                .findViewById(
                                                        R.id.med_hlz_terrain_rl);
                                        StringBuilder sBuilder = new StringBuilder();

                                        // special case the terrain slope nested child item
                                        // will do it first since it's the first item after None
                                        CheckBox terrainSlope = input
                                                .findViewById(R.id.hlz_slope);
                                        if (terrainSlope.isChecked()) {
                                            sBuilder.append(terrainSlope
                                                    .getText().toString());
                                            sBuilder.append(" ");
                                            PluginSpinner slopeDir = input
                                                    .findViewById(
                                                            R.id.slope_direction);
                                            sBuilder.append(slopeDir
                                                    .getItemAtPosition(slopeDir
                                                            .getSelectedItemPosition()));
                                            sBuilder.append("\n");
                                        }

                                        for (int i = 1; i < item
                                                .getChildCount(); i++) {
                                            View v = item.getChildAt(i);
                                            if (v.getClass() != LinearLayout.class) {
                                                if (((CheckBox) input
                                                        .findViewById(
                                                                v.getId()))
                                                                        .isChecked()) {
                                                    sBuilder.append(
                                                            ((CheckBox) v)
                                                                    .getText()
                                                                    .toString());
                                                    sBuilder.append("\n");
                                                }
                                            }
                                        }
                                        if (sBuilder.toString().trim()
                                                .length() > 0) {
                                            m_BtnLineNine.setText(sBuilder
                                                    .toString().trim());
                                        } else {
                                            m_BtnLineNine
                                                    .setText(_context.getString(
                                                            R.string.terrain_none_choice));
                                        }
                                        if (sBuilder.toString()
                                                .contains(_context.getString(
                                                        R.string.terrain_other_choice))) {
                                            m_TxtLineNine.setVisibility(
                                                    View.VISIBLE);
                                        } else {
                                            m_TxtLineNine
                                                    .setVisibility(View.GONE);
                                        }
                                        updateHLZObstacles();
                                        saveData();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        this.m_TxtLineNine = view.findViewById(R.id.txtLineNine);
        m_TxtLineNine.setFocusable(false);
        m_TxtLineNine.setFocusableInTouchMode(false);
        m_TxtLineNine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogConstructor.buildDialog(_context,
                        m_TxtLineNine,
                        "terrain_other_detail",
                        _context.getString(R.string.line9_dialogue),
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        true,
                        marker);
            }
        });
        m_TxtLineNine.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!init) {
                    updateHLZObstacles();
                }
            }
        });

        _attachmentsButton = view
                .findViewById(R.id.cotInfoAttachmentsButton);

        attachmentManager = new AttachmentManager(_mapView, _attachmentsButton);

        send = view.findViewById(R.id.med_sendBtn);
        send.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveData();
                if (!hasExternalMedevacProcessors()) {
                    attachmentManager.send();
                } else {
                    tileButtonDialog.show("Options", "");
                }

            }
        });

        View readout = view.findViewById(R.id.readoutButton);

        readout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createReadout();
            }
        });

        zMistView = view.findViewById(R.id.zmist_field);
        zMistView.init(_mapView);
    }

    private void updateHLZObstacles() {
        hlzView.updateObstacles(getHLZObstacles());
    }

    private void createReadout() {
        GenerateReadout.create9LineReadout(this, _context);
    }

    private void loadData() {
        init = true;

        ((ScrollView) view.findViewById(R.id.med_scroll))
                .fullScroll(ScrollView.FOCUS_UP);

        String medlineFreq = _prefs.getString(PREF_MEDLINE_FREQ, "0.0");
        String medlineCallsign = _prefs.getString(PREF_MEDLINE_CALLSIGN,
                "Med9Line");
        m_Title.setText("CASEVAC");
        m_BtnLineTwo_callsign
                .setText(marker.getMetaString("callsign", medlineCallsign));
        m_BtnLineTwo_freq.setText(marker.getMetaString("freq", medlineFreq));
        m_BtnLineThree.setText("Patient by Precedence");
        m_BtnLineFour
                .setText(_context.getString(R.string.equipment_none_choice));
        m_TxtLineFour.setText("");
        m_BtnLineFive.setText("Patient by Type");
        m_BtnLineSix.setSelection(0);
        m_BtnLineSeven.setSelection(3);
        m_TxtLineSeven.setText("");
        m_BtnLineEight.setText("Patients by Nationality");
        m_BtnLineNine.setText(_context.getString(R.string.terrain_none_choice));
        m_TxtLineNine.setText("");

        try {
            //Title
            if (marker.getMetaString("title", null) != null) {
                String med_title = marker.getMetaString("title", null);
                m_Title.setText(med_title);
            }
            //Line 2 Values
            if (marker.getMetaString("callsign", null) != null)
                m_BtnLineTwo_callsign.setText(
                        marker.getMetaString("callsign", medlineCallsign));

            if (marker.getMetaString("freq", null) != null)
                m_BtnLineTwo_freq
                        .setText(marker.getMetaString("freq", medlineFreq));

            //Line 3 Values
            StringBuilder sBuilder = new StringBuilder();

            boolean first = true;

            if (marker.getMetaString("urgent", null) != null) {
                sBuilder.append(marker.getMetaString("urgent", null));
                sBuilder.append("xA");
                first = false;
            }
            if (marker.getMetaString("priority", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("priority", null));
                sBuilder.append("xB");
            }
            if (marker.getMetaString("routine", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("routine", null));
                sBuilder.append("xC");
            }
            sBuilder.trimToSize();
            if (sBuilder.capacity() > 0) {
                m_BtnLineThree.setText(sBuilder.toString());
            }

            //Line 4 Values
            sBuilder = new StringBuilder();
            first = true;
            if (marker.getMetaBoolean("equipment_none", false)) {
                sBuilder.append(
                        _context.getString(R.string.equipment_none_choice));
                first = false;
            }

            if (marker.getMetaBoolean("hoist", false)) {
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;

                sBuilder.append(
                        _context.getString(R.string.equipment_hoist_choice));
            }

            if (marker.getMetaBoolean("extraction_equipment", false)) {
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;

                sBuilder.append(_context
                        .getString(R.string.equipment_extraction_choice));
            }

            if (marker.getMetaBoolean("ventilator", false)) {
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;

                sBuilder.append(
                        _context.getString(R.string.equipment_vent_choice));
            }

            if (marker.getMetaBoolean("equipment_other", false)) {
                if (!first)
                    sBuilder.append("\n");

                sBuilder.append(
                        _context.getString(R.string.equipment_other_choice));
                m_TxtLineFour.setVisibility(View.VISIBLE);
            } else {
                m_TxtLineFour.setVisibility(View.GONE);
            }

            sBuilder.trimToSize();
            if (sBuilder.capacity() > 0) {
                m_BtnLineFour.setText(sBuilder.toString());
            } else {
                marker.setMetaBoolean("equipment_none", true);
            }
            m_TxtLineFour.setText(marker.getMetaString("equipment_detail", ""));

            //Line 5 Values
            sBuilder = new StringBuilder();
            first = true;
            if (marker.getMetaString("litter", null) != null) {
                sBuilder.append(_context.getString(R.string.litter_x))
                        .append(" x ")
                        .append(marker.getMetaString("litter", null));
                first = false;
            }

            if (marker.getMetaString("ambulatory", null) != null) {
                if (!first)
                    sBuilder.append(", ");

                sBuilder.append(_context.getString(R.string.ambulance_x))
                        .append(" x ")
                        .append(marker.getMetaString("ambulatory", null));
            }

            sBuilder.trimToSize();
            if (sBuilder.capacity() > 0) {
                m_BtnLineFive.setText(sBuilder.toString());
            }

            //Line 6 Values
            final String secString = marker.getMetaString("security", null);
            if (secString != null) {
                final int securityIndex = Integer.parseInt(secString);
                if (securityIndex < m_BtnLineSix.getCount()) {
                    m_BtnLineSix.setSelection(securityIndex);
                }
            }

            final String hlzString = marker.getMetaString("hlz_marking", null);
            if (hlzString != null) {
                final int hlzIndex = Integer.parseInt(hlzString);
                if (hlzIndex < m_BtnLineSeven.getCount()) {
                    m_BtnLineSeven.setSelection(hlzIndex);
                }
            }
            m_TxtLineSeven.setText(marker.getMetaString("hlz_other", ""));

            //Line 8 Values
            sBuilder = new StringBuilder();

            first = true;
            if (marker.getMetaString("us_military", null) != null) {
                sBuilder.append(marker.getMetaString("us_military", null));
                sBuilder.append("xA");
                first = false;
            }
            if (marker.getMetaString("us_civilian", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("us_civilian", null));
                sBuilder.append("xB");
            }
            if (marker.getMetaString("nonus_military", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("nonus_military", null));
                sBuilder.append("xC");
            }
            if (marker.getMetaString("nonus_civilian", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("nonus_civilian", null));
                sBuilder.append("xD");
            }
            if (marker.getMetaString("epw", null) != null) {
                if (!first)
                    sBuilder.append(", ");
                if (first)
                    first = false;

                sBuilder.append(marker.getMetaString("epw", null));
                sBuilder.append("xE");
            }
            if (marker.getMetaString("child", null) != null) {
                if (!first)
                    sBuilder.append(", ");

                sBuilder.append(marker.getMetaString("child", null));
                sBuilder.append("xF");
            }

            sBuilder.trimToSize();
            if (sBuilder.capacity() > 0) {
                m_BtnLineEight.setText(sBuilder.toString());
            }

            //Line 9 Values
            sBuilder = new StringBuilder();
            first = true;
            if (marker.getMetaBoolean("terrain_none", false)) {
                sBuilder.append(
                        _context.getString(R.string.terrain_none_choice));
                first = false;
            }

            if (marker.getMetaBoolean("terrain_slope", false)) {
                sBuilder.append(
                        _context.getString(R.string.terrain_slope_choice));
                sBuilder.append(" ");
                sBuilder.append(marker.getMetaString("terrain_slope_dir", "N"));
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;
            }

            if (marker.getMetaBoolean("terrain_rough", false)) {
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;

                sBuilder.append(
                        _context.getString(R.string.terrain_rough_choice));
            }

            if (marker.getMetaBoolean("terrain_loose", false)) {
                if (!first)
                    sBuilder.append("\n");
                else
                    first = false;

                sBuilder.append(
                        _context.getString(R.string.terrain_loose_choice));
            }

            if (marker.getMetaBoolean("terrain_other", false)) {
                if (!first)
                    sBuilder.append("\n");

                sBuilder.append(
                        _context.getString(R.string.terrain_other_choice));
                m_TxtLineNine.setVisibility(View.VISIBLE);
            } else {
                m_TxtLineNine.setVisibility(View.GONE);
            }

            sBuilder.trimToSize();
            if (sBuilder.capacity() > 0) {
                m_BtnLineNine.setText(sBuilder.toString());
            } else {
                marker.setMetaBoolean("terrain_none", true);
            }
            m_TxtLineNine
                    .setText(marker.getMetaString("terrain_other_detail", ""));
            updateHLZObstacles();

        } catch (Exception e) {
            Log.d(TAG, "catch against bad call to loadData()", e);
        }

        remarksLayout.setText(marker.getRemarks());

        init = false;
    }

    public void saveData() {

        marker.setMetaString("title", m_Title.getText().toString());
        Marker m = (Marker) marker;
        m.setTitle(m_Title.getText().toString());
        //Line 2 Values
        marker.setMetaString("callsign", m_BtnLineTwo_callsign.getText());
        marker.setMetaString("freq", m_BtnLineTwo_freq.getText());

        // removes all of the entries so there is no left over values that do not match the current UI.
        MedicalDetailHandler.resetMarker(marker);

        //Line 3 Values
        HashMap<String, String> lineThreeMap = new HashMap<>();
        lineThreeMap.put("A", "urgent");
        lineThreeMap.put("B", "priority");
        lineThreeMap.put("C", "routine");
        String[] precedence = m_BtnLineThree.getText().split(", ");
        for (String i : precedence) {
            String[] temp = i.split("x");
            if (temp.length == 2) {
                marker.setMetaString(lineThreeMap.get(temp[1].trim()),
                        temp[0].trim());
            }
        }

        //Line 4 Values
        String[] equipment = m_BtnLineFour.getText().trim().split("\n");
        for (String i : equipment) {
            if (i.length() > 0) {
                char c = i.charAt(0);

                switch (c) {
                    case 'A':
                        marker.setMetaBoolean("equipment_none", true);
                        break;
                    case 'B':
                        marker.setMetaBoolean("hoist", true);
                        break;
                    case 'C':
                        marker.setMetaBoolean("extraction_equipment", true);
                        break;
                    case 'D':
                        marker.setMetaBoolean("ventilator", true);
                        break;
                    case 'O':
                        marker.setMetaBoolean("equipment_other", true);
                        break;
                }
            }
        }

        //Line 5 Values
        String[] aType = m_BtnLineFive.getText().split(", ");
        for (String i : aType) {
            //Log.d(TAG, "shb split ,: " + java.util.Arrays.toString(aType));
            String[] temp = i.split(" x ");
            //Log.d(TAG, "shb split: x" + java.util.Arrays.toString(temp));
            if (temp.length == 2) {
                //Log.d(TAG, "shb split: " + temp[0].toLowerCase(LocaleUtil.getCurrent()) + " to: " + temp[1]);
                marker.setMetaString(
                        temp[0].toLowerCase(LocaleUtil.getCurrent()), temp[1]);
            }

        }

        //Line 6
        marker.setMetaString("security",
                "" + m_BtnLineSix.getSelectedItemPosition());

        //Line 7
        marker.setMetaString("hlz_marking",
                "" + m_BtnLineSeven.getSelectedItemPosition());

        //Line 8 Values
        HashMap<String, String> lineEightMap = new HashMap<>();
        lineEightMap.put("A", "us_military");
        lineEightMap.put("B", "us_civilian");
        lineEightMap.put("C", "nonus_military");
        lineEightMap.put("D", "nonus_civilian");
        lineEightMap.put("E", "epw");
        lineEightMap.put("F", "child");
        String[] nationality = m_BtnLineEight.getText().split(", ");
        for (String i : nationality) {
            String[] temp = i.split("x");
            if (temp.length == 2) {
                final String label = lineEightMap.get(temp[1].trim());
                if (label != null)
                    marker.setMetaString(label, temp[0].trim());
                else
                    Log.d(TAG, "error getting a label for: " + temp[1]);
            }
        }

        //Line 9 Values
        String[] terrain = m_BtnLineNine.getText().trim().split("\n");
        for (String i : terrain) {
            if (i.length() > 0) {
                char c = i.charAt(0);

                switch (c) {
                    case 'N':
                        marker.setMetaBoolean("terrain_none", true);
                        break;
                    case 'S':
                        marker.setMetaBoolean("terrain_slope", true);
                        marker.setMetaString("terrain_slope_dir",
                                i.substring(i.length() - 1));
                        break;
                    case 'R':
                        marker.setMetaBoolean("terrain_rough", true);
                        break;
                    case 'L':
                        marker.setMetaBoolean("terrain_loose", true);
                        break;
                    case 'O':
                        marker.setMetaBoolean("terrain_other", true);
                        break;
                }
            }
        }

        marker.setRemarks(remarksLayout.getText());

        marker.setMetaBoolean("archive", true);
        marker.persist(_mapView.getMapEventDispatcher(), null, this.getClass());

    }

    private void editLocation() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        LayoutInflater inflater = LayoutInflater.from(_context);
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle("Enter Location: ");
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(marker.getGeoPointMetaData(),
                _mapView.getPoint(), _cFormat);
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
                            m_BtnLineOne.setText(coordView
                                    .getFormattedString());
                        }
                        _cFormat = cf;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {
                            /*
                            GeoPoint gp = marker.getPoint();
                            
                            // update the altitude
                            gp = new GeoPoint(gp.getLatitude(), gp
                                    .getLongitude(), p.getAltitude(),
                                    gp.getCE(), gp.getLE());
                                    */
                            marker.setPoint(p);
                            updateLocation();

                            CameraController.Programmatic.panTo(
                                    _mapView.getRenderer3(), marker.getPoint(),
                                    true);
                            locDialog.dismiss();
                        }
                    }
                });
    }

    private void updateLocation() {
        if (marker != null) {
            final String p = CoordinateFormatUtilities
                    .formatToString(marker.getPoint(), _cFormat);
            m_BtnLineOne.setText(p);
        }
    }

    public String getLineOneText() {
        return m_BtnLineOne.getText();
    }

    public String getCallSign() {
        return m_BtnLineTwo_callsign.getText();
    }

    public String getFreq() {
        return m_BtnLineTwo_freq.getText();
    }

    public String getLineThreeText() {
        return m_BtnLineThree.getText();
    }

    public String getLineFourText() {
        return m_BtnLineFour.getText();
    }

    public String getLineFourOther() {
        return m_TxtLineFour.getText().toString();
    }

    public String getLineFiveText() {
        return m_BtnLineFive.getText();
    }

    public String getLineSixText() {
        return m_BtnLineSix.getSelectedItem();
    }

    public String getLineSevenText() {
        return m_BtnLineSeven.getSelectedValue();
    }

    public String getLineSevenOther() {
        return m_TxtLineSeven.getText().toString();
    }

    public String getLineEightText() {
        return m_BtnLineEight.getText();
    }

    public String getLineNineText() {
        return m_BtnLineNine.getText();
    }

    public String getLineNineOther() {
        return m_TxtLineNine.getText().toString();
    }

    public String getMarkedBy() {
        String val = m_BtnLineSeven.getSelectedValue();
        if (val != null && val.contains("Other")) {
            StringBuilder sb = new StringBuilder();
            sb.append(m_BtnLineSeven.getSelectedValue());
            sb.append(System.getProperty("line.separator"));
            sb.append(m_TxtLineSeven.getText());
            return sb.toString();
        } else {
            return m_BtnLineSeven.getSelectedValue();
        }
    }

    public String getHLZObstacles() {
        if (m_BtnLineNine.getText().contains("Other")) {
            StringBuilder sb = new StringBuilder();
            sb.append(m_BtnLineNine.getText());
            sb.append(System.getProperty("line.separator"));
            sb.append(m_TxtLineNine.getText());
            return sb.toString();
        } else {
            return m_BtnLineNine.getText();
        }
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (item != marker) {
            item.removeOnPointChangedListener(this);
        } else {
            updateLocation();
            if (marker.getMetaString("zone_prot_marker", null) == null)
                hlzView.getLocationData(item.getPoint());
        }
    }

    public View getView() {
        return this.view;
    }

    public void shutdown(boolean save) {
        attachmentManager.cleanup();
        if (marker != null) {
            marker.removeOnPointChangedListener(this);
            if (save) {
                saveData();
                zMistView.shutdown();
                hlzView.shutdown();
            }
        }
        marker = null;
    }
}
