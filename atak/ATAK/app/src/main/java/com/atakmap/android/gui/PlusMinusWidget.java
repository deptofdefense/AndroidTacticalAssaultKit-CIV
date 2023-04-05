
package com.atakmap.android.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class PlusMinusWidget extends LinearLayout {

    private Button plusButton;
    private Button minusButton;
    private EditText et;

    private int start;
    private int min;
    private int max;

    public PlusMinusWidget(Context context) {
        super(context);

        min = Integer.MIN_VALUE;
        max = Integer.MAX_VALUE;
        start = 0;
    }

    public PlusMinusWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupAttributes(attrs);
    }

    public PlusMinusWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setupAttributes(attrs);
    }

    /**
     * broken but does not inhibit the proper functioning of the current use cases.
     */
    private void setupAttributes(final AttributeSet attrs) {
        min = 0;
        start = 0;
        max = Integer.MAX_VALUE;

        TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.PlusMinusWidget);

        int n = a.getIndexCount();
        //Log.d("SHB", "count: " + count);

        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);
            if (attr == R.styleable.PlusMinusWidget_minValue) {
                min = a.getInt(attr, Integer.MIN_VALUE);
                //Log.d("SHB", "min: " + min);

            } else if (attr == R.styleable.PlusMinusWidget_maxValue) {
                max = a.getInt(attr, Integer.MAX_VALUE);
                Log.d("SHB", "max: " + max);

            } else if (attr == R.styleable.PlusMinusWidget_startValue) {
                start = a.getInt(attr, 0);
                Log.d("SHB", "start: " + start);

            } else {
                Log.d("TAG", "Unknown attribute for "
                        + getClass() + ": " + attr);

            }
        }
        a.recycle();
    }

    public String getText() {
        return et.getText().toString();
    }

    public void setText(String txt) {
        try {
            Integer.parseInt(txt);
            et.setText(txt);
        } catch (NumberFormatException ignored) {
        }
    }

    public void init() {
        minusButton = findViewById(R.id.minus_button);
        minusButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = start;
                try {
                    cur = Integer.parseInt(et.getText().toString());
                } catch (NumberFormatException ignore) {
                }

                if (cur > min) {
                    et.setText(String.valueOf(--cur));
                }
                minusButton.setEnabled(cur - 1 >= min);
            }
        });

        plusButton = findViewById(R.id.plus_button);
        plusButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = start;
                try {
                    cur = Integer.parseInt(et.getText().toString());
                } catch (NumberFormatException ignore) {
                }
                if (cur < max) {
                    et.setText(String.valueOf(++cur));
                }
                plusButton.setEnabled(cur + 1 <= max);

            }
        });

        et = findViewById(R.id.value_text_view);
        et.setText(String.valueOf(start));
        et.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String text = et.getText().toString();

                if (text.length() == 0 || text.contentEquals("-")) {
                    return;
                }

                // test for valid number
                int curHr = start;

                try {
                    curHr = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    et.setText(String.valueOf(start));
                }

                if (curHr > max) {
                    curHr = max;
                    et.setText(String.valueOf(max));
                } else if (curHr < min) {
                    curHr = min;
                    et.setText(String.valueOf(min));
                }

                plusButton.setEnabled(curHr < max);

                minusButton.setEnabled(curHr > min);
            }
        });

        if (min == start)
            minusButton.setEnabled(false);
        if (max == start)
            plusButton.setEnabled(false);

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }

}
