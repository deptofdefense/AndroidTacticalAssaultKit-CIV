package com.atakmap.map.elevation;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.NotifyCallback;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.Interop;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeElevationSource extends ElevationSource implements Disposable {

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);

            Map<OnContentChangedListener, Pointer> listeners = (Map<OnContentChangedListener, Pointer>)opaque;
            if(listeners != null) {
                synchronized (listeners) {
                    for (Pointer callback : listeners.values())
                        removeOnContentChangedListener(0L, callback);
                    listeners.clear();
                }
            }
        }
    };

    final static Interop<Geometry> Geometry_interop = Interop.findInterop(Geometry.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Object owner;
    Cleaner cleaner;
    Map<OnContentChangedListener, Pointer> listeners;

    private NativeElevationSource(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
        this.listeners = new IdentityHashMap<>();

        cleaner = NativePeerManager.register(this, pointer, rwlock, this.listeners, CLEANER);
    }

    @Override
    public String getName() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getName(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Cursor query(QueryParameters params) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            Pointer cparams = null;
            try {
                if(params != null) {
                    cparams = QueryParameters_create();
                    QueryParameters_adapt(params, cparams.raw);
                }

                Pointer retval = query(this.pointer.raw, (cparams != null) ? cparams.raw : 0L);
                if(retval == null)
                    throw new IllegalStateException();
                return new NativeElevationSourceCursor(retval, this);
            } finally {
                if(cparams != null)
                    QueryParameters_destruct(cparams);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Envelope getBounds() {
        double[] mbb = new double[6];
        this.rwlock.acquireRead();
        try {
            getEnvelope(this.pointer.raw, mbb);
        } finally {
            this.rwlock.releaseRead();
        }
        return new Envelope(mbb[0], mbb[1], mbb[2], mbb[3], mbb[4], mbb[5]);
    }

    @Override
    public void addOnContentChangedListener(OnContentChangedListener l) {
        synchronized(listeners) {
            if(listeners.containsKey(l))
                return;
            this.rwlock.acquireRead();
            try {
                if(this.pointer.raw == 0L)
                    throw new IllegalStateException();
                listeners.put(l, addOnContentChangedListener(this.pointer.raw, new CallbackForwarder(this, l)));
            } finally {
                this.rwlock.releaseRead();
            }
        }
    }

    @Override
    public void removeOnContentChangedListener(OnContentChangedListener l) {
        final Pointer callback;
        synchronized(listeners) {
            callback = this.listeners.remove(l);
            if (callback == null)
                return;

            this.rwlock.acquireRead();
            try {
                removeOnContentChangedListener(this.pointer.raw, callback);
            } finally {
                this.rwlock.releaseRead();
            }
        }
    }

    @Override
    public void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    /*************************************************************************/

    @DontObfuscate
    final static class CallbackForwarder implements NotifyCallback {
        final WeakReference<ElevationSource> sourceRef;
        final ElevationSource.OnContentChangedListener impl;

        CallbackForwarder(ElevationSource source, ElevationSource.OnContentChangedListener impl) {
            this.sourceRef = new WeakReference<>(source);
            this.impl = impl;
        }

        @Override
        public boolean onEvent() {
            final ElevationSource source = this.sourceRef.get();
            if(source == null)
                return false;
            this.impl.onContentChanged(source);
            return true;
        }
    }

    @DontObfuscate
    final static class NativeOnContentChangedListener implements OnContentChangedListener {
        final ReadWriteLock rwlock = new ReadWriteLock();
        Pointer pointer;
        long sourcePtr;

        NativeOnContentChangedListener(Pointer pointer, long sourcePtr) {
            this.pointer = pointer;
            this.sourcePtr = sourcePtr;
        }


        @Override
        public void onContentChanged(ElevationSource source) {
            rwlock.acquireRead();
            try {
                if(this.pointer.raw != 0L)
                    OnContentChangedListener_onContentChanged(this.pointer.raw, this.sourcePtr);
            } finally {
                rwlock.releaseRead();
            }
        }

        @Override
        public void finalize() {
            rwlock.acquireWrite();
            try {
                if(this.pointer.raw != 0L)
                    OnContentChangedListener_destruct(this.pointer);
            } finally {
                rwlock.releaseWrite();
            }
        }
    }

    /*************************************************************************/
    // Interop Implementation

    static long getPointer(ElevationSource object) {
        if(object instanceof NativeElevationSource)
            return ((NativeElevationSource)object).pointer.raw;
        else
            return 0L;
    }
    static native Pointer wrap(ElevationSource object);
    static boolean hasPointer(ElevationSource object) {
        return (object instanceof NativeElevationSource);
    }
    static ElevationSource create(Pointer pointer, Object owner) {
        return new NativeElevationSource(pointer, owner);
    }
    static native boolean hasObject(long pointer);
    static native ElevationSource getObject(long pointer);
    // clone is not supported
    //static Pointer clone(long otherRawPointer);
    static native void destruct(Pointer pointer);

    /*************************************************************************/
    // Native Method Declarations

    static native void getEnvelope(long pointer, double[] envelope);
    static native Pointer query(long pointer, long cparams);
    static native String getName(long pointer);
    static native Pointer addOnContentChangedListener(long pointer, NotifyCallback callback);
    static native void removeOnContentChangedListener(long pointer, Pointer callback);

    static native Pointer QueryParameters_create();
    static native void QueryParameters_destruct(Pointer pointer);
    static native void QueryParameters_set(long pointer,
                                           long spatialFilter,
                                           double targetRes,
                                           double maxRes,
                                           double minRes,
                                           String[] types,
                                           boolean authoritativeSpecified, boolean authoritative,
                                           double minCE,
                                           double minLE,
                                           int[] order,
                                           boolean flagsSpecified, int flags);

    static void QueryParameters_adapt(ElevationSource.QueryParameters mparams, long cparams) {
        int[] order = null;
        if(mparams.order != null) {
            order = new int[mparams.order.size()];
            int idx = 0;
            for(ElevationSource.QueryParameters.Order mo : mparams.order) {
                switch(mo) {
                    case ResolutionDesc:
                        order[idx++] = QueryParameters_Order_getResolutionDesc();
                        break;
                    case ResolutionAsc:
                        order[idx++] = QueryParameters_Order_getResolutionAsc();
                        break;
                    case LEDesc:
                        order[idx++] = QueryParameters_Order_getLEDesc();
                        break;
                    case CEDesc:
                        order[idx++] = QueryParameters_Order_getCEDesc();
                        break;
                    case LEAsc:
                        order[idx++] = QueryParameters_Order_getLEAsc();
                        break;
                    case CEAsc:
                        order[idx++] = QueryParameters_Order_getCEAsc();
                        break;
                    default :
                        throw new IllegalArgumentException();
                }
            }
        }
        String[] types = null;
        if(mparams.types != null) {
            types = new String[mparams.types.size()];
            mparams.types.toArray(types);
        }

        QueryParameters_set(cparams,
                            (mparams.spatialFilter != null) ? Geometry_interop.getPointer(mparams.spatialFilter) : 0L,
                            mparams.targetResolution,
                            mparams.maxResolution,
                            mparams.minResolution,
                            types,
                            (mparams.authoritative != null), (mparams.authoritative != null) ? mparams.authoritative.booleanValue() : false,
                            mparams.minCE,
                            mparams.minLE,
                            order,
                            (mparams.flags != null), (mparams.flags != null) ? mparams.flags.intValue() : 0);
    }

    static native int QueryParameters_Order_getResolutionAsc();
    static native int QueryParameters_Order_getResolutionDesc();
    static native int QueryParameters_Order_getCEAsc();
    static native int QueryParameters_Order_getCEDesc();
    static native int QueryParameters_Order_getLEAsc();
    static native int QueryParameters_Order_getLEDesc();

    static native void OnContentChangedListener_onContentChanged(long cbptr, long sourceptr);
    static native void OnContentChangedListener_destruct(Pointer pointer);
}
