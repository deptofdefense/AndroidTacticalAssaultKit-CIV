
package com.atakmap.android.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Spinner with an attribute that allows you to specify the layout resource ID
 * Why this isn't part of the core class, I have no idea
 */
public class ThemedSpinner extends Spinner {

    public ThemedSpinner(Context context) {
        this(context, null);
    }

    public ThemedSpinner(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThemedSpinner(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ThemedSpinner(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ThemedSpinner, defStyleAttr, defStyleRes);

        int layoutID = a.getResourceId(R.styleable.ThemedSpinner_layout, 0);
        if (layoutID == 0)
            layoutID = R.layout.spinner_text_view_dark;

        a.recycle();

        List<CharSequence> entries = new ArrayList<>();
        SpinnerAdapter adapter = getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                Object o = adapter.getItem(i);
                if (o instanceof CharSequence)
                    entries.add((CharSequence) o);
            }
        }

        ArrayAdapter<CharSequence> newAdapter = new ArrayAdapter<>(
                context, layoutID);
        newAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        newAdapter.addAll(entries);
        setAdapter(newAdapter);
    }
}
