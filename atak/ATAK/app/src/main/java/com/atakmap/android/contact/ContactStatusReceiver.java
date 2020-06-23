
package com.atakmap.android.contact;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ContactStatusReceiver extends BroadcastReceiver {

    private static final String TAG = "ContactStatusReceiver";

    public final static String ITEM_STALE = "com.atakmap.android.cot.ITEM_STALE";
    public final static String ITEM_REFRESHED = "com.atakmap.android.cot.ITEM_REFRESHED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String uid = intent.getExtras().getString("uid");
        Contact c = Contacts.getInstance().getContactByUuid(uid);
        if (!(c instanceof IndividualContact))
            return;
        IndividualContact ic = (IndividualContact) c;
        if (action.equals(ITEM_STALE))
            ic.stale();
        else if (action.equals(ITEM_REFRESHED))
            ic.current();
    }
}
