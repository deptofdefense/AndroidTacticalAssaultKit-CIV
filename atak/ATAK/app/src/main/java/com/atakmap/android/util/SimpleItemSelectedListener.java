
package com.atakmap.android.util;

import android.widget.AdapterView;
import android.view.View;

/**
 * Provides a shell implementation of a OnItemSelectedListener that requires an implementation of
 * onItemSelected but does not require an implementation of onNothingSelected
 */
public abstract class SimpleItemSelectedListener
        implements AdapterView.OnItemSelectedListener {

    @Override
    public abstract void onItemSelected(AdapterView<?> parent, View view,
            int position, long id);

    /**
     * Empty implementation of the callback that is invoked whe the selection disappears from the view.
     */
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

}
