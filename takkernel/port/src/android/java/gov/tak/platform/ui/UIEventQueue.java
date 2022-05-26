package gov.tak.platform.ui;

import android.os.Handler;
import android.os.Looper;

public final class UIEventQueue {
    public static void invokeLater(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    public static boolean isCurrentThread() {
        return Looper.getMainLooper().getThread().getId() == Thread.currentThread().getId();
    }
}

