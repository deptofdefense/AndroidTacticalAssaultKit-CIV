
package com.atakmap.android.navigation;

import android.app.Activity;
import android.view.View;

import com.atakmap.app.R;

public class NavigationCompat {
    public static View setContentView(Activity activity) {
        activity.setContentView(R.layout.atak_frag_main);
        return activity.findViewById(R.id.atak_app_nav);
    }

    public static void startSplashProgress(Activity activity) {
        // Excluded from 4.5
        /*final ProgressBar progBar = activity
                .findViewById(R.id.atak_splash_progress_bar);
        CountDownTimer timer = new CountDownTimer(1500, 10) {
            @Override
            public void onTick(long millisUntilFinished) {
                progBar.setProgress(
                        (int) (((1500 - millisUntilFinished) / 1500f) * 100f));
            }
        
            @Override
            public void onFinish() {
                progBar.setProgress(100);
            }
        };
        timer.start();*/
    }
}
