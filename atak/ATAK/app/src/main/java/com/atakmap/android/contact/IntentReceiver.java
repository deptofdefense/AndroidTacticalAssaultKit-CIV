
package com.atakmap.android.contact;

import android.content.Context;
import android.content.Intent;

public interface IntentReceiver {
    String getName();

    void onReceive(Context context, Intent intent);
}
