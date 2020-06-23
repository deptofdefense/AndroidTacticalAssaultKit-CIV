
package com.atakmap.comms;

import android.os.Bundle;

import com.atakmap.android.contact.Contact;
import com.atakmap.commoncommo.CoTSendMethod;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dispatches CoT event Messages. The interface to this class is thread safe.
 */
public class CotDispatcher {

    public static final String TAG = "CotDispatcher";
    // Determines how messages are routed (internally or externally)
    private volatile int flags = DispatchFlags.INTERNAL;

    /**
     * Set the dispatch flags to determine basic dispatch behavior (@see DispatchFlags).
     *
     * @param flags one of INTERNAL or EXTERNAL.
     */
    public final void setDispatchFlags(final int flags) {
        this.flags = flags;
    }

    public final void dispatch(final CotEvent event) {
        dispatch(event, null);
    }

    public void dispatch(final CotEvent event, final Bundle data) {
        boolean reliable = (flags & DispatchFlags.DISPATCH_RELIABLE) != 0;
        boolean unreliable = (flags & DispatchFlags.DISPATCH_UNRELIABLE) != 0;
        if (reliable && !unreliable) {
            dispatch(event, data, CoTSendMethod.TAK_SERVER);
        } else if (unreliable && !reliable) {
            dispatch(event, data, CoTSendMethod.POINT_TO_POINT);
        } else {
            dispatch(event, data, CoTSendMethod.ANY);
        }
    }

    /**
     * Returns the contacts which are unknown, known to no longer be reachable, or 
     * who do not match the provided CoTSendMethod.  Will always return an empty list
     * for data Bundles that contain connect strings and no contacts, broadcasts
     * (no contacts specified), or for internal-only dispatches since no actual
     * remote contacts are involved.
     *
     * Data may contain a list of destination contacts
     * "toUIDs" is a list of UIDs
     * "toConnectStrings" is a list of stringified <code>{@link NetConnectString}</code>
     */
    public List<String> dispatch(final CotEvent event, final Bundle data,
            final CoTSendMethod sendMethod) {
        List<String> ret = new ArrayList<>();
        String[] toUIDs = null;
        String[] toConnectStrings = null;
        boolean broadcast = false;
        if (data == null) {
            broadcast = true;
        } else {
            toUIDs = data.getStringArray("toUIDs");
            if (toUIDs == null) {
                toConnectStrings = data.getStringArray("toConnectStrings");
            }
        }

        if ((flags & DispatchFlags.DISPATCH_EXTERNAL) != 0) {
            if (broadcast || toUIDs != null || toConnectStrings == null)
                CommsMapComponent.getInstance().sendCoT(ret, event, toUIDs,
                        sendMethod);
            else {
                Log.w(TAG,
                        "Got a dispatchEvent command w/o Contacts, using OLD NetConnectStr method... ",
                        new Exception());
                for (String toConnectString : toConnectStrings)
                    dispatchToConnectString(event, toConnectString);
            }
        }
        if ((flags & DispatchFlags.DISPATCH_INTERNAL) != 0)
            CommsMapComponent.getInstance().sendCoTInternally(event, data);
        return ret;
    }

    /**
     * Instead of the connect string, we will just need to pass down the appropriate 
     * multicast information.
     * @param event the cot event to dispatch
     * @param connectString the connect string to send the cot event too.
     */
    public final void dispatchToConnectString(final CotEvent event,
            final String connectString) {
        CommsMapComponent.getInstance().sendCoTToEndpoint(event, connectString);
    }

    /**
     * Send a CotEvent to a specific contact by any known connection type.
     * @param event the cot event to dispatch
     * @param contact the contact to send the event to
     */
    public final void dispatchToContact(CotEvent event, Contact contact) {
        dispatchToContact(event, contact, CoTSendMethod.ANY);
    }

    /**
     * Send a CotEvent to a specific contact by a specified sending method.
     * @param event the cot event to dispatch
     * @param contact the contact to send the event to
     * @param method can be ANY, TAK_SERVER or POINT TO POINT.
     */
    public final void dispatchToContact(CotEvent event, Contact contact,
            CoTSendMethod method) {
        String[] c;
        if (contact == null)
            c = null;
        else
            c = new String[] {
                    contact.getUID()
            };
        CommsMapComponent.getInstance().sendCoT(null, event, c, method);
    }

    /**
     * Sends a CoTEvent out as a broadcast over all connections, streaming and peer to peer.
     * @param event the event to dispatch.
     */
    public final void dispatchToBroadcast(CotEvent event) {
        dispatchToBroadcast(event, CoTSendMethod.ANY);
    }

    /**
     * Sends a CoTEvent out as a broadcast over a specified connection type.
     * @param event the event to dispatch.
     * @param method can be ANY, TAK_SERVER or POINT TO POINT.
     */
    public final void dispatchToBroadcast(CotEvent event,
            CoTSendMethod method) {
        CommsMapComponent.getInstance().sendCoT(null, event, null, method);
    }

    /**
     * Used as an internal dispatcher with the fromString only specifying which internal component sent it.
     *
     * @param event the cot event to dispatch
     * @param fromString what component is sending this event.
     */
    public final void dispatchFrom(CotEvent event, String fromString) {
        Bundle extras = new Bundle();
        extras.putString("from", fromString);
        CommsMapComponent.getInstance().sendCoTInternally(event, extras);
    }

    private String toDebugString(Bundle b) {
        StringBuilder sb = new StringBuilder();
        final Set<String> keySet = b.keySet();
        for (final String key : keySet) {
            sb.append('\"');
            sb.append(key);
            sb.append("\"=\"");
            sb.append(b.get(key));
            sb.append("\", ");
        }
        return sb.toString();
    }

}
