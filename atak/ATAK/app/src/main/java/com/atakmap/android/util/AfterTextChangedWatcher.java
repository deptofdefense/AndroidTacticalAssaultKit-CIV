
package com.atakmap.android.util;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * Provides a shell implementation of a TextWatcher where the only method required for
 * implementation is afterTextChanged.
 */
public abstract class AfterTextChangedWatcher implements TextWatcher {
    @Override
    public void onTextChanged(CharSequence s, int start, int before,
            int count) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
    }

    @Override
    abstract public void afterTextChanged(Editable s);

}
