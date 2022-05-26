
package com.atakmap.android.tools.menu;

import android.content.Context;
import android.content.pm.ActivityInfo;

import com.atakmap.android.preference.AtakPreferenceFragment;

public class AtakActionBarListData {

    public static AtakActionBarMenuData.Orientation getOrientation(
            Context context) {
        switch (AtakPreferenceFragment.getOrientation(context)) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT: {
                return AtakActionBarMenuData.Orientation.portrait;
            }
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            default: {
                return AtakActionBarMenuData.Orientation.landscape;
            }
        }
    }

}
