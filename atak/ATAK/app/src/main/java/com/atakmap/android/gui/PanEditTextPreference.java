
package com.atakmap.android.gui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.text.InputFilter;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Preference} that allows for string input geared towards Plugin Developers.
 * <p>
 * It is a subclass of {@link android.preference.DialogPreference} and shows the {@link android.widget.EditText}
 * in a dialog. This {@link android.widget.EditText} can be modified either programmatically
 * via {@link #getEditText()}, or through XML by setting any EditText
 * attributes on the EditTextPreference.
 * <p>
 * This preference will store a string into the SharedPreferences.
 *
 * This has been modified to allow for a pluginIcon attribute which will allow for icons
 * to be used in the entity.
 * </p>
 */
public class PanEditTextPreference extends EditTextPreference {

    public static final String TAG = "PanEditTextPreference";

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    private static final int MAX_VALID_WIDTH = 15;
    private static final int MIN_VALID_WIDTH = 1;
    private static Context appContext;

    private final Context pContext;

    /**
     * For plugins we are REQUIRED to set the application context to the 
     * ATAK owned Activity and not the context owned by the plugin.
     */
    public static void setContext(Context c) {
        appContext = c;
    }

    public PanEditTextPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super((appContext == null) ? context : appContext,
                PanPreference.filter(attrs), defStyleAttr, defStyleRes);
        PanPreference.setup(attrs, context, this, otherAttributes);
        pContext = context;
        setFilters(new InputFilter[0], false);
    }

    public PanEditTextPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super((appContext == null) ? context : appContext,
                PanPreference.filter(attrs), defStyleAttr);
        PanPreference.setup(attrs, context, this, otherAttributes);
        pContext = context;
        setFilters(new InputFilter[0], false);
    }

    public PanEditTextPreference(Context context, AttributeSet attrs) {
        super((appContext == null) ? context : appContext,
                PanPreference.filter(attrs));
        PanPreference.setup(attrs, context, this, otherAttributes);
        pContext = context;
        setFilters(new InputFilter[0], false);
    }

    public PanEditTextPreference(Context context) {
        super((appContext == null) ? context : appContext);
        pContext = context;
        setFilters(new InputFilter[0], false);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);
        return v;
    }

    /**
     * Convienence method to automatically install the EmojiExclusion filter along with any other filters
     * that are provided.    
     * @param filters a list of filters to be applied.
     * @param emojiSupport false if the instance does not support emoji's.   
     */
    public void setFilters(InputFilter[] filters, boolean emojiSupport) {
        if (!emojiSupport) {
            InputFilter[] nFilters = new InputFilter[filters.length + 1];
            nFilters[0] = new EditText.EmojiExcludeFilter();
            System.arraycopy(filters, 0, nFilters, 1, filters.length);
            if (getEditText() != null)
                getEditText().setFilters(nFilters);
        } else {
            if (getEditText() != null)
                getEditText().setFilters(filters);
        }

    }

    /**
     * Android is plain goofy when it comes to EditTextPreference.
     */
    @Override
    protected void showDialog(Bundle bundle) {
        super.showDialog(bundle);
        Dialog dialog = getDialog();
        if (dialog != null) {
            // use the plugin context to resolve the name
            try {
                Integer resId = otherAttributes.get("title");
                if (resId != null)
                    dialog.setTitle(pContext.getString(resId));
            } catch (Exception ignored) {
            }

            try {
                final Window window = dialog.getWindow();
                if (window != null) {
                    window.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

                    Rect displayRectangle = new Rect();
                    window.getDecorView()
                            .getWindowVisibleDisplayFrame(displayRectangle);
                    window.setLayout(
                            (int) (displayRectangle.width() * 1.0f),
                            (int) (displayRectangle.height() * 1.0f));
                }
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "error", iae);
            }
        }
    }

    /**
     * Before the preference value is saved, check to see if the
     * value is a valid width. If it is not, then the preference
     * is not saved and a message is shown.
     */
    public void checkValidWidth() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                try {
                    Integer.valueOf(newValue.toString());
                } catch (NumberFormatException nfe) {
                    Toast.makeText(appContext, R.string.invalid_value,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (Integer.parseInt(newValue.toString()) < MIN_VALID_WIDTH
                        || Integer.parseInt(
                                newValue.toString()) > MAX_VALID_WIDTH) {
                    Toast.makeText(appContext,
                            "Invalid value: Please enter a value between "
                                    + MIN_VALID_WIDTH + " & " + MAX_VALID_WIDTH,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * Before the preference value is saved, check to see if the 
     * value is a valid integer. If it is not, then the preference
     * is not saved and a message is shown. 
     */
    public void checkValidInteger() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                try {
                    Integer.valueOf(newValue.toString());
                } catch (NumberFormatException nfe) {
                    Toast.makeText(appContext, R.string.invalid_value,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * Before the preference value is saved, check to see if the 
     * value is a valid double. If it is not, then the preference
     * is not saved and a message is shown. 
     */
    public void checkValidDouble() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                try {
                    Double.valueOf(newValue.toString());
                } catch (NumberFormatException nfe) {
                    Toast.makeText(appContext, R.string.invalid_value,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * Before the preference value is saved, check to see if the 
     * value is a valid long. If it is not, then the preference
     * is not saved and a message is shown. 
     */
    public void checkValidLong() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                try {
                    Long.valueOf(newValue.toString());
                } catch (NumberFormatException nfe) {
                    Toast.makeText(appContext, R.string.invalid_value,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * Before the preference value is saved, check to see if the
     * value is valid integer and that it falls within a given range.
     * If it does not, then the preference is not saved and a message is shown.
     * @param minVal the minimum value supported by the preference
     * @param maxVal the maximumn value supported by rhe preference
     *
     */
    public void setValidIntegerRange(final int minVal, final int maxVal) {
        setValidIntegerRange(minVal, maxVal, false);
    }

    /**
     * Before the preference value is saved, check to see if the
     * value is valid integer and that it falls within a given range.
     * If it does not, then the preference is not saved and a message is shown.
     * @param minVal the minimum value supported by the preference
     * @param maxVal the maximumn value supported by rhe preference
     * @param allowForBlank allow for empty entry for the preference
     */
    public void setValidIntegerRange(final int minVal, final int maxVal,
            boolean allowEmpty) {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                int value;

                String val = "";

                if (newValue != null)
                    val = newValue.toString();

                if (allowEmpty && val.length() == 0)
                    return true;

                try {
                    value = Integer.parseInt(val);
                } catch (NumberFormatException nfe) {
                    showDialog(null);
                    Toast.makeText(appContext, R.string.invalid_value,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }

                if (value < minVal) {
                    showDialog(null);
                    Toast.makeText(appContext,
                            "Value must be at least " + minVal,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }

                if (value > maxVal) {
                    showDialog(null);
                    Toast.makeText(appContext,
                            "Value can't be greater than " + maxVal,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }

                return true;
            }
        });
    }

}
