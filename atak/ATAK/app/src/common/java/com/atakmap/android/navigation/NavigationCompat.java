
package com.atakmap.android.navigation;

import android.app.Activity;
import android.view.View;

import com.atakmap.app.R;

public class NavigationCompat {
    public static View setContentView(Activity activity) {
        activity.setContentView(R.layout.atak_frag_main);
        return null;
    }

    public static void startSplashProgress(Activity activity) {
        //Do Nothing
    }
}
