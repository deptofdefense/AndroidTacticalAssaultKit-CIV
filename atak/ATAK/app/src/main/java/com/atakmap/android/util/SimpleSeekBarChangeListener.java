
package com.atakmap.android.util;

import android.widget.SeekBar;

/**
 * Provides a shell implementation of a OnItemSelectedListener that requires an implementation of
 * onItemSelected but does not require an implementation of onNothingSelected
 */
public abstract class SimpleSeekBarChangeListener
        implements SeekBar.OnSeekBarChangeListener {
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    abstract public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser);
}
