
package com.atakmap.android.importexport;

import android.net.Uri;
import android.util.Pair;

import com.atakmap.coremap.cot.event.CotEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MarshalManager {
    private final static Comparator<Marshal> COMPARATOR = new Comparator<Marshal>() {
        @Override
        public int compare(Marshal m1, Marshal m2) {
            int retval = m2.getPriorityLevel() - m1.getPriorityLevel();
            if (retval == 0)
                retval = m1.hashCode() - m2.hashCode();
            return retval;
        }
    };

    public final static MarshalManager INSTANCE = new MarshalManager();

    private final static Map<String, Set<Marshal>> marshalsByContentType = new HashMap<>();
    private final static Set<Marshal> marshals = new TreeSet<>(
            COMPARATOR);
    private final static Set<Listener> listeners = Collections
            .newSetFromMap(new IdentityHashMap<Listener, Boolean>());

    private MarshalManager() {
    }

    public static synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public static synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static synchronized void registerMarshal(Marshal marshal) {
        marshals.add(marshal);

        Set<Marshal> s = marshalsByContentType.get(marshal.getContentType());
        if (s == null)
            marshalsByContentType.put(marshal.getContentType(),
                    s = new TreeSet<>(COMPARATOR));
        s.add(marshal);

        for (Listener l : listeners)
            l.onMarshalRegistered(marshal);
    }

    public static synchronized void unregisterMarshal(Marshal marshal) {
        marshals.remove(marshal);

        Set<Marshal> s = marshalsByContentType.get(marshal.getContentType());
        if (s == null)
            return;
        s.remove(marshal);

        for (Listener l : listeners)
            l.onMarshalUnregistered(marshal);
    }

    public static synchronized Set<Marshal> getMarshals() {
        return new LinkedHashSet<>(marshals);
    }

    public static synchronized String marshal(String contentType,
            InputStream source, int probeSize)
            throws IOException {

        if (contentType == null)
            throw new NullPointerException();

        Set<Marshal> s = marshalsByContentType.get(contentType);
        if (s == null)
            return null;
        Iterator<Marshal> iter = s.iterator();
        String retval;
        while (iter.hasNext()) {
            try {
                Marshal m = iter.next();
                retval = m.marshal(source, probeSize);
            } finally {
                source.reset();
            }
            if (retval != null)
                return retval;

        }
        return null;
    }

    public static synchronized String marshal(String contentType, Uri uri)
            throws IOException {

        if (contentType == null)
            throw new NullPointerException();

        Set<Marshal> s = marshalsByContentType.get(contentType);
        if (s == null)
            return null;
        Iterator<Marshal> iter = s.iterator();

        String retval = null;
        while (iter.hasNext()) {
            Marshal m = iter.next();
            retval = m.marshal(uri);

            if (retval != null)
                return retval;

        }
        return null;
    }

    public synchronized static Pair<String, String> marshal(InputStream source,
            int probeSize)
            throws IOException {

        Iterator<Marshal> iter = marshals.iterator();
        Marshal marshal;
        String mime;
        while (iter.hasNext()) {
            try {
                marshal = iter.next();
                mime = marshal.marshal(source, probeSize);
                if (mime != null)
                    return Pair.create(marshal.getContentType(), mime);
            } finally {
                source.reset();
            }
        }
        return null;
    }

    public synchronized static Pair<String, String> marshal(
            final CotEvent event)
            throws IOException {

        Iterator<Marshal> iter = marshals.iterator();
        Marshal marshal;
        String mime;
        while (iter.hasNext()) {
            try {
                marshal = iter.next();
                if (marshal instanceof AbstractCotEventMarshal) {
                    mime = ((AbstractCotEventMarshal) marshal).marshal(event);
                    if (mime != null)
                        return Pair.create(marshal.getContentType(), mime);
                }
            } finally {
            }
        }
        return null;
    }

    public synchronized static Pair<String, String> marshal(Uri uri)
            throws IOException {
        Iterator<Marshal> iter = marshals.iterator();
        Marshal marshal;
        String mime;
        while (iter.hasNext()) {
            marshal = iter.next();
            mime = marshal.marshal(uri);
            if (mime != null)
                return Pair.create(marshal.getContentType(), mime);
        }
        return null;
    }

    /**************************************************************************/

    public interface Listener {
        void onMarshalRegistered(Marshal marshal);

        void onMarshalUnregistered(Marshal marshal);
    }
}
