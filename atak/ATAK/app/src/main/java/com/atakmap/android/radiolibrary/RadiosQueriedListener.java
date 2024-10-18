
package com.atakmap.android.radiolibrary;

import java.util.List;

/**
 * Listener for when radios are finished being queried
 */
public interface RadiosQueriedListener {
    /**
     * Fires when the radios are finished being queried
     *
     * @param radios The radios they have been queried
     */
    void radiosQueried(List<HarrisRadio> radios);
}
