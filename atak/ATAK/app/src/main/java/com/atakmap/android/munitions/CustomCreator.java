
package com.atakmap.android.munitions;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

class CustomCreator {

    public static final String TAG = "CustomCreator";

    private final Context _context;
    private final CustomCreateListener listener;

    CustomCreator(final Context c, final CustomCreateListener l) {
        _context = c;
        listener = l;
    }

    /**
     * classes wishing to be notified when the user is done entering info about a custom RED need to
     * implement this interface
     */
    interface CustomCreateListener {
        void onCustomInfoReceived(int id, String name,
                String description, String standing, String prone,
                String proneProt, String ricochetFan);
    }

    void buildTypeDialog() {
        final CharSequence[] items = {
                _context.getString(R.string.dangerclose_text9),
                _context.getString(R.string.dangerclose_text10)
        };

        AlertDialog.Builder alt_bld = new AlertDialog.Builder(_context);

        alt_bld.setCancelable(false)
                .setNegativeButton(R.string.cancel, null);

        alt_bld.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                switch (item) {
                    case 0: {//RED
                        dialog.dismiss();
                        buildCustomREDDialog(null);
                    }
                        break;
                    case 1: {//MSD
                        dialog.dismiss();
                        buildCustomMSDDialog(null);
                    }
                        break;
                }

            }
        });

        final AlertDialog alert = alt_bld.create();
        alert.setTitle(_context.getString(R.string.select_a_type));
        alert.show();

    }

    /**
     * This will build the dialog window for adding an RED custom weapon
     */
    void buildCustomREDDialog(final Node n) {

        LayoutInflater inflater = LayoutInflater.from(_context);
        final View v = inflater.inflate(
                R.layout.danger_close_custom_red_dialog, null);

        AlertDialog.Builder alt_bld = new AlertDialog.Builder(_context);

        alt_bld.setCancelable(false)
                .setPositiveButton(R.string.ok, null) // handled below as a replacement for the positive button.
                .setNegativeButton(R.string.cancel, null);
        alt_bld.setView(v);
        final AlertDialog alert = alt_bld.create();

        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {

                //if n is not null, we are editing an existing weapon
                //so populate the fields
                if (n != null) {
                    NamedNodeMap attrs = n.getAttributes();

                    //set name field
                    EditText nameField = v
                            .findViewById(R.id.custom_name);
                    String name = attrs.getNamedItem("name").getNodeValue();
                    nameField.setText(name);

                    //set description field
                    if (attrs.getNamedItem("description") != null) {
                        EditText descriptionField = v
                                .findViewById(R.id.custom_description);
                        String description = attrs.getNamedItem("description")
                                .getNodeValue();
                        descriptionField.setText(description);
                    }

                    //set standing field
                    EditText standField = v
                            .findViewById(R.id.custom_standing);
                    String standing = attrs.getNamedItem("standing")
                            .getNodeValue();
                    standField.setText(standing);

                    //set prone field
                    EditText proneField = v
                            .findViewById(R.id.custom_prone);
                    String prone = attrs.getNamedItem("prone").getNodeValue();
                    proneField.setText(prone);

                    //set prone protected field
                    if (!attrs.getNamedItem("proneprotected").getNodeValue()
                            .equals("")) {
                        EditText proneProtField = v
                                .findViewById(R.id.custom_proneP);
                        String proneProt = attrs.getNamedItem("proneprotected")
                                .getNodeValue();
                        proneProtField.setText(proneProt);
                    }
                }

                Button b = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //OK button action

                        //name
                        String name = ((EditText) v
                                .findViewById(R.id.custom_name))
                                        .getText().toString();

                        if (name.equals("")) {
                            Toast.makeText(_context,
                                    R.string.dangerclose_text11,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        //description
                        String description = ((EditText) v
                                .findViewById(R.id.custom_description))
                                        .getText().toString();

                        //standing value
                        EditText standText = v
                                .findViewById(R.id.custom_standing);
                        int standingValue;
                        if (standText.getText().length() > 0) {
                            if (standText.getText().toString().contains(".")) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text12,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            standingValue = valueOf(standText.getText()
                                    .toString(), -1);

                            if (standingValue < 0) {
                                Toast.makeText(
                                        _context,
                                        R.string.dangerclose_text13,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            Toast.makeText(_context,
                                    R.string.dangerclose_text14,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String standing = String.valueOf(standingValue);

                        //prone value
                        EditText proneText = v
                                .findViewById(R.id.custom_prone);
                        int proneValue;
                        if (proneText.getText().length() > 0) {
                            if (proneText.getText().toString().contains(".")) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text15,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            proneValue = valueOf(proneText.getText()
                                    .toString(), -1);

                            if (proneValue < 0) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text16,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            Toast.makeText(_context,
                                    R.string.dangerclose_text17,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String prone = String.valueOf(proneValue);

                        //prone protected value
                        EditText proneProtText = v
                                .findViewById(R.id.custom_proneP);
                        int proneProtValue = -1;
                        if (proneProtText.getText().length() > 0) {
                            if (proneProtText.getText().toString()
                                    .contains(".")) {
                                Toast.makeText(
                                        _context,
                                        R.string.dangerclose_text18,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            proneProtValue = valueOf(proneProtText
                                    .getText().toString(), -1);

                            if (proneProtValue < 0) {
                                Toast.makeText(
                                        _context,
                                        R.string.dangerclose_text19,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        String proneProtected = "";
                        if (proneProtValue > 0)
                            proneProtected = String.valueOf(proneProtValue);

                        alert.dismiss();

                        String ricochetFan = "";

                        int id = -1;
                        //if we're editing existing weapon
                        if (n != null) {
                            id = valueOf(n.getAttributes()
                                    .getNamedItem("ID").getNodeValue(), -1);
                            if (id < 0)
                                return;

                        }

                        //alert listening classes that the 'OK' button has been pressed
                        //so the weapon can be created
                        listener.onCustomInfoReceived(id, name, description,
                                standing, prone, proneProtected, ricochetFan);
                    }
                });
            }
        });

        alert.show();
    }

    /**
     * This will build the dialog window for adding/editing a custom weapon
     */
    void buildCustomMSDDialog(final Node n) {

        LayoutInflater inflater = LayoutInflater.from(_context);
        final View v = inflater.inflate(
                R.layout.danger_close_custom_msd_dialog, null);

        AlertDialog.Builder alt_bld = new AlertDialog.Builder(_context);

        alt_bld.setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null);
        alt_bld.setView(v);
        final AlertDialog alert = alt_bld.create();

        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                //if n is not null, we are editing an existing weapon
                //so populate the fields
                if (n != null) {
                    NamedNodeMap attrs = n.getAttributes();

                    //set name field
                    EditText nameField = v
                            .findViewById(R.id.custom_name_msd);
                    String name = attrs.getNamedItem("name").getNodeValue();
                    nameField.setText(name);

                    //set description field
                    if (attrs.getNamedItem("description") != null) {
                        EditText descriptionField = v
                                .findViewById(R.id.custom_description_msd);
                        String description = attrs.getNamedItem("description")
                                .getNodeValue();
                        descriptionField.setText(description);
                    }

                    //set standing field
                    EditText standField = v
                            .findViewById(R.id.custom_distance_msd);
                    String standing = attrs.getNamedItem("standing")
                            .getNodeValue();
                    standField.setText(standing);

                    if (!attrs.getNamedItem("ricochetfan").getNodeValue()
                            .equals("N/A")) {
                        //set ricochet fan field
                        EditText ricoDegField = v
                                .findViewById(R.id.custom_rico_fan_degrees);
                        EditText ricoMetersField = v
                                .findViewById(R.id.custom_rico_fan_meters);
                        String ricochetFan = attrs.getNamedItem("ricochetfan")
                                .getNodeValue();

                        int degSymbol = ricochetFan
                                .indexOf(Angle.DEGREE_SYMBOL);
                        String degrees = ricochetFan.substring(0, degSymbol);
                        ricoDegField.setText(degrees);

                        int slash = ricochetFan.indexOf('/');
                        String meters = ricochetFan.substring(slash + 2,
                                ricochetFan.length() - 1);
                        ricoMetersField.setText(meters);
                    }
                }

                Button b = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //OK button action

                        //name
                        String name = ((EditText) v
                                .findViewById(R.id.custom_name_msd))
                                        .getText().toString();

                        if (name.equals("")) {
                            Toast.makeText(_context,
                                    R.string.dangerclose_text20,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        //description
                        String description = ((EditText) v
                                .findViewById(R.id.custom_description_msd))
                                        .getText().toString();

                        //minimum safe distance value
                        EditText distText = v
                                .findViewById(R.id.custom_distance_msd);
                        int distanceValue;
                        if (distText.getText().length() > 0) {
                            if (distText.getText().toString().contains(".")) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text21,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            distanceValue = valueOf(distText.getText()
                                    .toString(), -1);

                            if (distanceValue < 0) {
                                Toast.makeText(
                                        _context,
                                        R.string.dangerclose_text22,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            Toast.makeText(_context,
                                    R.string.dangerclose_text23,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String distance = String.valueOf(distanceValue);

                        //ricochet fan value
                        StringBuilder ricochetFan = new StringBuilder();

                        //degrees value
                        EditText ricoDegText = v
                                .findViewById(R.id.custom_rico_fan_degrees);
                        EditText ricoMetersText = v
                                .findViewById(R.id.custom_rico_fan_meters);
                        int ricoMetersValue;
                        int ricoDegValue;
                        if (ricoDegText.getText().length() > 0
                                && ricoMetersText.getText().length() > 0) {
                            ricoDegValue = valueOf(ricoDegText
                                    .getText().toString(), -1);
                            if (ricoDegValue > 360 || ricoDegValue < 0) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text24,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            ricochetFan.append(ricoDegValue);
                            ricochetFan.append(Angle.DEGREE_SYMBOL);
                            ricochetFan.append(" / ");

                            ricoMetersValue = valueOf(ricoMetersText
                                    .getText().toString(), -1);
                            if (ricoMetersValue < 0) {
                                Toast.makeText(_context,
                                        R.string.dangerclose_text25,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            ricochetFan.append(ricoMetersValue);
                            ricochetFan.append("m");
                        } else {
                            if (ricoDegText.getText().length() > 0
                                    || ricoMetersText.getText().length() > 0) {
                                Toast.makeText(
                                        _context,
                                        R.string.dangerclose_text26,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            ricochetFan.append("N/A");
                        }

                        alert.dismiss();

                        String prone = "";
                        String proneProtected = "";

                        int id = -1;
                        //if we're editing existing weapon
                        if (n != null) {
                            id = valueOf(n.getAttributes()
                                    .getNamedItem("ID").getNodeValue(), -1);
                            if (id < 0)
                                return;
                        }

                        //alert listening classes that the 'OK' button has been pressed
                        //so the weapon can be created
                        listener.onCustomInfoReceived(id, name, description,
                                distance, prone, proneProtected,
                                ricochetFan.toString());
                    }
                });
            }
        });

        alert.show();
    }

    private int valueOf(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return def;
        }
    }

}
