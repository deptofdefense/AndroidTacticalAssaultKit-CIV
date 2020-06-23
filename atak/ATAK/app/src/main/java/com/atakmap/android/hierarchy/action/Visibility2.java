
package com.atakmap.android.hierarchy.action;

/**
 * Visibility with 3 states:
 *  - Completely visible
 *  - Partly visible
 *  - Completely invisible
 */
public interface Visibility2 extends Visibility {

    int VISIBLE = 0;
    int SEMI_VISIBLE = 1;
    int INVISIBLE = 2;

    int getVisibility();
}
