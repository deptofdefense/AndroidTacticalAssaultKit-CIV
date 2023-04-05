
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.gui.ActionButton;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * View for the individual ZMIST
 */
public class ZMist extends LinearLayout {

    private final Context _context;
    private MapView _mapView;
    PointMapItem _marker;

    private String _title;

    private ActionButton z_line;
    private ActionButton m_line;
    private ActionButton i_line;
    private ActionButton s_line;
    private ActionButton t_line;
    private final LayoutInflater inflater;

    private Map<String, String> mechanismMap = new HashMap<>();
    private Map<String, String> injuryMap = new HashMap<>();

    public ZMist(final Context context) {
        this(context, null);
    }

    public ZMist(final Context context,
            final AttributeSet attrSet) {
        super(context, attrSet);
        this._context = context;
        inflater = (LayoutInflater) context
                .getSystemService(Service.LAYOUT_INFLATER_SERVICE);

        String[] mechanismKeys = this.getResources()
                .getStringArray(R.array.mechanism_abbr);
        String[] mechanismValues = this.getResources()
                .getStringArray(R.array.mechanism_full);
        mechanismMap = new HashMap<>();
        for (int i = 0; i < Math.min(mechanismKeys.length,
                mechanismValues.length); i++) {
            mechanismMap.put(mechanismKeys[i], mechanismValues[i]);
        }

        String[] injuryKeys = this.getResources()
                .getStringArray(R.array.injury_abbr);
        String[] injuryValues = this.getResources()
                .getStringArray(R.array.injury_full);
        injuryMap = new HashMap<>();
        for (int i = 0; i < Math.min(injuryKeys.length,
                injuryValues.length); i++) {
            injuryMap.put(injuryKeys[i], injuryValues[i]);
        }
    }

    private final OnClickListener mocl = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewParent parentView = v.getParent().getParent().getParent()
                    .getParent();

            if (parentView instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) parentView;
                EditText mistDetail = ll
                        .findViewById(R.id.mist_m_detail);

                Button mButton = (Button) v;
                String currentText = mistDetail.getText().toString();
                String buttonAbbr = mButton.getText().toString();
                String buttonText = mechanismMap.get(buttonAbbr);
                if (currentText.contains(buttonText)) {
                    // if the current text already contains the value remove it
                    // (and any trailing spaces)
                    mistDetail.setText(
                            currentText.replaceAll(buttonText + "\\s*", "")
                                    .replaceAll(" {2}", " "));
                    mButton.setSelected(false);

                } else {
                    if (currentText.length() > 0) {
                        // if there is already current text append the new value
                        mistDetail.setText(currentText + " " + buttonText);
                    } else {
                        // this must be the first value
                        mistDetail.setText(buttonText);
                    }
                    mButton.setSelected(true);
                }
                // move cursor to the end
                mistDetail.setSelection(mistDetail.getText().length());
                //Log.d("JUDD_DEBUG", "Mechanism onClick = " + buttonText);
            }
        }
    };

    private final OnClickListener iocl = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ViewParent parentView = v.getParent().getParent().getParent()
                    .getParent();

            if (parentView instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) parentView;
                EditText mistDetail = ll
                        .findViewById(R.id.mist_i_detail);

                Button iButton = (Button) v;
                String currentText = mistDetail.getText().toString();
                String buttonAbbr = iButton.getText().toString();
                String buttonText = injuryMap.get(buttonAbbr);
                if (currentText.contains(buttonText)) {
                    // if the current text already contains the value remove it
                    // (and any trailing spaces)
                    mistDetail.setText(
                            currentText.replaceAll(buttonText + "\\s*", "")
                                    .replaceAll(" {2}", " "));
                    iButton.setSelected(false);
                } else {
                    if (currentText.length() > 0) {
                        // if there is already current text append the new value
                        mistDetail.setText(currentText + " " + buttonText);
                    } else {
                        // this must be the first value
                        mistDetail.setText(buttonText);
                    }
                    iButton.setSelected(true);
                }
                // move cursor to the end
                mistDetail.setSelection(mistDetail.getText().length());
                //Log.d("JUDD_DEBUG", "Injury onClick = " + buttonText);
            }
        }
    };

    private final OnClickListener socl = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // 1. Figure out which button was pressed
            // 2. Figure out button state
            // 3. Remove current Symptom entry in details text field
            // 4. Add (if applicable) the new Symptom entry
            // 5. Figure out which other buttons are in the same group
            // 6. Set the correct state of all the buttons in this group
            ViewParent parentView = v.getParent().getParent().getParent()
                    .getParent();
            //Log.d("JUDD_DEBUG", "SOCL called");

            if (parentView instanceof LinearLayout) {
                //Log.d("JUDD_DEBUG", "Top level parent found");
                LinearLayout ll = (LinearLayout) parentView;
                EditText mistDetail = ll
                        .findViewById(R.id.mist_s_detail);

                Button sButton = (Button) v;
                String currentText = mistDetail.getText().toString();
                int buttonId = sButton.getId();
                String buttonText = sButton.getText().toString();
                LinearLayout buttonParent = (LinearLayout) sButton.getParent();

                boolean wasDown = false;
                String buttonLabel = "";
                StringBuilder buttonPressedText = new StringBuilder();
                for (int i = 0; i < buttonParent.getChildCount(); i++) {
                    if (i == 0) {
                        buttonLabel = ((TextView) buttonParent.getChildAt(i))
                                .getText().toString();
                        buttonPressedText.append(buttonLabel);
                        buttonPressedText.append(": ");
                        buttonPressedText.append(buttonText);
                        buttonPressedText
                                .append(System.getProperty("line.separator"));
                        wasDown = mistDetail.getText().toString()
                                .contains(buttonPressedText.toString());
                        //Log.d("JUDD_DEBUG",
                        //        buttonText + " was down = " + wasDown);
                    } else {
                        // The children of this parent are either TextViews or Buttons
                        // If it's a button and it's not the one who initiated the onClick
                        // handler and it was previously the active button in the group then
                        // it needs to be deactivated
                        if (!wasDown) {
                            if (buttonParent.getChildAt(i)
                                    .getId() != buttonId) {
                                buttonParent.getChildAt(i).setSelected(false);
                            }
                        }
                    }
                }

                // remove any current value for the associated data set
                StringBuilder newText = new StringBuilder();
                newText.append(
                        currentText.replaceAll(buttonLabel + ".*\\s+", ""));

                // set new button state for the button being clicked
                // if applicable update the field text
                if (wasDown) {
                    sButton.setSelected(false);
                } else {
                    sButton.setSelected(true);
                    newText.append(buttonPressedText);
                }
                mistDetail.setText(newText.toString());
                Log.d("JUDD_DEBUG", "New Text = " + newText);

                // move cursor to the end
                mistDetail.setSelection(mistDetail.getText().length());
                Log.d("JUDD_DEBUG", "Symptom onClick = " + buttonText);
            }
        }
    };

    private void toggleBackground(String data, Map<String, String> map,
            LinearLayout layout) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View v = layout.getChildAt(i);
            if (v instanceof LinearLayout) {
                toggleBackground(data, map, (LinearLayout) v);
            } else if (v instanceof Button) {
                Button tempButton = (Button) v;
                String buttonText = map.get(tempButton.getText().toString());
                if (buttonText != null && data.contains(buttonText)) {
                    tempButton.setSelected(true);
                }
            }
        }
    }

    private void toggleBackground(String data, LinearLayout layout) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View v = layout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) v;
                String buttonValue = "";
                for (int j = 0; j < row.getChildCount(); j++) {
                    if (j == 0) {
                        String rowLabel = ((TextView) row.getChildAt(j))
                                .getText().toString();
                        String pattern = rowLabel + ":\\s+(.*)";
                        Log.d("JUDD_DEBUG", "rowLabel = " + rowLabel);
                        Log.d("JUDD_DEBUG", "pattern = " + pattern);
                        Pattern p = Pattern.compile(pattern);
                        Matcher m = p.matcher(data);
                        if (m.find()) {
                            buttonValue = m.group(1);
                        } else {
                            buttonValue = "";
                        }
                    } else {
                        if (!buttonValue.isEmpty()) {
                            Button tempButton = (Button) row.getChildAt(j);
                            if (tempButton.getText().toString()
                                    .equals(buttonValue)) {
                                tempButton.setSelected(true);
                            } else {
                                tempButton.setSelected(false);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Create the ZMIST object
     * @param id - the id of the ZMIST
     * @param marker - the marker being dealt with
     * @return - the Linear layout associated with the ZMIST
     */
    public LinearLayout createZMist(int id, PointMapItem marker,
            final MapView mapView) {
        _marker = marker;
        _mapView = mapView;
        LinearLayout ll;

        final String title = _context.getString(R.string.zmist)
                + id;
        _title = title;

        //Inflate a new custom linear layout
        ll = (LinearLayout) View.inflate(_context, R.layout.zmist_layout,
                null);
        ll.setId(id);

        TextView titleView = ll.findViewById(R.id.zmist_title);
        titleView.setText(title);

        //Z Line
        z_line = new ActionButton(
                ll.findViewById(R.id.zmist_Z));
        z_line.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldZap = z_line.getText();
                if (!oldZap.equals(_context.getString(R.string.zap_num)))
                    input.setText(oldZap);
                new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.zap_num))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            z_line.setText(input
                                                    .getText().toString());

                                            saveData();
                                        } else
                                            z_line.setText(oldZap);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        //M Line
        m_line = new ActionButton(
                ll.findViewById(R.id.zmist_M));
        m_line.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.mist_mechanism, null, false);

                input.findViewById(R.id.mist_m_blast).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_blunt).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_gsw).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_burn).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_cold).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_heat).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_crush).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_fall).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_frag_single)
                        .setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_frag_multiple)
                        .setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_knife).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_radiation)
                        .setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_nuclear)
                        .setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_chemical)
                        .setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_smoke).setOnClickListener(mocl);
                input.findViewById(R.id.mist_m_sting).setOnClickListener(mocl);

                final EditText newMechanism = input
                        .findViewById(R.id.mist_m_detail);
                final String oldMechanism = m_line.getText();
                if (!oldMechanism
                        .equals(_context.getString(R.string.mech_of_injury)))
                    newMechanism.setText(oldMechanism);
                LinearLayout mButtons = input
                        .findViewById(R.id.mist_m_buttons);

                toggleBackground(oldMechanism, mechanismMap, mButtons);

                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.mech_of_injury))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (newMechanism.getText()
                                                .length() > 0) {
                                            m_line.setText(newMechanism
                                                    .getText().toString());
                                            saveData();
                                        } else {
                                            m_line.setText(_context.getString(
                                                    R.string.mech_of_injury));
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        //I Line
        i_line = new ActionButton(
                ll.findViewById(R.id.zmist_I));
        i_line.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.mist_injury, null, false);

                input.findViewById(R.id.mist_i_amputation)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_avulsion)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_bleeding)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_burn).setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_deformity)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_foreign)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_gunshot)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_hematoma)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_laceration)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_puncture)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_stab).setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_tourniquet)
                        .setOnClickListener(iocl);
                input.findViewById(R.id.mist_i_fracture)
                        .setOnClickListener(iocl);

                final EditText newInjury = input
                        .findViewById(R.id.mist_i_detail);
                final String oldInjury = i_line.getText();
                if (!oldInjury
                        .equals(_context.getString(R.string.injury_sustained)))
                    newInjury.setText(oldInjury);
                LinearLayout iButtons = input
                        .findViewById(R.id.mist_i_buttons);

                toggleBackground(oldInjury, injuryMap, iButtons);

                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.injury_sustained))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (newInjury.getText().length() > 0) {
                                            i_line.setText(newInjury.getText()
                                                    .toString());
                                            saveData();
                                        } else {
                                            i_line.setText(_context.getString(
                                                    R.string.injury_sustained));
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        //S Line
        s_line = new ActionButton(
                ll.findViewById(R.id.zmist_S));
        s_line.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final View input = inflater.inflate(
                        R.layout.mist_symptoms, null, false);

                input.findViewById(R.id.mist_s_bleeding_minimal)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_bleeding_massive)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_airway_has)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_airway_no)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_radial_has)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_radial_no)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_pulse_strong)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_pulse_weak)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_skin_warm)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_skin_cold)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_pupils_constricted)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_pupils_dialated)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_pupils_normal)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_breathing_labored)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_breathing_normal)
                        .setOnClickListener(socl);
                input.findViewById(R.id.mist_s_breathing_absent)
                        .setOnClickListener(socl);

                final EditText newSymptom = input
                        .findViewById(R.id.mist_s_detail);
                final String oldSymptom = s_line.getText();
                if (!oldSymptom
                        .equals(_context.getString(R.string.symp_and_signs)))
                    newSymptom.setText(
                            oldSymptom + System.getProperty("line.separator"));
                LinearLayout sButtons = input
                        .findViewById(R.id.mist_s_buttons);

                toggleBackground(oldSymptom, sButtons);
                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.symp_and_signs))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (newSymptom.getText().length() > 0) {
                                            s_line.setText(newSymptom.getText()
                                                    .toString().trim());
                                            saveData();
                                        } else {
                                            s_line.setText(_context.getString(
                                                    R.string.symp_and_signs));
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        //T Line
        t_line = new ActionButton(
                ll.findViewById(R.id.zmist_T));
        t_line.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldTreat = t_line.getText();
                if (!oldTreat.equals(_context
                        .getString(R.string.treatment_given)))
                    input.setText(oldTreat);
                new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.treatment_given))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            t_line.setText(input
                                                    .getText().toString());
                                            saveData();
                                        } else
                                            t_line.setText(oldTreat);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        });

        return ll;
    }

    /**
     * Save the data to the marker's
     * metadata
     */
    public void saveData() {
        Map<String, Object> zMistsMap = _marker.getMetaMap("zMists");
        if (zMistsMap == null)
            zMistsMap = new HashMap<>();

        Map<String, Object> zMist = new HashMap<>();

        zMist.put("z", z_line.getText());
        zMist.put("m", m_line.getText());
        zMist.put("i", i_line.getText());
        zMist.put("s", s_line.getText());
        zMist.put("t", t_line.getText());

        zMistsMap.put(_title, zMist);

        _marker.setMetaMap("zMists", zMistsMap);

        _marker.persist(_mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    /**
     * Load the data from metadata
     * @param zMap - the ZMIST's values
     */
    public void loadData(Map<String, Object> zMap) {
        z_line.setText(_context.getString(R.string.zap_num));
        m_line.setText(_context.getString(R.string.mech_of_injury));
        i_line.setText(_context.getString(R.string.injury_sustained));
        s_line.setText(_context.getString(R.string.symp_and_signs));
        t_line.setText(_context.getString(R.string.treatment_given));

        if (zMap != null) {
            if (!zMap.get("z").toString().equals(""))
                z_line.setText(zMap.get("z").toString());
            else
                z_line.setText(_context.getString(R.string.zap_num));

            if (!zMap.get("m").toString().equals(""))
                m_line.setText(zMap.get("m").toString());
            else
                m_line.setText(_context.getString(R.string.mech_of_injury));

            if (!zMap.get("i").toString().equals(""))
                i_line.setText(zMap.get("i").toString());
            else
                i_line.setText(_context.getString(R.string.injury_sustained));

            if (!zMap.get("s").toString().equals(""))
                s_line.setText(zMap.get("s").toString());
            else
                s_line.setText(_context.getString(R.string.symp_and_signs));

            if (!zMap.get("t").toString().equals(""))
                t_line.setText(zMap.get("t").toString());
            else
                t_line.setText(_context.getString(R.string.treatment_given));
        }
    }
}
