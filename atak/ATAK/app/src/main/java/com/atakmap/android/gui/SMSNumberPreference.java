
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.R;

import java.util.ArrayList;

/**
 *
 */
public class SMSNumberPreference extends DialogPreference {

    public static final String TAG = "PanEditTextPreference";
    private ImageView imageView;
    private static Context appContext;
    private static final ArrayList<String> numbers = new ArrayList<>();

    /**
     * For plugins we are REQUIRED to set the application context to the
     * ATAK owned Activity and not the context owned by the plugin.
     */
    public static void setContext(Context c) {
        appContext = c;
    }

    public SMSNumberPreference(Context context, AttributeSet attrs) {
        super((appContext == null) ? context : appContext, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);

        if (v instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) v;
            MapView view = MapView.getMapView();
            ImageView iv = new ImageView(view.getContext());
            if (BuildConfig.FLAVOR == "civUIMods") {
                iv.setImageDrawable(view.getContext()
                        .getDrawable(
                                R.drawable.ic_baseline_keyboard_arrow_right_24));
            } else {
                iv.setImageDrawable(
                        view.getContext().getDrawable(R.drawable.arrow_right));
            }
            layout.addView(iv);
            imageView = iv;
        }
        setRightSideIconVisibility(isEnabled());
        return v;
    }

    private void setRightSideIconVisibility(boolean enabled) {
        try {
            imageView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {

        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        setRightSideIconVisibility(isEnabled());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setRightSideIconVisibility(isEnabled());
    }

    @Override
    protected synchronized void showDialog(Bundle bundle) {
        super.showDialog(bundle);
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.cancel();
            final AlertDialog.Builder ad = new AlertDialog.Builder(appContext);
            ad.setNeutralButton(R.string.done,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            StringBuilder sb = new StringBuilder();
                            for (String s : numbers) {
                                sb.append(s);
                                sb.append("-");
                            }
                            persistString(sb.toString());
                        }
                    });
            final LayoutInflater inflater = LayoutInflater.from(appContext);
            final View mainView = inflater.inflate(R.layout.sms_manager, null);
            updateView(mainView.findViewById(R.id.number_list));
            Button b = mainView.findViewById(R.id.addSMS);
            final EditText numberField = mainView
                    .findViewById(R.id.enterNumber);
            final LinearLayout lv = mainView
                    .findViewById(R.id.number_list);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final String number = numberField.getText().toString();
                    if (number.length() <= 15 && number.length() >= 11) {
                        numbers.add(number);
                        numberField.setText("");
                        updateView(lv);
                    } else {
                        Toast.makeText(appContext,
                                R.string.details_text55, Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            });
            ad.setView(mainView);
            ad.setTitle(R.string.details_text56);

            final AlertDialog alert = ad.create();

            alert.show();
        }
    }

    private void updateView(final LinearLayout lv) {
        lv.removeAllViews();
        for (final String s : numbers) {
            final LayoutInflater inflater = LayoutInflater.from(appContext);
            final View item = inflater.inflate(R.layout.sms_row, null);
            ((TextView) item.findViewById(R.id.smsNumberView)).setText(s);
            ImageButton delete = item
                    .findViewById(R.id.btnRemoveSMS);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    numbers.remove(s);
                    StringBuilder sb = new StringBuilder();
                    for (String s : numbers) {
                        sb.append(s);
                        sb.append("-");
                    }
                    persistString(sb.toString());
                    updateView(lv);
                }
            });
            lv.addView(item);
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue,
            Object defaultValue) {
        numbers.clear();
        if (restorePersistedValue) {
            String s = getPersistedString("sms_numbers");
            String[] parsedString = s.split("-");
            for (String number : parsedString) {
                if (!number.equals("")) {
                    numbers.add(number);
                }
            }
        }
    }
}
