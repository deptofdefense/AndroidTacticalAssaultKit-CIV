
package com.atakmap.android.pairingline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbars.RangeAndBearingTool;

public class PairingLineMapReceiver extends BroadcastReceiver {

    public static final String TAG = "PairingLineMapReceiver";

    public static final String ACTION = "com.atakmap.android.maps.PAIRING_LINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(ACTION)) {

            Intent myIntent = new Intent();
            myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
            myIntent.putExtra("tool", RangeAndBearingTool.TOOL_NAME);
            Bundle extras = intent.getExtras();
            if (extras != null)
                myIntent.putExtras(extras);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        }
    }
}
