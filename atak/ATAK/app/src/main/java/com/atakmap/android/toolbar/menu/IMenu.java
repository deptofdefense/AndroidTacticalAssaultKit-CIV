
package com.atakmap.android.toolbar.menu;

import android.view.View;

public interface IMenu {
    View getView();

    String getIdentifier();

    int getPreferredWidth();

    int getPreferredHeight();
}
